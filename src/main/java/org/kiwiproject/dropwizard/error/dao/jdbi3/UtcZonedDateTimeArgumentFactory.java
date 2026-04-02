package org.kiwiproject.dropwizard.error.dao.jdbi3;

import org.jdbi.v3.core.argument.AbstractArgumentFactory;
import org.jdbi.v3.core.argument.Argument;
import org.jdbi.v3.core.config.ConfigRegistry;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.ZonedDateTime;

/**
 * JDBI {@link org.jdbi.v3.core.argument.ArgumentFactory} for {@link ZonedDateTime}.
 * <p>
 * This factory binds {@link ZonedDateTime} values via
 * {@link java.sql.PreparedStatement#setTimestamp(int, Timestamp)}, converting to a
 * UTC-based {@link Timestamp} before binding. This is necessary because JDBI 3.52.0
 * changed {@link ZonedDateTime} binding to use the JDBC 4.2 {@code setObject} API,
 * which causes databases with plain {@code TIMESTAMP} columns (such as H2 and SQLite)
 * to store values in ISO-8601 format that their JDBC drivers cannot subsequently parse
 * via {@link java.sql.ResultSet#getTimestamp}.
 * <p>
 * This library requires UTC and plain {@code TIMESTAMP} columns, so the pre-3.52.0
 * {@code setTimestamp} behavior is correct for all databases this library supports.
 *
 * @see <a href="https://github.com/jdbi/jdbi/releases/tag/v3.52.0">JDBI 3.52.0 release notes</a>
 */
public class UtcZonedDateTimeArgumentFactory extends AbstractArgumentFactory<ZonedDateTime> {

    public UtcZonedDateTimeArgumentFactory() {
        super(Types.TIMESTAMP);
    }

    @Override
    protected Argument build(ZonedDateTime value, ConfigRegistry config) {
        return (pos, stmt, ctx) ->
                stmt.setTimestamp(pos, Timestamp.from(value.toInstant()));
    }
}
