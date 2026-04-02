package org.kiwiproject.dropwizard.error.dao.jdbi3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.jdbi.v3.core.config.ConfigRegistry;
import org.jdbi.v3.core.statement.StatementContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@DisplayName("UtcZonedDateTimeArgumentFactory")
class UtcZonedDateTimeArgumentFactoryTest {

    private UtcZonedDateTimeArgumentFactory factory;
    private ConfigRegistry config;
    private StatementContext ctx;

    @BeforeEach
    void setUp() {
        factory = new UtcZonedDateTimeArgumentFactory();
        config = mock(ConfigRegistry.class);
        ctx = mock(StatementContext.class);
    }

    @Nested
    class Build {

        @Test
        void shouldReturnEmptyOptionalForNonZonedDateTimeType() {
            var result = factory.build(String.class, "not-a-zdt", config);
            assertThat(result).isEmpty();
        }

        @Test
        void shouldBindAsTimestampUsingUtcInstant() throws Exception {
            var zdt = ZonedDateTime.of(2026, 4, 2, 6, 38, 30, 0, ZoneOffset.UTC);
            var argument = factory.build(ZonedDateTime.class, zdt, config).orElseThrow();

            var stmt = mock(PreparedStatement.class);
            argument.apply(1, stmt, ctx);

            var expectedTimestamp = Timestamp.from(zdt.toInstant());
            verify(stmt).setTimestamp(1, expectedTimestamp);
        }

        @Test
        void shouldConvertNonUtcZonedDateTimeToUtcInstant() throws Exception {
            var zdt = ZonedDateTime.of(2026, 4, 2, 8, 38, 30, 0, ZoneId.of("Europe/Paris"));
            var argument = factory.build(ZonedDateTime.class, zdt, config).orElseThrow();

            var stmt = mock(PreparedStatement.class);
            argument.apply(1, stmt, ctx);

            // Paris is UTC+2, so 08:38:30 Europe/Paris = 06:38:30 UTC
            var expectedUtcTimestamp = Timestamp.from(
                    ZonedDateTime.of(2026, 4, 2, 6, 38, 30, 0, ZoneOffset.UTC).toInstant());
            verify(stmt).setTimestamp(1, expectedUtcTimestamp);
        }

        @Test
        void shouldBindAtCorrectParameterPosition() throws Exception {
            var zdt = ZonedDateTime.now(ZoneOffset.UTC);
            var argument = factory.build(ZonedDateTime.class, zdt, config).orElseThrow();

            var stmt = mock(PreparedStatement.class);
            argument.apply(5, stmt, ctx);

            verify(stmt).setTimestamp(5, Timestamp.from(zdt.toInstant()));
        }
    }

    @Nested
    class SqlTypeDeclaration {

        @Test
        void shouldBindNullAsTimestampType() throws Exception {
            var nullArgument = factory.build(ZonedDateTime.class, null, config).orElseThrow();

            var stmt = mock(PreparedStatement.class);
            nullArgument.apply(1, stmt, ctx);

            verify(stmt).setNull(1, Types.TIMESTAMP);
        }
    }
}
