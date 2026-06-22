import { create } from "zustand";
import type { CurrentUser } from "../types/domain";

const tokenStorageKey = "ticketdesk.token";
const userStorageKey = "ticketdesk.user";

interface AuthState {
  token: string | null;
  user: CurrentUser | null;
  setSession: (token: string, user: CurrentUser) => void;
  setUser: (user: CurrentUser | null) => void;
  clearSession: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  token: localStorage.getItem(tokenStorageKey),
  user: readStoredUser(),
  setSession: (token, user) => {
    localStorage.setItem(tokenStorageKey, token);
    localStorage.setItem(userStorageKey, JSON.stringify(user));
    set({ token, user });
  },
  setUser: (user) => {
    if (user) {
      localStorage.setItem(userStorageKey, JSON.stringify(user));
    } else {
      localStorage.removeItem(userStorageKey);
    }
    set({ user });
  },
  clearSession: () => {
    localStorage.removeItem(tokenStorageKey);
    localStorage.removeItem(userStorageKey);
    set({ token: null, user: null });
  },
}));

function readStoredUser(): CurrentUser | null {
  const raw = localStorage.getItem(userStorageKey);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as CurrentUser;
  } catch {
    localStorage.removeItem(userStorageKey);
    return null;
  }
}
