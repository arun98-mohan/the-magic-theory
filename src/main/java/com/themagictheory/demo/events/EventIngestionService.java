package com.themagictheory.demo.events;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

@Service
public class EventIngestionService {

    private static final Set<String> VALID_TYPES = Set.of("exposure", "conversion");

    private final EventRepository eventRepository;

    public EventIngestionService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    /**
     * Validates and stores a batch. Invalid events are rejected individually
     * (with a reason) instead of failing the whole batch: an ingestion
     * endpoint fed by retrying plugins should accept everything it can.
     */
    public IngestResult ingest(List<EventRequest> events) {
        Set<String> knownVariants = eventRepository.knownVariantKeys();

        List<EventRequest> valid = new ArrayList<>();
        List<IngestResult.RejectedEvent> rejected = new ArrayList<>();
        for (EventRequest event : events) {
            String reason = validationError(event, knownVariants);
            if (reason == null) {
                valid.add(event);
            } else {
                rejected.add(new IngestResult.RejectedEvent(event.eventId(), reason));
            }
        }

        int inserted = valid.isEmpty() ? 0 : eventRepository.insertAll(valid);
        return new IngestResult(events.size(), inserted, valid.size() - inserted, rejected);
    }

    private String validationError(EventRequest event, Set<String> knownVariants) {
        if (isBlank(event.eventId())) {
            return "event_id is required";
        }
        if (isBlank(event.visitorId())) {
            return "visitor_id is required";
        }
        if (isBlank(event.experimentId())) {
            return "experiment_id is required";
        }
        if (isBlank(event.variant())) {
            return "variant is required";
        }
        if (event.type() == null || !VALID_TYPES.contains(event.type())) {
            return "type must be 'exposure' or 'conversion'";
        }
        if (event.timestamp() == null) {
            return "timestamp is required";
        }
        if (!knownVariants.contains(event.experimentId() + "|" + event.variant())) {
            return "unknown experiment/variant '%s'/'%s'; configure the experiment first"
                    .formatted(event.experimentId(), event.variant());
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
