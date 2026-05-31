# JWT Authentication & RBAC Integration Guide

## Overview

This guide documents the complete JWT Authentication + Role-Based Access Control (RBAC) implementation for the Public Transport Tracker application.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                        React Frontend (Port 3000)                           │
│  ┌────────────┐  ┌─────────────┐  ┌──────────────┐  ┌────────────────────┐  │
│  │ Login.jsx  │  │ AuthContext │  │ apiService.js │  │ AdminPanel.jsx     │  │
│  │            │  │ + useAuth   │  │ (Bearer JWT)  │  │ (Role-based UI)    │  │
│  └────────────┘  └─────────────┘  └───────┬──────┘  └────────────────────┘  │
└──────────────────────────────────────┼───────────────────────────────────────────┘
                                      │ HTTP + Bearer JWT
┌──────────────────────────────────────┼───────────────────────────────────────────┐
│                     Spring Boot Backend (Port 8080)                          │
│  ┌─────────────────────┐  ┌───────────────────────────────────────────────────┐  │
│  │ JwtAuthFilter       │  │                                                   │  │
│  │ (OncePerRequest)    │  │  SecurityConfig                                  │  │
│  │   │                 │  │  - BCrypt password encoding                       │  │
│  │   ├─ Extract JWT     │  │  - In-memory users (admin, operator, viewer)      │  │
│  │   ├─ Validate       │  │  - Stateless sessions                             │  │
│  │   └─ Set Auth       │  │  - CORS for React                                │  │
│  └─────────────────────┘  └───────────────────────────────────────────────────┘  │
│                                                                              │
│  ┌─────────────────────┐  ┌─────────────────────┐  ┌────────────────────────┐  │
│  │ AuthController      │  │ TransportController │  │ CacheController        │  │
│  │ POST /auth/login    │  │ @PreAuthorize       │  │ @PreAuthorize          │  │
│  │                     │  │ VIEWER+OPERATOR+    │  │ Stats: OPERATOR+ADMIN  │  │
│  │                     │  │ ADMIN               │  │ Clear: ADMIN only      │  │
│  └─────────────────────┘  └─────────────────────┘  └────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Project Structure Changes

### Backend (New Files)

```
backend/src/main/java/com/transport/tracker/
├── dto/
│   ├── LoginRequest.java          # Login request DTO (username, password)
│   └── LoginResponse.java         # Login response DTO (token, username, role)
├── security/
│   ├── JwtService.java            # JWT token generation and validation
│   └── JwtAuthenticationFilter.java  # OncePerRequestFilter for JWT auth
```

### Backend (Modified Files)

```
backend/
├── build.gradle                       # Added jjwt dependencies
├── src/main/resources/
│   └── application.properties         # Added JWT config properties
└── src/main/java/com/transport/tracker/
    ├── config/
    │   └── SecurityConfig.java        # Rewrote: JWT filter, in-memory users, BCrypt, CORS
    └── controller/
        ├── AuthController.java        # NEW: POST /api/v1/auth/login
        ├── CacheController.java       # Added @PreAuthorize, POST /cache/clear
        └── TransportController.java   # Added @PreAuthorize at class level
```

### Frontend (New Files)

```
frontend/src/
├── context/
│   └── AuthContext.js             # React context for auth state management
├── hooks/
│   └── useAuth.js                 # Custom hook for auth context access
└── components/
    ├── Login/
    │   ├── Login.jsx              # Login page component
    │   └── Login.css              # Login page styles
    └── AdminPanel/
        ├── AdminPanel.jsx         # Cache stats & clear panel
        └── AdminPanel.css         # Admin panel styles
```

### Frontend (Modified Files)

```
frontend/src/
├── index.js                           # Wrapped App with AuthProvider
├── App.jsx                            # Added auth gate, user info, role-based tabs
├── App.css                            # Added user info & logout button styles
└── services/
    └── apiService.js                  # Added JWT Bearer header, loginUser(), 401/403 handling
```

