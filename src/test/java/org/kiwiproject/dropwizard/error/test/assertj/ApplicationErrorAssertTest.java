package org.kiwiproject.dropwizard.error.test.assertj;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kiwiproject.dropwizard.error.test.assertj.ApplicationErrorAssert.assertThatApplicationError;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kiwiproject.dropwizard.error.model.ApplicationError;
import org.kiwiproject.dropwizard.error.model.ApplicationError.Resolved;
import org.kiwiproject.dropwizard.error.model.PersistentHostInformation;
import org.kiwiproject.dropwizard.error.test.junit.jupiter.ApplicationErrorExtension;
import org.kiwiproject.dropwizard.error.test.junit.jupiter.ApplicationErrorExtension.HostInfo;

import java.io.IOException;

@DisplayName("ApplicationErrorAssert")
@ExtendWith(ApplicationErrorExtension.class)
// assertThatApplicationError is a trivial factory (no-throw), so S5778 does not apply
@SuppressWarnings("java:S5778")
class ApplicationErrorAssertTest {

    private String hostName;
    private String ipAddress;
    private int port;

    @BeforeEach
    void setUp(@HostInfo PersistentHostInformation hostInfo) {
        this.hostName = hostInfo.getHostName();
        this.ipAddress = hostInfo.getIpAddress();
        this.port = hostInfo.getPort();
    }

    @Nested
    class AssertThatApplicationErrorFactory {

        @Test
        void shouldReturnAssertInstance() {
            var error = ApplicationError.newUnresolvedError("test");
            assertThat(assertThatApplicationError(error)).isInstanceOf(ApplicationErrorAssert.class);
        }

        @Test
        void shouldFailWhenActualIsNull() {
            assertThatThrownBy(() -> assertThatApplicationError(null).hasDescription("anything"))
                    .isInstanceOf(AssertionError.class);
        }
    }

    @Nested
    class HasDescription {

        @Test
        void shouldPass_WhenDescriptionMatches() {
            var error = ApplicationError.newUnresolvedError("expected description");

            assertThatCode(() -> assertThatApplicationError(error).hasDescription("expected description"))
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldFail_WhenDescriptionDoesNotMatch() {
            var error = ApplicationError.newUnresolvedError("actual description");

            assertThatThrownBy(() -> assertThatApplicationError(error).hasDescription("wrong description"))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("wrong description")
                    .hasMessageContaining("actual description");
        }
    }

    @Nested
    class HasDescriptionContaining {

        @Test
        void shouldPass_WhenDescriptionContainsFragment() {
            var error = ApplicationError.newUnresolvedError("an I/O error occurred: no such file");

            assertThatCode(() -> assertThatApplicationError(error).hasDescriptionContaining("I/O error"))
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldFail_WhenDescriptionDoesNotContainFragment() {
            var error = ApplicationError.newUnresolvedError("some error");

            assertThatThrownBy(() -> assertThatApplicationError(error).hasDescriptionContaining("missing fragment"))
                    .isInstanceOf(AssertionError.class);
        }
    }

    @Nested
    class HasExceptionType {

