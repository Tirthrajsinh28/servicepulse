import { useCallback, useEffect, useMemo, useState } from "react";

import { useApi } from "../lib/api-context";
import type { CurrentUser, Workspace } from "../lib/types";
import { AuthContext, type AuthContextValue, type AuthStatus } from "./auth-context";

const WORKSPACE_KEY = "servicepulse.workspace-id";

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const api = useApi();
  const [status, setStatus] = useState<AuthStatus>("loading");
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [workspaceId, setWorkspaceId] = useState<string | null>(null);

  const applySession = useCallback(
    (session: { user: CurrentUser; workspaces: Workspace[] }) => {
      setUser(session.user);
      setWorkspaces(session.workspaces);
      const stored = sessionStorage.getItem(WORKSPACE_KEY);
      const selected =
        session.workspaces.find((workspace) => workspace.id === stored)?.id ??
        session.workspaces[0]?.id ??
        null;
      setWorkspaceId(selected);
      if (selected) sessionStorage.setItem(WORKSPACE_KEY, selected);
      setStatus("authenticated");
    },
    [],
  );

  useEffect(() => {
    let active = true;
    api.restoreSession().then((session) => {
      if (!active) return;
      if (session) {
        applySession(session);
      } else {
        setStatus("anonymous");
      }
    });
    return () => {
      active = false;
    };
  }, [api, applySession]);

  const login = useCallback(
    async (email: string, password: string) => {
      const session = await api.login(email, password);
      applySession(session);
    },
    [api, applySession],
  );

  const logout = useCallback(async () => {
    try {
      await api.logout();
    } finally {
      sessionStorage.removeItem(WORKSPACE_KEY);
      setUser(null);
      setWorkspaces([]);
      setWorkspaceId(null);
      setStatus("anonymous");
    }
  }, [api]);

  const selectWorkspace = useCallback(
    (selected: string) => {
      if (!workspaces.some((workspace) => workspace.id === selected)) return;
      sessionStorage.setItem(WORKSPACE_KEY, selected);
      setWorkspaceId(selected);
    },
    [workspaces],
  );

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      user,
      workspaces,
      workspaceId,
      login,
      logout,
      selectWorkspace,
    }),
    [status, user, workspaces, workspaceId, login, logout, selectWorkspace],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
