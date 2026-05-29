# Quick Learning Guide: Public Transport Tracker (1-2 Weeks)

## 🚀 1-Week Crash Course Overview

**Goal**: Understand and contribute to this Spring Boot + React project in 7-10 days.

**Focus**: Essential concepts only. Skip theory, prioritize practical understanding.

---

## 📅 Day 1-2: Java & Spring Boot Essentials (4-6 hours/day)

### Must-Know Java Concepts
**Priority: HIGH** - Used everywhere in backend

#### 1. Classes, Interfaces, Annotations (2 hours)
```java
// From TransportService.java
@Service  // Annotation - tells Spring this is a service
public class TransportService {
    // Constructor injection
    public TransportService(CacheService cache, AlertService alerts) {
        this.cache = cache;
        this.alerts = alerts;
    }
}
```

**What to learn**:
- `@Service`, `@Component`, `@RestController` annotations
- Constructor injection vs `@Autowired`
- Interfaces: `TransitApiClient` with multiple implementations

#### 2. Collections & Generics (2 hours)
```java
// Used throughout the codebase
List<VehicleLocation> vehicles = new ArrayList<>();
Map<String, TransportData> cache = new ConcurrentHashMap<>();
Optional<TransportData> result = cache.get(key);
```

**What to learn**:
- `List`, `Map`, `Optional`
- Generic types: `List<VehicleLocation>`
- Stream operations: `.map()`, `.filter()`, `.collect()`

#### 3. Spring Boot REST APIs (2 hours)
```java
@RestController
@RequestMapping("/api/v1/transport")
public class TransportController {
    @GetMapping
    public ResponseEntity<TransportData> getData(@RequestParam String city) {
        // Implementation
    }
}
```

**What to learn**:
- `@RestController` vs `@Controller`
- `@GetMapping`, `@PostMapping`
- `@RequestParam`, `@PathVariable`
- `ResponseEntity<T>` return types

### Spring Boot Configuration (1 hour)
```properties
# application.properties
server.port=8080
transit.cache.ttl-seconds=300
```

**What to learn**:
- Environment variables: `${CACHE_TTL_SECONDS:300}`
- Profiles: `application-dev.properties`, `application-prod.properties`

---

## 📅 Day 3-4: React Essentials (4-6 hours/day)

### Must-Know React Concepts
**Priority: HIGH** - Core frontend patterns

#### 1. Components & JSX (2 hours)
```jsx
// From App.jsx
export default function App() {
  const [city, setCity] = useState('nyc');  // State

  return (
    <div className="app">
      <h1>Transit Tracker</h1>
      {/* Conditional rendering */}
      {error && <div className="error">{error}</div>}
    </div>
  );
}
```

**What to learn**:
- Functional components (not class components)
- JSX syntax
- `className` instead of `class`

#### 2. Hooks: useState & useEffect (2 hours)
```jsx
// From useTransport.js
export function useTransport({ city, routeId }) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    fetchData();
  }, [city, routeId]);  // Runs when city/routeId changes

  return { data, loading };
}
```

**What to learn**:
- `useState`: `[value, setValue] = useState(initial)`
- `useEffect`: Side effects, dependencies array
- `useCallback`: Memoizing functions

#### 3. Props & Component Communication (1 hour)
```jsx
// Parent component
<VehicleMap vehicles={transportData.vehicles} loading={loading} />

// Child component
export default function VehicleMap({ vehicles, loading }) {
  if (loading) return <div>Loading...</div>;
  return <div>{/* render vehicles */}</div>;
}
```

**What to learn**:
- Passing data via props
- Destructuring props
- Conditional rendering with `&&`

### JavaScript Essentials (1 hour)
```javascript
// Async/await (used everywhere)
const fetchData = async () => {
  try {
    const response = await fetch('/api/data');
    const data = await response.json();
    setData(data);
  } catch (error) {
    setError(error.message);
  }
};

// Object/array destructuring
const { city, routeId } = props;
const [first, second] = array;
```

---

## 📅 Day 5-6: Project-Specific Patterns (4-6 hours/day)

### Backend Patterns (3 hours)
**Priority: MEDIUM** - Understanding the architecture

#### 1. Strategy Pattern (1 hour)
```java
// TransitApiClient interface
public interface TransitApiClient {
    List<VehicleLocation> fetchVehicleLocations(String city, String routeId);
}

// Multiple implementations
@Component @Qualifier("mtaClient")
public class MtaApiClient implements TransitApiClient { ... }

@Component @Qualifier("transitLandClient")
public class TransitLandApiClient implements TransitApiClient { ... }
```

**What to learn**:
- Interfaces define contracts
- `@Qualifier` selects which implementation to use
- Dependency injection chooses the right client

#### 2. Chain of Responsibility (2 hours)
```java
// TransportService.java degradation chain
public TransportResponse getTransportData(String city, String routeId) {
    // 1. Offline mode?
    if (offline) return mockData();

    // 2. Cache hit?
    Optional<TransportData> cached = cache.get(key);
    if (cached.isPresent()) return cached;

    // 3. Live API call
    try {
        TransportData live = fetchFromApi();
        cache.put(key, live);
        return live;
    } catch (Exception e) {
        // 4. Stale cache fallback
        Optional<TransportData> stale = cache.getStale(key);
        if (stale.isPresent()) return stale;

        // 5. Mock data fallback
        return mockData();
    }
}
```

