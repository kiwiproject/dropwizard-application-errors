package org.kiwiproject.dropwizard.error.test.junit.jupiter;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.kiwiproject.dropwizard.error.model.ApplicationError;
import org.kiwiproject.dropwizard.error.model.PersistentHostInformation;
import org.kiwiproject.net.KiwiInternetAddresses;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.InetAddress;

/**
 * A JUnit Jupiter extension that ensures the {@link PersistentHostInformation} is set on {@link ApplicationError}
 * before all tests, and cleared after all tests have completed.
 * <p>
 * If you need access to the underlying {@link PersistentHostInformation}, you can retrieve it via the {@link HostInfo}
 * annotation. You can get it using a {@link BeforeAll} annotated method:
 * <pre>
 * private static PersistentHostInformation myStaticHostInfo;
 *
 * {@literal @}BeforeAll
 *  static void setUpBeforeAll(@HostInfo PersistentHostInformation hostInfo) {
 *      myStaticHostInfo = hostInfo;
 *  }
 *
 * {@literal @}Test
 *  void test() {
 *      assertThat(myStaticHostInfo.getHostname()).isEqualTo("your-hostname");
 *      // ...
 *  }
 * </pre>
 * <p>
 * or via a {@link BeforeEach} annotated method:
 * <pre>
 * private PersistentHostInformation myHostInfo;
 *
 * {@literal @}BeforeEach
 *  void setUp(@HostInfo PersistentHostInformation hostInfo) {
 *      myHostInfo = hostInfo;
 *  }
 *
 * {@literal @}Test
 *  void test() {
 *      assertThat(myHostInfo.getHostname()).isEqualTo("your-hostname");
 *      //
 *  }
 * </pre>
 * <p>
 * or even on an individual test method:
 * <pre>
 * {@literal @}Test
 *  void test(@HostInfo PersistentHostInformation hostInfo) {
 *      assertThat(hostInfo.getHostname()).isEqualTo("your-hostname");
 *      //
 *  }
 * </pre>
 * <p>
 * <b>NOTE</b>: It is recommended that you do not mix and match these within a single unit test. It should
 * still work, but we would not be surprised if you get weird "Heisenbugs" popping up.
 */
@Slf4j
public class ApplicationErrorExtension implements BeforeAllCallback, AfterAllCallback, ParameterResolver {

    /**
     * Add this annotation to a {@link PersistentHostInformation} parameter in a method annotated with
     * {@link BeforeAll}, {@link BeforeEach}, or {@link Test} methods in order to get the persistent
     * host information.
     */
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    public @interface HostInfo {
    }

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(ApplicationErrorExtension.class);

    private static final String HOST_INFO_KEY = "PersistentHostInformation";
    private static final int DEFAULT_PORT = 8080;

    @Override
    public void beforeAll(ExtensionContext context) {
        LOG.trace("Setting persistent host information for ApplicationError");

        InetAddress localHost = getLocalHost();
        ApplicationError.setPersistentHostInformation(localHost.getHostName(), localHost.getHostAddress(), DEFAULT_PORT);

        context.getStore(NAMESPACE).put(HOST_INFO_KEY, ApplicationError.getPersistentHostInformation());
    }

    private InetAddress getLocalHost() {
        return KiwiInternetAddresses.getLocalHostInetAddress().orElseGet(InetAddress::getLoopbackAddress);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        LOG.trace("Clearing persistent host information from ApplicationError");
        ApplicationError.clearPersistentHostInformation();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        LOG.trace("Checking if we support this type: {}", parameterContext.getParameter().getType());

        return parameterContext.isAnnotated(HostInfo.class) &&
                parameterContext.getParameter().getType() == PersistentHostInformation.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        var hostInformation = extensionContext.getStore(ApplicationErrorExtension.NAMESPACE)
                .get(ApplicationErrorExtension.HOST_INFO_KEY, PersistentHostInformation.class);

        LOG.trace("Resolving parameter based on the ApplicationErrorExtension context: host={}, address={}, port= {}",
                hostInformation.getHostName(), hostInformation.getIpAddress(), hostInformation.getPort());

        return hostInformation;
    }
}
