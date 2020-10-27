package org.kiwiproject.dropwizard.error.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.kiwiproject.search.PaginatedResult;

import java.util.List;

/**
 * Represents a "page" of {@link ApplicationError} results, e.g. when using pagination.
 *
 * @implNote This class is not intended to be compared using equals or hashCode.
 */
@Builder
@ToString(exclude = "items")
public class ApplicationErrorPage implements PaginatedResult {

    @Getter
    private final List<ApplicationError> items;

    @Getter
    private final long totalCount;

    @Getter
    private final int pageNumber;

    @Getter
    private final int pageSize;
}
