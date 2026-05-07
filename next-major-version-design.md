# Next Major Version Design Plan

Captures design decisions from discussion. Relates to GitHub discussions
[#147](https://github.com/kiwiproject/dropwizard-application-errors/discussions/147),
[#313](https://github.com/kiwiproject/dropwizard-application-errors/discussions/313), and
[#314](https://github.com/kiwiproject/dropwizard-application-errors/discussions/314).

---

## Overview

Three features to implement together in the next major version:

1. **Severity levels** on `ApplicationError` with per-severity health check behavior
2. **Pinning** to prevent automatic deletion of specific errors
3. **Notes/comments** as a one-to-many child table

Because these require schema changes (new columns and a new table), this is a **breaking
change** requiring a Liquibase migration. There is no backward-compatibility/feature-detection
mechanism — upgrading requires running the migration.

---

## 1. Severity Levels

### Enum

```java
public enum ApplicationErrorSeverity {
    NORMAL,
    HIGH,
    HIGHEST
}
```

### Schema change

New column on `application_errors` with a default of `NORMAL` (see section 5 for
the Liquibase changeset):

```sql
-- illustrative; actual implementation uses Liquibase XML
ALTER TABLE application_errors
    ADD COLUMN severity TEXT NOT NULL DEFAULT 'NORMAL';
```

### Behavioral meaning

| Severity | Health check result | Time window |
|----------|--------------------|------------------------------------|
| NORMAL   | unhealthy WARN     | Configurable (default 15 minutes)  |
| HIGH     | unhealthy CRITICAL | Configurable (default e.g. 4 hours)|
| HIGHEST  | unhealthy CRITICAL | **None** — always unhealthy until explicitly resolved |

HIGHEST errors make the health check permanently unhealthy regardless of age. The only
way to clear them is to resolve the error. This is the key behavioral distinction from HIGH.

### Health check query

The health check runs a single query, calculating per-severity reference timestamps from
config, with HIGHEST using no time filter:

```sql
SELECT severity, COUNT(*) AS cnt
FROM application_errors
WHERE resolved = false
  AND host_name = :host
  AND ip_address = :ip
  AND (
      severity = 'HIGHEST'
   OR (severity = 'HIGH'   AND updated_at >= :highSince)
   OR (severity = 'NORMAL' AND updated_at >= :normalSince)
  )
GROUP BY severity
```

The health check evaluates the returned rows and returns the worst `HealthStatus` found:
- Any HIGHEST or HIGH rows present → `HealthStatus.CRITICAL`
- Only NORMAL rows present → `HealthStatus.WARN`
- No rows → healthy (`HealthStatus.OK`)

### HealthStatus mapping

`metrics-healthchecks-severity` is already a dependency. `RecentErrorsHealthCheck` currently
calls `newUnhealthyResult(message)` which produces a WARN-severity result by default. The
updated check will call an overload that accepts a `HealthStatus` to return CRITICAL when
HIGH or HIGHEST errors are present.

FATAL is intentionally not used for application errors — it is reserved for infrastructure
failures (e.g., the health check query itself throws an exception).

### Per-severity time window configuration

Replace the single `Duration` on `RecentErrorsHealthCheck` with a `SeverityTimeWindows`
config object:

```java
public class SeverityTimeWindows {
    // Defaults shown; all overridable
    private Duration normalWindow  = Duration.ofMinutes(15);
    private Duration highWindow    = Duration.ofHours(4);
    // HIGHEST has no window — field not needed
}
```

The existing single-window constructors on `RecentErrorsHealthCheck` should be kept as
convenience constructors that apply the same window to NORMAL and HIGH (and still treat
HIGHEST as windowless), to ease migration for existing users who don't need per-severity
windows.

### New DAO method

Add a severity-aware count method to `ApplicationErrorDao`:

```java
long countUnresolvedErrorsOnHostBySeverity(
    ZonedDateTime normalSince,
    ZonedDateTime highSince,
    String hostName,
    String ipAddress
);
// Returns a Map<ApplicationErrorSeverity, Long> or a dedicated result record
```

Or return a small result record:

```java
record SeverityErrorCounts(long normalCount, long highCount, long highestCount) {
    boolean hasAny() { return normalCount > 0 || highCount > 0 || highestCount > 0; }
    HealthStatus worstHealthStatus() { ... }
}
```

---

## 2. Pinning

### Purpose

Prevent specific `ApplicationError` records from being deleted by the automatic cleanup job.
Pinned errors accumulate indefinitely and must be manually managed (resolved or unpinned
before they will be eligible for cleanup).

### Schema change

See section 5 for the Liquibase changeset.

```sql
-- illustrative; actual implementation uses Liquibase XML
ALTER TABLE application_errors
    ADD COLUMN pinned BOOLEAN NOT NULL DEFAULT FALSE;
```

### Behavioral changes

- `deleteResolvedErrorsBefore(ZonedDateTime)` gains `AND pinned = false`
- `deleteUnresolvedErrorsBefore(ZonedDateTime)` gains `AND pinned = false`
- New DAO methods: `pin(long id)` and `unpin(long id)`

### ApplicationError model

Add `boolean pinned` with `@Builder.Default` of `false`. Additive — no existing call sites
need to change.

---

## 3. Notes / Comments

### Purpose

Allow operators to attach freeform notes to an `ApplicationError` (e.g., "investigating",
"known issue — ticket #123"). One error has many notes.

### New table

See section 5 for the Liquibase changeset.

```sql
-- illustrative; actual implementation uses Liquibase XML
CREATE TABLE application_error_notes (
    id              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    application_error_id BIGINT NOT NULL
        REFERENCES application_errors(id) ON DELETE CASCADE,
    author_identity TEXT,
    content         TEXT NOT NULL
);
```

`ON DELETE CASCADE` means the cleanup job (and any other deletion path) automatically
removes child notes with no application-level changes needed.

### N+1 avoidance — note count via LEFT JOIN

Rather than a counter cache column (which can drift), the list queries for
`ApplicationError` use a `LEFT JOIN` to compute `note_count` inline:

```sql
SELECT ae.*, COUNT(aen.id) AS note_count
FROM application_errors ae
LEFT JOIN application_error_notes aen ON ae.id = aen.application_error_id
WHERE ...
GROUP BY ae.id        -- valid in Postgres/H2/MySQL when id is PK
ORDER BY ae.updated_at DESC
LIMIT :pageSize OFFSET :offset
```

This means `note_count` is always accurate, always present (0 if no notes), and requires
no sync logic. Add a `int noteCount` field to `ApplicationError`.

### ApplicationErrorNote model

```java
public record ApplicationErrorNote(
    Long id,
    ZonedDateTime createdAt,
    long applicationErrorId,
    String authorIdentity,   // nullable — library has no auth concept
    String content
) {}
```

### New DAO

A separate `ApplicationErrorNoteDao` interface (with JDBI 3 and JDBC implementations,
mirroring the pattern used by `ApplicationErrorDao`):

```java
public interface ApplicationErrorNoteDao {
    ApplicationErrorNote addNote(long errorId, String authorIdentity, String content);
    List<ApplicationErrorNote> getNotesForError(long errorId);
    int deleteNote(long noteId);
    int deleteAllNotesForError(long errorId);  // supplemental; cascade handles cleanup job
}
```

### ErrorContext changes

```java
public interface ErrorContext {
    DataStoreType dataStoreType();
    ApplicationErrorDao errorDao();
    ApplicationErrorNoteDao noteDao();   // new
    Optional<RecentErrorsHealthCheck> recentErrorsHealthCheck();
}
```

`ErrorContextBuilder` gains corresponding `buildWithJdbi3`, `buildWithJdbc`, etc. variants
that wire up the note DAO alongside the error DAO.

---

## 4. ApplicationError Model Summary of New Fields

| Field | Type | Default | Purpose |
|-------|------|---------|---------|
| `severity` | `ApplicationErrorSeverity` | `NORMAL` | Controls health check window and severity |
| `pinned` | `boolean` | `false` | Prevents automatic deletion |
| `noteCount` | `int` | `0` | Denormalized count from LEFT JOIN (not a stored column) |

All additions are backward-compatible at the Java level — existing builders and factory
methods continue to work and produce errors with sensible defaults.

---

## 5. Migration File Structure

Changesets go in `dropwizard-app-errors-migrations.xml` (and the MySQL variant),
following the existing Liquibase conventions. The raw SQL shown elsewhere in this
document is illustrative — the actual changesets use Liquibase XML.

Suggested changesets:

```xml
<changeSet id="0002-add-severity-column" author="dropwizard-application-errors">
    <addColumn tableName="application_errors">
        <column name="severity" type="text" defaultValue="NORMAL">
            <constraints nullable="false"/>
        </column>
    </addColumn>
</changeSet>

<changeSet id="0003-add-pinned-column" author="dropwizard-application-errors">
    <addColumn tableName="application_errors">
        <column name="pinned" type="boolean" defaultValueBoolean="false">
            <constraints nullable="false"/>
        </column>
    </addColumn>
</changeSet>

<changeSet id="0004-add-application-error-notes-table" author="dropwizard-application-errors">
    <createTable tableName="application_error_notes">
        <column name="id" type="bigint" autoIncrement="true">
            <constraints primaryKey="true" nullable="false"/>
        </column>
        <column name="created_at" type="timestamp without time zone" defaultValueComputed="current_timestamp">
            <constraints nullable="false"/>
        </column>
        <column name="application_error_id" type="bigint">
            <constraints nullable="false"
                         foreignKeyName="fk_notes_application_error_id"
                         references="application_errors(id)"
                         deleteCascade="true"/>
        </column>
        <column name="author_identity" type="text"/>
        <column name="content" type="text">
            <constraints nullable="false"/>
        </column>
    </createTable>
</changeSet>
```

A separate MySQL variant file will be needed (as with `0001`) to handle timestamp
type and default differences.

---

## 6. Out of Scope / Deferred

- `LIMIT/OFFSET` → `OFFSET FETCH` SQL standard syntax: blocked by SQLite compatibility.
  Revisit if/when SQLite support is dropped.
- Per-error custom time windows (as a column on `application_errors`): superseded by
  per-severity time windows, which achieve the same operational goal without per-row
  complexity.
- User authentication / identity for notes: the library has no auth concept. `authorIdentity`
  is a freeform nullable string supplied by the caller.
