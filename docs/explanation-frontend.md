# Frontend Explanation — Written for Everyone

> This document explains the entire frontend (the user-facing part): every file, every component,  
> every decision, and how it all connects. No programming knowledge assumed.

---

## Table of Contents

1. [What Is a "Frontend"?](#1-what-is-a-frontend)
2. [Technology Choices and Why](#2-technology-choices-and-why)
3. [Project Structure](#3-project-structure)
4. [The API Service — Talking to the Backend](#4-the-api-service)
5. [Custom Hooks — Shared Brain](#5-custom-hooks)
6. [App.jsx — The Main Layout](#6-appjsx--the-main-layout)
7. [Every Component Explained](#7-every-component-explained)
8. [Styling — Colours, Dark Theme, Layout](#8-styling)
9. [Docker & Nginx — Serving to Users](#9-docker-and-nginx)
10. [CI/CD — Automated Building and Deploying](#10-cicd)

---

## 1. What Is a "Frontend"?

The frontend is everything the user sees and touches in their web browser:
- The search bar where they type a city and route
- The map showing train positions
- The arrival board showing times
- The alert banners warning about disruptions
- The toggle switch for offline mode

It runs entirely in the user's browser. It fetches data from the backend (the kitchen), then displays it nicely (the dining room).

---

## 2. Technology Choices and Why

### React 18

**What is React?** A toolkit (library) made by Facebook for building user interfaces. Instead of writing raw HTML and manually updating the page, you describe what the page should look like given some data, and React handles updating the screen when data changes.

**Why React?**
- Most popular frontend framework — huge community, lots of resources
- Component-based — break the UI into small, reusable pieces (a search bar component, a map component, etc.)
- Efficient updates — React only redraws the parts of the screen that actually changed

### No Third-Party HTTP Library

We use the browser's built-in `fetch()` function to talk to the backend. No Axios, no jQuery AJAX.

**Why?** Same philosophy as the backend — minimize dependencies. `fetch()` is available in every modern browser. Less code to download, fewer security vulnerabilities to track.

### No Third-Party Map Library

The vehicle map is drawn using SVG (Scalable Vector Graphics) — built into every browser. No Leaflet, no Google Maps, no Mapbox.

**Why?** A full map library adds 200-500KB to the download. Our schematic map (showing vehicle positions on a line, not a geographic map) doesn't need that. SVG is lightweight and built-in.

### CSS Variables for Theming

Instead of using a CSS framework (like Tailwind or Bootstrap), we use CSS custom properties (variables):

```css
:root {
  --bg-primary: #0f1419;
  --text-primary: #e7e9ea;
  --accent-blue: #1d9bf0;
}
```

**Why?** Full control over appearance, zero framework overhead, and easy to change. Swapping to a light theme = just change the variable values.

---

## 3. Project Structure

```
frontend/
├── public/
│   └── index.html            ← The single HTML page (React fills it dynamically)
├── src/
│   ├── index.js              ← The starting point — mounts React into index.html
│   ├── index.css             ← Global styles (dark theme variables, resets)
│   ├── App.jsx               ← Main layout (header, tabs, panels)
│   ├── App.css               ← Styles for the main layout
│   ├── services/
│   │   └── apiService.js     ← All backend communication in one place
│   ├── hooks/
│   │   └── useTransport.js   ← Shared data-fetching logic (auto-refresh, error handling)
│   └── components/
│       ├── RouteSearch/       ← City + route search bar
│       ├── AlertBanner/       ← Warning banners (delays, disruptions, etc.)
│       ├── ArrivalBoard/      ← Departure times board
│       ├── VehicleMap/        ← Schematic vehicle position map
│       ├── CrowdingIndicator/ ← How full each vehicle is
│       ├── RoutePlanner/      ← Journey planning form + results
│       └── OfflineToggle/     ← On/off switch for offline mode
├── package.json              ← Dependencies and build scripts
├── Dockerfile                ← How to package for deployment
└── nginx.conf                ← Web server configuration
```

Every component has its own folder with a `.jsx` file (the logic) and a `.css` file (the appearance). This keeps things organized — to change how the arrival board looks, you only touch files in `ArrivalBoard/`.

---

## 4. The API Service — Talking to the Backend

### `apiService.js`

This file handles ALL communication with the backend. Every other file calls functions from here instead of making HTTP requests directly.

**The core function: `apiFetch()`**

```javascript
async function apiFetch(url, options = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 8000);  // 8-second timeout

  try {
    const response = await fetch(url, {
      ...options,
      signal: controller.signal,
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
    });

    if (!response.ok) {
      const errorBody = await response.json().catch(() => ({}));
      throw new ApiError(response.status, errorBody.message || `Request failed`);
    }

    return response.json();
  } catch (err) {
    if (err.name === 'AbortError') {
      throw new ApiError(408, 'Request timed out. The server may be offline.');
    }
    throw err;
  }
}
```

**Line by line in plain English:**

1. `AbortController` — a "cancel button." If the backend takes too long, we cancel the request.
2. `setTimeout(() => controller.abort(), 8000)` — set a timer: after 8 seconds, press the cancel button.
3. `await fetch(url, ...)` — send the request and wait for a response. `await` means "pause here until the answer comes back."
4. `signal: controller.signal` — connect the cancel button to this request.
5. `if (!response.ok)` — check if the response was successful (status 200-299). If not, extract the error message and throw an error.
6. `return response.json()` — convert the response from text to a JavaScript object.
7. If the cancel button was pressed (`AbortError`), tell the user the request timed out.

**Why 8 seconds?** The backend has a 5-second timeout for external APIs plus processing time. 8 seconds gives the backend enough time to try the degradation chain before we give up.

**Available functions:**

| Function | Backend Endpoint | Purpose |
|---|---|---|
| `getTransportData({city, routeId, offline})` | `GET /api/v1/transport` | Get everything |
| `getVehicleLocations({city, routeId})` | `GET /api/v1/transport/vehicles` | Just vehicle positions |
| `getArrivals({stopId, routeId})` | `GET /api/v1/transport/arrivals` | Arrivals at a stop |
| `getServiceAlerts({city})` | `GET /api/v1/transport/alerts` | Active alerts |
| `planRoute({from, to, city})` | `GET /api/v1/transport/plan` | Journey planner |
| `getCacheStats()` | `GET /api/v1/cache/stats` | Cache health |
| `clearCache()` | `DELETE /api/v1/cache` | Clear all cached data |

**`buildParams()` helper:**
```javascript
function buildParams(params) {
  const filtered = Object.entries(params).filter(([, v]) => v !== null && v !== undefined && v !== '');
  return '?' + new URLSearchParams(filtered).toString();
}
```
Takes `{city: "nyc", routeId: "A", offline: null}` and produces `?city=nyc&routeId=A`. It automatically removes empty values so we don't send `?offline=null`.

### `ApiError` class

A custom error type with `status` (HTTP code like 408 or 503) and `message`. This lets the UI show different messages for different errors — "timed out" vs "server error" vs "bad request."

---

## 5. Custom Hooks — Shared Brain

### What Is a Hook?

In React, a "hook" is a reusable piece of logic. Instead of copy-pasting the same data-fetching code into every component, we write it once in a hook and share it.

### `useTransport` hook

```javascript
export function useTransport({ city, routeId, offline = false, autoRefreshMs = 30000 }) {
  const [transportData, setTransportData] = useState(null);    // The actual data
  const [metadata, setMetadata] = useState(null);              // Cache/source info
  const [loading, setLoading] = useState(false);               // Is a request in progress?
  const [error, setError] = useState(null);                    // Error message, if any
```

**`useState`** is React's way of remembering things. `useState(null)` creates a variable that starts as `null` and a function to update it. When you call `setLoading(true)`, React re-renders the component to show a loading spinner.

**The fetch logic:**
```javascript
const fetch = useCallback(async () => {
  setLoading(true);
  setError(null);
  try {
    const response = await getTransportData({ city, routeId, offline });
    setTransportData(response.data);
    setMetadata(response.metadata);
  } catch (err) {
    setError(err instanceof ApiError ? err.message : 'Unable to reach the transport service.');
  } finally {
    setLoading(false);
  }
}, [city, routeId, offline]);
```

**`useCallback`** — "remember this function." Without it, React would create a new function every time the component re-renders, causing unnecessary re-fetches.

**`try/catch/finally`** — "try to do this; if it fails, catch the error; regardless of success or failure, finally stop loading."

**Auto-refresh:**
```javascript
useEffect(() => {
  if (offline) return;  // don't auto-refresh in offline mode
  intervalRef.current = setInterval(fetch, autoRefreshMs);  // every 30 seconds
  return () => clearInterval(intervalRef.current);          // cleanup when component unmounts
}, [city, routeId, offline, autoRefreshMs, fetch]);
```

**`useEffect`** — "do something when these values change." When the city or route changes, this restarts the 30-second auto-refresh timer.

**`setInterval(fetch, 30000)`** — "call `fetch` every 30,000 milliseconds (30 seconds)."

**`return () => clearInterval(...)`** — "when this component is removed from the screen, stop the timer." Without this cleanup, the timer would keep running forever, wasting resources and causing errors.

### `useRoutePlanner` hook

Same pattern but for journey planning. Calls `planRoute()` from apiService and manages loading/error states.

---

## 6. App.jsx — The Main Layout

This is the skeleton of the entire page. It arranges all components.

```javascript
export default function App() {
  const [city, setCity] = useState('nyc');           // Default city: New York
  const [routeId, setRouteId] = useState('A');       // Default route: A Train
  const [offline, setOffline] = useState(false);     // Online by default
  const [activeTab, setActiveTab] = useState('live'); // Start on Live View tab

  const { transportData, metadata, loading, error, refresh } = useTransport({
    city, routeId, offline, autoRefreshMs: 30000,
  });
```

**State management:** The App remembers:
- Which city and route the user selected
- Whether offline mode is on
- Which tab is active (Live View / Plan Journey / Status)
- The transport data, metadata, loading state, and errors (via the hook)

**Layout structure:**
```
┌────────────────────────────────────────────────────────┐
│  🚇 Transit Tracker          [LIVE badge]  [⚡ Offline] [↺ Refresh] │  ← Header
├────────────────────────────────────────────────────────┤
│  [City: ___] [Route: ___] [Search]                     │  ← Search bar
├────────────────────────────────────────────────────────┤
│  ⚠ Offline mode active - showing sample data           │  ← Stale data warning (conditional)
├────────────────────────────────────────────────────────┤
│  ⚠ Significant delays - Plan accordingly               │  ← Alert banners (conditional)
├────────────────────────────────────────────────────────┤
│  [Live View]  [Plan Journey]  [Status]                 │  ← Tab navigation
├────────────────────────────────────────────────────────┤
│                                                         │
│  (Tab content: map, arrivals, crowding,                │  ← Main content area
│   route planner, or status panel)                       │
│                                                         │
└────────────────────────────────────────────────────────┘
```

**Data source badge:** The header shows a coloured badge:
- `LIVE` (green) — freshly fetched from APIs
- `CACHE` (blue) — from memory, recent
- `STALE_CACHE` (orange) — from memory, older
- `MOCK` (red) — synthetic/sample data

**Conditional rendering:** React only shows elements when conditions are met:
```javascript
{isStale && (
  <div className="stale-banner">⚠ Live API unavailable — showing cached data</div>
)}
```
The `&&` operator means "if `isStale` is true, render this banner."

---

## 7. Every Component Explained

### RouteSearch — The Search Bar

**Purpose:** Lets users type a city name and route ID, then press Search.

```javascript
export default function RouteSearch({ initialCity, initialRoute, onSearch }) {
  const [city, setCity] = useState(initialCity);
  const [route, setRoute] = useState(initialRoute);

  const handleSubmit = (e) => {
    e.preventDefault();           // Don't refresh the page
    onSearch({ city, routeId: route });  // Tell the parent (App) about the new selection
  };
```

**`e.preventDefault()`** — By default, submitting a form in HTML reloads the entire page. In a React app, we don't want that. This line says "don't do the default thing; I'll handle it myself."

**`onSearch`** — This is a function passed down from the parent (App.jsx). When the user searches, RouteSearch calls `onSearch({city: "nyc", routeId: "A"})`, which updates App's state, which triggers a new data fetch.

**This pattern is called "lifting state up"** — the search bar doesn't fetch data itself. It tells the parent what the user wants, and the parent coordinates the fetch.

### AlertBanner — Warning Banners

**Purpose:** Shows coloured warning banners at the top of the page.

```javascript
export default function AlertBanner({ alerts }) {
  if (!alerts || alerts.length === 0) return null;  // Nothing to show? Render nothing.

  return (
    <div className="alert-banner-container">
      {alerts.map((alert, index) => (
        <div key={index} className={`alert-banner alert-${alert.level?.toLowerCase()}`}>
          <span className="alert-icon">{getIcon(alert.type)}</span>
          <span className="alert-message">{alert.message}</span>
        </div>
      ))}
    </div>
  );
}
```

**`alerts.map()`** — "for each alert in the list, create a banner." If there are 3 alerts, it creates 3 banners.

**`key={index}`** — React needs a unique identifier for each item in a list so it can efficiently update only the changed ones.

**`alert-${alert.level?.toLowerCase()}`** — Creates a CSS class like `alert-warning` (yellow), `alert-error` (red), or `alert-info` (blue). CSS then applies the matching colour.

**`alert.level?.toLowerCase()`** — The `?.` is called "optional chaining." If `alert.level` is null/undefined, it returns `undefined` instead of crashing.

### ArrivalBoard — Departure Times

**Purpose:** A digital departure board showing upcoming arrivals at stations.

Displays a table with:
| Stop | Route | ETA | Delay | Status | Platform |
|---|---|---|---|---|---|
| Times Square | A Train | 3 min | On time | 🟢 | 2 |
| 34 St-Penn Stn | A Train | 10 min | 18 min late | 🔴 | 1 |

**Key logic:**
```javascript
const minutesToArrival = arrival.minutesToArrival || Math.round(
  (new Date(arrival.predictedArrival) - new Date()) / 60000
);
```
If the backend provides `minutesToArrival`, use it. Otherwise, calculate it: subtract the current time from the predicted arrival time, convert from milliseconds to minutes.

**Delay colouring:** On-time arrivals are green. Delays are red. This colour coding lets commuters scan the board quickly.

### VehicleMap — Schematic Map

**Purpose:** Shows vehicle positions on a simplified route line (not a geographic map).

```javascript
const mapWidth = 800, mapHeight = 400;
const routeLine = {
  x1: 50, y1: mapHeight / 2,
  x2: mapWidth - 50, y2: mapHeight / 2,
};
```

This draws a horizontal line from left to right across the page. Vehicles are placed as circles along this line based on their position in the route.

**Vehicle positioning:**
```javascript
const x = routeLine.x1 + (index / (vehicles.length - 1 || 1)) * (routeLine.x2 - routeLine.x1);
```
Vehicles are evenly spaced along the route line. First vehicle is at the left, last is at the right.

**Colour coding:**
- Green circle = on time
- Orange circle = slightly delayed
- Red circle = significantly delayed (>15 min)

**Why SVG?** SVG (Scalable Vector Graphics) is like drawing on graph paper in the browser. It's built-in, lightweight, and scales perfectly to any screen size. No external library needed.

### CrowdingIndicator — Capacity Bars

**Purpose:** Shows how full each vehicle is with progress bars.

```javascript
const getBarColor = (percentage) => {
  if (percentage >= 90) return 'var(--crowding-full)';     // Red
  if (percentage >= 70) return 'var(--crowding-high)';     // Orange
  if (percentage >= 40) return 'var(--crowding-medium)';   // Yellow
  return 'var(--crowding-low)';                             // Green
};
```

A simple rule: <40% = green (plenty of room), 40-70% = yellow (getting busy), 70-90% = orange (very busy), >90% = red (full).

The bars visually show: `████████░░` (80% full). The width of the filled portion is set to the occupancy percentage.

### RoutePlanner — Journey Planning

**Purpose:** Form where users enter origin and destination, then see journey options.

**The form:**
```javascript
const handleSubmit = (e) => {
  e.preventDefault();
  plan({ from, to, city, offline });
};
```
User types "Times Square" → "Atlantic Ave", presses Plan. The `plan()` function from `useRoutePlanner` hook calls the backend.

**Results display:**
Each plan shows:
- Route summary (origin → destination)
- Duration and number of transfers
- Confidence score (as a percentage bar)
- Status badge (OPTIMAL in green, DISRUPTED in red)
- Journey legs (steps):
  ```
  ● Times Square → 🚇 A Train (12 min) → Transfer Hub
  ● Transfer Hub → 🚶 Walk (3 min) → Atlantic Ave
  ```

**Confidence bar colour:**
- >80% = green (reliable)
- 50-80% = yellow (some uncertainty)
- <50% = red (expect issues)

### OfflineToggle — The Switch

**Purpose:** A simple on/off switch that forces the app into offline mode.

```javascript
export default function OfflineToggle({ offline, onToggle }) {
  return (
    <button className={`offline-toggle ${offline ? 'active' : ''}`}
            onClick={() => onToggle(!offline)}>
      {offline ? '⚡ Online' : '📡 Offline'}
    </button>
  );
}
```

When offline mode is ON:
- The backend immediately returns mock data (no API calls)
- Auto-refresh is disabled (no point refreshing fake data)
- The UI shows "Offline mode active — showing sample data"

**Why have offline mode?** 
1. Users with poor internet can still see the app working
2. Developers can test without API keys
3. Demonstrations can be given without depending on live services

---

## 8. Styling

### Dark Theme

```css
:root {
  --bg-primary: #0f1419;        /* Deep dark background */
  --bg-secondary: #1a1f27;      /* Slightly lighter panels */
  --text-primary: #e7e9ea;      /* Off-white text */
  --accent-blue: #1d9bf0;       /* Twitter-blue accent */
  --success: #00ba7c;           /* Green for "on time" */
  --warning: #ffad1f;           /* Orange for delays */
  --error: #f4212e;             /* Red for disruptions */
}
```

**Why dark theme?** Transit apps are often used in dimly lit subway stations or at night. Dark backgrounds reduce eye strain and save battery on OLED phone screens.

**CSS variables** mean every component automatically uses the same colours. Changing `--accent-blue` in one place changes it everywhere.

### Layout

The app uses CSS Grid for the main layout — the modern standard for creating two-dimensional layouts.

```css
.main-content { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
```

This creates a two-column layout. On smaller screens (mobile), media queries switch to a single column.

### Component Styling Convention

Every component has its own CSS file. Classes are prefixed with the component name to avoid conflicts:
- `.arrival-board`, `.arrival-row`, `.arrival-delay`
- `.vehicle-map`, `.vehicle-dot`, `.vehicle-label`

---

## 9. Docker and Nginx

### `frontend/Dockerfile` — Packaging

```dockerfile
# Stage 1: Build the React app
FROM node:18-alpine AS builder
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci                    # Install dependencies (fast, reproducible)
COPY . .
RUN npm run build             # Compile React into static HTML/CSS/JS files

# Stage 2: Serve with nginx
FROM nginx:alpine
COPY --from=builder /app/build /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
```

**Stage 1 (Builder):** Takes our React source code and compiles it into plain HTML, CSS, and JavaScript files that any browser can read. React code (JSX, hooks, components) is NOT understood by browsers — it must be compiled first.

**`npm ci`** instead of `npm install` — `ci` stands for "clean install." It uses the exact versions from `package-lock.json` (a lockfile that pins every dependency version). This ensures builds are reproducible — you get the same result every time, on every machine.

**Stage 2 (Server):** Takes only the compiled files and puts them into an nginx web server. Nginx is a lightweight, high-performance web server.

**Why two stages?** The builder stage contains `node_modules` (hundreds of megabytes of build tools). The final image only contains the compiled output (~2MB of HTML/CSS/JS) plus nginx (~20MB). Result: ~25MB image instead of ~800MB.

### `nginx.conf` — Web Server Rules

```nginx
server {
    listen 80;

    location / {
        root /usr/share/nginx/html;
        try_files $uri $uri/ /index.html;    # React Router support
    }

    location /api/ {
        proxy_pass http://backend:8080;       # Forward API requests to backend
    }
}
```

**`try_files $uri $uri/ /index.html`** — This is critical for React apps. When a user visits `/plan`, the browser asks nginx for a file called `/plan`. That file doesn't exist — React handles routing in the browser. This rule says "if the file doesn't exist, serve `index.html` instead, and let React's router figure it out."

**`proxy_pass http://backend:8080`** — When the browser sends `GET /api/v1/transport`, nginx forwards it to the backend service on port 8080. The browser never talks to the backend directly. This is a security best practice — the backend is hidden behind the web server.

**`backend` hostname** — In Docker Compose, services can find each other by name. The backend container is named "backend", so nginx connects to `http://backend:8080`.

---

## 10. CI/CD — Automated Building and Deploying

### `docker-compose.yml`

```yaml
services:
  backend:
    build: ./backend
    ports: ["8080:8080"]
    healthcheck:
      test: wget -q --spider http://localhost:8080/actuator/health
      interval: 30s

  frontend:
    build: ./frontend
    ports: ["80:80"]
    depends_on:
      backend:
        condition: service_healthy    # Wait for backend to be healthy before starting
```

`depends_on: condition: service_healthy` ensures the frontend doesn't start until the backend is ready. Without this, the frontend might start, a user visits, the frontend tries to call the backend, and gets an error because the backend is still booting.

### GitHub Actions (`ci-cd.yml`)

Runs automatically on every code push:

1. **Test** — Run all unit tests. If any test fails, stop.
2. **Build** — Compile the backend JAR and frontend static files.
3. **Docker** — Build Docker images.
4. **Deploy** — Push images to a container registry (if on main branch).

### `Jenkinsfile`

Same pipeline but for Jenkins (an alternative CI/CD tool used by many companies). Provided for compatibility.

---

## Summary of Frontend Decisions

| Decision | Reasoning |
|---|---|
| React 18 (no Next.js, no Vue) | Most widely used, component model fits our dashboard UI |
| Native `fetch()` (no Axios) | Zero dependencies for HTTP; `fetch` is built into all modern browsers |
| SVG map (no Leaflet/Mapbox) | Lightweight schematic view; full map library would be overkill |
| CSS variables (no Tailwind) | Full control, zero framework overhead, easy theme switching |
| 30-second auto-refresh | Transit data changes frequently; 30s balances freshness vs API load |
| 8-second timeout | Gives backend enough time to try the full degradation chain |
| Component-per-folder | Each component is self-contained — easy to find, modify, or remove |
| `useTransport` custom hook | Avoids duplicating fetch/error/loading logic across components |
| Dark theme | Transit apps used in low-light environments; OLED battery savings |
| Multi-stage Docker build | ~25MB final image vs ~800MB; faster deploys, smaller attack surface |
| nginx reverse proxy | Hides backend; handles React Router; serves static files efficiently |