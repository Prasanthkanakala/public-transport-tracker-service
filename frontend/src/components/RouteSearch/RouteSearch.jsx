import React, { useState } from 'react';
import './RouteSearch.css';

/**
* Route search form for entering city and route identifiers.
* Calls onSearch({ city, routeId }) when the form is submitted.
*/
export default function RouteSearch({ initialCity = 'nyc', initialRoute = 'A', onSearch }) {
  const [city, setCity] = useState(initialCity);
  const [routeId, setRouteId] = useState(initialRoute);

  const handleSubmit = (e) => {
    e.preventDefault();
    if (city.trim() || routeId.trim()) {
      onSearch({ city: city.trim(), routeId: routeId.trim() });
    }
  };

  return (
    <form className="route-search" onSubmit={handleSubmit} role="search" aria-label="Search for transit routes">
      <div className="search-fields">
        <div className="field-group">
          <label htmlFor="city-input" className="field-label">City</label>
          <input
            id="city-input"
            type="text"
            className="search-input"
            placeholder="e.g. nyc, london, sydney"
            value={city}
            onChange={(e) => setCity(e.target.value)}
            maxLength={50}
            autoComplete="off"
            spellCheck={false}
          />
        </div>
        <div className="field-group">
          <label htmlFor="route-input" className="field-label">Route</label>
          <input
            id="route-input"
            type="text"
            className="search-input"
            placeholder="e.g. A, 1, M15, 4B"
            value={routeId}
            onChange={(e) => setRouteId(e.target.value)}
            maxLength={20}
            autoComplete="off"
            spellCheck={false}
          />
        </div>
        <button type="submit" className="search-btn" aria-label="Search">
          <span>Search</span>
          <span className="search-icon">→</span>
        </button>
      </div>

      {/* Quick presets */}
      <div className="presets">
        <span className="presets-label">Quick select:</span>
        {[
          { city: 'nyc', route: 'A', label: 'NYC A' },
          { city: 'nyc', route: '1', label: 'NYC 1' },
          { city: 'nyc', route: 'L', label: 'NYC L' },
          { city: 'london', route: 'Central', label: 'London Central' },
        ].map(({ city: c, route: r, label }) => (
          <button
            key={label}
            type="button"
            className="preset-btn"
            onClick={() => {
              setCity(c);
              setRouteId(r);
              onSearch({ city: c, routeId: r });
            }}
          >
            {label}
          </button>
        ))}
      </div>
    </form>
  );
}