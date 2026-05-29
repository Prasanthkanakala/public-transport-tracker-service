import React from 'react';
import './VehicleMap.css';

/**
* Visualises vehicle positions on a schematic grid map.
* Since we cannot use a 3rd-party map library in this constraint,
* we render an SVG-based representation with coordinate-scaled positions.
*
* Each vehicle is shown as a dot with:
* - Colour indicating delay severity (green/orange/red)
* - Tooltip showing vehicle ID, delay, and occupancy
*/
export default function VehicleMap({ vehicles = [], loading, routeId }) {
  if (loading && !vehicles.length) {
    return <div className="vehicle-map-loading">⏳ Loading vehicle positions…</div>;
  }

  if (!vehicles.length) {
    return <div className="vehicle-map-empty">No vehicles currently tracked on route {routeId}</div>;
  }

  // Normalise coordinates to a 400x300 viewport
  const lats = vehicles.map(v => v.latitude).filter(Boolean);
  const lons = vehicles.map(v => v.longitude).filter(Boolean);
  const minLat = Math.min(...lats);
  const maxLat = Math.max(...lats);
  const minLon = Math.min(...lons);
  const maxLon = Math.max(...lons);
  const latRange = maxLat - minLat || 0.01;
  const lonRange = maxLon - minLon || 0.01;

  const toX = (lon) => ((lon - minLon) / lonRange) * 360 + 20;
  const toY = (lat) => ((maxLat - lat) / latRange) * 260 + 20;

  const getVehicleColor = (delaySeconds) => {
    if (!delaySeconds || delaySeconds <= 0) return '#22c55e';
    if (delaySeconds < 300) return '#f59e0b';
    if (delaySeconds < 900) return '#f97316';
    return '#ef4444'; // > 15 min
  };

  return (
    <div className="vehicle-map">
      <div className="map-legend">
        <span className="legend-item"><span className="legend-dot green" />On time</span>
        <span className="legend-item"><span className="legend-dot orange" />1–5 min late</span>
        <span className="legend-item"><span className="legend-dot red" />&gt;15 min late</span>
      </div>

      <div className="map-container" role="img" aria-label={`Vehicle positions for route ${routeId}`}>
        <svg viewBox="0 0 400 300" className="map-svg" preserveAspectRatio="xMidYMid meet">
          {/* Grid lines */}
          {[1,2,3,4].map(i => (
            <line key={`h${i}`} x1="0" y1={i * 60} x2="400" y2={i * 60}
                  stroke="rgba(255,255,255,0.04)" strokeWidth="1" />
          ))}
          {[1,2,3,4,5,6].map(i => (
            <line key={`v${i}`} x1={i * 60} y1="0" x2={i * 60} y2="300"
                  stroke="rgba(255,255,255,0.04)" strokeWidth="1" />
          ))}

          {/* Route label */}
          <text x="10" y="16" fill="rgba(255,255,255,0.3)" fontSize="11" fontFamily="mono">
            Route {routeId}
          </text>

          {/* Vehicles */}
          {vehicles.map((v) => {
            const x = toX(v.longitude);
            const y = toY(v.latitude);
            const color = getVehicleColor(v.delaySeconds);
            const delayMins = v.delaySeconds ? Math.round(v.delaySeconds / 60) : 0;

            return (
              <g key={v.vehicleId}>
                {/* Pulse animation for delayed vehicles */}
                {v.delaySeconds > 900 && (
                  <circle cx={x} cy={y} r="14" fill={color} opacity="0.15">
                    <animate attributeName="r" values="10;18;10" dur="2s" repeatCount="indefinite" />
                    <animate attributeName="opacity" values="0.15;0.05;0.15" dur="2s" repeatCount="indefinite" />
                  </circle>
                )}
                <circle
                  cx={x} cy={y} r="8"
                  fill={color}
                  stroke="rgba(255,255,255,0.8)"
                  strokeWidth="1.5"
                  style={{ cursor: 'pointer' }}
                >
                  <title>
                    Vehicle: {v.vehicleId}
                    {'\n'}Status: {v.status?.replace(/_/g, ' ')}
                    {'\n'}Delay: {delayMins > 0 ? `${delayMins} min late` : 'On time'}
                    {'\n'}Occupancy: {v.occupancyStatus?.replace(/_/g, ' ') || '—'}
                  </title>
                </circle>
                {/* Vehicle ID label (abbreviated) */}
                <text x={x} y={y + 20} textAnchor="middle" fill="rgba(255,255,255,0.5)"
                      fontSize="9" fontFamily="mono">
                  {v.vehicleId?.slice(-3)}
                </text>
              </g>
            );
          })}
        </svg>
      </div>

      {/* Vehicle list below the map */}
      <div className="vehicle-list">
        {vehicles.map((v) => {
          const color = getVehicleColor(v.delaySeconds);
          return (
            <div key={v.vehicleId} className="vehicle-row">
              <span className="vehicle-dot" style={{ background: color }} />
              <span className="vehicle-id">{v.vehicleId}</span>
              <span className="vehicle-status">{v.status?.replace(/_/g, ' ')}</span>
              <span className="vehicle-delay" style={{ color }}>
                {v.delayLabel || 'On time'}
              </span>
              <span className="vehicle-occ">{v.occupancyStatus?.replace(/_/g, ' ') || '—'}</span>
            </div>
          );
        })}
      </div>
    </div>
  );
}
