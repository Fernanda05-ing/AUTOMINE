import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, session, setUnauthorizedHandler, type Usuario } from './api';

interface AuthState {
  user: Usuario | null;
  login: (email: string, password: string) => Promise<Usuario>;
  loginGoogle: (credential: string) => Promise<Usuario>;
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
  can: (...roles: string[]) => boolean;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Usuario | null>(() => (session.token() ? session.user() : null));

  useEffect(() => {
    setUnauthorizedHandler(() => setUser(null));
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const res = await api.post<{ token: string; usuario: Usuario }>('/Login', { email, password });
    session.save(res.token, res.usuario);
    setUser(res.usuario);
    return res.usuario;
  }, []);

  const loginGoogle = useCallback(async (credential: string) => {
    const res = await api.post<{ token: string; usuario: Usuario }>('/LoginGoogle', { credential });
    session.save(res.token, res.usuario);
    setUser(res.usuario);
    return res.usuario;
  }, []);

  const logout = useCallback(async () => {
    try {
      await api.post('/Logout');
    } catch {
      /* el token puede haber vencido */
    }
    session.clear();
    setUser(null);
  }, []);

  const refresh = useCallback(async () => {
    const u = await api.get<Usuario>('/Profile');
    const token = session.token();
    if (token) session.save(token, u);
    setUser(u);
  }, []);

  const value = useMemo<AuthState>(
    () => ({
      user,
      login,
      loginGoogle,
      logout,
      refresh,
      can: (...roles: string[]) => !!user && (user.rol === 'ADMIN' || roles.includes(user.rol)),
    }),
    [user, login, loginGoogle, logout, refresh],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth fuera de AuthProvider');
  return ctx;
}