        @Test
        void shouldPass_WhenExceptionTypeMatchesClass() {
            var error = ApplicationError.newUnresolvedError("error with exception", new IOException("io problem"));

            assertThatCode(() -> assertThatApplicationError(error).hasExceptionType(IOException.class))
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldPass_WhenExceptionTypeMatchesFqcn() {
            var error = ApplicationError.newUnresolvedError("error with exception", new IOException("io problem"));

            assertThatCode(() -> assertThatApplicationError(error).hasExceptionType(IOException.class.getName()))
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldFail_WhenExceptionTypeDoesNotMatch() {
            var error = ApplicationError.newUnresolvedError("error with exception", new IOException("io problem"));

            assertThatThrownBy(() -> assertThatApplicationError(error).hasExceptionType(RuntimeException.class))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining(RuntimeException.class.getName())
                    .hasMessageContaining(IOException.class.getName());
        }

        @Test
        void shouldFail_WhenExceptionTypeFqcnDoesNotMatch() {
            var error = ApplicationError.newUnresolvedError("error with exception", new IOException("io problem"));

            assertThatThrownBy(() -> assertThatApplicationError(error).hasExceptionType(RuntimeException.class.getName()))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining(RuntimeException.class.getName())
                    .hasMessageContaining(IOException.class.getName());
        }
    }

    @Nested
    class HasNoExceptionType {

        @Test
        void shouldPass_WhenExceptionTypeIsNull() {
            var error = ApplicationError.newUnresolvedError("error without exception");

            assertThatCode(() -> assertThatApplicationError(error).hasNoExceptionType())
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldFail_WhenExceptionTypeIsNotNull() {
            var error = ApplicationError.newUnresolvedError("error with exception", new IOException("io problem"));

            assertThatThrownBy(() -> assertThatApplicationError(error).hasNoExceptionType())
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining(IOException.class.getName());
        }
    }

    @Nested
    class HasExceptionMessage {

        @Test
        void shouldPass_WhenExceptionMessageMatches() {
            var error = ApplicationError.newUnresolvedError("some error", new IOException("io problem"));

            assertThatCode(() -> assertThatApplicationError(error).hasExceptionMessage("io problem"))
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldFail_WhenExceptionMessageDoesNotMatch() {
            var error = ApplicationError.newUnresolvedError("some error", new IOException("io problem"));

            assertThatThrownBy(() -> assertThatApplicationError(error).hasExceptionMessage("wrong message"))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("wrong message")
                    .hasMessageContaining("io problem");
        }
    }

    @Nested
    class HasExceptionMessageContaining {

        @Test
        void shouldPass_WhenExceptionMessageContainsFragment() {
            var error = ApplicationError.newUnresolvedError("some error", new IOException("connection reset by peer"));

            assertThatCode(() -> assertThatApplicationError(error).hasExceptionMessageContaining("connection reset"))
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldFail_WhenExceptionMessageDoesNotContainFragment() {
            var error = ApplicationError.newUnresolvedError("some error", new IOException("connection reset by peer"));

            assertThatThrownBy(() -> assertThatApplicationError(error).hasExceptionMessageContaining("missing fragment"))
                    .isInstanceOf(AssertionError.class);
        }
    }

    @Nested
    class HasNoExceptionMessage {

        @Test
        void shouldPass_WhenExceptionMessageIsNull() {
            var error = ApplicationError.newUnresolvedError("error without exception");

            assertThatCode(() -> assertThatApplicationError(error).hasNoExceptionMessage())
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldFail_WhenExceptionMessageIsNotNull() {
            var error = ApplicationError.newUnresolvedError("some error", new IOException("io problem"));

            assertThatThrownBy(() -> assertThatApplicationError(error).hasNoExceptionMessage())
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("io problem");
        }
    }

    @Nested
    class HasExceptionCauseType {

        @Test
        void shouldPass_WhenExceptionCauseTypeMatchesClass() {
            var cause = new IOException("original cause");
            var error = ApplicationError.newUnresolvedError("some error", new RuntimeException("wrapper", cause));

            assertThatCode(() -> assertThatApplicationError(error).hasExceptionCauseType(IOException.class))
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldPass_WhenExceptionCauseTypeMatchesFqcn() {
            var cause = new IOException("original cause");
            var error = ApplicationError.newUnresolvedError("some error", new RuntimeException("wrapper", cause));

            assertThatCode(() -> assertThatApplicationError(error).hasExceptionCauseType(IOException.class.getName()))
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldFail_WhenExceptionCauseTypeDoesNotMatch() {
            var cause = new IOException("original cause");
            var error = ApplicationError.newUnresolvedError("some error", new RuntimeException("wrapper", cause));

            assertThatThrownBy(() -> assertThatApplicationError(error).hasExceptionCauseType(IllegalArgumentException.class))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining(IllegalArgumentException.class.getName())
                    .hasMessageContaining(IOException.class.getName());
        }

        @Test
        void shouldFail_WhenExceptionCauseTypeFqcnDoesNotMatch() {
            var cause = new IOException("original cause");
            var error = ApplicationError.newUnresolvedError("some error", new RuntimeException("wrapper", cause));

            assertThatThrownBy(() -> assertThatApplicationError(error).hasExceptionCauseType(IllegalArgumentException.class.getName()))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining(IllegalArgumentException.class.getName())
                    .hasMessageContaining(IOException.class.getName());
        }
    }

    @Nested
    class HasNoExceptionCauseType {

        @Test
        void shouldPass_WhenExceptionCauseTypeIsNull() {
            var error = ApplicationError.newUnresolvedError("error without exception");

            assertThatCode(() -> assertThatApplicationError(error).hasNoExceptionCauseType())
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldFail_WhenExceptionCauseTypeIsNotNull() {
            var cause = new IOException("original cause");
            var error = ApplicationError.newUnresolvedError("some error", new RuntimeException("wrapper", cause));

            assertThatThrownBy(() -> assertThatApplicationError(error).hasNoExceptionCauseType())
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining(IOException.class.getName());
        }
    }

    @Nested
    class HasExceptionCauseMessage {

        @Test
        void shouldPass_WhenExceptionCauseMessageMatches() {
            var cause = new IOException("original cause message");
            var error = ApplicationError.newUnresolvedError("some error", new RuntimeException("wrapper", cause));

            assertThatCode(() -> assertThatApplicationError(error).hasExceptionCauseMessage("original cause message"))
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldFail_WhenExceptionCauseMessageDoesNotMatch() {
            var cause = new IOException("original cause message");
            var error = ApplicationError.newUnresolvedError("some error", new RuntimeException("wrapper", cause));

            assertThatThrownBy(() -> assertThatApplicationError(error).hasExceptionCauseMessage("wrong message"))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("wrong message")
                    .hasMessageContaining("original cause message");
        }
    }

    @Nested
    class HasExceptionCauseMessageContaining {

        @Test
        void shouldPass_WhenExceptionCauseMessageContainsFragment() {
            var cause = new IOException("file /data/foo.txt not found");
            var error = ApplicationError.newUnresolvedError("some error", new RuntimeException("wrapper", cause));

            assertThatCode(() -> assertThatApplicationError(error).hasExceptionCauseMessageContaining("/data/foo.txt"))
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldFail_WhenExceptionCauseMessageDoesNotContainFragment() {
            var cause = new IOException("file /data/foo.txt not found");
            var error = ApplicationError.newUnresolvedError("some error", new RuntimeException("wrapper", cause));

            assertThatThrownBy(() -> assertThatApplicationError(error).hasExceptionCauseMessageContaining("missing fragment"))
                    .isInstanceOf(AssertionError.class);
        }
    }

    @Nested
    class HasNoExceptionCauseMessage {

        @Test
        void shouldPass_WhenExceptionCauseMessageIsNull() {
            var error = ApplicationError.newUnresolvedError("error without exception");

            assertThatCode(() -> assertThatApplicationError(error).hasNoExceptionCauseMessage())
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldFail_WhenExceptionCauseMessageIsNotNull() {
            var cause = new IOException("original cause message");
            var error = ApplicationError.newUnresolvedError("some error", new RuntimeException("wrapper", cause));

            assertThatThrownBy(() -> assertThatApplicationError(error).hasNoExceptionCauseMessage())
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("original cause message");
        }
    }

    @Nested
    class HasStackTrace {

        @Test
        void shouldPass_WhenStackTraceMatches() {
            var exception = new IOException("io error");
            var error = ApplicationError.newUnresolvedError("some error", exception);
            var expectedStackTrace = error.getStackTrace();

            assertThatCode(() -> assertThatApplicationError(error).hasStackTrace(expectedStackTrace))
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldFail_WhenStackTraceDoesNotMatch() {
            var error = ApplicationError.newUnresolvedError("some error", new IOException("io error"));

            assertThatThrownBy(() -> assertThatApplicationError(error).hasStackTrace("wrong stack trace"))
                    .isInstanceOf(AssertionError.class);
        }
    }

    @Nested
    class HasStackTraceContaining {

        @Test
        void shouldPass_WhenStackTraceContainsFragment() {
            var error = ApplicationError.newUnresolvedError("some error", new IOException("io error"));

            assertThatCode(() ->
                    assertThatApplicationError(error).hasStackTraceContaining("java.io.IOException"))
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldFail_WhenStackTraceDoesNotContainFragment() {
            var error = ApplicationError.newUnresolvedError("some error", new IOException("io error"));

            assertThatThrownBy(() -> assertThatApplicationError(error).hasStackTraceContaining("someNonExistentMethod"))
                    .isInstanceOf(AssertionError.class);
        }
    }

    @Nested
    class HasNoStackTrace {

        @Test
        void shouldPass_WhenStackTraceIsNull() {
            var error = ApplicationError.newUnresolvedError("error without exception");

            assertThatCode(() -> assertThatApplicationError(error).hasNoStackTrace())
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldFail_WhenStackTraceIsNotNull() {
            var error = ApplicationError.newUnresolvedError("some error", new IOException("io error"));

            assertThatThrownBy(() -> assertThatApplicationError(error).hasNoStackTrace())
                    .isInstanceOf(AssertionError.class);
        }
    }

    @Nested
    class HasNumTimesOccurred {

        @Test
        void shouldPass_WhenCountMatches() {
            var error = ApplicationError.newUnresolvedError("some error");

            assertThatCode(() -> assertThatApplicationError(error).hasNumTimesOccurred(1))
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldFail_WhenCountDoesNotMatch() {
            var error = ApplicationError.newUnresolvedError("some error");

            assertThatThrownBy(() -> assertThatApplicationError(error).hasNumTimesOccurred(5))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("5")
                    .hasMessageContaining("1");
        }
    }

    @Nested
    class IsResolved {

        @Test
        void shouldPass_WhenErrorIsResolved() {
            var error = ApplicationError.newError("some error", Resolved.YES, hostName, ipAddress, port, null);

            assertThatCode(() -> assertThatApplicationError(error).isResolved())
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldFail_WhenErrorIsNotResolved() {
            var error = ApplicationError.newUnresolvedError("some error");

            assertThatThrownBy(() -> assertThatApplicationError(error).isResolved())
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("resolved");
        }
    }

    @Nested
    class IsNotResolved {

        @Test
        void shouldPass_WhenErrorIsNotResolved() {
            var error = ApplicationError.newUnresolvedError("some error");

            assertThatCode(() -> assertThatApplicationError(error).isNotResolved())
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldFail_WhenErrorIsResolved() {
            var error = ApplicationError.newError("some error", Resolved.YES, hostName, ipAddress, port, null);

            assertThatThrownBy(() -> assertThatApplicationError(error).isNotResolved())
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("resolved");
        }
    }

    @Nested
    class HasHostName {

        @Test
        void shouldPass_WhenHostNameMatches() {
            var error = ApplicationError.newUnresolvedError("some error");

            assertThatCode(() -> assertThatApplicationError(error).hasHostName(hostName))
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldFail_WhenHostNameDoesNotMatch() {
            var error = ApplicationError.newUnresolvedError("some error");

            assertThatThrownBy(() -> assertThatApplicationError(error).hasHostName("wrong-host"))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("wrong-host")
                    .hasMessageContaining(hostName);
        }
    }

    @Nested
    class HasIpAddress {

        @Test
        void shouldPass_WhenIpAddressMatches() {
            var error = ApplicationError.newUnresolvedError("some error");

            assertThatCode(() -> assertThatApplicationError(error).hasIpAddress(ipAddress))
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldFail_WhenIpAddressDoesNotMatch() {
            var error = ApplicationError.newUnresolvedError("some error");

            assertThatThrownBy(() -> assertThatApplicationError(error).hasIpAddress("1.2.3.4"))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("1.2.3.4")
                    .hasMessageContaining(ipAddress);
        }
    }

    @Nested
    class HasPort {

        @Test
        void shouldPass_WhenPortMatches() {
            var error = ApplicationError.newUnresolvedError("some error");

            assertThatCode(() -> assertThatApplicationError(error).hasPort(port))
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldFail_WhenPortDoesNotMatch() {
            var error = ApplicationError.newUnresolvedError("some error");

            assertThatThrownBy(() -> assertThatApplicationError(error).hasPort(9999))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("9999")
                    .hasMessageContaining(String.valueOf(port));
        }
    }

    @Nested
    class HasId {

        @Test
        void shouldPass_WhenIdMatches() {
            var error = ApplicationError.newUnresolvedError("some error").withId(42L);

            assertThatCode(() -> assertThatApplicationError(error).hasId(42L))
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldFail_WhenIdDoesNotMatch() {
            var error = ApplicationError.newUnresolvedError("some error").withId(42L);

            assertThatThrownBy(() -> assertThatApplicationError(error).hasId(99L))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("99")
                    .hasMessageContaining("42");
        }
    }

    @Nested
    class HasNoId {

        @Test
        void shouldPass_WhenIdIsNull() {
            var error = ApplicationError.newUnresolvedError("some error");

            assertThatCode(() -> assertThatApplicationError(error).hasNoId())
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldFail_WhenIdIsNotNull() {
            var error = ApplicationError.newUnresolvedError("some error").withId(42L);

            assertThatThrownBy(() -> assertThatApplicationError(error).hasNoId())
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("42");
        }
    }

    @Nested
    class FluentChaining {

        @Test
        void shouldSupportChainingMultipleAssertions() {
            var cause = new IOException("file not found");
            var error = ApplicationError.newUnresolvedError("an I/O error occurred", new RuntimeException("wrapper", cause));

            assertThatApplicationError(error)
                    .hasDescription("an I/O error occurred")
                    .hasExceptionType(RuntimeException.class)
                    .hasExceptionMessage("wrapper")
                    .hasExceptionCauseType(IOException.class)
                    .hasExceptionCauseMessage("file not found")
                    .hasStackTraceContaining("java.io.IOException")
                    .hasNumTimesOccurred(1)
                    .isNotResolved()
                    .hasHostName(hostName)
                    .hasIpAddress(ipAddress)
                    .hasPort(port)
                    .hasNoId();
        }
    }
}
