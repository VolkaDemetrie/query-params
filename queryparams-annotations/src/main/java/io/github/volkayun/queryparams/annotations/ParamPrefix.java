package io.github.volkayun.queryparams.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Add a prefix to the query parameter key for a field or nested object.
 */
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.SOURCE)
public @interface ParamPrefix {
    /**
     * The prefix to add (e.g., "filter." for filter.brand).
     */
    String value();
}
