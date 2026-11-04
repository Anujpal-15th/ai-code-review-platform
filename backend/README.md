# Backend (Spring Boot)

Owner: Anuj (Team Leader)

## Responsibilities
- REST API: see `docs/api-contract.md`
- Calls the runner for the student's code and the correct version, then finds the first difference between the two runs
- Calls the AI API with a strict-JSON prompt, validates the reply and retries once if invalid
- Checks that the AI's correct version passes the failing test before using it
- Calculates the score
- Saves submissions and results in PostgreSQL

## Build first
`POST /api/submissions` returning hardcoded sample data, so the frontend can start right away.

## Run locally
Needs Java 17+ and a PostgreSQL database. Set these environment variables, then run:

```
DB_URL=jdbc:postgresql://localhost:5432/mentorlens DB_USER=mentorlens DB_PASSWORD=... JWT_SECRET=<at least 32 random characters> ./mvnw spring-boot:run
```

Runs on port 8080. `./mvnw test` needs no database (it uses H2). Enable CORS for the frontend early.

## Secrets
Never commit API keys. Put them in environment variables or a local `.env` file (already in `.gitignore`).
