package io.github.volkayun.queryparams.runtime;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * Interface for types that can be converted to query parameters.
 * Classes annotated with @QueryParams should implement this interface.
 * The default implementation uses reflection to call the generated helper class.
 */
public interface QueryParamConvertible {
    /**
     * Convert this object to a map of query parameters.
     * The default implementation automatically finds and invokes the generated
     * helper class (ClassName__QueryParams).
     *
     * @return Map where keys are parameter names and values are lists of parameter values
     * @throws RuntimeException if the generated helper class is not found or cannot be invoked
     */
    default Map<String, List<String>> toQueryParams() {
        try {
            // Get the actual class of the object (not the interface)
            Class<?> actualClass = this.getClass();
            String className = actualClass.getName();

            // Find the generated helper class: ClassName__QueryParams
            String helperClassName = className + "__QueryParams";
            Class<?> helperClass = Class.forName(helperClassName);

            // Find the static toQueryParams method
            Method method = helperClass.getMethod("toQueryParams", actualClass);

            // Invoke the method
            @SuppressWarnings("unchecked")
            Map<String, List<String>> result = (Map<String, List<String>>) method.invoke(null, this);

            return result;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(
                "Generated helper class not found. Make sure the class is annotated with @QueryParams " +
                "and annotation processing is enabled. Class: " + this.getClass().getName(), e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(
                "Generated helper class found but toQueryParams method is missing. " +
                "This might indicate a version mismatch or compilation issue.", e);
        } catch (Exception e) {
            throw new RuntimeException(
                "Failed to invoke generated toQueryParams method for class: " + this.getClass().getName(), e);
        }
    }
}
