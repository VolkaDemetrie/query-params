package io.github.volkayun.queryparams.annotations;

import io.github.volkayun.queryparams.annotations.constant.ParamCase;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Main annotation to mark a class or record for query parameter conversion.
 */
@Target({ElementType.TYPE, ElementType.PARAMETER})
@Retention(RetentionPolicy.SOURCE)
public @interface QueryParams {

//    CaseStrategy value() default CaseStrategy.IDENTITY;

    /**
     * Case conversion strategy for field names.
     */
    CaseStrategy caseStrategy() default CaseStrategy.IDENTITY;

    /**
     * Prefix to add to all field keys.
     */
    String prefix() default "";

    /**
     * Include null values in the output (as empty strings).
     */
    boolean includeNulls() default false;

    /**
     * Explode arrays/collections into multiple keys (k=a and k=b) vs single key (k=a,b).
     */
    boolean explodeArrays() default true;

    /**
     * Assume values are already URL-encoded.
     */
    boolean encoded() default false;

    /**
     * DateTime format strategy for java.time.* types.
     */
    DateTimeFormat dateTimeFormat() default DateTimeFormat.ISO_INSTANT;

    /**
     * Custom date-time pattern when dateTimeFormat is PATTERN.
     */
    String pattern() default "";

    /**
     * Flatten nested DTOs with dot notation (a.b=value).
     */
    boolean flattenNested() default false;

    /**
     * Fail compilation if duplicate keys are detected.
     */
    boolean failOnDuplicateKeys() default false;
}
