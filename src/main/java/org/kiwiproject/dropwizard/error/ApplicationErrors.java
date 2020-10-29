package org.kiwiproject.dropwizard.error;

import static java.util.Objects.nonNull;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.base.KiwiStrings;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorDao;
import org.kiwiproject.dropwizard.error.model.ApplicationError;
import org.slf4j.Logger;

import java.util.OptionalLong;

/**
 * Utility providing static convenience methods to easily create and log application errors. For easier mocking in
 * tests, consider using an {@link ApplicationErrorThrower} instead.
 * <p>
 * Each method first logs the error to a specified SLF4J {@link Logger} and then attempts to insert (or increment
 * the count of) an {@link ApplicationError}. If any error occurs trying to save the error, it is logged but not
 * allowed to escape, so that callers should not need to worry about those second-level error cases.
 * <p>
 * Methods return the generated error ID as an {@link OptionalLong}, so that if an exception occurs saving an
 * application error, an empty {@link OptionalLong} is returned, which would indicate to the caller that there was a
 * problem saving the error.
 */
@UtilityClass
@Slf4j
public class ApplicationErrors {

    /**
     * Log and save an {@link ApplicationError} with the given parameterized message using the supplied message
     * arguments.
     *
     * @param errorDao        an ApplicationErrorDao that can store application errors
     * @param logger          the SLF4J logger to use when logging
     * @param messageTemplate a template for the description of the problem that occurred, formatted
     *                        using {@link KiwiStrings#format(String, Object...)}
     * @param args            the arguments to supply to the message template
     * @return an OptionalLong containing the ID of the saved ApplicationError, or empty if a problem occurred saving
     */
    public static OptionalLong logAndSaveApplicationError(ApplicationErrorDao errorDao,
                                                          Logger logger,
                                                          String messageTemplate,
                                                          Object... args) {
        var errorMessage = KiwiStrings.format(messageTemplate, args);
        return logAndSaveApplicationError(errorDao, logger, errorMessage);
    }

    /**
     * Log and save an {@link ApplicationError} with the given message.
     *
     * @param errorDao an ApplicationErrorDao that can store application errors
     * @param logger   the SLF4J logger to use when logging
     * @param message  a description of the problem that occurred
     * @return an OptionalLong containing the ID of the saved ApplicationError, or empty if a problem occurred saving
     */
    public static OptionalLong logAndSaveApplicationError(ApplicationErrorDao errorDao,
                                                          Logger logger,
                                                          String message) {
        try {
            logger.error(message);
            var unresolvedError = ApplicationError.newUnresolvedError(message);
            return OptionalLong.of(errorDao.insertOrIncrementCount(unresolvedError));
        } catch (Exception e) {
            logErrorSavingApplicationError(e, message, null);
            return OptionalLong.empty();
        }
    }

    /**
     * Log and save an {@link ApplicationError} with the given {@link Throwable} and parameterized message
     * using the supplied message arguments.
     *
     * @param errorDao        an ApplicationErrorDao that can store application errors
     * @param logger          the SLF4J logger to use when logging
     * @param throwable       the underlying cause of the application error
     * @param messageTemplate a template for the description of the problem that occurred, formatted
     *                        using {@link KiwiStrings#format(String, Object...)}
     * @param args            the arguments to supply to the message template
     * @return an OptionalLong containing the ID of the saved ApplicationError, or empty if a problem occurred saving
     */
    public static OptionalLong logAndSaveApplicationError(ApplicationErrorDao errorDao,
                                                          Logger logger,
                                                          Throwable throwable,
                                                          String messageTemplate,
                                                          Object... args) {
        var errorMessage = KiwiStrings.format(messageTemplate, args);
        return logAndSaveApplicationError(errorDao, logger, throwable, errorMessage);
    }

    /**
     * Log and save an {@link ApplicationError} with the given {@link Throwable} and parameterized message
     * using the supplied message arguments.
     *
     * @param errorDao  an ApplicationErrorDao that can store application errors
     * @param logger    the SLF4J logger to use when logging
     * @param throwable the underlying cause of the application error
     * @param message   a description of the problem that occurred
     * @return an OptionalLong containing the ID of the saved ApplicationError, or empty if a problem occurred saving
     */
    public static OptionalLong logAndSaveApplicationError(ApplicationErrorDao errorDao,
                                                          Logger logger,
                                                          Throwable throwable,
                                                          String message) {
        try {
            logger.error(message, throwable);
            var unresolvedError = ApplicationError.newUnresolvedError(message, throwable);
            return OptionalLong.of(errorDao.insertOrIncrementCount(unresolvedError));
        } catch (Exception e) {
            logErrorSavingApplicationError(e, message, throwable);
            return OptionalLong.empty();
        }
    }

    /**
     * @param saveException     the exception that was thrown when we tried to save the ApplicationError
     * @param appErrorMessage   the message from the ApplicationError that we tried (and failed) to save
     * @param appErrorThrowable the Throwable from the ApplicationError that we tried (and failed) to save
     */
    private static void logErrorSavingApplicationError(Exception saveException,
                                                       String appErrorMessage,
                                                       Throwable appErrorThrowable) {
        if (nonNull(appErrorThrowable)) {
            LOG.error("Error saving ApplicationError with description [{}] and {} exception having message: {}.",
                    appErrorMessage,
                    appErrorThrowable.getClass().getName(),
                    appErrorThrowable.getMessage(),
                    saveException);
        } else {
            LOG.error("Error saving ApplicationError with description [{}].", appErrorMessage, saveException);
        }
    }
}
