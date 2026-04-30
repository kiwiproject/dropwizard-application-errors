package org.kiwiproject.dropwizard.error.test.assertj;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.kiwiproject.dropwizard.error.model.ApplicationError;

import static java.util.Objects.nonNull;

import java.util.Objects;

/**
 * AssertJ-style fluent assertions for {@link ApplicationError}, intended for use in tests.
 * <p>
 * Use {@link #assertThatApplicationError(ApplicationError)} as the entry point.
 * All methods return {@code this} to support fluent assertion chaining.
 * Note that assertions stop at the first failure, unlike JUnit's {@code assertAll}.
 * <p>
 * For Mockito-based verification of {@link org.kiwiproject.dropwizard.error.dao.ApplicationErrorDao}
 * interactions, see {@link org.kiwiproject.dropwizard.error.test.mockito.ApplicationErrorVerifications}
 * and {@link org.kiwiproject.dropwizard.error.test.mockito.ApplicationErrorMatchers}.
 */
@SuppressWarnings("UnusedReturnValue")
public class ApplicationErrorAssert extends AbstractAssert<ApplicationErrorAssert, ApplicationError> {

    ApplicationErrorAssert(ApplicationError actual) {
        super(actual, ApplicationErrorAssert.class);
    }

    /**
     * Starting point for fluent assertions on an {@link ApplicationError}.
     *
     * @param actual the ApplicationError to assert upon
     * @return a new {@link ApplicationErrorAssert} instance
     */
    public static ApplicationErrorAssert assertThatApplicationError(ApplicationError actual) {
        return new ApplicationErrorAssert(actual);
    }

    /**
     * Asserts the error has the given description.
     *
     * @param description the expected description
     * @return this instance
     */
    public ApplicationErrorAssert hasDescription(String description) {
        isNotNull();
        if (!Objects.equals(actual.getDescription(), description)) {
            failWithMessage("Expected description <%s> but was <%s>", description, actual.getDescription());
        }
        return this;
    }

    /**
     * Asserts the error description contains the given fragment.
     *
     * @param fragment the expected fragment
     * @return this instance
     */
    public ApplicationErrorAssert hasDescriptionContaining(String fragment) {
        isNotNull();
        Assertions.assertThat(actual.getDescription()).contains(fragment);
        return this;
    }

    /**
     * Asserts the error has an exception type matching the given class's fully-qualified name.
     *
     * @param type the expected exception type; must not be null
     * @return this instance
     * @implNote Compares against {@link Class#getName()}, not {@code getSimpleName()} or
     * {@code getCanonicalName()}, which matters for inner classes and arrays.
     */
    public ApplicationErrorAssert hasExceptionType(Class<?> type) {
        Objects.requireNonNull(type, "type must not be null");
        return hasExceptionType(type.getName());
    }

    /**
     * Asserts the error has the given fully-qualified exception type name.
     *
     * @param fqcn the expected fully-qualified class name
     * @return this instance
     */
    public ApplicationErrorAssert hasExceptionType(String fqcn) {
        isNotNull();
        if (!Objects.equals(actual.getExceptionType(), fqcn)) {
            failWithMessage("Expected exceptionType <%s> but was <%s>", fqcn, actual.getExceptionType());
        }
        return this;
    }

    /**
     * Asserts the error has a null exception type.
     *
     * @return this instance
     */
    public ApplicationErrorAssert hasNoExceptionType() {
        isNotNull();
        if (nonNull(actual.getExceptionType())) {
            failWithMessage("Expected exceptionType to be null but was <%s>", actual.getExceptionType());
        }
        return this;
    }

    /**
     * Asserts the error has the given exception message.
     *
     * @param message the expected exception message
     * @return this instance
     */
    public ApplicationErrorAssert hasExceptionMessage(String message) {
        isNotNull();
        if (!Objects.equals(actual.getExceptionMessage(), message)) {
            failWithMessage("Expected exceptionMessage <%s> but was <%s>", message, actual.getExceptionMessage());
        }
        return this;
    }

    /**
     * Asserts the error exception message contains the given fragment.
     *
     * @param fragment the expected fragment
     * @return this instance
     */
    public ApplicationErrorAssert hasExceptionMessageContaining(String fragment) {
        isNotNull();
        Assertions.assertThat(actual.getExceptionMessage()).contains(fragment);
        return this;
    }

    /**
     * Asserts the error has a null exception message.
     *
     * @return this instance
     */
    public ApplicationErrorAssert hasNoExceptionMessage() {
        isNotNull();
        if (nonNull(actual.getExceptionMessage())) {
            failWithMessage("Expected exceptionMessage to be null but was <%s>", actual.getExceptionMessage());
        }
        return this;
    }

    /**
     * Asserts the error has an exception cause type matching the given class's fully-qualified name.
     *
     * @param type the expected exception cause type; must not be null
     * @return this instance
     * @implNote Compares against {@link Class#getName()}, not {@code getSimpleName()} or
     * {@code getCanonicalName()}, which matters for inner classes and arrays.
     */
    public ApplicationErrorAssert hasExceptionCauseType(Class<?> type) {
        Objects.requireNonNull(type, "type must not be null");
        return hasExceptionCauseType(type.getName());
    }

    /**
     * Asserts the error has the given fully-qualified exception cause type name.
     *
     * @param fqcn the expected fully-qualified class name
     * @return this instance
     */
    public ApplicationErrorAssert hasExceptionCauseType(String fqcn) {
        isNotNull();
        if (!Objects.equals(actual.getExceptionCauseType(), fqcn)) {
            failWithMessage("Expected exceptionCauseType <%s> but was <%s>", fqcn, actual.getExceptionCauseType());
        }
        return this;
    }

