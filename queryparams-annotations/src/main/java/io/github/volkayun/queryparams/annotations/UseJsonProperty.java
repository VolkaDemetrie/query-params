package io.github.volkayun.queryparams.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Use Jackson's @JsonProperty value as the query parameter key.
 */
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.SOURCE)
public @interface UseJsonProperty {
}
