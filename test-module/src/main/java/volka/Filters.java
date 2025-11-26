package volka;

import io.github.volkayun.queryparams.annotations.UseJsonProperty;

public record Filters(
        String brand,
        @UseJsonProperty
        @com.fasterxml.jackson.annotation.JsonProperty("in_stock")
        Boolean inStock
) {
}
