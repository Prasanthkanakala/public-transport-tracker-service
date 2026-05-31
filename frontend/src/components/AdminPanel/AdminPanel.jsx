import React, { useState, useCallback } from 'react';
import { useAuth } from '../../hooks/useAuth';
import { getCacheStats, clearCache } from '../../services/apiService';
import './AdminPanel.css';

/**
 * Admin Panel component for cache management.
 * Shows cache statistics (OPERATOR and ADMIN) and
 * cache clear functionality (ADMIN only).
 *
 * Handles 401 (session expired) and 403 (access denied) errors.
 */
export default function AdminPanel() {
  const { role, hasRole } = useAuth();
  const [stats, setStats] = useState(null);
  const [statsLoading, setStatsLoading] = useState(false);
  const [statsError, setStatsError] = useState(null);
  const [clearLoading, setClearLoading] = useState(false);
  const [clearMessage, setClearMessage] = useState(null);
  const [clearError, setClearError] = useState(null);

  const handleViewStats = useCallback(async () => {
    setStatsLoading(true);
    setStatsError(null);
    try {
      const data = await getCacheStats();
      setStats(data);
    } catch (err) {
      if (err.status === 401) {
        setStatsError('Session expired. Please log in again.');
      } else if (err.status === 403) {
        setStatsError('Access denied. Insufficient permissions.');
      } else {
        setStatsError(err.message || 'Failed to load cache statistics.');
      }
    } finally {
      setStatsLoading(false);
    }
  }, []);

  const handleClearCache = useCallback(async () => {
    setClearLoading(true);
    setClearError(null);
    setClearMessage(null);
    try {
      const data = await clearCache();
      setClearMessage(data.message || 'Cache cleared successfully.');
      // Refresh stats after clearing
      if (stats) {
        handleViewStats();
      }
    } catch (err) {
      if (err.status === 401) {
        setClearError('Session expired. Please log in again.');
      } else if (err.status === 403) {
        setClearError('Access denied. Only ADMIN can clear cache.');
      } else {
        setClearError(err.message || 'Failed to clear cache.');
      }
    } finally {
      setClearLoading(false);
    }
  }, [stats, handleViewStats]);

  // Don't render if user doesn't have at least OPERATOR role
  if (!hasRole('OPERATOR')) {
    return null;
  }

  return (
    <section className="admin-panel" aria-label="Administration Panel">
      <h2 className="admin-panel-title">
        <span role="img" aria-label="Admin">⚙️</span> Admin Panel
        <span className="role-badge">{role}</span>
      </h2>

      {/* Cache Statistics Section */}
      <div className="admin-section">
        <div className="admin-section-header">
          <h3>Cache Statistics</h3>
          <button
            className="admin-btn stats-btn"
            onClick={handleViewStats}
            disabled={statsLoading}
          >
            {statsLoading ? 'Loading...' : 'View Cache Stats'}
          </button>
        </div>

        {statsError && (
          <div className="admin-error" role="alert">
            <span>✕</span> {statsError}
          </div>
        )}

        {stats && (
          <div className="stats-grid">
            <div className="stat-card">
              <span className="stat-label">Size</span>
              <span className="stat-value">{stats.size}</span>
            </div>
            <div className="stat-card">
              <span className="stat-label">Hits</span>
              <span className="stat-value hit">{stats.hits}</span>
            </div>
            <div className="stat-card">
              <span className="stat-label">Misses</span>
              <span className="stat-value miss">{stats.misses}</span>
            </div>
            <div className="stat-card">
              <span className="stat-label">Stale Hits</span>
              <span className="stat-value stale">{stats.staleHits}</span>
            </div>
            <div className="stat-card">
              <span className="stat-label">Evictions</span>
              <span className="stat-value">{stats.evictions}</span>
            </div>
            <div className="stat-card">
              <span className="stat-label">Hit Rate</span>
              <span className="stat-value rate">{stats.hitRate}</span>
            </div>
          </div>
        )}
      </div>

      {/* Cache Clear Section - ADMIN only */}
      {hasRole('ADMIN') && (
        <div className="admin-section">
          <div className="admin-section-header">
            <h3>Cache Management</h3>
            <button
              className="admin-btn clear-btn"
              onClick={handleClearCache}
              disabled={clearLoading}
            >
              {clearLoading ? 'Clearing...' : 'Clear Cache'}
            </button>
          </div>

          {clearError && (
            <div className="admin-error" role="alert">
              <span>✕</span> {clearError}
            </div>
          )}

          {clearMessage && (
            <div className="admin-success" role="status">
              <span>✓</span> {clearMessage}
            </div>
          )}
        </div>
      )}
    </section>
  );
}
