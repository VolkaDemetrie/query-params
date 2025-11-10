import org.junit.jupiter.api.Test;
import volka.Dto;
import volka.TestRecord;

public class QueryParamsTest {

    @Test
    public void test() {
        Dto dto = new Dto("nameVal", "123123", "emailVal", "testVal");
        TestRecord record = new TestRecord("nameVal", "123123", "emailVal", "testVal");
        System.out.println("dto :: " + dto);
        System.out.println("record :: " + record);
    }
}


