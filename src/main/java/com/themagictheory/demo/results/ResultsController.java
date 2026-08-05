package com.themagictheory.demo.results;

import com.themagictheory.demo.experiments.ExperimentRepository;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ResultsController {

    private final ResultsRepository resultsRepository;
    private final ExperimentRepository experimentRepository;

    public ResultsController(ResultsRepository resultsRepository, ExperimentRepository experimentRepository) {
        this.resultsRepository = resultsRepository;
        this.experimentRepository = experimentRepository;
    }

    @GetMapping("/api/experiments/{experimentId}/results")
    public ExperimentResults results(@PathVariable String experimentId) {
        if (!experimentRepository.exists(experimentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "unknown experiment '%s'".formatted(experimentId));
        }
        return new ExperimentResults(experimentId, resultsRepository.variantResults(experimentId));
    }
}
