package com.themagictheory.demo.events;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EventRepository {

    private final JdbcTemplate jdbcTemplate;

    public EventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Inserts events idempotently: retries of an already-stored event_id are
     * no-ops, enforced by the primary key. Returns the number of rows that
     * were actually new; the remainder were duplicates.
     */
    public int insertAll(List<EventRequest> events) {
        int[] counts = jdbcTemplate.batchUpdate("""
                INSERT INTO events (event_id, visitor_id, experiment_id, variant, type, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                EventRequest event = events.get(i);
                ps.setString(1, event.eventId());
                ps.setString(2, event.visitorId());
                ps.setString(3, event.experimentId());
                ps.setString(4, event.variant());
                ps.setString(5, event.type());
                ps.setObject(6, event.timestamp());
            }

            @Override
            public int getBatchSize() {
                return events.size();
            }
        });
        return Arrays.stream(counts).sum();
    }

    /** Known (experiment_id, variant) pairs, as "experimentId|variant" keys. */
    public Set<String> knownVariantKeys() {
        List<String> keys = jdbcTemplate.query(
                "SELECT experiment_id, name FROM variants",
                (rs, rowNum) -> rs.getString("experiment_id") + "|" + rs.getString("name"));
        return new HashSet<>(keys);
    }
}
