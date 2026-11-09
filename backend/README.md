# Backend (Spring Boot)

Owner: Anuj (Team Leader)

## Responsibilities
- REST API: see `docs/api-contract.md`
- Calls the runner for the student's code and the correct version, then finds the first difference between the two runs
- Calls the AI API with a strict-JSON prompt, validates the reply and retries once if invalid
- Checks that the AI's correct version passes the failing test before using it
- Calculates the score
- Saves submissions and results in PostgreSQL

## Run locally
Needs Java 17+ and a PostgreSQL database. Set these environment variables, then run:

```
DB_URL=jdbc:postgresql://localhost:5432/mentorlens DB_USER=mentorlens DB_PASSWORD=... JWT_SECRET=<at least 32 random characters> ./mvnw spring-boot:run
```

Runs on port 8080. `./mvnw test` needs no database (it uses H2).

Optional variables:

| Variable | Default | What it does |
| --- | --- | --- |
| `AI_API_KEY` | none | Key for the AI API. Without it, results have no AI feedback. |
| `app.runner.url` (or `APP_RUNNER_URL`) | `http://localhost:8000` | Address of the runner service. |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Comma-separated exact origins allowed to call the API. A wildcard stops startup. |
| `ALLOWED_EMAIL_DOMAIN` | none | When set, only emails at exactly this domain can register. When unset, registration is open. |

Limits on `/api/auth/**` are per IP, in memory, and for one instance. Behind a trusted proxy, set `server.forward-headers-strategy=native` so the limit sees real client addresses. Do not use `framework`, and do not set this without a trusted proxy in front: clients could then fake their address.

## Pilot groups
Assign pilot groups with `scripts/set-pilot-group.sql` before students start. The group is applied when a submission is processed, so results that already exist do not change and a student moved later will have seen both kinds of feedback.

## Secrets
Never commit API keys. Put them in environment variables or a local `.env` file (already in `.gitignore`).
