package org.kiwiproject.dropwizard.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorDao;
import org.kiwiproject.dropwizard.error.test.junit.jupiter.ApplicationErrorExtension;

@DisplayName("ApplicationErrorThrower")
@Slf4j
@ExtendWith({ApplicationErrorExtension.class, SoftAssertionsExtension.class})
class ApplicationErrorThrowerTest {

    private static final String MESSAGE_TEMPLATE = "A parameterized message with answer {} and random word {}";

    private ApplicationErrorDao errorDao;
    private ApplicationErrorThrower errorThrower;

    @BeforeEach
    void setUp() {
        errorDao = mock(ApplicationErrorDao.class);
        errorThrower = ApplicationErrorThrower.builder()
                .errorDao(errorDao)
                .logger(LOG)
                .build();
    }

    @Nested
    class AllArgsConstructor {

        @Test
        void shouldThrow_WhenGivenNullErrorDao() {
            //noinspection DataFlowIssue
            assertThrowsNullPointerException("errorDao", () -> new ApplicationErrorThrower(null, LOG));
        }

        @Test
        void shouldThrow_WhenGivenNullLogger() {
            //noinspection DataFlowIssue
            assertThrowsNullPointerException("logger", () -> new ApplicationErrorThrower(errorDao, null));
        }
    }

    @Nested
    class Builder {

        @Test
        void shouldThrow_WhenGivenNullErrorDao() {
            //noinspection DataFlowIssue
            assertThrowsNullPointerException("errorDao",
                    () -> ApplicationErrorThrower.builder().errorDao(null).logger(LOG).build());
        }

        @Test
        void shouldThrow_WhenGivenNullLogger() {
            //noinspection DataFlowIssue
            assertThrowsNullPointerException("logger",
                    () -> ApplicationErrorThrower.builder().errorDao(errorDao).logger(null).build());
        }
    }

    private static void assertThrowsNullPointerException(String propertyName,
                                                         ThrowingCallable throwingCallable) {
        assertThatNullPointerException()
                .isThrownBy(throwingCallable)
                .withMessageStartingWith(propertyName);
    }

    @Nested
    class LogAndSaveApplicationError {

        @Nested
        class WithMessage {

            @Test
            void shouldSaveApplicationError(SoftAssertions softly) {
                var id = 42L;
                when(errorDao.insertOrIncrementCount(any())).thenReturn(id);

                var optionalId = errorThrower.logAndSaveApplicationError("A simple message");

                softly.assertThat(optionalId).hasValue(42);
                verify(errorDao).insertOrIncrementCount(argThat(error -> {
                    softly.assertThat(error.getDescription()).isEqualTo("A simple message");
                    softly.assertThat(error.getExceptionType()).isNull();
                    return true;
                }));
            }

            @Test
            void shouldNotThrowException_IfExceptionThrownSavingError() {
                when(errorDao.insertOrIncrementCount(any())).thenThrow(new RuntimeException("persistence error..."));

                assertThatCode(() -> {
                    var optionalId = errorThrower.logAndSaveApplicationError("A simple message");
                    assertThat(optionalId).isEmpty();
                }).doesNotThrowAnyException();
            }
        }

        @Nested
        class WithParameterizedMessage {

            @Test
            void shouldSaveApplicationError(SoftAssertions softly) {
                var id = 84L;
                when(errorDao.insertOrIncrementCount(any())).thenReturn(id);

                var optionalId = errorThrower.logAndSaveApplicationError(MESSAGE_TEMPLATE, 42, "insouciance");

                softly.assertThat(optionalId).hasValue(id);
                verify(errorDao).insertOrIncrementCount(argThat(error -> {
                    softly.assertThat(error.getDescription()).isEqualTo("A parameterized message with answer 42 and random word insouciance");
                    softly.assertThat(error.getExceptionType()).isNull();
                    return true;
                }));
            }

            @Test
            void shouldNotThrowException_IfExceptionThrownSavingError() {
                when(errorDao.insertOrIncrementCount(any())).thenThrow(new RuntimeException("persistence error..."));

                assertThatCode(() -> {
                    var optionalId = errorThrower.logAndSaveApplicationError(MESSAGE_TEMPLATE, 42, "sapient");
                    assertThat(optionalId).isEmpty();
                }).doesNotThrowAnyException();
            }
        }

        @Nested
        class WithThrowableAndMessage {

            @Test
            void shouldPermitNullThrowable(SoftAssertions softly) {
                var id = 142L;
                when(errorDao.insertOrIncrementCount(any())).thenReturn(id);

                var optionalId = errorThrower.logAndSaveApplicationError(null, "A simple message");

                softly.assertThat(optionalId).hasValue(id);
                verify(errorDao).insertOrIncrementCount(argThat(error -> {
                    softly.assertThat(error.getDescription()).isEqualTo("A simple message");
                    softly.assertThat(error.getExceptionType()).isNull();
                    softly.assertThat(error.getExceptionMessage()).isNull();
                    return true;
                }));
            }

