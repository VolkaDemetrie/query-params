package io.github.volkayun.queryparams.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Override the query parameter key name for a field.
 */
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.SOURCE)
public @interface ParamName {
    /**
     * The custom key name to use for this field.
     */
    String value();
}
