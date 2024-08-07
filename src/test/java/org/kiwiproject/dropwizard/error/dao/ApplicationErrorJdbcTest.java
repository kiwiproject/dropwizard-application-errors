package org.kiwiproject.dropwizard.error.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.kiwiproject.jdbc.KiwiJdbc.nextOrThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.dropwizard.db.DataSourceFactory;
import org.h2.Driver;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorJdbc.ApplicationErrorJdbcException;
import org.kiwiproject.dropwizard.error.model.DataStoreType;
import org.kiwiproject.jdbc.UncheckedSQLException;
import org.kiwiproject.test.junit.jupiter.ClearBoxTest;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@DisplayName("ApplicationErrorJdbc")
@SuppressWarnings({ "SqlDialectInspection", "SqlNoDataSourceInspection" })
class ApplicationErrorJdbcTest {

    private DataSourceFactory dataSourceFactory;

    @BeforeEach
    void setUp() {
        dataSourceFactory = new DataSourceFactory();
    }

    @Nested
    class CreateInMemoryH2Database {

        private static final String COUNT_QUERY = "select count(*) from application_errors";

        @Test
        void shouldCreateInMemoryDatabase() {
            dataSourceFactory = ApplicationErrorJdbc.createInMemoryH2Database();

            var url = dataSourceFactory.getUrl();
            var user = dataSourceFactory.getUser();
            var password = dataSourceFactory.getPassword();

            assertThat(url).startsWith("jdbc:h2:");
            assertThat(user).isNotNull();
            assertThat(password).isNotNull();

            // Connect to database using JDBI
            var jdbi = Jdbi.create(url, user, password);
            jdbi.useHandle(handle -> {
                var count = handle.createQuery(COUNT_QUERY)
                        .mapTo(Long.TYPE)
                        .one();

                assertThat(count).isZero();
            });

            // Connect to database via plain old JDBC
            try (var conn = DriverManager.getConnection(url, user, password);
                 var stmt = conn.createStatement();
                 var rs = stmt.executeQuery(COUNT_QUERY)) {
                nextOrThrow(rs);
                assertThat(rs.getInt(1)).isZero();
            } catch (SQLException e) {
                throw new UncheckedSQLException(e);
            }
        }
    }

    @Nested
    class GetDatabaseProductNameOrUnknown {

        @ClearBoxTest
        void shouldGetDatabaseProductName() throws SQLException {
            dataSourceFactory = ApplicationErrorJdbc.createInMemoryH2Database();

            var url = dataSourceFactory.getUrl();
            var user = dataSourceFactory.getUser();
            var password = dataSourceFactory.getPassword();

            try (var conn = DriverManager.getConnection(url, user, password)) {
                var productName = ApplicationErrorJdbc.getDatabaseProductNameOrUnknown(conn);
                assertThat(productName).isEqualTo("H2");
            }
        }

        @ClearBoxTest
        void shouldReturnUnknownIfExceptionThrown() throws SQLException {
            dataSourceFactory = ApplicationErrorJdbc.createInMemoryH2Database();

            var url = dataSourceFactory.getUrl();
            var user = dataSourceFactory.getUser();
            var password = dataSourceFactory.getPassword();

            var conn = DriverManager.getConnection(url, user, password);

            // Closing the connection causes an exception when trying to get database metadata
            conn.close();

            assertThat(ApplicationErrorJdbc.getDatabaseProductNameOrUnknown(conn)).isEqualTo("[Unknown Error]");
        }
    }

    @Nested
    class DataStoreTypeOf {

        @ParameterizedTest
        @ValueSource(strings = {
                "jdbc:h2:mem:",
                "jdbc:h2:mem:test_db",
                "jdbc:h2:~/test",
                "jdbc:h2:file:/data/sample",
                "jdbc:h2:file:C:/data/sample.db",
        })
        void shouldReturn_NOT_SHARED_WhenH2Driver_AndEmbeddedConnectionUrl(String url) {
            dataSourceFactory.setDriverClass(org.h2.Driver.class.getName());
            dataSourceFactory.setUrl(url);
            assertThat(ApplicationErrorJdbc.dataStoreTypeOf(dataSourceFactory)).isEqualTo(DataStoreType.NOT_SHARED);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "jdbc:h2:tcp://localhost/~/test",
                "jdbc:h2:tcp://dbserv:8084/~/sample",
                "jdbc:h2:ssl://localhost:8085/~/sample",
                "jdbc:h2:zip:~/db.zip!/test",
                "jdbc:h2:/data/test;AUTO_SERVER=TRUE",  // mixed mode
        })
        void shouldReturn_SHARED_WhenH2Driver_AndServerOrMixedModeConnectionUrl(String url) {
            dataSourceFactory.setDriverClass(org.h2.Driver.class.getName());
            dataSourceFactory.setUrl(url);
            assertThat(ApplicationErrorJdbc.dataStoreTypeOf(dataSourceFactory)).isEqualTo(DataStoreType.SHARED);
        }

