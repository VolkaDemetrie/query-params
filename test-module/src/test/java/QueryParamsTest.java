import org.junit.jupiter.api.Test;
import volka.Dto;
import volka.PriceRange;
import volka.TestRecord;

import java.util.List;
import java.util.Map;

public class QueryParamsTest {

    @Test
    public void test() {
        Dto dto = new Dto("nameVal", "123123", "emailVal", "testVal");
        TestRecord record = new TestRecord("nameVal", "123123", "emailVal", "testVal");
        PriceRange range = new PriceRange(1d, 3d);

        // Now we can call toQueryParams() on the objects directly!
        Map<String, List<String>> dtoParams = dto.toQueryParams();
        Map<String, List<String>> recordParams = record.toQueryParams();
        Map<String, List<String>> rangeParams = range.toQueryParams();

        System.out.println("dto :: " + dto);
        System.out.println("dto params :: " + dtoParams);
        System.out.println();
        System.out.println("record :: " + record);
        System.out.println("record params :: " + recordParams);
        System.out.println();
        System.out.println("range :: " + range);
        System.out.println("range params :: " + rangeParams);
    }
}


