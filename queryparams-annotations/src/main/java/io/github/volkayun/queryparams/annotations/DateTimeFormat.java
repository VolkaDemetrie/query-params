package io.github.volkayun.queryparams.annotations;

/**
 * Format strategy for java.time.* types.
 */
public enum DateTimeFormat {
    /**
     * ISO-8601 instant format (e.g., 2023-01-01T12:00:00Z).
     */
    ISO_INSTANT,

    /**
     * ISO-8601 local date-time format (e.g., 2023-01-01T12:00:00).
     */
    ISO_LOCAL_DATE_TIME,

    /**
     * ISO-8601 local date format (e.g., 2023-01-01).
     */
    ISO_LOCAL_DATE,

    /**
     * Custom pattern format specified in pattern() attribute.
     */
    PATTERN
}
