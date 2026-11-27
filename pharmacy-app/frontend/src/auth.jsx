import { createContext, useContext, useState } from 'react';
import api from './api';

const Ctx = createContext(null);
export const useAuth = () => useContext(Ctx);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => JSON.parse(localStorage.getItem('user') || 'null'));

  const login = async (username, password) => {
    const { data } = await api.post('/auth/login', { username, password });
    localStorage.setItem('token', data.token);
    localStorage.setItem('user', JSON.stringify(data));
    setUser(data);
  };
  const logout = () => {
    localStorage.clear();
    setUser(null);
  };
  const hasRole = (...roles) => !!user && user.roles.some((r) => roles.includes(r));

  return <Ctx.Provider value={{ user, login, logout, hasRole }}>{children}</Ctx.Provider>;
}
