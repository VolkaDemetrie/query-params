package volka;

import io.github.volkayun.queryparams.annotations.*;
import io.github.volkayun.queryparams.runtime.QueryParamConvertible;

import java.util.List;

@QueryParams(caseStrategy = CaseStrategy.SNAKE, dateTimeFormat = DateTimeFormat.ISO_LOCAL_DATE_TIME)
public record SearchReq(
        @ParamName("q") String keyword,
        Integer page,
        Integer size,
        @ParamPrefix("filter.")
        Filters filter,
        List<String> tags,
        @ParamConverter(PriceRangeConv.class)
        PriceRange price
) implements QueryParamConvertible {
}
