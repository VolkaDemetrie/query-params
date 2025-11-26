package io.github.volkayun.queryparams.runtime;

import java.util.List;
import java.util.Map;

/**
 * Marker interface for types that can be converted to query parameters.
 */
public interface QueryParamConvertible {
    /**
     * Convert this object to a map of query parameters.
     * @return Map where keys are parameter names and values are lists of parameter values
     */
    Map<String, List<String>> toQueryParams();
}
