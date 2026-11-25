import React, { createContext, useContext, useState } from 'react';
import { api, getStoredSession, setStoredSession } from '../api/client';
import { AuthResponse, UserProfile } from '../types';

interface AuthContextValue {
  user: UserProfile | null;
  isAuthenticated: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [session, setSession] = useState<AuthResponse | null>(() => getStoredSession());

  const login = async (email: string, password: string) => {
    const res = await api.login(email, password);
    setStoredSession(res);
    setSession(res);
  };

  const logout = () => {
    setStoredSession(null);
    setSession(null);
  };

  return (
    <AuthContext.Provider
      value={{
        user: session?.user ?? null,
        isAuthenticated: Boolean(session?.accessToken),
        login,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
