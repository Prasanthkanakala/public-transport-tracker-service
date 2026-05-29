import React from 'react';
import './ArrivalBoard.css';

function formatTime(isoString) {
  if (!isoString) return '—';
  return new Date(isoString).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function DelayBadge({ delaySeconds, status }) {
  if (status === 'CANCELLED') return <span className="badge cancelled">Cancelled</span>;
  if (!delaySeconds || Math.abs(delaySeconds) < 60) return <span className="badge on-time">On time</span>;
  if (delaySeconds < 0) {
    return <span className="badge early">{Math.abs(Math.round(delaySeconds / 60))} min early</span>;
  }
  const mins = Math.round(delaySeconds / 60);
  return (
    <span className={`badge ${mins >= 15 ? 'very-late' : 'late'}`}>
      {mins} min late
    </span>
  );
}

/**
* Departure board style display of upcoming arrivals.
* Highlights delays > 15 minutes in red.
*/
export default function ArrivalBoard({ arrivals = [], loading }) {
  if (loading && !arrivals.length) {
    return <div className="arrival-board-loading">⏳ Loading arrivals…</div>;
  }

  if (!arrivals.length) {
    return <div className="arrival-board-empty">No arrival data available</div>;
  }

  // Sort by minutes to arrival
  const sorted = [...arrivals].sort((a, b) =>
    (a.minutesToArrival ?? 999) - (b.minutesToArrival ?? 999)
  );

  return (
    <div className="arrival-board" role="list" aria-label="Upcoming arrivals">
      {/* Header row */}
      <div className="arrival-row arrival-header" role="row">
        <span>Route</span>
        <span>Destination</span>
        <span>Arrives</span>
        <span>Status</span>
        <span>Min</span>
      </div>

      {sorted.map((arrival, idx) => {
        const isSignificantDelay = (arrival.delaySeconds || 0) > 900; // > 15 min
        return (
          <div
            key={`${arrival.routeId}-${arrival.stopId}-${idx}`}
            className={`arrival-row arrival-item${isSignificantDelay ? ' significant-delay' : ''}`}
            role="listitem"
            aria-label={`${arrival.routeName || arrival.routeId} to ${arrival.headsign}`}
          >
            <span className="route-badge">{arrival.routeId}</span>
            <span className="headsign">{arrival.headsign || '—'}</span>
            <span className="time-cell">
              <span className="predicted-time">{formatTime(arrival.predictedArrival)}</span>
              {arrival.delaySeconds > 60 && (
                <span className="scheduled-time">(sched {formatTime(arrival.scheduledArrival)})</span>
              )}
            </span>
            <span>
              <DelayBadge delaySeconds={arrival.delaySeconds} status={arrival.status} />
            </span>
            <span className="mins-cell">
              {arrival.minutesToArrival != null ? (
                <strong>{arrival.minutesToArrival}</strong>
              ) : '—'}
            </span>
          </div>
        );
      })}
    </div>
  );
}