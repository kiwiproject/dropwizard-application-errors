package org.kiwiproject.dropwizard.error.dao;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.Locale;

/**
 * Defines status of errors to be retrieved in certain DAO methods, i.e. do you want to see all errors, or only
 * resolved or only unresolved ones?
 */
public enum ApplicationErrorStatus {

    ALL, RESOLVED, UNRESOLVED;

    /**
     * Like {@link #valueOf(String)} except this is case-insensitive.
     *
     * @param value the value to parse
     * @return the enum constant with the specified name
     * @throws IllegalArgumentException if this enum type has no constant with the specified name
     */
    public static ApplicationErrorStatus from(String value) {
        if (isBlank(value)) {
            return ALL;
        }

        return valueOf(value.toUpperCase(Locale.getDefault()));
    }

}
