package org.kiwiproject.dropwizard.error.dao.jdbi3;

import static org.kiwiproject.jdbc.KiwiJdbc.intValueOrNull;
import static org.kiwiproject.jdbc.KiwiJdbc.utcZonedDateTimeFromTimestamp;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.kiwiproject.dropwizard.error.model.ApplicationError;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * JDBI 3 row mapper for ApplicationError objects.
 */
public class Jdbi3ApplicationErrorRowMapper implements RowMapper<ApplicationError> {

    @Override
    public ApplicationError map(ResultSet rs, StatementContext ctx) throws SQLException {
        return ApplicationError.builder()
                .id(rs.getLong("id"))
                .createdAt(utcZonedDateTimeFromTimestamp(rs, "created_at"))
                .updatedAt(utcZonedDateTimeFromTimestamp(rs, "updated_at"))
                .numTimesOccurred(rs.getInt("num_times_occurred"))
                .description(rs.getString("description"))
                .exceptionType(rs.getString("exception_type"))
                .exceptionMessage(rs.getString("exception_message"))
                .exceptionCauseType(rs.getString("exception_cause_type"))
                .exceptionCauseMessage(rs.getString("exception_cause_message"))
                .stackTrace(rs.getString("stack_trace"))
                .resolved(rs.getBoolean("resolved"))
                .hostName(rs.getString("host_name"))
                .ipAddress(rs.getString("ip_address"))
                .port(intValueOrNull(rs, "port"))
                .build();
    }
}