        @Test
        void shouldReturn_SHARED_WhenPostgresDriver() {
            dataSourceFactory.setDriverClass(org.postgresql.Driver.class.getName());
            assertThat(ApplicationErrorJdbc.dataStoreTypeOf(dataSourceFactory)).isEqualTo(DataStoreType.SHARED);
        }

        @Test
        void shouldAssume_SHARED_WhenAnyOtherRandomDriver() {
            dataSourceFactory.setDriverClass("some.other.Driver");

            assertThat(ApplicationErrorJdbc.dataStoreTypeOf(dataSourceFactory)).isEqualTo(DataStoreType.SHARED);
        }
    }

    @Nested
    class MigrateDatabase {

        @Test
        void shouldMigrate_H2InMemoryDatabase() throws SQLException {
            try (var conn = DriverManager.getConnection(
                    "jdbc:h2:mem:rt-h2-test;DB_CLOSE_DELAY=-1",
                    "rt-test",
                    "rt-pass")) {

                ApplicationErrorJdbc.migrateDatabase(conn);

                try (var stmt = conn.createStatement()) {
                    var rs = stmt.executeQuery("select * from databasechangelog");
                    nextOrThrow(rs);

                    var filename = rs.getString("filename");
                    assertThat(filename).isEqualTo("dropwizard-app-errors-migrations.xml");
                }
            }
        }

        @Test
        void shouldThrow_WhenSQLErrorOccurs() throws SQLException {
            //noinspection resource
            var conn = mock(Connection.class);
            when(conn.getAutoCommit()).thenThrow(new SQLException("Unable to get autocommit status"));

            assertThatThrownBy(() -> ApplicationErrorJdbc.migrateDatabase(conn))
                    .isExactlyInstanceOf(UncheckedSQLException.class)
                    .hasMessage("JDBC/SQL error while migrating [Unknown Error] database");
        }

        @Test
        void shouldThrow_WhenMigrationErrorOccurs() {
            //noinspection resource
            var conn = mock(Connection.class);

            assertThatThrownBy(() -> ApplicationErrorJdbc.migrateDatabase(conn))
                    .isExactlyInstanceOf(ApplicationErrorJdbcException.class)
                    .hasMessage("Error migrating [Unknown Error] database");
        }
    }

    @Nested
    class IsH2DataStore {

        @Test
        void shouldReturnFalse_WhenGivenNullDataSourceFactory() {
            assertThat(ApplicationErrorJdbc.isH2DataStore(null)).isFalse();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {
                "jdbc:mysql://localhost:3306/test_db",
                "jdbc:postgresql://localhost:5432/sample",
                "jdbc:sqlite:sample.db"
        })
        void shouldReturnFalse_WhenDriverIsH2_ButUrlIsNotH2(String jdbcUrl) {
            dataSourceFactory.setDriverClass(Driver.class.getName());
            dataSourceFactory.setUrl(jdbcUrl);
            assertThat(ApplicationErrorJdbc.isH2DataStore(dataSourceFactory)).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "org.postgresql.Driver",
                "com.mysql.jdbc.Driver",
                "org.acme.db.Driver"
        })
        void shouldReturnFalse_WhenGivenNonH2DataSourceFactory(String value) {
            dataSourceFactory.setDriverClass(value);
            assertThat(ApplicationErrorJdbc.isH2DataStore(dataSourceFactory)).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "jdbc:h2:mem:",
                "jdbc:h2:file:/data/sample",
                "jdbc:h2:tcp://localhost/~/test"
        })
        void shouldReturnTrue_WhenGivenH2DataSourceFactory(String jdbcUrl) {
            dataSourceFactory.setDriverClass(Driver.class.getName());
            dataSourceFactory.setUrl(jdbcUrl);
            assertThat(ApplicationErrorJdbc.isH2DataStore(dataSourceFactory)).isTrue();
        }
    }

    @Nested
    class IsH2EmbeddedDataStore {

        @Test
        void shouldReturnFalse_WhenGivenNullDataSourceFactory() {
            assertThat(ApplicationErrorJdbc.isH2EmbeddedDataStore(null)).isFalse();
        }

        @ParameterizedTest
        @CsvSource(textBlock = """
                org.h2.Driver, jdbc:sqlite:sample.db
                org.acme.db.Driver, jdbc:h2:~/test_db
                '', jdbc:h2:~/test_db
                null, jdbc:h2:~/test_db
                org.h2.Driver, ''
                org.h2.Driver, null
                """, nullValues = "null")
        void shouldReturnFalse_WhenGivenNonH2_DataSourceFactory(String driverClass, String jdbcUrl) {
            dataSourceFactory.setDriverClass(driverClass);
            dataSourceFactory.setUrl(jdbcUrl);
            assertThat(ApplicationErrorJdbc.isH2EmbeddedDataStore(dataSourceFactory)).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "org.postgresql.Driver",
                "com.mysql.jdbc.Driver",
                "org.acme.db.Driver"
        })
        void shouldReturnFalse_WhenGivenNonH2DataSourceFactory(String value) {
            dataSourceFactory.setDriverClass(value);
            assertThat(ApplicationErrorJdbc.isH2EmbeddedDataStore(dataSourceFactory)).isFalse();
        }

        /**
         * We do NOT consider the "Automatic mixed mode" to be embedded, since it
         * allows connections from other processes, JVMs, etc. This mode is enabled
         * via the AUTO_SERVER=TRUE
         */
        @ParameterizedTest
        @CsvSource(textBlock = """
                jdbc:h2:mem:, true
                jdbc:h2:mem:test_db, true
                jdbc:h2:~/test, true
                jdbc:h2:~/test.db, true
                jdbc:h2:file:/data/sample, true
                jdbc:h2:file:/data/var/h2/test.db, true
                jdbc:h2:file:C:/data/sample.db, true
                jdbc:h2:file:~/secure;CIPHER=AES, true
                jdbc:h2:file:~/private;CIPHER=AES;FILE_LOCK=SOCKET, true
                jdbc:h2:file:~/sample;IFEXISTS=TRUE, true
                jdbc:h2:file:~/sample;USER=sa;PASSWORD=123, true
                jdbc:h2:~/test;MODE=MYSQL;DATABASE_TO_LOWER=TRUE, true

                jdbc:h2:tcp://localhost/~/test, false
                jdbc:h2:tcp://dbserv:8084/~/sample, false
                jdbc:h2:tcp://localhost/mem:test, false
                jdbc:h2:tcp://localhost/~/test;AUTO_RECONNECT=TRUE, false
                jdbc:h2:ssl://localhost:8085/~/sample, false
                jdbc:h2:ssl://localhost/~/test;CIPHER=AES, false
                jdbc:h2:zip:~/db.zip!/test, false
                jdbc:h2:/data/test;AUTO_SERVER=TRUE, false
                jdbc:h2:/data/test;AUTO_SERVER=true, false
                jdbc:h2:/data/test;auto_server=true, false
                """)
        void shouldReturnTrue_WhenGivenEmbeddedH2DataSourceFactory(String url, boolean isEmbeddedUrl) {
            dataSourceFactory.setDriverClass(Driver.class.getName());
            dataSourceFactory.setUrl(url);
            assertThat(ApplicationErrorJdbc.isH2EmbeddedDataStore(dataSourceFactory)).isEqualTo(isEmbeddedUrl);
        }
    }

    @Nested
    class ApplicationErrorJdbcExceptionClass {

        @Test
        void shouldAcceptThrowable() {
            var sqlEx = new SQLException("bad SQL");
            var ex = new ApplicationErrorJdbcException(sqlEx);

            assertAll(
                    () -> assertThat(ex.getMessage()).contains("bad SQL"),
                    () -> assertThat(ex.getCause()).isSameAs(sqlEx)
            );
        }
    }
}
