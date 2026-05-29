# Backend Explanation — Written for Everyone

> This document explains every backend file, every decision, and every line of code in plain English.  
> No prior programming knowledge is assumed.

---

## Table of Contents

1. [What Is a "Backend"?](#1-what-is-a-backend)
2. [Project Setup Files](#2-project-setup-files)
3. [The Starting Point — Application Entry](#3-the-starting-point)
4. [Configuration Files — How Settings Work](#4-configuration-files)
5. [Data Models — The Shape of Information](#5-data-models)
6. [API Clients — Talking to External Services](#6-api-clients)
7. [Controllers — The Front Door](#7-controllers)
8. [Error Handling — What Happens When Things Go Wrong](#8-error-handling)
9. [Cache System — Remembering Data](#9-cache-system)
10. [Security — Keeping Things Safe](#10-security)
11. [Testing — Making Sure It Works](#11-testing)
12. [Docker & Deployment — Packaging and Shipping](#12-docker-and-deployment)

---

## 1. What Is a "Backend"?

Imagine a restaurant:
- The **frontend** is the dining room — what customers see and interact with (menus, tables, waiters).
- The **backend** is the kitchen — where the actual work happens (cooking, plating, managing ingredients).

Our backend is the kitchen. It:
- Receives requests from the user's browser ("I want subway data for NYC")
- Fetches data from real transit systems (like NYC's MTA)
- Processes and enriches that data (adds alerts, crowding info)
- Sends it back in a neat package

We chose **Java** with **Spring Boot** because:
- Java is one of the most widely used languages in enterprise software — it's reliable, fast, and well-tested
- Spring Boot is a framework (a toolkit) that removes 80% of the boring setup work — it handles web servers, security, configuration, and more out of the box
- Together they are the industry standard for building REST APIs (the way frontends talk to backends)

---

## 2. Project Setup Files

### `build.gradle` — The Shopping List

Think of this as a shopping list for the project. It says "I need these ingredients (libraries) to build this application."

```
plugins {
    id 'java'                                           // We're writing Java code
    id 'org.springframework.boot' version '3.2.3'       // Use Spring Boot toolkit v3.2.3
    id 'io.spring.dependency-management' version '1.1.4' // Automatically match compatible versions
}

group = 'com.transport'        // Our company/project namespace
version = '1.0.0'             // Version 1.0.0 of our app
sourceCompatibility = '17'     // Use Java 17 (a modern, stable version)
```

**Why Java 17?** It's a "Long Term Support" (LTS) release — meaning it receives security updates for years. Companies use LTS versions because they're stable and supported.

**Dependencies (ingredients):**

| Dependency | What It Does | Why We Need It |
|---|---|---|
| `spring-boot-starter-web` | Gives us a web server that can receive and respond to HTTP requests | Without this, our app can't talk to browsers |
| `spring-boot-starter-actuator` | Adds health-check endpoints (`/actuator/health`) | Docker and load balancers ping this to know if the app is alive |
| `spring-boot-starter-validation` | Allows us to say "this field is required" or "this must be a number" | Prevents bad data from entering the system |
| `spring-boot-starter-security` | Adds security features (headers, CSRF protection, authentication hooks) | Protects against common web attacks |
| `springdoc-openapi-starter-webmvc-ui` | Auto-generates interactive API documentation (Swagger UI) | Developers can test the API in a browser without writing code |
| `jackson-datatype-jsr310` | Teaches JSON serializer how to handle dates/times | Without this, dates like "2026-04-22T10:30:00Z" would break |
| `lombok` | Auto-generates repetitive code (getters, setters, constructors) | Reduces boilerplate from 50 lines to 5 |

**Why no third-party HTTP library (like OkHttp, RestTemplate)?**  
We deliberately use Java's built-in `java.net.http.HttpClient` (available since Java 11). This means:
- Zero extra dependencies for HTTP calls
- Smaller application size
- No risk of library version conflicts

### `settings.gradle` — The Project Name

```
rootProject.name = 'public-transport-tracker'
```

One line. It just tells Gradle "this project is called public-transport-tracker."

### `application.properties` — The Control Panel

This is the main settings file. Think of it as the control panel of a machine — every knob and dial that controls how the backend behaves.

```properties
server.port=8080                    # The app listens on port 8080 (like a radio tuned to channel 8080)

spring.jackson.serialization.write-dates-as-timestamps=false    # Send dates as "2026-04-22T10:30:00Z" not as a number
spring.jackson.default-property-inclusion=NON_NULL              # Don't include empty/null fields in responses

transit.api.mta.base-url=${MTA_BASE_URL:https://api.mta.info}         # MTA's address (overridable by env var)
transit.api.mta.api-key=${MTA_API_KEY:}                                # Optional — MTA public endpoints work without a key
transit.api.mta.timeout-ms=${MTA_TIMEOUT_MS:5000}                      # Wait max 5 seconds for MTA to respond

transit.api.transitland.base-url=${TRANSITLAND_BASE_URL:https://transit.land/api/v2/rest}
transit.api.transitland.api-key=${TRANSITLAND_API_KEY:demo-key}
transit.api.transitland.timeout-ms=${TRANSITLAND_TIMEOUT_MS:5000}

transit.cache.ttl-seconds=${CACHE_TTL_SECONDS:300}                     # Data is "fresh" for 5 minutes (300 seconds)
transit.cache.stale-ttl-seconds=${CACHE_STALE_TTL_SECONDS:3600}        # Usable in emergencies for 1 hour
transit.cache.max-size=${CACHE_MAX_SIZE:1000}                          # Store up to 1000 entries

transit.delays.significant-threshold-minutes=15                        # Alert users only if delay is 15+ minutes
transit.offline.enabled=${OFFLINE_MODE:false}                          # Offline mode OFF by default
```

**Why `${VARIABLE:default}`?** This is a security pattern. The actual API keys are stored in **environment variables** on the server, never in the code. The MTA API key is **optional** — MTA’s public endpoints work without one. If you do have a key (for higher rate limits), set `MTA_API_KEY` in your environment. TransitLand requires a key, so `TRANSITLAND_API_KEY` should be set in production.

**Why two API providers (MTA + TransitLand)?** Redundancy. If MTA goes down, we fall back to TransitLand. This is like having two suppliers for the same ingredient — if one is out of stock, you go to the other.

---

## 3. The Starting Point

### `TransportTrackerApplication.java` — The Ignition Key

```java
@SpringBootApplication
@EnableScheduling
public class TransportTrackerApplication {
    public static void main(String[] args) {
        SpringApplication.run(TransportTrackerApplication.class, args);
    }
}
```

This is the ignition key of the entire application. When you start the app, Java runs `main()`, which tells Spring Boot to:

1. **Scan the project** — find all files marked as services, controllers, configurations
2. **Wire everything together** — connect services to each other automatically (called "dependency injection")
3. **Start the web server** — begin listening on port 8080 for incoming requests

**`@SpringBootApplication`** = "This is the starting point. Auto-configure everything."  
**`@EnableScheduling`** = "Allow scheduled tasks" — we use this for the cache statistics logger that runs every 5 minutes.

---

## 4. Configuration Files

### `AppConfig.java` — Bean Wiring

```java
@Configuration
public class AppConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Bean @Qualifier("mtaClient")
    public TransitApiClient mtaApiClientBean(MtaApiClient mtaApiClient) { return mtaApiClient; }

    @Bean @Qualifier("transitLandClient")
    public TransitApiClient transitLandClientBean(TransitLandApiClient transitLandApiClient) { return transitLandApiClient; }
}
```

**What is an ObjectMapper?** It's the translator between Java objects and JSON text. When we send data to the browser, it needs to be in JSON format (a text format browsers understand). The ObjectMapper converts `VehicleLocation` objects into `{"vehicleId": "VEH-001", "latitude": 40.71}`.

**Why `JavaTimeModule`?** Java has its own date types (`Instant`, `LocalDateTime`). Without this module, the ObjectMapper doesn't know how to convert them to text. With it, dates become readable strings like `"2026-04-22T10:30:00Z"`.

**Why `@Qualifier`?** We have TWO implementations of `TransitApiClient` — MTA and TransitLand. When `TransportService` says "give me a client", Spring needs to know WHICH one. `@Qualifier("mtaClient")` is like a name tag that says "this bean is the MTA one."

### `SecurityConfig.java` — The Bouncer

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)              // Disable CSRF (explained below)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/api/v1/**").permitAll()
                .anyRequest().authenticated()
            )
            .headers(headers -> headers
                .contentTypeOptions(ct -> {})    // X-Content-Type-Options: nosniff
                .frameOptions(fo -> fo.deny())   // X-Frame-Options: DENY
            );
        return http.build();
    }
}
```

This is the bouncer at the door. It decides:

**CSRF disabled — why?** CSRF (Cross-Site Request Forgery) protection is for websites that use cookies and sessions (like banking sites). Our API is **stateless** — each request is independent, no cookies, no sessions. CSRF protection would break legitimate API calls without adding security.

**Stateless sessions — why?** In a stateless API, the server doesn't remember who you are between requests. Every request must carry its own credentials (like an API key). This is simpler, more scalable (any server can handle any request), and follows REST best practices.

**`permitAll()` on API endpoints — is this insecure?** For this assessment, yes, all endpoints are public. In production, you'd add an `ApiKeyAuthenticationFilter` that checks for a secret key in the `X-Api-Key` header. The comment in the code explains exactly where to add this.

**Security headers:**
- `X-Content-Type-Options: nosniff` — prevents browsers from guessing file types (a common attack vector)
- `X-Frame-Options: DENY` — prevents the page from being embedded in an `<iframe>` (prevents clickjacking attacks)

### `WebConfig.java` — Cross-Origin Rules

CORS (Cross-Origin Resource Sharing) is a browser security feature. By default, a webpage at `localhost:3000` (our frontend) cannot talk to a server at `localhost:8080` (our backend) because they're different "origins." `WebConfig` explicitly says "yes, `localhost:3000` is allowed to talk to us."

### `OpenApiConfig.java` — API Documentation Setup

Configures the Swagger UI page — an interactive webpage where developers can test every API endpoint by clicking buttons. It lists the API name, version, description, and security requirements.

---

## 5. Data Models — The Shape of Information

Models are like forms or templates. They define what a "Vehicle Location" looks like, what an "Arrival Prediction" contains, etc.

### `TransportResponse.java` — The Envelope

Every API response is wrapped in this envelope:

```json
{
  "data": { ... },          // The actual information (vehicles, arrivals, etc.)
  "metadata": {
    "cached": false,         // Was this served from memory or fetched live?
    "cacheAgeSeconds": null, // How old is the cached data?
    "dataSource": "LIVE",    // Where did this data come from? LIVE / CACHE / STALE_CACHE / MOCK
    "timestamp": "2026-04-22T10:30:00Z",
    "city": "nyc",
    "offlineMode": false
  },
  "_links": {                // Navigation links (HATEOAS)
    "self": { "href": "/api/v1/transport?city=nyc", "method": "GET" },
    "vehicles": { "href": "/api/v1/transport/vehicles?city=nyc", "method": "GET" }
  }
}
```

**Why an envelope?** Three reasons:
1. **Transparency** — The frontend knows if data is live or cached. If it's `STALE_CACHE`, the UI shows a warning banner.
2. **Navigation** — The `_links` section tells the frontend what other endpoints exist (HATEOAS pattern). The frontend doesn't need to hardcode URLs.
3. **Consistency** — Every endpoint returns the same shape. The frontend code that reads responses only needs one parser.

### `TransportData.java` — The Aggregate

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TransportData {
    private List<VehicleLocation> vehicles;
    private List<ArrivalPrediction> arrivals;
    private List<ServiceAlert> alerts;
    private List<RoutePlan> routePlans;
    private List<CrowdingInfo> crowding;
    private List<AlertMessage> conditionalAlerts;
    private String city;
    private String routeId;
}
```

This is a container that holds ALL types of transit data in one place. Instead of making 5 separate API calls, the frontend can make ONE call and get everything.

**`@Data`** = Lombok auto-generates getters (`getVehicles()`), setters (`setVehicles()`), `toString()`, `equals()`, and `hashCode()`. Without Lombok, this class would be 100+ lines of boilerplate.

**`@Builder`** = Allows creating objects like: `TransportData.builder().city("nyc").vehicles(list).build()` instead of calling a constructor with 8 arguments in the right order.

### Other Models

| Model | What It Represents | Key Fields |
|---|---|---|
| `VehicleLocation` | A single bus/train's GPS position | vehicleId, latitude, longitude, speed, delaySeconds, occupancyStatus |
| `ArrivalPrediction` | When a vehicle will arrive at a stop | stopName, scheduledArrival, predictedArrival, delaySeconds, realtime |
| `ServiceAlert` | A disruption/warning | type (DISRUPTION/WEATHER), severity, headerText, affectedRoutes, activeFrom/Until |
| `CrowdingInfo` | How full a vehicle is | capacity, currentPassengers, occupancyPercentage, level (LOW/MEDIUM/HIGH/FULL) |
| `RoutePlan` | A suggested journey | origin, destination, legs (steps), durationMinutes, transfers, confidence score |
| `AlertMessage` | A processed warning for the UI | type (DELAY/CROWDING), level (WARNING/ERROR/INFO), message, icon |

---

## 6. API Clients — Talking to External Services

### `TransitApiClient.java` — The Contract (Interface)

```java
public interface TransitApiClient {
    List<VehicleLocation> fetchVehicleLocations(String city, String routeId);
    List<ArrivalPrediction> fetchArrivalPredictions(String stopId, String routeId);
    List<ServiceAlert> fetchServiceAlerts(String city);
    List<RoutePlan> fetchRoutePlans(String from, String to, String city);
    List<CrowdingInfo> fetchCrowdingInfo(String routeId);
    boolean isAvailable();
    String getProviderName();
}
```

This is a **contract** (called an "interface" in Java). It says: "Any transit data provider MUST be able to do these 7 things." It doesn't say HOW — just WHAT.

**Why an interface?** It's the **Strategy Pattern** — a design principle where you can swap implementations without changing the code that uses them. Today we have MTA, TransitLand, SEPTA, and TfL. Tomorrow we could add more cities by just creating a new class that implements this interface. Nothing else changes.

### `MtaApiClient.java` — NYC MTA Implementation

This is one concrete implementation of the contract above, specifically for NYC's Metropolitan Transportation Authority.

```java
@Component
public class MtaApiClient implements TransitApiClient {
    private final HttpClient httpClient;

    public MtaApiClient(ObjectMapper objectMapper) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(5000))   // Give up after 5 seconds
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }
```

**Why a 5-second timeout?** If MTA's server is slow or down, we don't want our users waiting forever. After 5 seconds, we give up and fall back to cached data or mock data. This is part of the resilience strategy.

**URL Encoding:** `URLEncoder.encode(routeId.toUpperCase(), StandardCharsets.UTF_8)` — user input like "A train" must be sanitized before putting it in a URL. This prevents injection attacks and handles special characters.

**The `sendGet()` helper:** Every request to MTA includes:
- `x-api-key` header — authentication (only sent when a key is configured; MTA works without it)
- `Accept: application/json` — "please send JSON, not HTML"
- Timeout — don't wait forever

**Parsers (`parseMtaVehicles`, `parseMtaArrivals`, `parseMtaAlerts`):** MTA returns data in their own custom format. These parsers translate MTA's format into our standardized `VehicleLocation`, `ArrivalPrediction`, and `ServiceAlert` models. This way, the rest of our code never needs to know about MTA's specific format.

### `TransitLandApiClient.java` — Fallback Provider

Identical structure to MtaApiClient but talks to transit.land (a different data source). If MTA is down, this is our backup. The API key is passed as a query parameter (`?apikey=...`) instead of a header — different providers have different conventions.

### `SeptaApiClient.java` — Philadelphia Provider

Similar structure for Philadelphia's SEPTA transit system.

### `TflApiClient.java` — London Provider

Implementation for London's Transport for London (TfL) API, providing data for the London Underground, Overground, DLR, and TfL Rail services.

---

## 7. Controllers — The Front Door

Controllers are the front door of the application. They define what URLs exist and what happens when someone visits them.

### `TransportController.java`

```java
@RestController                              // "I handle web requests and return JSON"
@RequestMapping("/api/v1/transport")         // "My URLs all start with /api/v1/transport"
@RequiredArgsConstructor                     // Auto-create constructor for dependencies
@Validated                                   // Enable input validation
public class TransportController {

    private final TransportService transportService;

    @GetMapping
    public ResponseEntity<TransportResponse<TransportData>> getTransportData(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String routeId,
            @RequestParam(required = false) Boolean offline) {
        return ResponseEntity.ok(transportService.getTransportData(city, routeId, offline));
    }
```

**What is `@GetMapping`?** It says "when someone sends a GET request to this URL, run this method." GET is the HTTP method browsers use when you type a URL or click a link.

**What is `@RequestParam`?** URL parameters. In `http://localhost:8080/api/v1/transport?city=nyc&routeId=A`, `city` is "nyc" and `routeId` is "A". `required = false` means these are optional.

**What is `@NotBlank`?** On the arrivals endpoint, `stopId` is marked `@NotBlank` — meaning the server automatically rejects requests without a stop ID and returns a 400 "Bad Request" error. No manual checking needed.

**Endpoints available:**

| URL | What It Does |
|---|---|
| `GET /api/v1/transport` | Everything: vehicles, arrivals, alerts, crowding |
| `GET /api/v1/transport/vehicles` | Just vehicle positions |
| `GET /api/v1/transport/arrivals?stopId=X` | Arrivals at a specific stop |
| `GET /api/v1/transport/alerts` | Active service alerts |
| `GET /api/v1/transport/plan?from=X&to=Y` | Journey planner |
| `GET /api/v1/transport/crowding?routeId=X` | Crowding levels |

**Why version the URL (`/api/v1/...`)?** If we redesign the API later, we create `/api/v2/...` without breaking existing users of v1. This is a standard practice.

### `CacheController.java`

```java
GET  /api/v1/cache/stats              — View cache statistics (hit rate, size, etc.)
DELETE /api/v1/cache                  — Clear the entire cache
DELETE /api/v1/cache/entry?city=X&routeId=Y  — Remove one specific cache entry
```

This is an admin tool. In development, you can check if the cache is working and manually clear it.

---

## 8. Error Handling — What Happens When Things Go Wrong

### `GlobalExceptionHandler.java`

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(...) { ... }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(...) { ... }

    @ExceptionHandler(TransitApiException.class)
    public ResponseEntity<ErrorResponse> handleTransitApi(...) { ... }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(...) { ... }
}
```

**What is `@RestControllerAdvice`?** It's a safety net that catches errors from ANY controller. Without it, each controller would need its own error handling — duplicated everywhere.

**Error mapping:**

| Error Type | HTTP Status | When It Happens |
|---|---|---|
| Validation failure | 400 Bad Request | User sent invalid data (e.g., blank stopId) |
| Missing parameter | 400 Bad Request | Required URL parameter missing |
| Transit API failure | 503 Service Unavailable | MTA or TransitLand is down |
| Anything else | 500 Internal Server Error | Unexpected bug |

**Why 503 for API failures?** HTTP 503 means "service temporarily unavailable" — it tells the caller "this isn't your fault, try again later." The frontend sees 503 and displays a "service unavailable" message instead of a confusing error.

**Why a generic 500 handler?** The catch-all handler returns a safe, generic message: `"An unexpected error occurred."` It does NOT expose the actual error details (like stack traces or database queries) to the user — that would be a security vulnerability.

### `ErrorResponse.java`

The shape of every error response:
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Required parameter 'stopId' is missing",
  "path": "/api/v1/transport/arrivals",
  "timestamp": "2026-04-22T10:30:00Z",
  "fieldErrors": [...]
}
```

---

## 9. Cache System — Remembering Data

The cache is the heart of our resilience strategy. It "remembers" data so we don't need to call external APIs for every request.

### `CacheEntry.java` — A Single Memory

```java
public class CacheEntry<V> {
    private final V value;               // The stored data
    private final Instant createdAt;     // When it was stored
    private final long ttlSeconds;       // How long it's "fresh"
    private volatile Instant lastAccessedAt;  // Last time someone read it

    public boolean isExpired() {
        return Instant.now().isAfter(createdAt.plusSeconds(ttlSeconds));
    }

    public boolean isStaleFor(long staleTtlSeconds) {
        return Instant.now().isAfter(createdAt.plusSeconds(staleTtlSeconds));
    }
}
```

Think of each cache entry as a container of leftovers in your fridge:
- `createdAt` = when you cooked it
- `ttlSeconds` = "best before" (5 minutes) — after this, you'd prefer fresh food
- `staleTtlSeconds` = "absolute expiry" (1 hour) — after this, throw it away
- `isExpired()` = "past best before?" — still safe, just not ideal
- `isStaleFor()` = "past absolute expiry?" — throw it out

**`volatile`** on `lastAccessedAt` — this keyword means "multiple threads (parallel workers) might read/write this field simultaneously, so always use the latest value." Without it, one thread might see an outdated value.

### `InMemoryCache.java` — The Fridge

```java
public class InMemoryCache<K, V> {
    private final ConcurrentHashMap<K, CacheEntry<V>> store;
    private final AtomicInteger hits, misses, staleHits, evictions;
    private final ScheduledExecutorService cleaner;
```

**`ConcurrentHashMap`** — a thread-safe dictionary. In a web server, many users make requests simultaneously. Each request runs in its own "thread" (parallel worker). A normal HashMap would corrupt data if two threads write at the same time. ConcurrentHashMap handles this safely.

**`AtomicInteger`** for counters — same reason. If two threads both try to increment `hits` at the same time, a normal `int` could lose a count. `AtomicInteger` guarantees accuracy.

**`ScheduledExecutorService`** — a background timer that runs `evictStaleEntries()` periodically to clean up old data, like a janitor checking the fridge and throwing out expired items.

**Key methods:**
- `get(key)` — returns data only if it's fresh (within TTL). This is your first choice.
- `getStale(key)` — returns data even if it's past TTL but within the stale window. This is your emergency fallback.
- `put(key, value)` — stores data. If the fridge is full (`maxSize`), evicts the least recently used item first.
- `evictLeastRecentlyAccessed()` — when full, removes the item nobody has read for the longest time (LRU eviction).

**Why build our own cache instead of using Redis/Ehcache/Caffeine?**
The case study requirement was to avoid third-party dependencies for core logic. A custom cache lets us control exactly how stale data works — the "fresh → expired-but-usable → fully-stale" lifecycle is central to Option A's resilience pattern. Off-the-shelf caches don't have this two-tier expiry concept built in.

### `CacheService.java` — The Cache Manager

```java
@Service
public class CacheService {
    private final InMemoryCache<String, TransportData> dataCache;

    public String buildKey(String city, String routeId) {
        return city.toLowerCase() + ":" + routeId.toLowerCase();
        // e.g., "nyc:a" — standardized, case-insensitive
    }

    @Scheduled(fixedDelay = 300000)  // Every 5 minutes
    public void logCacheStats() { ... }
}
```

This is a Spring-managed wrapper around `InMemoryCache`. It:
- Reads settings from `application.properties` (TTL, stale TTL, max size)
- Builds standardized cache keys (`"nyc:a"`)
- Logs cache statistics every 5 minutes so operators can monitor performance

---

## 10. Security

**API Keys in Environment Variables** — Never hardcoded. MTA key is optional (public endpoints work without it). TransitLand key is passed via `TRANSITLAND_API_KEY`.

**URL Encoding** — All user input placed in URLs is encoded with `URLEncoder.encode()` to prevent URL injection.

**Input Validation** — `@NotBlank` annotations reject empty inputs at the controller level before any processing.

**Error Sanitization** — The generic 500 error handler never exposes internal details to users.

**Security Headers** — `nosniff` and `DENY` frame options prevent MIME-sniffing and clickjacking attacks.

**No Sessions** — Stateless design means no session hijacking risk.

---

## 11. Testing

### `InMemoryCacheTest.java`
Tests the cache in isolation: put/get, TTL expiry, stale window, LRU eviction, concurrent access. Each test creates a fresh cache, stores data, then verifies behavior.

### `TransportServiceTest.java`
Tests all 5 stages of the degradation chain using mock (fake) API clients:
1. Offline mode → returns mock data
2. Cache hit → returns cached data
3. Live API success → fetches, caches, returns
4. Live API failure + stale cache → returns stale data
5. Live API failure + no stale cache → returns mock data

### `AlertServiceTest.java`
Tests each of the 4 alert rules: delay detection, disruption detection, crowding detection, weather detection.

### `TransportControllerTest.java`
Tests HTTP endpoints using Spring's `@WebMvcTest` — sends fake HTTP requests and verifies the response status and body.

**Why use Mockito?** Mockito creates fake versions of dependencies. When testing `TransportService`, we don't want to actually call MTA's API — that would be slow and unreliable. Instead, Mockito creates a fake `MtaApiClient` that returns predefined data.

---

## 12. Docker and Deployment

### `backend/Dockerfile` — Packaging

```dockerfile
# Stage 1: Build — use a full JDK (Java Development Kit) image
FROM eclipse-temurin:17-jdk-alpine AS builder
COPY . .
RUN ./gradlew bootJar --no-daemon

# Stage 2: Run — use a minimal JRE (Java Runtime Environment) image
FROM eclipse-temurin:17-jre-alpine
COPY --from=builder /app/build/libs/*.jar app.jar
```

**Why multi-stage?** Stage 1 compiles the code (needs ~400MB of tools). Stage 2 only contains the compiled output (~150MB). The final image is small and secure — no compiler, no source code, no build tools.

**Why Alpine Linux?** Alpine is a tiny Linux distribution (~5MB). Combined with JRE-only, the final image is around 150MB instead of 600MB+. Smaller images deploy faster and have less attack surface.

**Non-root user:** The Dockerfile creates a dedicated `appuser` with no admin privileges. If an attacker compromises the app, they can't modify system files.

**Health check:** `HEALTHCHECK CMD wget -q --spider http://localhost:8080/actuator/health` — Docker periodically checks if the app is alive. If it fails 3 times, Docker restarts it automatically.