    /**
     * Asserts the error has a null exception cause type.
     *
     * @return this instance
     */
    public ApplicationErrorAssert hasNoExceptionCauseType() {
        isNotNull();
        if (nonNull(actual.getExceptionCauseType())) {
            failWithMessage("Expected exceptionCauseType to be null but was <%s>", actual.getExceptionCauseType());
        }
        return this;
    }

    /**
     * Asserts the error has the given exception cause message.
     *
     * @param message the expected exception cause message
     * @return this instance
     */
    public ApplicationErrorAssert hasExceptionCauseMessage(String message) {
        isNotNull();
        if (!Objects.equals(actual.getExceptionCauseMessage(), message)) {
            failWithMessage("Expected exceptionCauseMessage <%s> but was <%s>", message, actual.getExceptionCauseMessage());
        }
        return this;
    }

    /**
     * Asserts the error exception cause message contains the given fragment.
     *
     * @param fragment the expected fragment
     * @return this instance
     */
    public ApplicationErrorAssert hasExceptionCauseMessageContaining(String fragment) {
        isNotNull();
        Assertions.assertThat(actual.getExceptionCauseMessage()).contains(fragment);
        return this;
    }

    /**
     * Asserts the error has a null exception cause message.
     *
     * @return this instance
     */
    public ApplicationErrorAssert hasNoExceptionCauseMessage() {
        isNotNull();
        if (nonNull(actual.getExceptionCauseMessage())) {
            failWithMessage("Expected exceptionCauseMessage to be null but was <%s>", actual.getExceptionCauseMessage());
        }
        return this;
    }

    /**
     * Asserts the error has the given stack trace.
     *
     * @param stackTrace the expected stack trace
     * @return this instance
     */
    public ApplicationErrorAssert hasStackTrace(String stackTrace) {
        isNotNull();
        if (!Objects.equals(actual.getStackTrace(), stackTrace)) {
            failWithMessage("Expected stackTrace <%s> but was <%s>", stackTrace, actual.getStackTrace());
        }
        return this;
    }

    /**
     * Asserts the error stack trace contains the given fragment.
     *
     * @param fragment the expected fragment
     * @return this instance
     */
    public ApplicationErrorAssert hasStackTraceContaining(String fragment) {
        isNotNull();
        Assertions.assertThat(actual.getStackTrace()).contains(fragment);
        return this;
    }

    /**
     * Asserts the error has a null stack trace.
     *
     * @return this instance
     */
    public ApplicationErrorAssert hasNoStackTrace() {
        isNotNull();
        if (nonNull(actual.getStackTrace())) {
            failWithMessage("Expected stackTrace to be null but was <%s>", actual.getStackTrace());
        }
        return this;
    }

    /**
     * Asserts the error has occurred the given number of times.
     *
     * @param expected the expected number of occurrences
     * @return this instance
     */
    public ApplicationErrorAssert hasNumTimesOccurred(int expected) {
        isNotNull();
        if (actual.getNumTimesOccurred() != expected) {
            failWithMessage("Expected numTimesOccurred <%d> but was <%d>", expected, actual.getNumTimesOccurred());
        }
        return this;
    }

    /**
     * Asserts the error is resolved.
     *
     * @return this instance
     */
    public ApplicationErrorAssert isResolved() {
        isNotNull();
        if (!actual.isResolved()) {
            failWithMessage("Expected ApplicationError to be resolved but it was not");
        }
        return this;
    }

    /**
     * Asserts the error is not resolved.
     *
     * @return this instance
     */
    public ApplicationErrorAssert isNotResolved() {
        isNotNull();
        if (actual.isResolved()) {
            failWithMessage("Expected ApplicationError to not be resolved but it was");
        }
        return this;
    }

    /**
     * Asserts the error has the given host name.
     *
     * @param hostName the expected host name
     * @return this instance
     */
    public ApplicationErrorAssert hasHostName(String hostName) {
        isNotNull();
        if (!Objects.equals(actual.getHostName(), hostName)) {
            failWithMessage("Expected hostName <%s> but was <%s>", hostName, actual.getHostName());
        }
        return this;
    }

    /**
     * Asserts the error has the given IP address.
     *
     * @param ipAddress the expected IP address
     * @return this instance
     */
    public ApplicationErrorAssert hasIpAddress(String ipAddress) {
        isNotNull();
        if (!Objects.equals(actual.getIpAddress(), ipAddress)) {
            failWithMessage("Expected ipAddress <%s> but was <%s>", ipAddress, actual.getIpAddress());
        }
        return this;
    }

    /**
     * Asserts the error has the given port.
     *
     * @param port the expected port
     * @return this instance
     */
    public ApplicationErrorAssert hasPort(int port) {
        isNotNull();
        if (actual.getPort() != port) {
            failWithMessage("Expected port <%d> but was <%d>", port, actual.getPort());
        }
        return this;
    }

    /**
     * Asserts the error has the given id.
     *
     * @param id the expected id
     * @return this instance
     */
    public ApplicationErrorAssert hasId(long id) {
        isNotNull();
        if (!Objects.equals(actual.getId(), id)) {
            failWithMessage("Expected id <%d> but was <%d>", id, actual.getId());
        }
        return this;
    }

    /**
     * Asserts the error has a null id.
     *
     * @return this instance
     */
    public ApplicationErrorAssert hasNoId() {
        isNotNull();
        if (nonNull(actual.getId())) {
            failWithMessage("Expected id to be null but was <%d>", actual.getId());
        }
        return this;
    }
}
