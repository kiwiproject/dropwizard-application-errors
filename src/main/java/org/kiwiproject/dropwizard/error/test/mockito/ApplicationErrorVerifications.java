package org.kiwiproject.dropwizard.error.test.mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kiwiproject.collect.KiwiLists.first;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import lombok.experimental.UtilityClass;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorDao;
import org.kiwiproject.dropwizard.error.model.ApplicationError;
import org.mockito.ArgumentCaptor;
import org.mockito.verification.VerificationMode;

import java.util.List;

/**
 * Utilities for performing Mockito verifications on calls on mock {@link ApplicationErrorDao} instances.
 */
@UtilityClass
public class ApplicationErrorVerifications {

    /**
     * {@link org.mockito.Mockito#verify(Object) Verify} that
     * {@link ApplicationErrorDao#insertOrIncrementCount(ApplicationError)} was called <em>exactly one</em> time on
     * {@code errorDao} <em>and that no other interactions occurred</em>.
     * <p>
     * Returns the ApplicationError that was verified, which can be used to perform further inspection and/or
     * assertions.
     *
     * @param errorDao a Mockito mock of {@link ApplicationErrorDao}
     * @return the {@link ApplicationError} argument assuming the verification passes
     * @throws org.mockito.exceptions.base.MockitoAssertionError if verification fails. The exact type will be a
     *                                                           subclass describing on the problem.
     */
    public static ApplicationError verifyExactlyOneInsertOrIncrementCount(ApplicationErrorDao errorDao) {
        var argumentCaptor = ArgumentCaptor.forClass(ApplicationError.class);
        verify(errorDao).insertOrIncrementCount(argumentCaptor.capture());
        verifyNoMoreInteractions(errorDao);

        // If we're here, there should be only ONE value given the above verifications,
        // but be conservative and make the assertion anyway.
        var allValues = argumentCaptor.getAllValues();
        assertThat(allValues)
                .describedAs("Expected exactly one ApplicationError but found %d", allValues.size())
                .hasSize(1);

        return first(allValues);
    }

    /**
     * {@link org.mockito.Mockito#verify(Object) Verify} that
     * {@link ApplicationErrorDao#insertOrIncrementCount(ApplicationError)} was called <em>at least</em> one time on
     * {@code errorDao} and <em>that no other interactions occurred</em>.
     * <p>
     * Returns the list of ApplicationErrors that were verified, which can be used to perform further inspection and/or
     * assertions. The order of the returned ApplicationError list is the order in which {@code insertOrIncrementCount}
     * was called.
     *
     * @param errorDao a Mockito mock of {@link ApplicationErrorDao}
     * @return all {@link ApplicationError} arguments assuming the verification passes
     * @throws org.mockito.exceptions.base.MockitoAssertionError if verification fails. The exact type will be a
     *                                                           subclass describing on the problem.
     */
    public static List<ApplicationError> verifyAtLeastOneInsertOrIncrementCount(ApplicationErrorDao errorDao) {
        var argumentCaptor = ArgumentCaptor.forClass(ApplicationError.class);
        verify(errorDao, atLeastOnce()).insertOrIncrementCount(argumentCaptor.capture());
        verifyNoMoreInteractions(errorDao);

        return argumentCaptor.getAllValues();
    }


    /**
     * {@link org.mockito.Mockito#verify(Object) Verify} that
     * {@link ApplicationErrorDao#insertOrIncrementCount(ApplicationError)} was called using the given
     * {@link VerificationMode} to verify the expected behavior, e.g. at least once, a specific number of
     * times, within a timeout combined with a specific number of times, etc.
     * <p>
     * Returns the ApplicationError that was verified, or the latest one if multiple invocations occurred. It
     * can be used to perform further inspection and/or assertions.
     *
     * @param errorDao a Mockito mock of {@link ApplicationErrorDao}
     * @param mode     the {@link VerificationMode} to use when performing verification, e.g. {@code timeout(100).only()}
     * @return the single {@link ApplicationError} argument, or the latest argument in the case of multiple
     * invocations, assuming the verification passes
     * @throws org.mockito.exceptions.base.MockitoAssertionError if verification fails. The exact type will be a
     *                                                           subclass describing on the problem.
     */
    public static ApplicationError verifyOneOrLatestInsertOrIncrementCount(ApplicationErrorDao errorDao,
                                                                           VerificationMode mode) {

        var argumentCaptor = ArgumentCaptor.forClass(ApplicationError.class);
        verify(errorDao, mode).insertOrIncrementCount(argumentCaptor.capture());

        return argumentCaptor.getValue();
    }

    /**
     * {@link org.mockito.Mockito#verify(Object) Verify} that
     * {@link ApplicationErrorDao#insertOrIncrementCount(ApplicationError)} was called using the given
     * {@link VerificationMode} to verify the expected behavior, e.g. at least once, a specific number of
     * times, within a timeout combined with a specific number of times, etc.
     * <p>
     * Returns the list of ApplicationErrors that were verified, which can be used to perform further inspection and/or
     * assertions. The order of the returned ApplicationError list is the order in which {@code insertOrIncrementCount}
     * was called.
     *
     * @param errorDao a Mockito mock of {@link ApplicationErrorDao}
     * @param mode     the {@link VerificationMode} to use when performing verification, e.g. {@code timeout(250).times(3)}
     * @return all {@link ApplicationError} arguments assuming the verification passes
     * @throws org.mockito.exceptions.base.MockitoAssertionError if verification fails. The exact type will be a
     *                                                           subclass describing on the problem.
     */
    public static List<ApplicationError> verifyManyInsertOrIncrementCount(ApplicationErrorDao errorDao,
                                                                          VerificationMode mode) {

        var argumentCaptor = ArgumentCaptor.forClass(ApplicationError.class);
        verify(errorDao, mode).insertOrIncrementCount(argumentCaptor.capture());

        return argumentCaptor.getAllValues();
    }
}
