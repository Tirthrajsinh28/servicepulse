import { createContext, useContext } from "react";

import type { CurrentUser, Workspace } from "../lib/types";

export type AuthStatus = "loading" | "anonymous" | "authenticated";

export interface AuthContextValue {
  status: AuthStatus;
  user: CurrentUser | null;
  workspaces: Workspace[];
  workspaceId: string | null;
  login(email: string, password: string): Promise<void>;
  logout(): Promise<void>;
  selectWorkspace(workspaceId: string): void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return value;
}
