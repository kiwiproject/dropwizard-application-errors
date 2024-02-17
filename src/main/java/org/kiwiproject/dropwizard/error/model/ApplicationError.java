package org.kiwiproject.dropwizard.error.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotBlank;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;
import static org.kiwiproject.base.KiwiThrowables.EMPTY_THROWABLE_INFO;
import static org.kiwiproject.base.KiwiThrowables.nextCauseOfNullable;
import static org.kiwiproject.base.KiwiThrowables.throwableInfoOfNullable;

import com.google.common.annotations.VisibleForTesting;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Synchronized;
import lombok.Value;
import lombok.With;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Defines an application error that you want to save in a data store.
 * <p>
 * You can construct an instance using the provided fluent builder, or you can use one of the static factory
 * convenience methods. The {@code id} and {@code numTimesOccurred} can only be set via the builder, however.
 * <p>
 * One thing to note is that the "persistent host information" must be set if any of the factory methods without
 * host, IP, and port are called. This "persistent host information" contains a host, IP, and port that act as
 * the default value for these factory methods.
 * <p>
 * <strong>NOTE:</strong> Currently only the factory methods perform validation in inputs. We assume clients using
 * the builder will set all values, though the exception-related values may be omitted. For practical usage we are
 * assuming most clients will simply use one of the factory methods, as that has been our own general usage pattern.
 */
