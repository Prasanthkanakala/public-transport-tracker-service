import React, { useState } from 'react';
import './App.css';
import Login from './components/Login/Login';
import RouteSearch from './components/RouteSearch/RouteSearch';
import AlertBanner from './components/AlertBanner/AlertBanner';
import ArrivalBoard from './components/ArrivalBoard/ArrivalBoard';
import VehicleMap from './components/VehicleMap/VehicleMap';
import OfflineToggle from './components/OfflineToggle/OfflineToggle';
import CrowdingIndicator from './components/CrowdingIndicator/CrowdingIndicator';
import RoutePlanner from './components/RoutePlanner/RoutePlanner';
import AdminPanel from './components/AdminPanel/AdminPanel';
import { useTransport } from './hooks/useTransport';
import { useAuth } from './hooks/useAuth';

export default function App() {
  const { isAuthenticated, username, role, logout, hasRole } = useAuth();
  const [city, setCity] = useState('london');
  const [routeId, setRouteId] = useState('central');
  const [offline, setOffline] = useState(false);
  const [activeTab, setActiveTab] = useState('live'); // live | plan | status | admin

  const { transportData, metadata, loading, error, refresh } = useTransport({
    city,
    routeId,
    offline,
    autoRefreshMs: 30000,
  });

  // Show login page if not authenticated
  if (!isAuthenticated) {
    return <Login />;
  }

  const handleSearch = ({ city: c, routeId: r }) => {
    setCity(c);
    setRouteId(r);
  };

  const dataSource = metadata?.dataSource;
  const isStale = dataSource === 'STALE_CACHE' || dataSource === 'MOCK';

  return (
    <div className="app">
      {/* ── Header ── */}
      <header className="app-header">
        <div className="header-brand">
          <span className="brand-icon">🚇</span>
          <span className="brand-name">Transit Tracker</span>
          {metadata && (
            <span className={`data-badge ${dataSource?.toLowerCase().replace('_', '-')}`}>
              {dataSource}
            </span>
          )}
        </div>
        <div className="header-controls">
          <div className="user-info">
            <span className="user-name">{username}</span>
            <span className="user-role-badge">{role}</span>
          </div>
          <OfflineToggle offline={offline} onToggle={setOffline} />
          <button className="refresh-btn" onClick={refresh} disabled={loading} aria-label="Refresh data">
            <span className={loading ? 'spin' : ''}>↺</span>
          </button>
          <button className="logout-btn" onClick={logout} aria-label="Sign out">
            Sign Out
          </button>
        </div>
      </header>

      {/* ── Search bar ── */}
      <section className="search-section">
        <RouteSearch initialCity={city} initialRoute={routeId} onSearch={handleSearch} />
      </section>

      {/* ── Stale data warning ── */}
      {isStale && (
        <div className="stale-banner">
          <span>⚠</span>
          <span>
            {offline
              ? 'Offline mode active – showing sample data'
              : `Live API unavailable – showing ${dataSource === 'STALE_CACHE' ? `cached data (${metadata?.cacheAgeSeconds}s old)` : 'sample data'}`}
          </span>
        </div>
      )}

      {/* ── Conditional alert banners ── */}
      {transportData?.conditionalAlerts?.length > 0 && (
        <AlertBanner alerts={transportData.conditionalAlerts} />
      )}

      {/* ── Error state ── */}
      {error && (
        <div className="error-banner">
          <span>✕</span> {error}
        </div>
      )}

      {/* ── Tab navigation ── */}
      <nav className="tab-nav" role="tablist">
        <button
          role="tab"
          aria-selected={activeTab === 'live'}
          className={`tab ${activeTab === 'live' ? 'active' : ''}`}
          onClick={() => setActiveTab('live')}
        >
          Live View
        </button>
        <button
          role="tab"
          aria-selected={activeTab === 'plan'}
          className={`tab ${activeTab === 'plan' ? 'active' : ''}`}
          onClick={() => setActiveTab('plan')}
        >
          Plan Journey
        </button>
        <button
          role="tab"
          aria-selected={activeTab === 'status'}
          className={`tab ${activeTab === 'status' ? 'active' : ''}`}
          onClick={() => setActiveTab('status')}
        >
          Service Status
        </button>
        {/* Admin tab - only visible to OPERATOR and ADMIN */}
        {hasRole('OPERATOR') && (
          <button
            role="tab"
            aria-selected={activeTab === 'admin'}
            className={`tab ${activeTab === 'admin' ? 'active' : ''}`}
            onClick={() => setActiveTab('admin')}
          >
            Admin Panel
          </button>
        )}
      </nav>

      {/* ── Tab panels ── */}
      <main className="app-main">
        {activeTab === 'live' && (
          <div className="live-grid">
            <section className="panel vehicles-panel" aria-label="Vehicle Positions">
              <h2 className="panel-title">Vehicle Positions</h2>
              <VehicleMap
                vehicles={transportData?.vehicles || []}
                loading={loading}
                routeId={routeId}
              />
            </section>

            <section className="panel arrivals-panel" aria-label="Upcoming Arrivals">
              <h2 className="panel-title">Upcoming Arrivals</h2>
              <ArrivalBoard
                arrivals={transportData?.arrivals || []}
                loading={loading}
              />
            </section>

            <section className="panel crowding-panel" aria-label="Crowding Levels">
              <h2 className="panel-title">Crowding Levels</h2>
              <CrowdingIndicator
                crowding={transportData?.crowding || []}
                loading={loading}
              />
            </section>
          </div>
        )}

        {activeTab === 'plan' && (
          <section className="panel full-panel" aria-label="Route Planner">
            <h2 className="panel-title">Plan Your Journey</h2>
            <RoutePlanner city={city} offline={offline} />
          </section>
        )}

        {activeTab === 'status' && (
          <section className="panel full-panel" aria-label="Service Status">
            <h2 className="panel-title">Service Alerts</h2>
            {transportData?.alerts?.length ? (
              <ul className="alert-list">
                {transportData.alerts.map((alert) => (
                  <li key={alert.alertId} className={`alert-item severity-${alert.severity?.toLowerCase()}`}>
                    <div className="alert-header">
                      <span className="alert-type-badge">{alert.type}</span>
                      <span className="alert-severity">{alert.severity}</span>
                    </div>
                    <p className="alert-headline">{alert.headerText}</p>
                    <p className="alert-desc">{alert.descriptionText}</p>
                    {alert.affectedRoutes?.length > 0 && (
                      <div className="affected-routes">
                        {alert.affectedRoutes.map((r) => (
                          <span key={r} className="route-pill">{r}</span>
                        ))}
                      </div>
                    )}
                  </li>
                ))}
              </ul>
            ) : (
              <p className="empty-state">
                {loading ? '⏳ Loading service status…' : '✓ No active service alerts'}
              </p>
            )}
          </section>
        )}

        {/* Admin Panel - only visible to OPERATOR and ADMIN */}
        {activeTab === 'admin' && hasRole('OPERATOR') && (
          <AdminPanel />
        )}
      </main>

      {/* ── Footer metadata ── */}
      {metadata && (
        <footer className="app-footer">
          <span>Last updated: {new Date(metadata.timestamp).toLocaleTimeString()}</span>
          {metadata.cacheAgeSeconds > 0 && (
            <span>Cache age: {metadata.cacheAgeSeconds}s</span>
          )}
          <span>City: {metadata.city || '—'} · Route: {metadata.routeId || '—'}</span>
        </footer>
      )}
    </div>
  );
}
