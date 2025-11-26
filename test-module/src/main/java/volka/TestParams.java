package volka;

import io.github.volkayun.queryparams.annotations.CaseStrategy;
import io.github.volkayun.queryparams.annotations.QueryParams;

@QueryParams(caseStrategy = CaseStrategy.SNAKE)
public record TestParams(String myName, Integer myNumber) {
}
