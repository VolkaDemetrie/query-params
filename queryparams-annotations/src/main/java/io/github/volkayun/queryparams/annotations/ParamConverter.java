package io.github.volkayun.queryparams.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specify a custom converter for a field.
 */
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.SOURCE)
public @interface ParamConverter {
    /**
     * The converter class implementing ParamValueConverter.
     */
    Class<?> value();
}
