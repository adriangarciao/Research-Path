-- Deliberately broken migration for MigrationFaultTests (never on the main classpath location).
CREATE TABLE half_done (id INTEGER PRIMARY KEY);
INSERT INTO half_done VALUES (1);
ALTER TABLE does_not_exist ADD COLUMN x INTEGER;
