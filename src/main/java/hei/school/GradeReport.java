package hei.school;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Grade transcript of a student.
 */
@Component
public class GradeReport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public Map<String, Object> build(long studentId) {
        List<Map<String, Object>> students = jdbcTemplate.queryForList("SELECT * FROM student WHERE id = ?", studentId);
        if (students.isEmpty()) {
            return null;
        }
        Map<String, Object> student = students.get(0);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT e.title, e.deadline, e.max_points, e.timer_minutes, es.started_at, "
                        + "s.id AS submission_id, s.submitted_at, s.raw_points, s.feedback "
                        + "FROM exercise e "
                        + "LEFT JOIN exercise_start es ON es.exercise_id = e.id AND es.student_id = ? "
                        + "LEFT JOIN submission s ON s.exercise_id = e.id AND s.student_id = ? "
                        + "ORDER BY e.deadline NULLS LAST, e.title",
                studentId, studentId);

        List<Map<String, Object>> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int graded = 0;
        for (Map<String, Object> row : rows) {
            Map<String, Object> line = new LinkedHashMap<>();
            LocalDate deadline = row.get("deadline") == null ? null : ((Date) row.get("deadline")).toLocalDate();
            Integer timerMinutes = (Integer) row.get("timer_minutes");
            Timestamp started = (Timestamp) row.get("started_at");
            LocalDateTime startedAt = started == null ? null : started.toLocalDateTime();
            line.put("exercise", row.get("title"));
            line.put("deadline", deadline);
            if (timerMinutes != null) {
                line.put("timerMinutes", timerMinutes);
            }

            if (row.get("submission_id") == null) {
                boolean expired = deadline != null
                        ? deadline.isBefore(LocalDate.now())
                        : timerMinutes != null && startedAt != null
                                && LocalDateTime.now().isAfter(startedAt.plusMinutes(timerMinutes));
                line.put("status", expired ? "NON RENDU" : "À RENDRE");
                line.put("grade", null);
            } else {
                LocalDateTime submittedAt = ((Timestamp) row.get("submitted_at")).toLocalDateTime();
                line.put("submittedAt", submittedAt);
                boolean late = deadline != null && submittedAt.toLocalDate().isAfter(deadline);
                line.put("late", late);
                if (timerMinutes != null && startedAt != null) {
                    long millis = Duration.between(startedAt, submittedAt).toMillis();
                    line.put("durationMinutes", BigDecimal.valueOf(millis)
                            .divide(BigDecimal.valueOf(60000), 1, RoundingMode.HALF_UP));
                }
                if (row.get("raw_points") == null) {
                    line.put("status", "EN ATTENTE DE CORRECTION");
                    line.put("grade", null);
                } else {
                    BigDecimal raw = (BigDecimal) row.get("raw_points");
                    BigDecimal max = (BigDecimal) row.get("max_points");
                    BigDecimal penalty = BigDecimal.ZERO;
                    if (late) {
                        long lateMillis = Duration.between(deadline.plusDays(1).atStartOfDay(), submittedAt).toMillis();
                        long days = Math.max(1, (lateMillis + 86_399_999L) / 86_400_000L);
                        penalty = BigDecimal.valueOf(Math.min(days * 10L, 50L)).divide(BigDecimal.valueOf(100));
                    }
                    BigDecimal grade = raw.multiply(BigDecimal.valueOf(20))
                            .multiply(BigDecimal.ONE.subtract(penalty))
                            .divide(max, 1, RoundingMode.HALF_UP)
                            .max(BigDecimal.ZERO);
                    line.put("status", "CORRIGÉ");
                    line.put("grade", grade);
                    line.put("feedback", row.get("feedback"));
                    total = total.add(grade);
                    graded++;
                }
            }
            lines.add(line);
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reference", student.get("reference"));
        report.put("student", student.get("full_name"));
        report.put("email", student.get("email"));
        report.put("cohort", student.get("cohort"));
        report.put("exercises", lines);
        report.put("average", graded == 0 ? null : total.divide(new BigDecimal(graded), 2, RoundingMode.HALF_UP));
        return report;
    }
}
