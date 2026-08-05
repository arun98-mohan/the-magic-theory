package com.themagictheory.demo.events;

import java.time.OffsetDateTime;

/** Shape sent by the site plugin; JSON field names are snake_case (event_id, ...). */
public record EventRequest(
        String eventId,
        String visitorId,
        String experimentId,
        String variant,
        String type,
        OffsetDateTime timestamp) {
}
