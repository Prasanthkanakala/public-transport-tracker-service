import React, { createContext, useState, useCallback, useMemo, useEffect } from 'react';

/**
 * Authentication Context for managing JWT-based auth state.
 * Provides login, logout, and role-checking capabilities.
 * Token, username, and role are persisted in localStorage.
 */
export const AuthContext = createContext(null);

const STORAGE_KEYS = {
  TOKEN: 'auth_token',
  USERNAME: 'auth_username',
  ROLE: 'auth_role',
};

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem(STORAGE_KEYS.TOKEN));
  const [username, setUsername] = useState(() => localStorage.getItem(STORAGE_KEYS.USERNAME));
  const [role, setRole] = useState(() => localStorage.getItem(STORAGE_KEYS.ROLE));

  const isAuthenticated = Boolean(token);

  const login = useCallback((authData) => {
    const { token: newToken, username: newUsername, role: newRole } = authData;
    localStorage.setItem(STORAGE_KEYS.TOKEN, newToken);
    localStorage.setItem(STORAGE_KEYS.USERNAME, newUsername);
    localStorage.setItem(STORAGE_KEYS.ROLE, newRole);
    setToken(newToken);
    setUsername(newUsername);
    setRole(newRole);
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem(STORAGE_KEYS.TOKEN);
    localStorage.removeItem(STORAGE_KEYS.USERNAME);
    localStorage.removeItem(STORAGE_KEYS.ROLE);
    setToken(null);
    setUsername(null);
    setRole(null);
  }, []);

  /**
   * Check if the current user has the required role.
   * Role hierarchy: ADMIN > OPERATOR > VIEWER
   */
  const hasRole = useCallback((requiredRole) => {
    if (!role) return false;
    const hierarchy = { VIEWER: 1, OPERATOR: 2, ADMIN: 3 };
    return (hierarchy[role] || 0) >= (hierarchy[requiredRole] || 0);
  }, [role]);

  /**
   * Check if the current user has any of the required roles.
   */
  const hasAnyRole = useCallback((...roles) => {
    return roles.some((r) => r === role);
  }, [role]);

  // Listen for storage events (multi-tab sync)
  useEffect(() => {
    const handleStorageChange = (e) => {
      if (e.key === STORAGE_KEYS.TOKEN) {
        if (!e.newValue) {
          logout();
        } else {
          setToken(e.newValue);
          setUsername(localStorage.getItem(STORAGE_KEYS.USERNAME));
          setRole(localStorage.getItem(STORAGE_KEYS.ROLE));
        }
      }
    };
    window.addEventListener('storage', handleStorageChange);
    return () => window.removeEventListener('storage', handleStorageChange);
  }, [logout]);

  const value = useMemo(() => ({
    token,
    username,
    role,
    isAuthenticated,
    login,
    logout,
    hasRole,
    hasAnyRole,
  }), [token, username, role, isAuthenticated, login, logout, hasRole, hasAnyRole]);

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
}
