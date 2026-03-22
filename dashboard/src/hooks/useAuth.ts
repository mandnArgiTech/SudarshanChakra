import { createContext, useContext, useState, useCallback, useMemo, type ReactNode } from 'react';
import React from 'react';
import type { User } from '@/types';

interface AuthContextType {
  token: string | null;
  user: User | null;
  login: (token: string, refreshToken: string, user: User) => void;
  logout: () => void;
  /** If user has no modules array (legacy), treat as full access. */
  hasModule: (moduleId: string | null) => boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem('sc_token'));
  const [user, setUser] = useState<User | null>(() => {
    const stored = localStorage.getItem('sc_user');
    return stored ? JSON.parse(stored) : null;
  });

  const login = useCallback((tok: string, _refreshToken: string, usr: User) => {
    localStorage.setItem('sc_token', tok);
    localStorage.setItem('sc_user', JSON.stringify(usr));
    setToken(tok);
    setUser(usr);
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem('sc_token');
    localStorage.removeItem('sc_user');
    setToken(null);
    setUser(null);
  }, []);

  const hasModule = useCallback(
    (moduleId: string | null) => {
      if (moduleId === null) return true;
      const m = user?.modules;
      if (!m || m.length === 0) return true;
      return m.includes(moduleId);
    },
    [user],
  );

  const value = useMemo(
    () => ({ token, user, login, logout, hasModule }),
    [token, user, login, logout, hasModule],
  );

  return React.createElement(AuthContext.Provider, { value }, children);
}

export function useAuth(): AuthContextType {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within AuthProvider');
  return context;
}
