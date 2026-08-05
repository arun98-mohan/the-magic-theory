package com.themagictheory.demo.results;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ResultsRepository {

    private final JdbcClient jdbcClient;

    public ResultsRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Unique-visitor counts computed on read, straight from the raw events.
     * COUNT(DISTINCT visitor_id) collapses repeat exposures and repeat
     * conversions to one per visitor; the LEFT JOIN keeps variants with no
     * events yet visible with zero counts.
     */
    public List<ExperimentResults.VariantResult> variantResults(String experimentId) {
        return jdbcClient
                .sql("""
                        SELECT v.name AS variant,
                               COUNT(DISTINCT e.visitor_id) FILTER (WHERE e.type = 'exposure')   AS exposed,
                               COUNT(DISTINCT e.visitor_id) FILTER (WHERE e.type = 'conversion') AS conversions
                        FROM variants v
                        LEFT JOIN events e
                               ON e.experiment_id = v.experiment_id AND e.variant = v.name
                        WHERE v.experiment_id = :experimentId
                        GROUP BY v.name
                        ORDER BY v.name
                        """)
                .param("experimentId", experimentId)
                .query((rs, rowNum) -> {
                    long exposed = rs.getLong("exposed");
                    long conversions = rs.getLong("conversions");
                    return new ExperimentResults.VariantResult(
                            rs.getString("variant"), exposed, conversions, rate(conversions, exposed));
                })
                .list();
    }

    private static BigDecimal rate(long conversions, long exposed) {
        if (exposed == 0) {
            return null;
        }
        return BigDecimal.valueOf(conversions)
                .divide(BigDecimal.valueOf(exposed), 4, RoundingMode.HALF_UP);
    }
}
