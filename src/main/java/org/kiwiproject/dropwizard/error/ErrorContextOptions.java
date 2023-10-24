package org.kiwiproject.dropwizard.error;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

import org.kiwiproject.dropwizard.error.config.CleanupConfig;
import org.kiwiproject.dropwizard.error.health.TimeWindow;
import org.kiwiproject.dropwizard.error.model.DataStoreType;

import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;

/**
 * Contains options common to different types of {@link ErrorContext}.
 *
 * @implNote This is not public and is subject to change.
 */
@Builder
@Getter
class ErrorContextOptions {

    @NonNull
    @Builder.Default
    private DataStoreType dataStoreType = DataStoreType.SHARED;

    @Builder.Default
    private boolean addErrorsResource = true;

    @Builder.Default
    private boolean addGotErrorsResource = true;

    @Builder.Default
    private boolean addHealthCheck = true;

    @Builder.Default
    private long timeWindowValue = TimeWindow.DEFAULT_TIME_WINDOW_MINUTES;

    @NonNull
    @Builder.Default
    private TemporalUnit timeWindowUnit = ChronoUnit.MINUTES;

    @Builder.Default
    private boolean addCleanupJob = true;

    @Builder.Default
    private CleanupConfig cleanupConfig = new CleanupConfig();
}
