package io.github.volkayun.queryparams.annotations;

/**
 * Strategy for converting field names to query parameter keys.
 */
public enum CaseStrategy {
    /**
     * Keep the original field name as-is.
     */
    IDENTITY,

    /**
     * Convert to camelCase (e.g., myFieldName).
     */
    CAMEL,

    /**
     * Convert to snake_case (e.g., my_field_name).
     */
    SNAKE,

    /**
     * Convert to kebab-case (e.g., my-field-name).
     */
    KEBAB,

    /**
     * Convert to UPPER_SNAKE_CASE (e.g., MY_FIELD_NAME).
     */
    UPPER_SNAKE,

    /**
     * Convert to PascalCase (e.g., MyFieldName).
     */
    PASCAL
}
