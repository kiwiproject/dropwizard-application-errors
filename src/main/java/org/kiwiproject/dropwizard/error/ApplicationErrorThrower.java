package org.kiwiproject.dropwizard.error;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.kiwiproject.base.KiwiStrings;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorDao;
import org.kiwiproject.dropwizard.error.model.ApplicationError;
import org.slf4j.Logger;

import java.util.OptionalLong;

/**
 * Wrapper around {@link ApplicationErrors} that uses the required constructor/builder properties
 * {@link ApplicationErrorDao} and {@link Logger} to log and throw (save) application errors.
 * <p>
 * <strong>NOTE:</strong> You should create a <em>separate instance</em> of this class for each class from which you
 * want to throw application errors, otherwise the logged messages will appear to come from the wrong location, i.e. it
 * will appear to come from whatever logger was supplied in the constructor/builder, which is almost certainly not the
 * class throwing the application error! Supply each instance with the (thread-safe) {@link ApplicationErrorDao}
 * and a {@link Logger} specific to that class, e.g. an {@code OrderService} object should supply its own Logger
 * to the ApplicationErrorThrower.
 * <p>
 * Generally you will inject an {@link ApplicationErrorDao} into the class that wants to throw application errors, and
 * then create and store the thrower in a {@code private final} field, which you would initialize during construction
 * with the {@link ApplicationErrorDao} and that class' {@link Logger}.
 */
@Builder
@AllArgsConstructor
public class ApplicationErrorThrower {

    @NonNull
    private final ApplicationErrorDao errorDao;

    @NonNull
    private final Logger logger;

    /**
     * Log and save an {@link ApplicationError} with the given message.
     *
     * @param message a description of the problem that occurred
     * @return an OptionalLong containing the ID of the saved ApplicationError, or empty if a problem occurred saving
     */
    public OptionalLong logAndSaveApplicationError(String message) {
        return ApplicationErrors.logAndSaveApplicationError(errorDao, logger, message);
    }


    /**
     * Log and save an {@link ApplicationError} with the given parameterized message using the supplied
     * message arguments.
     *
     * @param messageTemplate a template for the description of the problem that occurred, formatted
     *                        using {@link KiwiStrings#format(String, Object...)}
     * @param args            the arguments to supply to the message template
     * @return an OptionalLong containing the ID of the saved ApplicationError, or empty if a problem occurred saving
     */
    public OptionalLong logAndSaveApplicationError(String messageTemplate, Object... args) {
        return ApplicationErrors.logAndSaveApplicationError(errorDao, logger, messageTemplate, args);
    }

    /**
     * Log and save an {@link ApplicationError} with the given {@link Throwable} and message.
     *
     * @param throwable the underlying cause of the application error (can be null)
     * @param message   a description of the problem that occurred
     * @return an OptionalLong containing the ID of the saved ApplicationError, or empty if a problem occurred saving
     */
    public OptionalLong logAndSaveApplicationError(@Nullable Throwable throwable, String message) {
        return ApplicationErrors.logAndSaveApplicationError(errorDao, logger, throwable, message);
    }

    /**
     * Log and save an {@link ApplicationError} with the given {@link Throwable} and parameterized message
     * using the supplied message arguments.
     *
     * @param throwable       the underlying cause of the application error (can be null)
     * @param messageTemplate a template for the description of the problem that occurred, formatted
     *                        using {@link KiwiStrings#format(String, Object...)}
     * @param args            the arguments to supply to the message template
     * @return an OptionalLong containing the ID of the saved ApplicationError, or empty if a problem occurred saving
     */
    public OptionalLong logAndSaveApplicationError(@Nullable Throwable throwable, String messageTemplate, Object... args) {
        return ApplicationErrors.logAndSaveApplicationError(errorDao, logger, throwable, messageTemplate, args);
    }
}
