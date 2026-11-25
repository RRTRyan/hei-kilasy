package hei.school;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Grades export for the registrar's office (opened in Excel, semicolon separator).
 */
@RestController
public class CsvExport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/exports/grades.csv")
    public ResponseEntity<String> export() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT st.reference, st.full_name, st.email, st.cohort, e.title, e.deadline, s.submitted_at, s.raw_points, e.max_points, "
                        + "(s.submitted_at::date > e.deadline) AS late "
                        + "FROM submission s JOIN student st ON st.id = s.student_id JOIN exercise e ON e.id = s.exercise_id "
                        + "ORDER BY st.full_name, e.deadline");

        StringBuilder csv = new StringBuilder();
        csv.append("reference;etudiant;email;promotion;exercice;echeance;soumis_le;en_retard;note_sur_20\n");
        for (Map<String, Object> row : rows) {
            String note = "";
            if (row.get("raw_points") != null) {
                BigDecimal raw = (BigDecimal) row.get("raw_points");
                BigDecimal max = (BigDecimal) row.get("max_points");
                LocalDate deadline = ((Date) row.get("deadline")).toLocalDate();
                LocalDateTime submittedAt = ((Timestamp) row.get("submitted_at")).toLocalDateTime();
                BigDecimal penalty = BigDecimal.ZERO;
                if (submittedAt.toLocalDate().isAfter(deadline)) {
                    long lateMillis = Duration.between(deadline.plusDays(1).atStartOfDay(), submittedAt).toMillis();
                    long days = Math.max(1, (lateMillis + 86_399_999L) / 86_400_000L);
                    penalty = BigDecimal.valueOf(Math.min(days * 10L, 50L)).divide(BigDecimal.valueOf(100));
                }
                BigDecimal n = raw.multiply(BigDecimal.valueOf(20))
                        .multiply(BigDecimal.ONE.subtract(penalty))
                        .divide(max, 1, RoundingMode.HALF_UP)
                        .max(BigDecimal.ZERO);
                note = n.toPlainString().replace('.', ',');
            }
            csv.append(row.get("reference")).append(';')
                    .append(row.get("full_name")).append(';')
                    .append(row.get("email")).append(';')
                    .append(row.get("cohort")).append(';')
                    .append(row.get("title")).append(';')
                    .append(row.get("deadline")).append(';')
                    .append(row.get("submitted_at")).append(';')
                    .append(Boolean.TRUE.equals(row.get("late")) ? "oui" : "non").append(';')
                    .append(note).append('\n');
        }

        return ResponseEntity.ok()
                .header("Content-Type", "text/csv; charset=UTF-8")
                .header("Content-Disposition", "attachment; filename=\"notes-kilasy.csv\"")
                .body(csv.toString());
    }
}
