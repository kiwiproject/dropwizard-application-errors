package org.kiwiproject.dropwizard.error.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ApplicationErrorDaoTest {

    @Nested
    class CheckPagingArgumentsAndCalculateZeroBasedOffset {

        @ParameterizedTest
        @CsvSource({
                "1, 25, 0",
                "1, 10, 0",
                "2, 25, 25",
                "2, 10, 10",
                "5, 20, 80",
                "10, 25, 225"
        })
        void shouldReturnZeroBasedOffset_WhenValidArguments(int pageNumber, int pageSize, int expectedOffset) {
            assertThat(ApplicationErrorDao.checkPagingArgumentsAndCalculateZeroBasedOffset(pageNumber, pageSize))
                    .describedAs("Expected offset %d for page number %d and size %d", expectedOffset, pageNumber, pageSize)
                    .isEqualTo(expectedOffset);
        }

        @ParameterizedTest
        @CsvSource({
                "0, 25",
                "1, 0",
                "0, 0",
        })
        void shouldRejectInvalidArguments(int pageNumber, int pageSize) {
            assertThatThrownBy(() ->
                    ApplicationErrorDao.checkPagingArgumentsAndCalculateZeroBasedOffset(pageNumber, pageSize))
                    .isExactlyInstanceOf(IllegalArgumentException.class);
        }
    }
}