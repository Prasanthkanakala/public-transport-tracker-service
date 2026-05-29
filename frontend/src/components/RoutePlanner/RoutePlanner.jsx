import React, { useState } from 'react';
import { useRoutePlanner } from '../../hooks/useTransport';
import './RoutePlanner.css';

function formatTime(isoString) {
  if (!isoString) return '—';
  return new Date(isoString).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function ModeIcon({ mode }) {
  const icons = { BUS: '🚌', SUBWAY: '🚇', RAIL: '🚆', FERRY: '⛴', WALK: '🚶', TRAM: '🚊' };
  return <span className="mode-icon">{icons[mode] || '🚌'}</span>;
}

/**
* Journey route planner component.
* Allows users to input origin/destination and displays route options.
*/
export default function RoutePlanner({ city, offline }) {
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [submitted, setSubmitted] = useState(false);

  const { plans, metadata, loading, error, plan } = useRoutePlanner();

  const handleSubmit = (e) => {
    e.preventDefault();
    if (from.trim() && to.trim()) {
      setSubmitted(true);
      plan({ from: from.trim(), to: to.trim(), city, offline: offline || undefined });
    }
  };

  return (
    <div className="route-planner">
      <form className="planner-form" onSubmit={handleSubmit}>
        <div className="planner-fields">
          <div className="planner-field">
            <label htmlFor="from-input" className="planner-label">From</label>
            <input
              id="from-input"
              type="text"
              className="planner-input"
              placeholder="Origin stop or address"
              value={from}
              onChange={(e) => setFrom(e.target.value)}
              required
              maxLength={100}
            />
          </div>
          <div className="swap-btn-wrapper">
            <button
              type="button"
              className="swap-btn"
              aria-label="Swap from and to"
              onClick={() => { setFrom(to); setTo(from); }}
            >⇅</button>
          </div>
          <div className="planner-field">
            <label htmlFor="to-input" className="planner-label">To</label>
            <input
              id="to-input"
              type="text"
              className="planner-input"
              placeholder="Destination stop or address"
              value={to}
              onChange={(e) => setTo(e.target.value)}
              required
              maxLength={100}
            />
          </div>
          <button type="submit" className="plan-btn" disabled={loading}>
            {loading ? 'Planning…' : 'Plan Route →'}
          </button>
        </div>
      </form>

      {error && <div className="planner-error">{error}</div>}

      {submitted && !loading && plans?.length === 0 && (
        <div className="planner-empty">No routes found between these stops.</div>
      )}

      {plans?.length > 0 && (
        <div className="plans-container">
          <div className="plans-header">
            <span>{plans.length} option{plans.length !== 1 ? 's' : ''} found</span>
            {metadata && (
              <span className="plans-source">Source: {metadata.dataSource}</span>
            )}
          </div>

          {plans.map((p, idx) => (
            <div key={p.planId} className={`plan-card status-${p.status?.toLowerCase()}`}>
              <div className="plan-top">
                <span className="plan-label">
                  {idx === 0 ? '★ Best option' : `Option ${idx + 1}`}
                </span>
                <span className={`plan-status status-badge ${p.status?.toLowerCase()}`}>
                  {p.status}
                </span>
                <span className="plan-confidence">
                  {Math.round(p.confidence * 100)}% confidence
                </span>
              </div>

              <div className="plan-summary">
                <span className="plan-duration">{p.durationMinutes} min</span>
                <span className="plan-time">
                  {formatTime(p.departureTime)} → {formatTime(p.arrivalTime)}
                </span>
                <span className="plan-transfers">
                  {p.transfers === 0 ? 'Direct' : `${p.transfers} transfer${p.transfers > 1 ? 's' : ''}`}
                </span>
              </div>

              {/* Legs timeline */}
              <div className="legs-timeline">
                {p.legs?.map((leg, li) => (
                  <div key={li} className="leg">
                    <ModeIcon mode={leg.mode} />
                    <div className="leg-details">
                      <span className="leg-route">{leg.routeName || leg.mode}</span>
                      <span className="leg-stops">
                        {leg.fromStopName} → {leg.toStopName}
                      </span>
                      <span className="leg-time">
                        {formatTime(leg.departureTime)} · {leg.durationMinutes} min
                        {leg.delaySeconds > 0 && (
                          <span className="leg-delay"> (+{Math.round(leg.delaySeconds/60)} min)</span>
                        )}
                      </span>
                    </div>
                  </div>
                ))}
              </div>

              {p.status === 'DISRUPTED' && (
                <div className="plan-disruption-warn">
                  ⚠ Active disruptions may affect this route. Check service alerts.
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}