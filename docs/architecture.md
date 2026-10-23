# Architecture

## System overview

```mermaid
flowchart LR
    U[User] --> D[Dashboard HTML / JS]
    D -->|POST /api/review| B[Spring Boot Backend]
    B --> DB[(PostgreSQL)]
    B -->|code + prompt| AI[AI API Claude / OpenAI]
    B -->|code file| PY[Python Analysis Service Pylint, Bandit]
    PY -->|findings JSON| B
    AI -->|issues JSON| B
    B -->|report| D
```

## Layers
- Presentation: dashboard with code box, language picker, report page.
- Application: Spring Boot REST API (controller, service, repository).
- Intelligence: AI API client, plus the Python analysis service from Version 2.
- Data: PostgreSQL for submissions, reports and issues.

## Request flow

```mermaid
sequenceDiagram
    participant U as User
    participant D as Dashboard
    participant B as Backend
    participant A as Python Analysis
    participant AI as AI API
    participant DB as Database
    U->>D: Paste code, choose language
    D->>B: POST /api/review
    B->>DB: Save submission
    B->>A: Send code (Version 2)
    A-->>B: Static findings JSON
    B->>AI: Code + findings + prompt
    AI-->>B: Issues, explanations, fixes
    B->>DB: Save report
    B-->>D: Score + issues
    D-->>U: Show report
```

## Backend flow

```mermaid
flowchart TD
    A[Receive request] --> B{Valid code and language?}
    B -- No --> C[Return error 400]
    B -- Yes --> D[Save submission]
    D --> E[Call analysis service]
    E --> F[Build AI prompt]
    F --> G[Call AI API]
    G --> H{Valid JSON response?}
    H -- No --> I[Retry once, then return error]
    H -- Yes --> J[Calculate quality score]
    J --> K[Save report and issues]
    K --> L[Return report to dashboard]
```

## Who owns what
| Part | Owner |
| --- | --- |
| Backend, AI call, database, score | Team Leader |
| Python analysis service | Aryan |
| Input page | Abhi |
| Report page | Rudra |

Parts talk to each other only through JSON over HTTP. See `api-contract.md`.

## Quality score
`Score = 100 - sum(weight x count)` per issue type. Security issues weigh the most, then bugs, then style problems. The score never goes below 0.
