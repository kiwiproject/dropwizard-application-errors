package org.kiwiproject.dropwizard.error.dao.jdbi3;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorJdbc;
import org.kiwiproject.dropwizard.error.model.ApplicationError;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * JDBI 3 row mapper for ApplicationError objects.
 */
public class Jdbi3ApplicationErrorRowMapper implements RowMapper<ApplicationError> {

    @Override
    public ApplicationError map(ResultSet rs, StatementContext ctx) throws SQLException {
        return ApplicationErrorJdbc.mapFrom(rs);         
    }
}
