package com.themagictheory.demo.experiments;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ExperimentRepository {

    private final JdbcClient jdbcClient;

    public ExperimentRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** Creates the experiment with its variants. Returns false if the id already exists. */
    @Transactional
    public boolean create(ExperimentRequest request) {
        int inserted = jdbcClient
                .sql("INSERT INTO experiments (id, name) VALUES (:id, :name) ON CONFLICT (id) DO NOTHING")
                .param("id", request.id())
                .param("name", request.name())
                .update();
        if (inserted == 0) {
            return false;
        }
        for (String variant : request.variants()) {
            jdbcClient
                    .sql("INSERT INTO variants (experiment_id, name) VALUES (:experimentId, :name)")
                    .param("experimentId", request.id())
                    .param("name", variant)
                    .update();
        }
        return true;
    }

    public boolean exists(String experimentId) {
        return jdbcClient
                .sql("SELECT EXISTS (SELECT 1 FROM experiments WHERE id = :id)")
                .param("id", experimentId)
                .query(Boolean.class)
                .single();
    }

    public List<ExperimentSummary> findAll() {
        return jdbcClient
                .sql("""
                        SELECT e.id, e.name, ARRAY_AGG(v.name ORDER BY v.name) AS variants
                        FROM experiments e
                        JOIN variants v ON v.experiment_id = e.id
                        GROUP BY e.id, e.name
                        ORDER BY e.id
                        """)
                .query((rs, rowNum) -> new ExperimentSummary(
                        rs.getString("id"),
                        rs.getString("name"),
                        List.of((String[]) rs.getArray("variants").getArray())))
                .list();
    }
}
