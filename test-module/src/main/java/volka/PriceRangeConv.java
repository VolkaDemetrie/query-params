package volka;

import io.github.volkayun.queryparams.runtime.ParamValueConverter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PriceRangeConv implements ParamValueConverter<PriceRange> {
    @Override
    public Map<String, List<String>> convert(String key, PriceRange value) {
        Map<String, List<String>> result = new HashMap<>();
        if (value != null) {
            if (value.min() != null) {
                result.put("price_min", List.of(String.valueOf(value.min())));
            }
            if (value.max() != null) {
                result.put("price_max", List.of(String.valueOf(value.max())));
            }
        }
        return result;
    }
}
