package org.kiwiproject.dropwizard.error.test.junit.jupiter;

import static java.util.Objects.isNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.base.Verify;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.kiwiproject.dropwizard.error.model.ApplicationError;
import org.kiwiproject.dropwizard.error.model.PersistentHostInformation;
import org.kiwiproject.dropwizard.error.test.junit.jupiter.ApplicationErrorExtension.HostInfo;

import java.lang.reflect.Parameter;

/**
 * This test is a unit test of {@link ApplicationErrorExtension}, exercising each callback method individually.
 */
@DisplayName("ApplicationErrorExtension: Unit")
@ExtendWith(SoftAssertionsExtension.class)
class ApplicationErrorExtensionUnitTest {

    private ApplicationErrorExtension extension;
    private ExtensionContext extensionContext;
    private ExtensionContext.Store store;

    @BeforeEach
    void setUp() {
        ApplicationError.clearPersistentHostInformation();
        Verify.verify(isNull(ApplicationError.getPersistentHostInformation()),
                "Tests cannot proceed b/c PersistentHostInformation exists!");

        extension = new ApplicationErrorExtension();
        extensionContext = mock(ExtensionContext.class);
        store = mock(ExtensionContext.Store.class);
        when(extensionContext.getStore(any(ExtensionContext.Namespace.class))).thenReturn(store);
    }

    @Test
    void shouldSetPersistentHostInformation_InBeforeAll(SoftAssertions softly) {
        extension.beforeAll(extensionContext);

        var hostInfo = ApplicationError.getPersistentHostInformation();
        assertThat(hostInfo).isNotNull();

        softly.assertThat(hostInfo.getHostName()).isNotBlank();
        softly.assertThat(hostInfo.getIpAddress()).isNotBlank();
        softly.assertThat(hostInfo.getPort()).isGreaterThan(0);

        verifyGetStore();
        verify(store).put("PersistentHostInformation", hostInfo);
        verifyNoMoreInteractions(extensionContext, store);
    }

    @Test
    void shouldClearPersistentHostInformation_InAfterAll(SoftAssertions softly) {
        ApplicationError.setPersistentHostInformation("host42", "192.168.1.42", 9042);

        extension.afterAll(extensionContext);

        softly.assertThat(ApplicationError.getPersistentHostInformation()).isNull();

        verifyNoInteractions(extensionContext, store);
    }

    @Nested
    class SupportsParameter {

        private ParameterContext parameterContext;

        @BeforeEach
        void setUp() {
            parameterContext = mock(ParameterContext.class);
        }

        @Test
        void shouldBeFalse_WhenNotHostInfoAnnotation() {
            when(parameterContext.isAnnotated(any())).thenReturn(false);

            var parameter = getParameter("sampleMethodWithSoftAssertionsParameter", SoftAssertions.class);
            when(parameterContext.getParameter()).thenReturn(parameter);

            var supports = extension.supportsParameter(parameterContext, null);

            assertThat(supports).isFalse();

            verifyNoInteractions(extensionContext);
            verify(parameterContext).isAnnotated(HostInfo.class);
        }

        @Test
        void shouldBeFalse_WhenHostInfoAnnotation_OnIncorrectParameterType() {
            when(parameterContext.isAnnotated(any())).thenReturn(true);

            var parameter = getParameter("sampleMethodWithHostInfo", PersistentHostInformation.class);
            when(parameterContext.getParameter()).thenReturn(parameter);

            var supports = extension.supportsParameter(parameterContext, null);

            assertThat(supports).isTrue();

            verifyNoInteractions(extensionContext);
            verify(parameterContext).isAnnotated(HostInfo.class);
        }

        @Test
        void shouldBeTrue_WhenHostInfoAnnotation_OnPersistentHostInformationParameter() {
            when(parameterContext.isAnnotated(any())).thenReturn(true);

            var parameter = getParameter("sampleMethodWithIncorrectHostInfoParameterType", String.class);
            when(parameterContext.getParameter()).thenReturn(parameter);

            var supports = extension.supportsParameter(parameterContext, null);

            assertThat(supports).isFalse();

            verifyNoInteractions(extensionContext);
            verify(parameterContext).isAnnotated(HostInfo.class);
        }
    }

    private Parameter getParameter(String methodName, Class<?> parameterTypes) {
        try {
            var method = getClass().getDeclaredMethod(methodName, parameterTypes);
            return method.getParameters()[0];
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Method not found: " + methodName);
        }
    }

    @SuppressWarnings({"unused", "EmptyMethod"})
    void sampleMethodWithSoftAssertionsParameter(SoftAssertions softly) {
        // no-op
    }

    @SuppressWarnings({"unused", "EmptyMethod"})
    void sampleMethodWithHostInfo(@HostInfo PersistentHostInformation hostInfo) {
        // nop-op
    }

    @SuppressWarnings({"unused", "EmptyMethod"})
    void sampleMethodWithIncorrectHostInfoParameterType(@HostInfo String hostInfo) {
        // no-op
    }

    @Test
    void shouldResolveHostInfoParameter() {
        ApplicationError.setPersistentHostInformation("host42", "192.168.1.42", 9042);
        var hostInfo = ApplicationError.getPersistentHostInformation();
        when(store.get(anyString(), any())).thenReturn(hostInfo);

        var resolved = extension.resolveParameter(null, extensionContext);

        assertThat(resolved).isSameAs(hostInfo);

        verifyGetStore();
        verify(store).get("PersistentHostInformation", PersistentHostInformation.class);
        verifyNoMoreInteractions(extensionContext, store);
    }

    private void verifyGetStore() {
        verify(extensionContext).getStore(argThat(namespaceArg -> {
            assertThat(namespaceArg).isEqualTo(ExtensionContext.Namespace.create(ApplicationErrorExtension.class));
            return true;
        }));
    }
}
