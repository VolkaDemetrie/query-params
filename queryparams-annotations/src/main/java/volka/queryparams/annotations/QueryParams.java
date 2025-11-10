package volka.queryparams.annotations;

import volka.queryparams.annotations.constant.Case;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.PARAMETER})
@Retention(RetentionPolicy.SOURCE)
public @interface QueryParams {
    Case value() default Case.CAMEL;
}
