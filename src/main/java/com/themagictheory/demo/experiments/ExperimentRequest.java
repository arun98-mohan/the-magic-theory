package com.themagictheory.demo.experiments;

import java.util.List;

public record ExperimentRequest(String id, String name, List<String> variants) {

    /** Returns a human-readable problem, or null if the request is valid. */
    public String validationError() {
        if (id == null || id.isBlank()) {
            return "id is required";
        }
        if (name == null || name.isBlank()) {
            return "name is required";
        }
        if (variants == null || variants.isEmpty()) {
            return "at least one variant is required";
        }
        if (variants.stream().anyMatch(v -> v == null || v.isBlank())) {
            return "variant names must not be blank";
        }
        if (variants.stream().distinct().count() != variants.size()) {
            return "variant names must be unique";
        }
        return null;
    }
}
