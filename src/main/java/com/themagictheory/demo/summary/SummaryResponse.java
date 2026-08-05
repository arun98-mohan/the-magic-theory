package com.themagictheory.demo.summary;

/** source is "llm" when Gemini produced the text, "fallback" when it was computed locally. */
public record SummaryResponse(String experimentId, String summary, String source) {
}