            @Test
            void shouldSaveApplicationError(SoftAssertions softly) {
                var id = 168L;
                when(errorDao.insertOrIncrementCount(any())).thenReturn(id);
                var throwable = new RuntimeException("oopsy");

                var optionalId = errorThrower.logAndSaveApplicationError(throwable, "A simple message");

                softly.assertThat(optionalId).hasValue(id);
                verify(errorDao).insertOrIncrementCount(argThat(error -> {
                    softly.assertThat(error.getDescription()).isEqualTo("A simple message");
                    softly.assertThat(error.getExceptionType()).isEqualTo(RuntimeException.class.getName());
                    softly.assertThat(error.getExceptionMessage()).isEqualTo("oopsy");
                    return true;
                }));
            }

            @Test
            void shouldNotThrowException_IfExceptionThrownSavingError() {
                when(errorDao.insertOrIncrementCount(any())).thenThrow(new RuntimeException("persistence error..."));

                assertThatCode(() -> {
                    var throwable = new RuntimeException("oopsy");
                    var optionalId = errorThrower.logAndSaveApplicationError(throwable, "A simple message");
                    assertThat(optionalId).isEmpty();
                }).doesNotThrowAnyException();
            }
        }

        @Nested
        class WithThrowableAndParameterizedMessage {

            @Test
            void shouldPermitNullThrowable(SoftAssertions softly) {
                var id = 442L;
                when(errorDao.insertOrIncrementCount(any())).thenReturn(id);

                var optionalId = errorThrower.logAndSaveApplicationError((Throwable) null, MESSAGE_TEMPLATE, 336, "anodyne");

                softly.assertThat(optionalId).hasValue(id);
                verify(errorDao).insertOrIncrementCount(argThat(error -> {
                    softly.assertThat(error.getDescription()).isEqualTo("A parameterized message with answer 336 and random word anodyne");
                    softly.assertThat(error.getExceptionType()).isNull();
                    softly.assertThat(error.getExceptionMessage()).isNull();
                    return true;
                }));
            }

            @Test
            void shouldSaveApplicationError(SoftAssertions softly) {
                var id = 336L;
                when(errorDao.insertOrIncrementCount(any())).thenReturn(id);
                var throwable = new RuntimeException("follow the while rabbit...");

                var optionalId = errorThrower.logAndSaveApplicationError(throwable, MESSAGE_TEMPLATE, 84, "desultory");

                softly.assertThat(optionalId).hasValue(id);
                verify(errorDao).insertOrIncrementCount(argThat(error -> {
                    softly.assertThat(error.getDescription()).isEqualTo("A parameterized message with answer 84 and random word desultory");
                    softly.assertThat(error.getExceptionType()).isEqualTo(RuntimeException.class.getName());
                    softly.assertThat(error.getExceptionMessage()).isEqualTo("follow the while rabbit...");
                    return true;
                }));
            }

            @Test
            void shouldNotThrowException_IfExceptionThrownSavingError() {
                when(errorDao.insertOrIncrementCount(any())).thenThrow(new RuntimeException("persistence error..."));

                assertThatCode(() -> {
                    var throwable = new RuntimeException("follow the while rabbit...");
                    var optionalId = errorThrower.logAndSaveApplicationError(throwable, MESSAGE_TEMPLATE, 84, "ersatz");
                    assertThat(optionalId).isEmpty();
                }).doesNotThrowAnyException();
            }
        }
    }

    // Words used in this test, for your own edification...

    // edification:
    // noun
    // the instruction or improvement of a person morally or intellectually

    // insouciance:
    // noun
    // casual lack of concern; indifference

    // sapient:
    // adjective
    // 1. (formal) wise, or attempting to appear wise
    // 2. relating to the human species (Homo sapiens)
    //
    // noun
    // a human of the species Homo sapiens

    // desultory:
    // adjective
    // lacking a plan, purpose, or enthusiasm
    // - (of conversation or speech) going constantly from one subject to another in a halfhearted way;
    // - occurring randomly or occasionally

    // ersatz:
    // adjective
    // (of a product) made or used as a substitute, typically an inferior one, for something else
    // - not real or genuine

    // anodyne:
    // adjective
    // 1. serving to alleviate pain
    // 2. not likely to offend or arouse tensions, innocuous
    //
    // noun
    // 1. something that soothes, calms, or comforts
    // 2. a drug that allays pain
}
