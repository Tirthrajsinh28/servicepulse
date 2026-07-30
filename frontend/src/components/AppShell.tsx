import type { ReactNode } from "react";

import { useAuth } from "../auth/auth-context";
import { NavLink } from "../router";

export function AppShell({ children }: { children: ReactNode }) {
  const auth = useAuth();
  const currentWorkspace = auth.workspaces.find(
    (workspace) => workspace.id === auth.workspaceId,
  );

  return (
    <div className="app-frame">
      <a className="skip-link" href="#main-content">
        Skip to main content
      </a>
      <header className="app-header">
        <NavLink className="brand" to="/" aria-label="ServicePulse dashboard">
          <span className="brand-mark" aria-hidden="true">
            SP
          </span>
          <span>
            <strong>ServicePulse</strong>
            <small>Incident operations</small>
          </span>
        </NavLink>
        <div className="header-actions">
          <label className="workspace-picker">
            <span>Workspace</span>
            <select
              value={auth.workspaceId ?? ""}
              onChange={(event) => auth.selectWorkspace(event.target.value)}
              disabled={auth.workspaces.length < 2}
            >
              {auth.workspaces.map((workspace) => (
                <option key={workspace.id} value={workspace.id}>
                  {workspace.name}
                </option>
              ))}
            </select>
          </label>
          <button className="button button-ghost" type="button" onClick={() => void auth.logout()}>
            Sign out
          </button>
        </div>
      </header>
      <div className="app-body">
        <nav className="side-nav" aria-label="Primary navigation">
          <NavLink end to="/">
            Overview
          </NavLink>
          <NavLink to="/services">Services</NavLink>
          <NavLink to="/members">Members</NavLink>
          {currentWorkspace?.role === "ADMIN" ? (
            <NavLink to="/notifications">Notifications</NavLink>
          ) : null}
          {currentWorkspace?.role !== "VIEWER" ? (
            <NavLink to="/incidents/new">Declare incident</NavLink>
          ) : null}
          <div className="workspace-meta">
            <span>{currentWorkspace?.role ?? "No role"}</span>
            <small>{currentWorkspace?.slug ?? "No workspace selected"}</small>
          </div>
        </nav>
        <main id="main-content" tabIndex={-1}>
          {children}
        </main>
      </div>
    </div>
  );
}
