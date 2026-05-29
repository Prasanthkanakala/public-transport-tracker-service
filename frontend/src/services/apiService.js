/**
* Transit Tracker API Service
* Uses native fetch (no third-party HTTP library).
* All requests go through the Spring Boot backend on port 8080.
* Proxy is configured in package.json for development.
*/

const BASE_URL = process.env.REACT_APP_API_URL || '/api/v1/transport';
const CACHE_URL = '/api/v1/cache';

const DEFAULT_TIMEOUT_MS = 8000;

/**
* Wraps fetch with a timeout and consistent error handling.
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
        ...options.headers,
      },
    });

    clearTimeout(timer);

    if (!response.ok) {
      const errorBody = await response.json().catch(() => ({}));
      throw new ApiError(
        response.status,
        errorBody.message || `Request failed with status ${response.status}`,
        errorBody
      );
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

export async function getCacheStats() {
  return apiFetch(`${CACHE_URL}/stats`);
}

export async function clearCache() {
  return apiFetch(CACHE_URL, { method: 'DELETE' });
}