@Value
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApplicationError {

    /**
     * Indicates whether an ApplicationError is resolved.
     */
    public enum Resolved {
        YES(true), NO(false);

        private final boolean value;

        Resolved(boolean value) {
            this.value = value;
        }

        public static Resolved of(boolean value) {
            return value ? YES : NO;
        }

        public boolean toBoolean() {
            return value;
        }
    }

    @With
    Long id;

    ZonedDateTime createdAt;
    ZonedDateTime updatedAt;
    int numTimesOccurred;
    String description;
    String exceptionType;
    String exceptionMessage;
    String exceptionCauseType;
    String exceptionCauseMessage;
    String stackTrace;
    boolean resolved;
    String hostName;
    String ipAddress;
    int port;

    /**
     * Host information to be shared across all ApplicationError instances in this JVM.
     */
    private static PersistentHostInformation persistentHostInformation;

    /**
     * @return the date/time created in milliseconds since the epoch
     */
    public Long getCreatedAtMillis() {
        return isNull(createdAt) ? null : createdAt.toInstant().toEpochMilli();
    }

    /**
     * @return the date/time updated in milliseconds since the epoch
     */
    public Long getUpdatedAtMillis() {
        return isNull(updatedAt) ? null : updatedAt.toInstant().toEpochMilli();
    }

    /**
     * Sets the persistent host name, IP address, and port to use when factory methods without host information
     * are used. Once set, new instances will automatically use these values for {@code hostName}, {@code ipAddress},
     * and {@code port} when created via the static factory methods.
     * <p>
     * Please note this is intended to be called only <em>once</em> at initialization (see the "{@code build*}" methods
     * in {@link org.kiwiproject.dropwizard.error.ErrorContextBuilder ErrorContextBuilder}). Alas, Java does not permit
     * a "set only once" semantic to non-final variables. However, this does allow calling more than once for unit
     * testing purposes.
     *
     * @param hostName  the persistent host name
     * @param ipAddress the persistent IP address
     * @param port      the persistent port
     * @implNote This is synchronized internally even though it is highly unlikely two threads will be initializing
     * persistent host information at the same time.
     * @implNote If you rename this method, you <strong>must</strong> also update the error message in
     * {@link #checkPersistentHostState()} for the error message to be correct.
     */
    @Synchronized
    public static void setPersistentHostInformation(String hostName, String ipAddress, int port) {
        persistentHostInformation = new PersistentHostInformation(hostName, ipAddress, port);
    }

    /**
     * Overload/alias of {@link #setPersistentHostInformation(String, String, int)} that accepts a
     * {@link PersistentHostInformation} instance.
     *
     * @param hostInfo the persistent host information
     * @implNote This is synchronized internally even though it is highly unlikely two threads will be initializing
     * persistent host information at the same time.
     * @implNote If you rename this method, you <strong>must</strong> also update the error message in
     * {@link #checkPersistentHostState()} for the error message to be correct.
     */
    @Synchronized
    public static synchronized void setPersistentHostInformation(PersistentHostInformation hostInfo) {
        checkArgumentNotNull(hostInfo);
        setPersistentHostInformation(hostInfo.getHostName(), hostInfo.getIpAddress(), hostInfo.getPort());
    }

    /**
     * Clears out (by nullifying) the persistent host name, IP address, and port.
     * <p>
     * <em>This is intended only for unit testing purposes.</em>
     *
     * @implNote This is synchronized internally even though it is unlikely two threads will be initializing the
     * persistent host information at the same time.
     */
    @Synchronized
    @VisibleForTesting
    public static synchronized void clearPersistentHostInformation() {
        persistentHostInformation = null;
    }

    /**
     * Return the currently set persistent host information.
     *
     * @return the persistent host information
     * @implNote This is <em>intentionally not synchronized</em>, as it is assumed these values are set once (when an
     * application starts on a specific host and port) and is then never changed during the lifetime of the
     * application. If you change this information, you violate this contract and assume personal responsibility, and
     * all bets are off regarding concurrent modifications. This is why we are suppressing the Sonar rule java:S2886:
     * "Getters and setters should be synchronized in pairs".
     */
    @SuppressWarnings("java:S2886")
    public static PersistentHostInformation getPersistentHostInformation() {
        return persistentHostInformation;
    }

    /**
     * Create a new unresolved error with one occurrence using only the given description. The returned
     * ApplicationError will not have any exception-related information and those values will all be null.
     *
     * @param description a description of the error
     * @return a new instance
     */
    public static ApplicationError newUnresolvedError(String description) {
        return newUnresolvedError(description, null);
    }

    /**
     * Create a new unresolved error with one occurrence using the given description and cause.
     *
     * @param description a description of the error
     * @param throwable   a Throwable that is the cause of this application error
     * @return a new instance
     */
    public static ApplicationError newUnresolvedError(String description, @Nullable Throwable throwable) {
        checkPersistentHostState();
        return newError(description, Resolved.NO, throwable);
    }

    /**
     * Create a new unresolved error with one occurrence using the given description, host information, and cause.
     *
     * @param description a description of the error
     * @param hostName    the host name on which the error occurred
     * @param ipAddress   the IP address of the host on which the error occurred
     * @param port        the port on which the error occurred
     * @param throwable   a Throwable that is the cause of this application error
     * @return a new instance
     */
    public static ApplicationError newUnresolvedError(String description,
                                                      String hostName,
                                                      String ipAddress,
                                                      int port,
                                                      @Nullable Throwable throwable) {
        return newError(description, Resolved.NO, hostName, ipAddress, port, throwable);
    }

    /**
     * Create a new error instance with one occurrence the given description, resolution status, and cause.
     *
     * @param description a description of the error
     * @param resolved    is this error resolved?
     * @param throwable   a Throwable that is the cause of this application error
     * @return a new instance
     */
    public static ApplicationError newError(String description, Resolved resolved, @Nullable Throwable throwable) {
        checkPersistentHostState();
        return newError(description,
                resolved,
                persistentHostInformation.getHostName(),
                persistentHostInformation.getIpAddress(),
                persistentHostInformation.getPort(),
                throwable);
    }

    /**
     * Create a new error instance with one occurrence the given description, resolution status, host information,
     * and cause.
     *
     * @param description a description of the error
     * @param resolved    is this error resolved?
     * @param hostName    the host name on which the error occurred
     * @param ipAddress   the IP address of the host on which the error occurred
     * @param port        the port on which the error occurred
     * @param throwable   a Throwable that is the cause of this application error
     * @return a new instance
     */
    public static ApplicationError newError(String description,
                                            Resolved resolved,
                                            String hostName,
                                            String ipAddress,
                                            int port,
                                            @Nullable Throwable throwable) {

        checkArgumentNotBlank(description, "description must not be blank");
        checkArgumentNotNull(resolved, "resolved must not be null");
        checkArgumentNotBlank(hostName, "hostName must not be blank");
        checkArgumentNotBlank(ipAddress, "ipAddress must not be blank");
        checkArgument(PersistentHostInformation.isValidPort(port), "port must be a valid port");

        var now = ZonedDateTime.now(ZoneOffset.UTC);
        var throwableInfo = throwableInfoOfNullable(throwable).orElse(EMPTY_THROWABLE_INFO);
        var nextCause = nextCauseOfNullable(throwable).orElse(null);
        var causeThrowableInfo = throwableInfoOfNullable(nextCause).orElse(EMPTY_THROWABLE_INFO);

        return ApplicationError.builder()
                .createdAt(now)
                .updatedAt(now)
                .numTimesOccurred(1)
                .description(description)
                .exceptionType(throwableInfo.type)
                .exceptionMessage(throwableInfo.getMessage().orElse(null))
                .exceptionCauseType(causeThrowableInfo.type)
                .exceptionCauseMessage(causeThrowableInfo.getMessage().orElse(null))
                .stackTrace(throwableInfo.stackTrace)
                .resolved(resolved.value)
                .hostName(hostName)
                .ipAddress(ipAddress)
                .port(port)
                .build();
    }

    private static void checkPersistentHostState() {
        checkState(nonNull(persistentHostInformation),
                "Persistent host properties have not been set. Please call setPersistentHostInformation first. " +
                        "If this is a test be sure the test class has @ExtendWith(ApplicationErrorExtension.class) to set the properties.");
    }
}
