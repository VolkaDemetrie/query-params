package volka;

import io.github.volkayun.queryparams.annotations.CaseStrategy;
import io.github.volkayun.queryparams.annotations.QueryParams;
import io.github.volkayun.queryparams.runtime.QueryParamConvertible;

import java.util.List;
import java.util.Map;

@QueryParams(caseStrategy = CaseStrategy.SNAKE)
public record TestRecord(
        String name,
        String mobileNumber,
        String myEmailTest,
        String testString
) implements QueryParamConvertible {
}
