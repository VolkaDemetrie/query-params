package volka;

import io.github.volkayun.queryparams.annotations.QueryParams;
import io.github.volkayun.queryparams.annotations.constant.Case;

import java.util.List;
import java.util.Map;

@QueryParams(Case.SNAKE)
public record TestRecord(
        String name,
        String mobileNumber,
        String myEmailTest,
        String testString
) {

    Map<String, List<String>> toQueryParams() {
        return TestRecordQueryParams.toQueryParams(this);
    }
}
