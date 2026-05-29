# Sequence Diagrams – Public Transport Tracker

All diagrams are in [Mermaid](https://mermaid.js.org/) format.
Render at https://mermaid.live or import to draw.io.

---

## 1. Normal Flow – Live Data Fetch

```mermaid
sequenceDiagram
    participant UI   as React Frontend
    participant GW   as nginx (Port 80)
    participant API  as TransportController
    participant SVC  as TransportService
    participant CSH  as CacheService
    participant MTA  as MtaApiClient
    participant TL   as TransitLandApiClient
    participant ALT  as AlertService

    UI->>GW:  GET /api/v1/transport?city=nyc&routeId=A
    GW->>API: Proxy request
    API->>SVC: getTransportData("nyc","A", null)

    SVC->>CSH: get("nyc:A")
    CSH-->>SVC: Optional.empty (cache miss)

    SVC->>MTA: isAvailable()
    MTA-->>SVC: true

    SVC->>MTA: fetchVehicleLocations("nyc","A")
    MTA->>MTA: HTTP GET /api/schedule/A/vehicles.json
    MTA-->>SVC: List<VehicleLocation>

    SVC->>MTA: fetchServiceAlerts("nyc")
    MTA-->>SVC: List<ServiceAlert>

    SVC->>MTA: fetchCrowdingInfo("A")
    MTA-->>SVC: List<CrowdingInfo>

    SVC->>ALT: evaluate(transportData)
    ALT-->>SVC: List<AlertMessage> [DELAY warning triggered]

    SVC->>CSH: put("nyc:A", transportData)
    CSH-->>SVC: cached (TTL=300s)

    SVC-->>API: TransportResponse{data, metadata{source=LIVE}, _links}
    API-->>GW:  200 OK + JSON
    GW-->>UI:   200 OK + JSON

    UI->>UI: Render VehicleMap, ArrivalBoard, AlertBanner
```

---

## 2. Cache Hit Flow

```mermaid
sequenceDiagram
    participant UI  as React Frontend
    participant API as TransportController
    participant SVC as TransportService
    participant CSH as CacheService

    UI->>API: GET /api/v1/transport?city=nyc&routeId=A

    API->>SVC: getTransportData("nyc","A", null)
    SVC->>CSH: get("nyc:A")
    CSH-->>SVC: Optional.of(cachedData) [age=120s]

    Note over SVC: Cache hit – no upstream call made

    SVC-->>API: TransportResponse{metadata{source=CACHE, cacheAgeSeconds=120}}
    API-->>UI:  200 OK (< 50ms)
```

---

## 3. API Failure – Stale Cache Fallback

```mermaid
sequenceDiagram
    participant UI  as React Frontend
    participant SVC as TransportService
    participant CSH as CacheService
    participant MTA as MtaApiClient
    participant TL  as TransitLandApiClient
    participant MCK as MockDataService

    UI->>SVC: getTransportData("nyc","A", null)

    SVC->>CSH: get("nyc:A")
    CSH-->>SVC: Optional.empty (TTL expired)

    SVC->>MTA: isAvailable()
    MTA-->>SVC: false (timeout / 5xx)

    SVC->>TL: isAvailable()
    TL-->>SVC: false (timeout)

    Note over SVC: All APIs down → TransitApiException thrown

    SVC->>CSH: getStale("nyc:A")
    CSH-->>SVC: Optional.of(staleData) [age=450s, within 3600s window]

    Note over SVC: Serving stale data with warning

    SVC-->>UI: TransportResponse{metadata{source=STALE_CACHE, cacheAge=450}}

    UI->>UI: Show stale data banner: "Live API unavailable – cached data (450s old)"
```

---

## 4. Offline Mode Flow

```mermaid
sequenceDiagram
    participant UI  as React Frontend
    participant SVC as TransportService
    participant MCK as MockDataService
    participant ALT as AlertService

    Note over UI: User toggles Offline Mode ON

    UI->>SVC: getTransportData("nyc","A", offline=true)

    Note over SVC: Offline mode intercepted immediately
    SVC->>MCK: getMockData("nyc","A")
    MCK->>MCK: Load from mock-data/*.json (or generate synthetic)
    MCK-->>SVC: TransportData (mock vehicles, arrivals, alerts)

    SVC->>ALT: evaluate(mockData)
    ALT-->>SVC: List<AlertMessage> [DELAY, DISRUPTION, CROWDING, WEATHER triggered by mock data]

    SVC-->>UI: TransportResponse{metadata{source=MOCK, offlineMode=true}}

    UI->>UI: Show "Offline mode active – showing sample data" banner
    UI->>UI: Render mock vehicles, arrivals, alerts
```

---

## 5. Route Planning Flow

```mermaid
sequenceDiagram
    participant UI  as React Frontend
    participant API as TransportController
    participant SVC as TransportService
    participant PLN as RoutePlannerService
    participant MTA as MtaApiClient

    UI->>API: GET /api/v1/transport/plan?from=Times+Square&to=Atlantic+Av&city=nyc

    API->>SVC: getRoutePlans("Times Square","Atlantic Av","nyc", null)

    SVC->>MTA: fetchServiceAlerts("nyc")
    MTA-->>SVC: [Disruption: A/C suspended]

    SVC->>MTA: fetchArrivalPredictions("Times Square", null)
    MTA-->>SVC: [16-min delay on A train]

    SVC->>PLN: plan("Times Square","Atlantic Av","nyc", alerts, arrivals)

    PLN->>PLN: Build direct plan (DISRUPTED – A train affected)
    PLN->>PLN: Build alternative plan (via F train, 1 transfer)
    PLN->>PLN: calculateConfidence() → 0.60 (disruption penalty)

    PLN-->>SVC: [RoutePlan{DISRUPTED,0 transfers}, RoutePlan{ALTERNATIVE,1 transfer}]
    SVC-->>API: TransportResponse{data: plans, _links}
    API-->>UI:  200 OK

    UI->>UI: Show both route options, highlight DISRUPTED warning
```

---

## 6. Conditional Alert Evaluation

```mermaid
sequenceDiagram
    participant SVC as TransportService
    participant ALT as AlertService

    SVC->>ALT: evaluate(transportData)

    ALT->>ALT: checkVehicleDelays(vehicles)
    Note over ALT: VEH-A-001: delaySeconds=960 > 900 (15 min threshold)
    ALT->>ALT: → ADD AlertMessage{DELAY, "Significant delays - Plan accordingly"}

    ALT->>ALT: checkServiceDisruptions(alerts)
    Note over ALT: Alert type=DISRUPTION found
    ALT->>ALT: → ADD AlertMessage{DISRUPTION, "Service alert - Check alternative routes"}

    ALT->>ALT: checkCrowding(crowding)
    Note over ALT: CrowdingInfo{level=FULL}
    ALT->>ALT: → ADD AlertMessage{CROWDING, "Vehicle at capacity - Consider next service"}

    ALT->>ALT: checkWeatherAlerts(alerts)
    Note over ALT: Alert cause=WEATHER found
    ALT->>ALT: → ADD AlertMessage{WEATHER, "Weather impact on schedule"}

    ALT-->>SVC: [DELAY, DISRUPTION, CROWDING, WEATHER alerts]
```

---

## 7. CI/CD Pipeline

```mermaid
sequenceDiagram
    participant Dev  as Developer
    participant GH   as GitHub
    participant CI   as GitHub Actions
    participant Reg  as Container Registry
    participant Prod as Production Server

    Dev->>GH:  git push origin main

    GH->>CI:   Trigger workflow

    CI->>CI:   Stage 1: backend-ci
    CI->>CI:   gradle clean test jacocoTestReport bootJar
    CI->>CI:   Publish JUnit results & coverage report

    CI->>CI:   Stage 2: frontend-ci (parallel)
    CI->>CI:   npm ci && npm test && npm run build

    CI->>CI:   Stage 3: docker-publish (needs both CI jobs)
    CI->>Reg:  docker push transport-backend:latest + SHA
    CI->>Reg:  docker push transport-frontend:latest + SHA

    CI->>Prod: SSH: docker-compose pull && docker-compose up -d

    Prod-->>CI: Health check passes

    CI-->>GH:  ✓ Workflow complete
    GH-->>Dev: Pipeline success notification
```