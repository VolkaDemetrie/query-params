package volka;

import io.github.volkayun.queryparams.annotations.CaseStrategy;
import io.github.volkayun.queryparams.annotations.QueryParams;

import java.util.List;
import java.util.Map;

@QueryParams(caseStrategy = CaseStrategy.SNAKE)
public record TestRecord(
        String name,
        String mobileNumber,
        String myEmailTest,
        String testString
) {
    public Map<String, List<String>> toQueryParams() {
        return TestRecord__QParams.toQueryParams(this);
    }
}
