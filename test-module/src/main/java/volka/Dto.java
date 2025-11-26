package volka;

import io.github.volkayun.queryparams.annotations.CaseStrategy;
import io.github.volkayun.queryparams.annotations.QueryParams;
import io.github.volkayun.queryparams.runtime.QueryParamConvertible;

@QueryParams(caseStrategy = CaseStrategy.SNAKE)
public class Dto implements QueryParamConvertible {
    private String name;
    private String mobileNumber;
    private String myEmailTest;
    private String testString;

    public Dto(String name, String mobileNumber, String myEmailTest, String testString) {
        this.name = name;
        this.mobileNumber = mobileNumber;
        this.myEmailTest = myEmailTest;
        this.testString = testString;
    }

    public String getName() {
        return name;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public String getMyEmailTest() {
        return myEmailTest;
    }

    public String getTestString() {
        return testString;
    }

    @Override
    public String toString() {
        return "Dto{" +
                "name='" + name + '\'' +
                ", mobileNumber='" + mobileNumber + '\'' +
                ", myEmailTest='" + myEmailTest + '\'' +
                ", testString='" + testString + '\'' +
                '}';
    }
}
