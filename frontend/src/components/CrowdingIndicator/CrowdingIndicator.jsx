import React from 'react';
import './CrowdingIndicator.css';

const LEVEL_CONFIG = {
  LOW:    { color: '#22c55e', text: 'Low',    pct: 30, icon: '🟢' },
  MEDIUM: { color: '#f59e0b', text: 'Medium', pct: 65, icon: '🟡' },
  HIGH:   { color: '#f97316', text: 'High',   pct: 85, icon: '🟠' },
  FULL:   { color: '#ef4444', text: 'Full',   pct: 100,icon: '🔴' },
};

/**
* Occupancy / crowding level indicators for vehicles on a route.
*/
export default function CrowdingIndicator({ crowding = [], loading }) {
  if (loading && !crowding.length) {
    return <div className="crowding-empty">⏳ Loading crowding data…</div>;
  }

  if (!crowding.length) {
    return <div className="crowding-empty">No crowding data available</div>;
  }

  return (
    <div className="crowding-list" role="list" aria-label="Vehicle crowding levels">
      {crowding.map((c, idx) => {
        const level = (c.level || 'LOW').toUpperCase();
        const config = LEVEL_CONFIG[level] || LEVEL_CONFIG.LOW;
        const pct = c.occupancyPercentage ?? config.pct;

        return (
          <div key={c.vehicleId || idx} className="crowding-item" role="listitem">
            <div className="crowding-header">
              <span className="crowding-vehicle">{c.vehicleId || `Vehicle ${idx + 1}`}</span>
              <span className="crowding-level" style={{ color: config.color }}>
                {config.icon} {config.text}
              </span>
            </div>

            <div className="crowding-bar-container" role="progressbar"
                 aria-valuenow={Math.round(pct)} aria-valuemin={0} aria-valuemax={100}
                 aria-label={`${Math.round(pct)}% full`}>
              <div
                className="crowding-bar"
                style={{ width: `${Math.min(100, pct)}%`, background: config.color }}
              />
            </div>

            <div className="crowding-meta">
              <span>{Math.round(pct)}% capacity</span>
              {c.capacity && c.currentPassengers != null && (
                <span>{c.currentPassengers}/{c.capacity} passengers</span>
              )}
            </div>

            {c.displayMessage && (
              <div className="crowding-alert" style={{ borderColor: config.color }}>
                {c.displayMessage}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}