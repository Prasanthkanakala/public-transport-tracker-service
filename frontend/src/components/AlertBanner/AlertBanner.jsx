import React from 'react';
import './AlertBanner.css';

const ALERT_CONFIG = {
  DELAY:      { icon: '⏱', color: 'warning' },
  DISRUPTION: { icon: '⚠', color: 'error' },
  CROWDING:   { icon: '👥', color: 'warning' },
  WEATHER:    { icon: '🌧', color: 'info' },
};

/**
* Displays conditional alert banners at the top of the page.
* Each alert has an icon, colour, and prescribed message text.
*/
export default function AlertBanner({ alerts = [] }) {
  if (!alerts.length) return null;

  return (
    <div className="alert-banner-container" role="alert" aria-live="polite">
      {alerts.map((alert, idx) => {
        const config = ALERT_CONFIG[alert.type] || { icon: 'ℹ', color: 'info' };
        return (
          <div key={idx} className={`alert-banner alert-banner--${config.color}`}>
            <span className="alert-banner-icon" aria-hidden="true">{config.icon}</span>
            <span className="alert-banner-message">{alert.message}</span>
          </div>
        );
      })}
    </div>
  );
}