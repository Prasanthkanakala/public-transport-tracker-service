/**
 * Transit Tracker API Service
 * Uses native fetch (no third-party HTTP library).
 * All requests go through the Spring Boot backend on port 8080.
 * Proxy is configured in package.json for development.
 *
 * JWT Authentication:
 * - Automatically attaches Bearer token from localStorage
 * - Handles 401 (session expired) by clearing auth state
 * - Handles 403 (access denied) with descriptive error messages
 */

const BASE_URL = process.env.REACT_APP_API_URL || '/api/v1/transport';
const CACHE_URL = '/api/v1/cache';
const AUTH_URL = '/api/v1/auth';

const DEFAULT_TIMEOUT_MS = 8000;

/**
 * Retrieves the stored JWT token from localStorage.
 * @returns {string|null} The JWT token or null if not authenticated
 */
function getAuthToken() {
  return localStorage.getItem('auth_token');
}

/**
 * Builds authorization headers with the JWT Bearer token.
 * @returns {object} Headers object with Authorization if token exists
 */
function getAuthHeaders() {
  const token = getAuthToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

/**
 * Wraps fetch with a timeout, JWT auth headers, and consistent error handling.
 * Automatically attaches the Bearer token to every authenticated request.
 */
async function apiFetch(url, options = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), DEFAULT_TIMEOUT_MS);

  try {
    const response = await fetch(url, {
      ...options,
      signal: controller.signal,
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json',
        ...getAuthHeaders(),
        ...options.headers,
      },
    });

    clearTimeout(timer);

    if (!response.ok) {
      const errorBody = await response.json().catch(() => ({}));

      // Handle authentication/authorization errors with descriptive messages
      let errorMessage;
      if (response.status === 401) {
        errorMessage = 'Session expired. Please log in again.';
        // Clear stored auth data on 401
        localStorage.removeItem('auth_token');
        localStorage.removeItem('auth_username');
        localStorage.removeItem('auth_role');
      } else if (response.status === 403) {
        errorMessage = 'Access denied. You do not have permission for this action.';
      } else {
        errorMessage = errorBody.message || `Request failed with status ${response.status}`;
      }

      throw new ApiError(response.status, errorMessage, errorBody);
    }

    return response.json();
  } catch (err) {
    clearTimeout(timer);
    if (err.name === 'AbortError') {
      throw new ApiError(408, 'Request timed out. The server may be offline.');
    }
    throw err;
  }
}

export class ApiError extends Error {
  constructor(status, message, body = {}) {
    super(message);
    this.status = status;
    this.body = body;
    this.name = 'ApiError';
  }
}

function buildParams(params) {
  const filtered = Object.entries(params).filter(([, v]) => v !== null && v !== undefined && v !== '');
  if (filtered.length === 0) return '';
  return '?' + new URLSearchParams(filtered).toString();
}

// ─── Authentication API ─────────────────────────────────────────────────────────

/**
 * Authenticate user and obtain JWT token.
 * This endpoint does NOT require a Bearer token.
 * @param {object} credentials - { username, password }
 * @returns {Promise<{token: string, username: string, role: string}>}
 */
export async function loginUser({ username, password }) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), DEFAULT_TIMEOUT_MS);

  try {
    const response = await fetch(`${AUTH_URL}/login`, {
      method: 'POST',
      signal: controller.signal,
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ username, password }),
    });

    clearTimeout(timer);

    if (!response.ok) {
      const errorBody = await response.json().catch(() => ({}));
      throw new ApiError(
        response.status,
        response.status === 401
          ? 'Invalid username or password.'
          : errorBody.message || 'Login failed.',
        errorBody
      );
    }

    return response.json();
  } catch (err) {
    clearTimeout(timer);
    if (err.name === 'AbortError') {
      throw new ApiError(408, 'Login request timed out.');
    }
    throw err;
  }
}

// ─── Transport API ────────────────────────────────────────────────────────────

/**
 * Fetch complete transport data (vehicles, arrivals, alerts, crowding).
 * @param {object} params - { city, routeId, offline }
 */
export async function getTransportData({ city, routeId, offline } = {}) {
  const query = buildParams({ city, routeId, offline });
  return apiFetch(`${BASE_URL}${query}`);
}

/**
 * Fetch real-time vehicle locations for a route.
 * @param {object} params - { city, routeId, offline }
 */
export async function getVehicleLocations({ city, routeId, offline } = {}) {
  const query = buildParams({ city, routeId, offline });
  return apiFetch(`${BASE_URL}/vehicles${query}`);
}

/**
 * Fetch arrival predictions for a stop.
 * @param {object} params - { stopId (required), routeId, offline }
 */
export async function getArrivals({ stopId, routeId, offline } = {}) {
  if (!stopId) throw new Error('stopId is required');
  const query = buildParams({ stopId, routeId, offline });
  return apiFetch(`${BASE_URL}/arrivals${query}`);
}

/**
 * Fetch active service alerts for a city.
 * @param {object} params - { city, offline }
 */
export async function getServiceAlerts({ city, offline } = {}) {
  const query = buildParams({ city, offline });
  return apiFetch(`${BASE_URL}/alerts${query}`);
}

/**
 * Plan a route from origin to destination.
 * @param {object} params - { from (required), to (required), city, offline }
 */
export async function planRoute({ from, to, city, offline } = {}) {
  if (!from || !to) throw new Error('from and to are required');
  const query = buildParams({ from, to, city, offline });
  return apiFetch(`${BASE_URL}/plan${query}`);
}

// ─── Cache API ────────────────────────────────────────────────────────────────

/**
 * Fetch cache statistics. Requires OPERATOR or ADMIN role.
 */
export async function getCacheStats() {
  return apiFetch(`${CACHE_URL}/stats`);
}

/**
 * Clear all cache entries. Requires ADMIN role.
 */
export async function clearCache() {
  return apiFetch(`${CACHE_URL}/clear`, { method: 'POST' });
}
