package com.themagictheory.demo.results;

import java.math.BigDecimal;
import java.util.List;

public record ExperimentResults(String experimentId, List<VariantResult> variants) {

    /** conversionRate is null when nobody has been exposed yet. */
    public record VariantResult(String variant, long exposed, long conversions, BigDecimal conversionRate) {
    }
}