---

## Step-by-Step Integration Guide

### Step 1: Backend Dependencies

The following dependencies were added to `build.gradle`:

```groovy
// JWT (JSON Web Token)
implementation 'io.jsonwebtoken:jjwt-api:0.12.5'
runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.5'
runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.5'
```

### Step 2: JWT Configuration

Added to `application.properties`:

```properties
jwt.secret=${JWT_SECRET:MySecretKeyForJWTAuthenticationThatIsAtLeast256BitsLong!}
jwt.expiration-ms=${JWT_EXPIRATION_MS:3600000}
```

> **Important:** In production, set `JWT_SECRET` as an environment variable with a strong random key.

### Step 3: Security Configuration

`SecurityConfig.java` was rewritten to:
- Enable `@EnableMethodSecurity` for `@PreAuthorize` support
- Register `JwtAuthenticationFilter` before `UsernamePasswordAuthenticationFilter`
- Configure in-memory users with BCrypt-encoded passwords
- Set up CORS for React frontend
- Configure custom 401/403 error responses
- Keep stateless session management

### Step 4: JWT Service

`JwtService.java` handles:
- Token generation with username (sub) and role claims
- Token validation (signature + expiration)
- Username and role extraction from tokens
- HMAC-SHA256 signing

### Step 5: JWT Authentication Filter

`JwtAuthenticationFilter.java`:
- Extends `OncePerRequestFilter`
- Extracts Bearer token from Authorization header
- Validates token and sets Spring Security authentication context
- Adds `ROLE_` prefix for Spring Security's `hasRole()` checks

### Step 6: Auth Controller

`AuthController.java`:
- `POST /api/v1/auth/login` — public endpoint
- Validates credentials via `AuthenticationManager`
- Returns JWT token, username, and role
- Returns 401 for invalid credentials

### Step 7: Secure Existing Controllers

`TransportController.java`:
- Added `@PreAuthorize("hasAnyRole('VIEWER','OPERATOR','ADMIN')")` at class level

`CacheController.java`:
- `GET /stats` — `@PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")`
- `POST /clear` — `@PreAuthorize("hasRole('ADMIN')")`
- `DELETE /` — `@PreAuthorize("hasRole('ADMIN')")`

### Step 8: Frontend Auth Context

`AuthContext.js` + `useAuth.js`:
- Manages token, username, role in React context
- Persists to localStorage
- Provides `login()`, `logout()`, `hasRole()`, `hasAnyRole()`
- Multi-tab sync via storage events

### Step 9: Frontend Login Page

`Login.jsx`:
- Username/password form
- Calls `loginUser()` API
- Stores auth data via `AuthContext.login()`
- Shows demo credentials
- Handles error states

### Step 10: API Service Updates

`apiService.js`:
- `getAuthHeaders()` — builds `Authorization: Bearer <token>` header
- All `apiFetch()` calls automatically include Bearer token
- `loginUser()` — new function for authentication (no Bearer token)
- 401 responses clear localStorage auth data
- 403 responses show "Access denied" message
- `clearCache()` updated to use `POST /api/v1/cache/clear`

### Step 11: App Integration

`App.jsx`:
- Shows `Login` component when not authenticated
- Displays username and role badge in header
- Sign Out button in header
- Admin Panel tab visible only to OPERATOR and ADMIN roles

`index.js`:
- Wraps `<App />` with `<AuthProvider>`

---

## Roles & Permissions Matrix

| Feature | VIEWER | OPERATOR | ADMIN |
|---|---|---|---|
| Transport Search | ✓ | ✓ | ✓ |
| Arrival Board | ✓ | ✓ | ✓ |
| Vehicle Map | ✓ | ✓ | ✓ |
| Crowding Info | ✓ | ✓ | ✓ |
| Route Planner | ✓ | ✓ | ✓ |
| Service Alerts | ✓ | ✓ | ✓ |
| Cache Statistics | ✗ | ✓ | ✓ |
| Clear Cache | ✗ | ✗ | ✓ |
| Admin Panel Tab | ✗ | ✓ | ✓ |

