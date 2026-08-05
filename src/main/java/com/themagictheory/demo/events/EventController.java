package com.themagictheory.demo.events;

import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventIngestionService eventIngestionService;

    public EventController(EventIngestionService eventIngestionService) {
        this.eventIngestionService = eventIngestionService;
    }

    /** Accepts a single event object or a JSON array of events. */
    @PostMapping
    public IngestResult ingest(@RequestBody List<EventRequest> events) {
        return eventIngestionService.ingest(events);
    }
}
