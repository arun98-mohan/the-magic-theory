package com.themagictheory.demo.experiments;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/experiments")
public class ExperimentController {

    private final ExperimentRepository experimentRepository;

    public ExperimentController(ExperimentRepository experimentRepository) {
        this.experimentRepository = experimentRepository;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody ExperimentRequest request) {
        String problem = request.validationError();
        if (problem != null) {
            return ResponseEntity.badRequest().body(Map.of("error", problem));
        }
        boolean created = experimentRepository.create(request);
        if (!created) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "experiment '%s' already exists".formatted(request.id())));
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ExperimentSummary(request.id(), request.name(), request.variants()));
    }

    @GetMapping
    public List<ExperimentSummary> list() {
        return experimentRepository.findAll();
    }
}
