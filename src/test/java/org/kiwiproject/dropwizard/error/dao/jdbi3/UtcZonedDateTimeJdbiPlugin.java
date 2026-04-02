package org.kiwiproject.dropwizard.error.dao.jdbi3;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.spi.JdbiPlugin;

/**
 * A JDBI {@link JdbiPlugin} that registers {@link UtcZonedDateTimeArgumentFactory}.
 * <p>
 * This is used in tests to ensure {@link java.time.ZonedDateTime} values are bound
 * correctly via {@code setTimestamp()} rather than the JDBC 4.2 {@code setObject()}
 * API introduced in JDBI 3.52.0, which causes plain {@code TIMESTAMP} columns in
 * SQLite and MySQL to store values in an ISO-8601 format that their drivers cannot
 * subsequently parse via {@code ResultSet.getTimestamp()}.
 * <p>
 * This plugin is necessary because {@link org.kiwiproject.test.junit.jupiter.Jdbi3DaoExtension}
 * does not currently support registering argument factories directly via its builder.
 * See <a href="https://github.com/kiwiproject/kiwi-test/issues/638">kiwi-test issue #638</a>.
 *
 * @see UtcZonedDateTimeArgumentFactory
 */
class UtcZonedDateTimeJdbiPlugin implements JdbiPlugin {
    @Override
    public void customizeJdbi(Jdbi jdbi) {
        jdbi.registerArgument(new UtcZonedDateTimeArgumentFactory());
    }
}
