package com.themagictheory.demo.events;

import java.util.List;

public record IngestResult(int received, int inserted, int duplicates, List<RejectedEvent> rejected) {

    public record RejectedEvent(String eventId, String reason) {
    }
}
