package com.themagictheory.demo.summary;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.themagictheory.demo.results.ExperimentResults.VariantResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Produces a short plain-language summary of experiment results via Gemini.
 * The LLM is strictly optional: any misbehavior (missing key, timeout,
 * non-200, malformed response, empty or oversized output) degrades to a
 * deterministic summary computed from the numbers, never an error.
 */
@Service
public class GeminiSummaryService {

    private static final Logger log = LoggerFactory.getLogger(GeminiSummaryService.class);
    private static final int MAX_SUMMARY_CHARS = 700;

    private final String apiKey;
    private final String model;
    // Local mapper: only used for the small Gemini request/response payloads.
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public GeminiSummaryService(
            @Value("${app.gemini.api-key:}") String apiKey,
            @Value("${app.gemini.model:gemini-flash-latest}") String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    public SummaryResponse summarize(String experimentId, List<VariantResult> results) {
        if (apiKey.isBlank()) {
            log.warn("GEMINI_API_KEY not configured; serving fallback summary");
            return fallback(experimentId, results);
        }
        try {
            String text = callGemini(buildPrompt(experimentId, results));
            if (text == null || text.isBlank()) {
                log.warn("Gemini returned no usable text; serving fallback summary");
                return fallback(experimentId, results);
            }
            return new SummaryResponse(experimentId, tidy(text), "llm");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Gemini call interrupted; serving fallback summary");
            return fallback(experimentId, results);
        } catch (Exception e) {
            log.warn("Gemini call failed ({}); serving fallback summary", e.toString());
            return fallback(experimentId, results);
        }
    }

    private String buildPrompt(String experimentId, List<VariantResult> results) throws Exception {
        return """
                Summarize the results of A/B test experiment "%s" for a non-technical reader.
                Data (per variant): %s
                "exposed" is unique visitors who saw the variant; "conversions" is unique visitors who converted.
                Respond with 2-3 short sentences of plain text only: no markdown, no lists, no headings.
                Say which variant is ahead and by how much. Do not claim statistical significance.
                If no variant has been seen by any visitors, say the experiment has no data yet.
                """.formatted(experimentId, objectMapper.writeValueAsString(results));
    }

    private String callGemini(String prompt) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.putArray("contents").addObject().putArray("parts").addObject().put("text", prompt);
        ObjectNode generationConfig = body.putObject("generationConfig");
        generationConfig.put("temperature", 0.2);
        // Generous budget: current flash models spend a variable share of it
        // on internal "thinking" tokens that cannot be disabled.
        generationConfig.put("maxOutputTokens", 4096);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/"
                        + model + ":generateContent"))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Gemini responded with HTTP " + response.statusCode());
        }
        // Defensive traversal: any missing node yields "" and triggers the
        // fallback. Thinking models may split output across several parts.
        JsonNode root = objectMapper.readTree(response.body());
        String finishReason = root.path("candidates").path(0).path("finishReason").asText("");
        if (!"STOP".equals(finishReason)) {
            // e.g. MAX_TOKENS (truncated mid-sentence) or SAFETY: not usable.
            throw new IllegalStateException("Gemini finished with reason " + finishReason);
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode part : root.path("candidates").path(0).path("content").path("parts")) {
            if (!part.path("thought").asBoolean(false)) {
                text.append(part.path("text").asText(""));
            }
        }
        return text.toString();
    }

    /** Normalizes whitespace and caps runaway output. */
    private static String tidy(String text) {
        String cleaned = text.strip().replaceAll("\\s+", " ");
        if (cleaned.length() > MAX_SUMMARY_CHARS) {
            cleaned = cleaned.substring(0, MAX_SUMMARY_CHARS) + "…";
        }
        return cleaned;
    }

    /** Deterministic summary from the numbers; used whenever the LLM is unavailable. */
    private SummaryResponse fallback(String experimentId, List<VariantResult> results) {
        List<VariantResult> withData = results.stream()
                .filter(r -> r.exposed() > 0)
                .sorted(Comparator.comparing(VariantResult::conversionRate).reversed())
                .toList();

        String text;
        if (withData.isEmpty()) {
            text = "Experiment %s has no exposure data yet, so there are no results to summarize."
                    .formatted(experimentId);
        } else if (withData.size() == 1) {
            VariantResult only = withData.get(0);
            text = "Only variant '%s' of experiment %s has data so far: %d unique visitors and %d conversions (%s)."
                    .formatted(only.variant(), experimentId, only.exposed(), only.conversions(),
                            percent(only));
        } else {
            VariantResult leader = withData.get(0);
            VariantResult runnerUp = withData.get(1);
            text = ("In experiment %s, variant '%s' is ahead with a %s conversion rate "
                    + "(%d of %d unique visitors), compared with '%s' at %s (%d of %d).")
                    .formatted(experimentId, leader.variant(), percent(leader),
                            leader.conversions(), leader.exposed(),
                            runnerUp.variant(), percent(runnerUp),
                            runnerUp.conversions(), runnerUp.exposed());
            if (withData.size() > 2) {
                text += " %d other variant(s) trail behind.".formatted(withData.size() - 2);
            }
        }
        return new SummaryResponse(experimentId, text, "fallback");
    }

    private static String percent(VariantResult r) {
        return String.format(Locale.US, "%.1f%%", r.conversionRate().doubleValue() * 100);
    }
}
