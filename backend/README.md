# Backend (Spring Boot)

Owner: Team Leader

## Responsibilities
- REST API: `POST /api/review`
- Calls the AI API with a strict-JSON prompt, validates the reply, retries once if invalid
- Calculates the quality score
- Saves submissions and reports in PostgreSQL
- Calls the Python analysis service (Version 2)

## Run locally
Add the Spring Boot project here (Spring Initializr: Web, Validation, Spring Data JPA, PostgreSQL Driver), then:

```
./mvnw spring-boot:run
```

Runs on port 8080. Enable CORS for the frontend early.

## Secrets
Never commit API keys. Put them in environment variables or a local `.env` file (already in `.gitignore`).
