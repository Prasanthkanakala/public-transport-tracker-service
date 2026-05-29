import React from 'react';
import './OfflineToggle.css';

/**
* Toggle switch for enabling/disabling offline mode.
* When offline=true, the app serves mock data without making API calls.
*/
export default function OfflineToggle({ offline, onToggle }) {
  return (
    <div className="offline-toggle" title={offline ? 'Offline mode ON – showing mock data' : 'Online mode – live data'}>
      <label className="toggle-label" htmlFor="offline-toggle">
        <span className={`toggle-text ${offline ? 'active' : ''}`}>
          {offline ? '📵 Offline' : '📡 Live'}
        </span>
        <div className="toggle-track">
          <input
            id="offline-toggle"
            type="checkbox"
            className="toggle-checkbox"
            checked={offline}
            onChange={(e) => onToggle(e.target.checked)}
            aria-label="Toggle offline mode"
          />
          <span className={`toggle-thumb ${offline ? 'checked' : ''}`} />
        </div>
      </label>
    </div>
  );
}