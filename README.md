# QueryParams - Compile-time Query Parameter Mapper

롬복처럼 컴파일 타임에 AST를 조작하여 DTO/Record를 쿼리 파라미터 Map으로 변환하는 라이브러리입니다.

## 특징

- ✅ **컴파일 타임 코드 생성**: 런타임 오버헤드 없음
- ✅ **Record와 Class 모두 지원**: Java Record와 일반 클래스 모두 사용 가능
- ✅ **다양한 Case 변환 전략**: SNAKE_CASE, camelCase, kebab-case 등
- ✅ **중첩 객체 지원**: @ParamPrefix로 중첩된 DTO 평탄화
- ✅ **커스텀 변환기**: @ParamConverter로 복잡한 타입 변환
- ✅ **Jackson 연동**: @UseJsonProperty로 @JsonProperty 값 사용
- ✅ **컬렉션 지원**: List, Set, Array 등을 다중 파라미터로 변환
- ✅ **Eclipse와 IntelliJ 모두 지원**: AutoService 기반 애노테이션 프로세서

## 모듈 구조

```
query-params/
├── queryparams-annotations/  # 애노테이션 정의
├── queryparams-runtime/      # 런타임 인터페이스 (가벼움)
├── queryparams-processor/    # 애노테이션 프로세서 (JSR 269 + JavaPoet)
└── test-module/              # 예제 및 테스트
```

## 사용법

### Gradle 설정

```gradle
dependencies {
    implementation 'io.github.volka-yun:queryparams-annotations:0.0.1'
    implementation 'io.github.volka-yun:queryparams-runtime:0.0.1'
    annotationProcessor 'io.github.volka-yun:queryparams-processor:0.0.1'
}
```

### 기본 사용 예제

```java
@QueryParams(caseStrategy = CaseStrategy.SNAKE)
public record TestParams(String myName, Integer myNumber) {
}

// 사용
TestParams params = new TestParams("John", 123);
Map<String, List<String>> queryParams = TestParams__QParams.toQueryParams(params);
// 결과: {"my_name": ["John"], "my_number": ["123"]}
```

### 고급 사용 예제

```java
@QueryParams(
    caseStrategy = CaseStrategy.SNAKE,
    dateTimeFormat = DateTimeFormat.ISO_LOCAL_DATE_TIME,
    includeNulls = false,
    explodeArrays = true
)
public record SearchReq(
    @ParamName("q") String keyword,           // 커스텀 키 이름
    Integer page,
    Integer size,
    @ParamPrefix("filter.") Filters filter,   // 중첩 객체 평탄화
    List<String> tags,                        // 컬렉션 지원
    @ParamConverter(PriceRangeConv.class)     // 커스텀 변환기
    PriceRange price
) {}

public record Filters(
    String brand,
    @UseJsonProperty                          // Jackson @JsonProperty 사용
    @JsonProperty("in_stock")
    Boolean inStock
) {}

// 사용
SearchReq req = new SearchReq(
    "running shoes",
    1,
    20,
    new Filters("Nike", true),
    List.of("sport", "outdoor"),
    new PriceRange(10.0, 100.0)
);

Map<String, List<String>> params = SearchReq__QParams.toQueryParams(req);
// 결과:
// {
//   "q": ["running+shoes"],
//   "page": ["1"],
//   "size": ["20"],
//   "filter.brand": ["Nike"],
//   "filter.in_stock": ["true"],
//   "tags": ["sport", "outdoor"],
//   "price_min": ["10.0"],
//   "price_max": ["100.0"]
// }
```

### 커스텀 변환기 구현

```java
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
```

## 지원되는 애노테이션

### @QueryParams (클래스/레코드 레벨)

| 속성 | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| `caseStrategy` | CaseStrategy | IDENTITY | 필드명 변환 전략 |
| `prefix` | String | "" | 모든 키에 추가할 접두사 |
| `includeNulls` | boolean | false | null 값 포함 여부 |
| `explodeArrays` | boolean | true | 배열을 여러 키로 분해 (k=a&k=b vs k=a,b) |
| `encoded` | boolean | false | 값이 이미 인코딩됨 |
| `dateTimeFormat` | DateTimeFormat | ISO_INSTANT | 날짜/시간 포맷 전략 |
| `pattern` | String | "" | 커스텀 날짜/시간 패턴 |
| `flattenNested` | boolean | false | 중첩 객체 평탄화 |
| `failOnDuplicateKeys` | boolean | false | 중복 키 발견 시 컴파일 오류 |

### 필드 레벨 애노테이션

- **@ParamName(String value)**: 커스텀 키 이름 지정
- **@ParamIgnore**: 필드 제외
- **@ParamPrefix(String value)**: 필드별 접두사
- **@ParamConverter(Class<?> converter)**: 커스텀 변환기 사용
- **@UseJsonProperty**: Jackson의 @JsonProperty 값을 키로 사용

## CaseStrategy 옵션

- `IDENTITY`: 원본 유지
- `CAMEL`: camelCase
- `SNAKE`: snake_case
- `KEBAB`: kebab-case
- `UPPER_SNAKE`: UPPER_SNAKE_CASE
- `PASCAL`: PascalCase

## 지원 타입

### 기본 타입
- 원시 타입 및 박싱 타입 (int, Integer, long, Long 등)
- String
- Enum
- BigDecimal, BigInteger

### 날짜/시간 타입
- LocalDate, LocalDateTime, LocalTime
- ZonedDateTime, OffsetDateTime
- Instant

### 컬렉션 타입
- List<T>, Set<T>, Collection<T>
- 배열 (T[])
- Optional<T>
- Map<String, ?> (키-값 쌍으로 평탄화)

### 복합 타입
- 중첩 DTO/Record (@ParamPrefix 사용)
- 커스텀 변환기 (@ParamConverter 사용)

## 생성된 코드 예시

입력:
```java
@QueryParams(caseStrategy = CaseStrategy.SNAKE)
public record TestParams(String myName, Integer myNumber) {}
```

생성된 코드:
```java
public final class TestParams__QParams {
    public static Map<String, List<String>> toQueryParams(TestParams source) {
        Map<String, List<String>> map = new LinkedHashMap<>();

        {
            String value = source.myName();
            if (value != null) {
                String strValue = String.valueOf(value);
                try {
                    String encoded = URLEncoder.encode(strValue, StandardCharsets.UTF_8.name());
                    map.put("my_name", List.of(encoded));
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        // ... myNumber 처리 ...

        return map;
    }
}
```

## 로드맵

- [ ] Spring의 MultiValueMap 직접 지원
- [ ] 쿼리 스트링 생성 메서드 (toQueryString())
- [ ] 더 많은 날짜/시간 포맷 옵션
- [ ] 검증(validation) 통합
- [ ] Kotlin 지원 (KSP)

## 라이선스

Apache License 2.0

## 기여

이슈와 PR을 환영합니다!
