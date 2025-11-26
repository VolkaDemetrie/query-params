package volka;

import io.github.volkayun.queryparams.annotations.QueryParams;
import io.github.volkayun.queryparams.runtime.QueryParamConvertible;

@QueryParams
public record PriceRange(Double min, Double max) implements QueryParamConvertible {
}
