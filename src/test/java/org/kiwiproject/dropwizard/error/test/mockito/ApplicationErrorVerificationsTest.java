package org.kiwiproject.dropwizard.error.test.mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kiwiproject.collect.KiwiLists.first;
import static org.kiwiproject.collect.KiwiLists.second;
import static org.mockito.Mockito.mock;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorDao;
import org.kiwiproject.dropwizard.error.model.ApplicationError;
import org.kiwiproject.dropwizard.error.test.junit.jupiter.ApplicationErrorExtension;
import org.mockito.exceptions.verification.NoInteractionsWanted;
import org.mockito.exceptions.verification.TooManyActualInvocations;
import org.mockito.exceptions.verification.WantedButNotInvoked;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.stream.IntStream;

@DisplayName("ApplicationErrorVerifications")
@ExtendWith(ApplicationErrorExtension.class)
@Slf4j
class ApplicationErrorVerificationsTest {

    /**
     * Sample class that records application errors when given specific input.
     */
    @AllArgsConstructor
    private static class BusinessLogicService {

        private final ApplicationErrorDao errorDao;

        void performSomeProcessingThatCanFail(String input) {
            switch (input) {
                case "foo": // one error with exception
                    var fooError = ApplicationError.newUnresolvedError("Processing failed for input: foo");
                    errorDao.insertOrIncrementCount(fooError);
                    logProcessingComplete(Level.WARN, input);
                    break;

                case "bar": // one error with exception
                    var cause = new IOException("I/O error");
                    var ex = new UncheckedIOException(cause);
                    var barError = ApplicationError.newUnresolvedError("Processing threw error with cause for input: bar", ex);
                    errorDao.insertOrIncrementCount(barError);
                    logProcessingComplete(Level.WARN, input);
                    break;

                case "baz": // one error plus a second call on errorDao
                    var bazError = ApplicationError.newUnresolvedError("Processing failed for input: baz");
                    errorDao.insertOrIncrementCount(bazError);
                    errorDao.countAllErrors();
                    logProcessingComplete(Level.WARN, input);
                    break;

                default:
                    logProcessingComplete(Level.INFO, input);
                    break;
            }
        }

        private enum Level {INFO, WARN}

        static void logProcessingComplete(Level level, String input) {
            if (level == Level.WARN) {
                LOG.warn("Processing completed with errors for input: {}", input);
            } else if (level == Level.INFO) {
                LOG.info("Processing completed successfully for input: {}", input);
            }
        }
    }

    private ApplicationErrorDao errorService;
    private BusinessLogicService businessService;

    @BeforeEach
    void setUp() {
        errorService = mock(ApplicationErrorDao.class);
        businessService = new BusinessLogicService(errorService);
    }

    @Nested
    class VerifyExactlyOneInsertOrIncrementCount {

        @Nested
        class WhenVerificationSucceeds {

            @Test
            void shouldReturnApplicationError() {
                businessService.performSomeProcessingThatCanFail("foo");
                var appError = ApplicationErrorVerifications.verifyExactlyOneInsertOrIncrementCount(errorService);

                assertThat(appError.getDescription()).isEqualTo("Processing failed for input: foo");
                assertThat(appError.getExceptionType()).isNull();
                assertThat(appError.getExceptionCauseType()).isNull();
            }

            @Test
            void shouldReturnApplicationError_HavingCause() {
                businessService.performSomeProcessingThatCanFail("bar");
                var appError = ApplicationErrorVerifications.verifyExactlyOneInsertOrIncrementCount(errorService);

                assertThat(appError.getDescription()).isEqualTo("Processing threw error with cause for input: bar");
                assertThat(appError.getExceptionType()).isEqualTo(UncheckedIOException.class.getName());
                assertThat(appError.getExceptionCauseType()).isEqualTo(IOException.class.getName());
            }
        }

        @Nested
        class WhenVerificationFails {

            @Test
            void shouldFailWhenNoApplicationErrors() {
                businessService.performSomeProcessingThatCanFail("this won't fail");

                assertThatThrownBy(() ->
                        ApplicationErrorVerifications.verifyExactlyOneInsertOrIncrementCount(errorService))
                        .isExactlyInstanceOf(WantedButNotInvoked.class);
            }

            @Test
            void shouldFailWhenUnwantedInteractions() {
                businessService.performSomeProcessingThatCanFail("baz");

                assertThatThrownBy(() ->
                        ApplicationErrorVerifications.verifyExactlyOneInsertOrIncrementCount(errorService))
                        .isExactlyInstanceOf(NoInteractionsWanted.class);
            }

            @Test
            void shouldFailWhenMoreThanOneApplicationErrorCreated() {
                businessService.performSomeProcessingThatCanFail("foo");
                businessService.performSomeProcessingThatCanFail("foo");

                assertThatThrownBy(() ->
                        ApplicationErrorVerifications.verifyExactlyOneInsertOrIncrementCount(errorService))
                        .isExactlyInstanceOf(TooManyActualInvocations.class);
            }
        }
    }

    @Nested
    class VerifyAtLeastOneInsertOrIncrementCount {

        @Nested
        class WhenVerificationSucceeds {

            @Test
            void shouldReturnApplicationErrors() {
                businessService.performSomeProcessingThatCanFail("foo");
                businessService.performSomeProcessingThatCanFail("bar");
                var appErrors = ApplicationErrorVerifications.verifyAtLeastOneInsertOrIncrementCount(errorService);

                var firstAppError = first(appErrors);
                assertThat(firstAppError.getDescription()).isEqualTo("Processing failed for input: foo");
                assertThat(firstAppError.getExceptionType()).isNull();
                assertThat(firstAppError.getExceptionCauseType()).isNull();

                var secondAppError = second(appErrors);
                assertThat(secondAppError.getDescription()).isEqualTo("Processing threw error with cause for input: bar");
                assertThat(secondAppError.getExceptionType()).isEqualTo(UncheckedIOException.class.getName());
                assertThat(secondAppError.getExceptionCauseType()).isEqualTo(IOException.class.getName());
            }

            @Test
            void shouldReturnManyApplicationErrors() {
                var numErrors = 20;
                IntStream.rangeClosed(1, numErrors).forEach(ignored -> businessService.performSomeProcessingThatCanFail("foo"));

                var appErrors = ApplicationErrorVerifications.verifyAtLeastOneInsertOrIncrementCount(errorService);
                assertThat(appErrors).hasSize(numErrors);
            }
        }

        @Nested
        class WhenVerificationFails {

            @Test
            void shouldFailWhenNoApplicationErrors() {
                businessService.performSomeProcessingThatCanFail("this won't fail");
                businessService.performSomeProcessingThatCanFail("this also won't fail");
                businessService.performSomeProcessingThatCanFail("good here too");

                assertThatThrownBy(() ->
                        ApplicationErrorVerifications.verifyAtLeastOneInsertOrIncrementCount(errorService))
                        .isExactlyInstanceOf(WantedButNotInvoked.class);
            }

            @Test
            void shouldFailWhenUnwantedInteractions() {
                businessService.performSomeProcessingThatCanFail("foo");
                businessService.performSomeProcessingThatCanFail("baz");

                assertThatThrownBy(() ->
                        ApplicationErrorVerifications.verifyAtLeastOneInsertOrIncrementCount(errorService))
                        .isExactlyInstanceOf(NoInteractionsWanted.class);
            }
        }
    }

}
