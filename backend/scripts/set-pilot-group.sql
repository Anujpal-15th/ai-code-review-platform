-- Assign a student to a pilot group. Run by an admin against the MentorLens database.
-- Groups: STEP_BASED (step comparison + AI explanation) or EXPLANATION_ONLY (AI explanation alone).
-- A student with no group (NULL) is treated as STEP_BASED.
-- The group is not exposed by any API, so students cannot choose or see it.
-- Replace the placeholders before running; email is stored lower-case.

UPDATE users
SET pilot_group = 'EXPLANATION_ONLY'   -- or 'STEP_BASED'
WHERE email = 'student@example.com';   -- <-- the student's email

-- Check it took effect (expect 1 row):
SELECT email, pilot_group, consented_at FROM users WHERE email = 'student@example.com';