**What to learn**:
- Graceful degradation: never fail
- Cache-first architecture
- Fallback strategies

### Frontend Patterns (3 hours)
**Priority: MEDIUM** - How components work together

#### 1. Custom Hooks (2 hours)
```jsx
// useTransport.js - reusable data fetching
export function useTransport({ city, routeId, offline }) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const fetch = useCallback(async () => {
    setLoading(true);
    try {
      const result = await getTransportData({ city, routeId, offline });
      setData(result.data);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [city, routeId, offline]);

  useEffect(() => {
    fetch();
    const interval = setInterval(fetch, 30000);  // Auto-refresh
    return () => clearInterval(interval);  // Cleanup
  }, [fetch]);

  return { data, loading, error, refresh: fetch };
}
```

**What to learn**:
- Extracting shared logic into hooks
- Auto-refresh with intervals
- Cleanup functions in useEffect

#### 2. API Service Layer (1 hour)
```javascript
// apiService.js - centralized HTTP calls
export async function getTransportData(params) {
  const url = buildUrl('/api/v1/transport', params);
  const response = await fetch(url);

  if (!response.ok) {
    throw new ApiError(response.status, await response.text());
  }

  return response.json();
}

function buildUrl(base, params) {
  const filtered = Object.entries(params)
    .filter(([, v]) => v != null && v !== '')
    .map(([k, v]) => `${k}=${encodeURIComponent(v)}`);
  return base + (filtered.length ? '?' + filtered.join('&') : '');
}
```

**What to learn**:
- Centralized API calls
- Error handling
- URL building with query parameters

---

## 📅 Day 7: Docker & Running the Project (2-4 hours)

### Docker Essentials (2 hours)
**Priority: HIGH** - You need this to run the project

```dockerfile
# backend/Dockerfile
FROM eclipse-temurin:17-jdk-alpine AS builder
COPY . .
RUN ./gradlew bootJar

FROM eclipse-temurin:17-jre-alpine
COPY --from=builder build/libs/*.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
```

**What to learn**:
- Multi-stage builds
- `COPY`, `RUN`, `EXPOSE`
- Alpine Linux images (small size)

### Running Commands (1 hour)
```bash
# Backend
cd backend
./gradlew bootRun  # Development
./gradlew test     # Run tests

# Frontend
cd frontend
npm install        # First time
npm start          # Development server

# Full stack
docker-compose up --build  # Production-like
```

**What to learn**:
- Gradle wrapper (`./gradlew`)
- npm scripts
- Docker Compose for multi-service apps

### Debugging (1 hour)
- Check backend logs in terminal
- Frontend: Browser DevTools (Network, Console)
- API testing: `http://localhost:8080/swagger-ui/index.html`

---

## 🎯 Quick Wins: What You Can Do Immediately

### Day 1-2 Tasks
1. **Run the project**: `docker-compose up --build`
2. **Explore the API**: Visit Swagger UI
3. **Change a React component**: Modify text in `App.jsx`
4. **Add a console.log**: Debug data flow

### Day 3-4 Tasks
1. **Modify API response**: Add a field to a model class
2. **Create a new React component**: Copy an existing one and modify
3. **Change styling**: Update CSS variables
4. **Add error handling**: Improve error messages

### Day 5-7 Tasks
1. **Implement a feature**: Add a new API endpoint
2. **Fix a bug**: Find and resolve an issue
3. **Add a test**: Write a unit test
4. **Deploy changes**: Push to a branch

---

## 📚 Minimal Resources (1-2 hours total)

### Documentation
- **Spring Boot**: https://spring.io/guides/gs/rest-service/ (1 hour)
- **React**: https://react.dev/learn/tutorial-tic-tac-toe (1 hour)

### Cheat Sheets
- **Java**: Basic syntax, common classes
- **React**: Hooks reference, JSX
- **Spring**: Annotations, configuration

### Skip For Now
- Advanced Java (generics deep dive)
- React class components
- Complex design patterns
- Testing frameworks (JUnit, Jest)
- Advanced Docker features
- Kubernetes/deployment details

---

## ✅ Success Checklist

**By end of Week 1, you should be able to:**
- [ ] Run the full application locally
- [ ] Understand the request flow (Frontend → Backend → APIs)
- [ ] Modify text/content in React components
- [ ] Add simple fields to API responses
- [ ] Explain the caching strategy
- [ ] Navigate the codebase confidently

**By end of Week 2, you should be able to:**
- [ ] Implement a new feature end-to-end
- [ ] Debug issues in both frontend and backend
- [ ] Write basic tests
- [ ] Deploy changes with Docker
- [ ] Explain architecture decisions

---

## 🚨 Emergency Backup Plan

If you're stuck:
1. **Focus on running the app first** - everything else builds from there
2. **Start with frontend changes** - easier to see results quickly
3. **Copy existing patterns** - don't invent new approaches
4. **Ask specific questions** - "How does the cache work?" vs "I don't understand anything"

**Remember**: This project demonstrates real-world patterns. Understanding it will make you job-ready for Spring Boot + React roles.