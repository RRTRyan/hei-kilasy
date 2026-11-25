package hei.school;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExerciseController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/exercises")
    public List<Exercise> list() {
        return jdbcTemplate.query("SELECT * FROM exercise ORDER BY deadline", (rs, i) -> {
            Exercise e = new Exercise();
            e.setId(rs.getLong("id"));
            e.setTeacherId(rs.getLong("teacher_id"));
            e.setTitle(rs.getString("title"));
            e.setDescription(rs.getString("description"));
            e.setMaxPoints(rs.getDouble("max_points"));
            Date deadline = rs.getDate("deadline");
            if (deadline != null) {
                e.setDeadline(deadline.toLocalDate());
            }
            e.setTimerMinutes((Integer) rs.getObject("timer_minutes"));
            return e;
        });
    }

    @GetMapping("/exercises/{id}")
    public ResponseEntity<?> get(@PathVariable long id) {
        List<Exercise> found = jdbcTemplate.query("SELECT * FROM exercise WHERE id = ?", (rs, i) -> {
            Exercise e = new Exercise();
            e.setId(rs.getLong("id"));
            e.setTeacherId(rs.getLong("teacher_id"));
            e.setTitle(rs.getString("title"));
            e.setDescription(rs.getString("description"));
            e.setMaxPoints(rs.getDouble("max_points"));
            Date deadline = rs.getDate("deadline");
            if (deadline != null) {
                e.setDeadline(deadline.toLocalDate());
            }
            e.setTimerMinutes((Integer) rs.getObject("timer_minutes"));
            return e;
        }, id);
        if (found.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Exercice introuvable"));
        }
        return ResponseEntity.ok(found.get(0));
    }

    @PostMapping("/exercises")
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        String title = (String) body.get("title");
        if (title == null || title.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Le titre est obligatoire"));
        }
        Object deadlineValue = body.get("deadline");
        Object timerValue = body.get("timerMinutes");
        if (deadlineValue == null && timerValue == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Une échéance ou un minuteur est obligatoire"));
        }
        LocalDate deadline = null;
        if (deadlineValue != null) {
            try {
                deadline = LocalDate.parse((String) deadlineValue);
            } catch (Exception ex) {
                return ResponseEntity.badRequest().body(Map.of("error", "L'échéance doit être au format AAAA-MM-JJ"));
            }
            if (deadline.isBefore(LocalDate.now())) {
                return ResponseEntity.badRequest().body(Map.of("error", "L'échéance doit être dans le futur"));
            }
        }
        Integer timerMinutes = null;
        if (timerValue != null) {
            timerMinutes = ((Number) timerValue).intValue();
            if (timerMinutes <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "Le minuteur doit être positif"));
            }
        }
        double maxPoints = 20;
        if (body.get("maxPoints") != null) {
            maxPoints = ((Number) body.get("maxPoints")).doubleValue();
        }
        if (maxPoints <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Le barème doit être positif"));
        }
        // TODO: get the logged-in teacher once authentication exists
        long teacherId = 1;
        if (body.get("teacherId") != null) {
            teacherId = ((Number) body.get("teacherId")).longValue();
        }

        jdbcTemplate.update(
                "INSERT INTO exercise (teacher_id, title, description, max_points, deadline, timer_minutes) VALUES (?, ?, ?, ?, ?, ?)",
                teacherId, title, body.get("description"), maxPoints,
                deadline == null ? null : Date.valueOf(deadline), timerMinutes);
        Long id = jdbcTemplate.queryForObject("SELECT max(id) FROM exercise", Long.class);

        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("title", title);
        result.put("maxPoints", maxPoints);
        result.put("deadline", deadline);
        result.put("timerMinutes", timerMinutes);
        System.out.println("Nouvel exercice créé : " + title + " (id " + id + ")");
        return ResponseEntity.status(201).body(result);
    }

    @PostMapping("/exercises/{id}/start")
    public ResponseEntity<?> start(@PathVariable long id, @RequestBody Map<String, Object> body) {
        if (body.get("studentId") == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "studentId est obligatoire"));
        }
        long studentId = ((Number) body.get("studentId")).longValue();

        List<Map<String, Object>> exercises = jdbcTemplate.queryForList(
                "SELECT timer_minutes FROM exercise WHERE id = ?", id);
        if (exercises.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Exercice introuvable"));
        }
        Integer timerMinutes = (Integer) exercises.get(0).get("timer_minutes");
        if (timerMinutes == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cet exercice n'a pas de minuteur"));
        }
        List<Map<String, Object>> students = jdbcTemplate.queryForList("SELECT id FROM student WHERE id = ?", studentId);
        if (students.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Étudiant introuvable"));
        }

        jdbcTemplate.update("INSERT INTO exercise_start (exercise_id, student_id, started_at) VALUES (?, ?, now()) "
                + "ON CONFLICT (exercise_id, student_id) DO NOTHING", id, studentId);
        Timestamp startedAt = jdbcTemplate.queryForObject(
                "SELECT started_at FROM exercise_start WHERE exercise_id = ? AND student_id = ?",
                Timestamp.class, id, studentId);

        Map<String, Object> result = new HashMap<>();
        result.put("exerciseId", id);
        result.put("studentId", studentId);
        result.put("timerMinutes", timerMinutes);
        result.put("startedAt", startedAt.toLocalDateTime());
        result.put("endsAt", startedAt.toLocalDateTime().plusMinutes(timerMinutes));
        return ResponseEntity.ok(result);
    }
}