---

## API Endpoints Summary

| Method | Endpoint | Auth | Roles |
|---|---|---|---|
| POST | `/api/v1/auth/login` | Public | None |
| GET | `/api/v1/transport` | Bearer JWT | VIEWER, OPERATOR, ADMIN |
| GET | `/api/v1/transport/vehicles` | Bearer JWT | VIEWER, OPERATOR, ADMIN |
| GET | `/api/v1/transport/arrivals` | Bearer JWT | VIEWER, OPERATOR, ADMIN |
| GET | `/api/v1/transport/alerts` | Bearer JWT | VIEWER, OPERATOR, ADMIN |
| GET | `/api/v1/transport/plan` | Bearer JWT | VIEWER, OPERATOR, ADMIN |
| GET | `/api/v1/transport/crowding` | Bearer JWT | VIEWER, OPERATOR, ADMIN |
| GET | `/api/v1/cache/stats` | Bearer JWT | OPERATOR, ADMIN |
| POST | `/api/v1/cache/clear` | Bearer JWT | ADMIN |
| DELETE | `/api/v1/cache` | Bearer JWT | ADMIN |

---

## Security Checklist

- [x] Stateless authentication (no server-side sessions)
- [x] JWT expiration: 1 hour (configurable)
- [x] BCrypt password encoding
- [x] CORS configured for React frontend
- [x] No passwords in logs (LoginRequest.toString() sanitized)
- [x] No JWT tokens in logs (LoginResponse.toString() sanitized)
- [x] Custom 401 Unauthorized response
- [x] Custom 403 Forbidden response
- [x] HMAC-SHA256 token signing
- [x] Secret key externalized via environment variable
- [x] CSRF disabled (stateless API)
- [x] Security headers (X-Content-Type-Options, X-Frame-Options)

---

## Postman Testing Flow

Import `docs/POSTMAN_COLLECTION.json` into Postman.

### Quick Test Sequence:

1. **Login as VIEWER** → `POST /api/v1/auth/login` with `viewer/viewer123`
2. **Copy JWT** from response `token` field
3. **Call transport API** → `GET /api/v1/transport?city=london` with Bearer token → **200 OK**
4. **Call cache stats** → `GET /api/v1/cache/stats` with Bearer token → **403 Forbidden**
5. **Login as OPERATOR** → `POST /api/v1/auth/login` with `operator/operator123`
6. **Call cache stats** → `GET /api/v1/cache/stats` with Bearer token → **200 OK**
7. **Call cache clear** → `POST /api/v1/cache/clear` with Bearer token → **403 Forbidden**
8. **Login as ADMIN** → `POST /api/v1/auth/login` with `admin/admin123`
9. **Call cache clear** → `POST /api/v1/cache/clear` with Bearer token → **200 OK**

### cURL Examples:

```bash
# Login as admin
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# Use the returned token
curl http://localhost:8080/api/v1/transport?city=london \
  -H "Authorization: Bearer <paste-token-here>"

# Cache stats (OPERATOR or ADMIN)
curl http://localhost:8080/api/v1/cache/stats \
  -H "Authorization: Bearer <paste-token-here>"

# Clear cache (ADMIN only)
curl -X POST http://localhost:8080/api/v1/cache/clear \
  -H "Authorization: Bearer <paste-token-here>"
```

---

## Troubleshooting

| Issue | Solution |
|---|---|
| 401 on all requests | Check that Bearer token is included in Authorization header |
| 403 on cache stats | Ensure you're logged in as OPERATOR or ADMIN |
| CORS errors | Verify frontend runs on localhost:3000 |
| Token expired | Re-login to get a new token (1 hour expiry) |
| Build fails | Run `./gradlew clean build` to refresh dependencies |
| Frontend auth loop | Clear localStorage and refresh the page |
