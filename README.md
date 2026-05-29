# Public Transport Tracker

A production-ready microservice for real-time public transport tracking.  
**Stack:** Java 17 · Spring Boot 3.2 · Gradle · React 18 · Docker · GitHub Actions · Jenkins

---

## Quick Start

```bash
# 1. Copy and configure environment
cp .env.example .env
# Edit .env — MTA_API_KEY is optional; add TRANSITLAND_API_KEY if desired

# 2. Start all services
docker-compose up -d --build

# 3. Open the app
open http://localhost          # React UI
open http://localhost:8080/swagger-ui/index.html  # API docs
```

---

## Architecture

```
Frontend (React/nginx :80)
    │  HTTP proxy /api/*
    ▼
Backend (Spring Boot :8080)
    │  Java HttpClient
    ├──▶ NYC MTA API      (https://api.mta.info/)
    ├──▶ Transit.land API (https://transit.land/api/v2/rest)
    └──▶ TfL API          (https://api.tfl.gov.uk/)
```

**Chosen Implementation: Option A – Resilience & Offline Mode**

The service never returns an error to the end user. Degradation chain:

```
1. Offline toggle ON       → return mock data immediately
2. Fresh cache hit         → return cached data (TTL: 5 min)
3. Live API call succeeds  → cache + return live data
4. API fails + stale cache → return stale data (up to 1 hr old)
5. No stale data           → return rich mock data
```

**Provider Selection Logic:**
- **London**: TfL → TransitLand → MTA → SEPTA
- **NYC**: MTA → TransitLand → TfL → SEPTA
- **Philly**: SEPTA → TransitLand → TfL → MTA
- **Other cities**: TransitLand → TfL → MTA → SEPTA

---

## Features

| Feature | Status |
|---------|--------|
| Real-time vehicle positions | ✅ |
| Arrival predictions with ETA | ✅ |
| Service disruption alerts | ✅ |
| Route planning with alternatives | ✅ |
| Crowding level indicators | ✅ |
| Conditional alerts (delay/disruption/crowding/weather) | ✅ |
| Offline mode toggle | ✅ |
| In-memory cache with TTL (no 3rd party) | ✅ |
| Stale data fallback | ✅ |
| HATEOAS / HAL responses | ✅ |
| OpenAPI / Swagger UI | ✅ |
| Docker + Docker Compose | ✅ |
| GitHub Actions CI/CD | ✅ |
| Jenkins pipeline | ✅ |
| TDD (JUnit 5 + Mockito) | ✅ |

---

## Conditional Alerts

| Condition | Threshold | Message |
|-----------|-----------|---------|
| Vehicle delay | > 15 minutes | "Significant delays - Plan accordingly" |
| Service disruption | Any DISRUPTION/SUSPENSION | "Service alert - Check alternative routes" |
| Crowding | Level HIGH or FULL | "Vehicle at capacity - Consider next service" |
| Weather | Cause = WEATHER | "Weather impact on schedule" |

---

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/transport` | Full transport data |
| GET | `/api/v1/transport/vehicles` | Vehicle locations |
| GET | `/api/v1/transport/arrivals` | Arrival predictions |
| GET | `/api/v1/transport/alerts` | Service alerts |
| GET | `/api/v1/transport/plan` | Route planning |
| GET | `/api/v1/transport/crowding` | Crowding levels |
| GET | `/api/v1/cache/stats` | Cache statistics |
| DELETE | `/api/v1/cache` | Clear all cache |
| GET | `/actuator/health` | Health check |

Full documentation: [`docs/API.md`](docs/API.md)

---

## Running Locally (without Docker)

### Backend
```bash
cd backend

# Set environment variables (MTA key is optional — public endpoints work without it)
export TRANSITLAND_API_KEY=your-key

# Run with dev profile (uses demo keys)
./gradlew bootRun --args='--spring.profiles.active=dev'

# Run tests
./gradlew test
```

### Frontend
```bash
cd frontend
npm install
npm start          # http://localhost:3000
npm test           # run tests
npm run build      # production build
```

---

## Configuration

All configuration is via environment variables (12-Factor):

| Variable | Default | Description |
|----------|---------|-------------|
| `MTA_API_KEY` | _(empty)_ | NYC MTA API key (optional — public endpoints work without it) |
| `TRANSITLAND_API_KEY` | `demo-key` | Transit.land API key |
| `TFL_API_KEY` | _(empty)_ | TfL API key (optional — public endpoints work without it) |
| `CACHE_TTL_SECONDS` | `300` | Cache TTL (5 minutes) |
| `CACHE_STALE_TTL_SECONDS` | `3600` | Stale data window (1 hour) |
| `CACHE_MAX_SIZE` | `1000` | Max cache entries |
| `OFFLINE_MODE` | `false` | Global offline mode |
| `SPRING_PROFILE` | `prod` | Spring profile: dev/prod |

---

## Data Sources

- **NYC MTA:** https://api.mta.info/ – real-time NYC subway/bus data  
  Register: https://api.mta.info/#/landing
- **Transit.land:** https://transit.land/api/v2/rest – global transit data aggregator  
  Register: https://www.transit.land/

> Without API keys, the service automatically serves realistic mock data.  
> Set `OFFLINE_MODE=true` to always use mock data during development.

---

## Project Structure

```
.
├── backend/                   Spring Boot service
│   ├── src/main/java/         Source code
│   ├── src/main/resources/    Config + mock data
│   ├── src/test/              Unit + integration tests
│   ├── Dockerfile
│   └── build.gradle
├── frontend/                  React app
│   ├── src/
│   ├── Dockerfile
│   └── nginx.conf
├── docs/
│   ├── LLD.md                 Low-level design
│   ├── API.md                 API reference
│   ├── sequence-diagram.md    Mermaid sequence diagrams
│   └── design-patterns.md     Patterns and rationale
├── .github/workflows/ci-cd.yml  GitHub Actions
├── Jenkinsfile                Jenkins pipeline
├── docker-compose.yml
├── .env.example
└── README.md
```

---

## CI/CD

**GitHub Actions** (`.github/workflows/ci-cd.yml`):
1. Backend: `gradle test` + `jacocoTestReport`
2. Frontend: `npm test` + `npm run build`
3. Docker: build and push to GitHub Container Registry (on `main`)
4. OWASP dependency check

**Jenkins** (`Jenkinsfile`):
- Same stages, deployable to self-hosted Jenkins
- SSH deployment to production via `docker-compose`

---

## Security

- API keys stored in environment variables – never in code
- Stateless REST (no sessions, CSRF disabled)
- Security headers: `X-Content-Type-Options`, `X-Frame-Options`
- CORS restricted to explicit origins
- Input validation with `@NotBlank` and `@Validated`
- Non-root Docker user
- OWASP Top 10 addressed in design

---

## Documentation Index

| Document | Path |
|----------|------|
| Low-Level Design | [`docs/LLD.md`](docs/LLD.md) |
| API Reference | [`docs/API.md`](docs/API.md) |
| Sequence Diagrams | [`docs/sequence-diagram.md`](docs/sequence-diagram.md) |
| Design Patterns | [`docs/design-patterns.md`](docs/design-patterns.md) |
| Swagger UI | `http://localhost:8080/swagger-ui/index.html` |
