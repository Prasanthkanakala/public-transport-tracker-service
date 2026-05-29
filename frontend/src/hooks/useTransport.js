import { useState, useEffect, useCallback, useRef } from 'react';
import { getTransportData, planRoute, ApiError } from '../services/apiService';

/**
* Custom hook encapsulating all transport data fetching and state management.
* Supports:
* - Auto-refresh every 30 seconds
* - Per-request offline mode
* - Loading / error states
* - Stale data indicators
*/
export function useTransport({ city, routeId, offline = false, autoRefreshMs = 30000 }) {
  const [transportData, setTransportData] = useState(null);
  const [metadata, setMetadata] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const intervalRef = useRef(null);

  const fetch = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await getTransportData({ city, routeId, offline: offline || undefined });
      setTransportData(response.data);
      setMetadata(response.metadata);
    } catch (err) {
      setError(err instanceof ApiError
        ? err.message
        : 'Unable to reach the transport service. Check your connection.');
    } finally {
      setLoading(false);
    }
  }, [city, routeId, offline]);

  useEffect(() => {
    if (city || routeId) {
      fetch();
    }
  }, [city, routeId, offline, fetch]);

  // Auto-refresh
  useEffect(() => {
    if (!city && !routeId) return;
    if (offline) return; // no auto-refresh in offline mode

    intervalRef.current = setInterval(fetch, autoRefreshMs);
    return () => clearInterval(intervalRef.current);
  }, [city, routeId, offline, autoRefreshMs, fetch]);

  return { transportData, metadata, loading, error, refresh: fetch };
}

/**
* Custom hook for route planning.
*/
export function useRoutePlanner() {
  const [plans, setPlans] = useState(null);
  const [metadata, setMetadata] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const plan = useCallback(async ({ from, to, city, offline }) => {
    if (!from || !to) return;
    setLoading(true);
    setError(null);
    try {
      const response = await planRoute({ from, to, city, offline: offline || undefined });
      setPlans(response.data);
      setMetadata(response.metadata);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Route planning failed.');
    } finally {
      setLoading(false);
    }
  }, []);

  return { plans, metadata, loading, error, plan };
}