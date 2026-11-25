CREATE TABLE IF NOT EXISTS teacher
(
    id        BIGSERIAL PRIMARY KEY,
    reference VARCHAR(20)  NOT NULL UNIQUE,
    full_name VARCHAR(100) NOT NULL,
    email     VARCHAR(150) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS student
(
    id        BIGSERIAL PRIMARY KEY,
    reference VARCHAR(20)  NOT NULL UNIQUE,
    full_name VARCHAR(100) NOT NULL,
    email     VARCHAR(150) NOT NULL UNIQUE,
    phone     VARCHAR(20),
    cohort    VARCHAR(20)
);

CREATE TABLE IF NOT EXISTS exercise
(
    id          BIGSERIAL PRIMARY KEY,
    teacher_id  BIGINT       NOT NULL REFERENCES teacher (id),
    title       VARCHAR(200) NOT NULL,
    description TEXT,
    max_points  NUMERIC(5, 2) NOT NULL DEFAULT 20,
    deadline    DATE,
    timer_minutes INT,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT exercise_deadline_or_timer CHECK (deadline IS NOT NULL OR timer_minutes IS NOT NULL)
);

CREATE TABLE IF NOT EXISTS submission
(
    id           BIGSERIAL PRIMARY KEY,
    exercise_id  BIGINT    NOT NULL REFERENCES exercise (id),
    student_id   BIGINT    NOT NULL REFERENCES student (id),
    content      TEXT,
    repo_url     VARCHAR(300),
    submitted_at TIMESTAMP NOT NULL DEFAULT now(),
    raw_points   NUMERIC(5, 2),
    feedback     TEXT,
    graded_at    TIMESTAMP,
    UNIQUE (exercise_id, student_id)
);

CREATE TABLE IF NOT EXISTS exercise_start
(
    exercise_id BIGINT NOT NULL REFERENCES exercise (id),
    student_id  BIGINT NOT NULL REFERENCES student (id),
    started_at  TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (exercise_id, student_id)
);

-- Idempotent upgrade for databases created before the timer feature.
ALTER TABLE exercise ADD COLUMN IF NOT EXISTS timer_minutes INT;
ALTER TABLE exercise ALTER COLUMN deadline DROP NOT NULL;
ALTER TABLE exercise DROP CONSTRAINT IF EXISTS exercise_deadline_or_timer;
ALTER TABLE exercise ADD CONSTRAINT exercise_deadline_or_timer CHECK (deadline IS NOT NULL OR timer_minutes IS NOT NULL);
