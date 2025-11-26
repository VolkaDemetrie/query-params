package io.github.volkayun.queryparams.runtime;

import java.util.List;
import java.util.Map;

/**
 * Custom converter interface for complex field types.
 *
 * @param <T> The type to convert
 */
public interface ParamValueConverter<T> {
    /**
     * Convert a value to query parameter key-value pairs.
     *
     * @param key The base key for this field
     * @param value The value to convert
     * @return Map of query parameter keys to lists of values
     */
    Map<String, List<String>> convert(String key, T value);
}
