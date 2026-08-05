package com.themagictheory.demo.experiments;

import java.util.List;

public record ExperimentSummary(String id, String name, List<String> variants) {
}
