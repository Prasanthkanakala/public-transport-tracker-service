import React, { useState } from 'react';
import { useAuth } from '../../hooks/useAuth';
import { loginUser } from '../../services/apiService';
import './Login.css';

/**
 * Login page component.
 * Provides a form for username/password authentication.
 * On success, stores JWT token and redirects to main app.
 */
export default function Login() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);
  const { login } = useAuth();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    try {
      const response = await loginUser({ username, password });
      login(response);
    } catch (err) {
      if (err.status === 401) {
        setError('Invalid username or password.');
      } else {
        setError(err.message || 'Login failed. Please try again.');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-container">
      <div className="login-card">
        <div className="login-header">
          <span className="login-icon" role="img" aria-label="Transit">🚇</span>
          <h1>Transit Tracker</h1>
          <p>Sign in to access the dashboard</p>
        </div>

        <form className="login-form" onSubmit={handleSubmit} noValidate>
          {error && (
            <div className="login-error" role="alert">
              <span>✕</span> {error}
            </div>
          )}

          <div className="form-group">
            <label htmlFor="username">Username</label>
            <input
              id="username"
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="Enter your username"
              required
              autoComplete="username"
              disabled={loading}
              aria-required="true"
            />
          </div>

          <div className="form-group">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Enter your password"
              required
              autoComplete="current-password"
              disabled={loading}
              aria-required="true"
            />
          </div>

          <button
            type="submit"
            className="login-btn"
            disabled={loading || !username || !password}
          >
            {loading ? 'Signing in...' : 'Sign In'}
          </button>
        </form>

        <div className="login-demo-info">
          <p><strong>Demo Credentials:</strong></p>
          <ul>
            <li><code>admin</code> / <code>admin123</code> — Full access</li>
            <li><code>operator</code> / <code>operator123</code> — View + Stats</li>
            <li><code>viewer</code> / <code>viewer123</code> — View only</li>
          </ul>
        </div>
      </div>
    </div>
  );
}
