package com.themagictheory.demo.summary;

import com.themagictheory.demo.experiments.ExperimentRepository;
import com.themagictheory.demo.results.ResultsRepository;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class SummaryController {

    private final ExperimentRepository experimentRepository;
    private final ResultsRepository resultsRepository;
    private final GeminiSummaryService geminiSummaryService;

    public SummaryController(ExperimentRepository experimentRepository,
            ResultsRepository resultsRepository, GeminiSummaryService geminiSummaryService) {
        this.experimentRepository = experimentRepository;
        this.resultsRepository = resultsRepository;
        this.geminiSummaryService = geminiSummaryService;
    }

    @GetMapping("/api/experiments/{experimentId}/summary")
    public SummaryResponse summary(@PathVariable String experimentId) {
        if (!experimentRepository.exists(experimentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "unknown experiment '%s'".formatted(experimentId));
        }
        return geminiSummaryService.summarize(experimentId,
                resultsRepository.variantResults(experimentId));
    }
}
