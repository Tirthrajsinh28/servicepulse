import { useEffect, useMemo, useState } from "react";

import { useAuth } from "../auth/auth-context";
import { EmptyState, ErrorNotice, LoadingPanel } from "../components/Feedback";
import { ApiError } from "../lib/api";
import { useApi } from "../lib/api-context";
import { formatDateTime, humanize } from "../lib/format";
import type { WorkspaceMember, WorkspaceRole } from "../lib/types";

const ROLE_OPTIONS: WorkspaceRole[] = ["ADMIN", "RESPONDER", "VIEWER"];
const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export function MembersPage() {
  const api = useApi();
  const auth = useAuth();
  const [members, setMembers] = useState<WorkspaceMember[]>([]);
  const [loading, setLoading] = useState(true);
  const [adding, setAdding] = useState(false);
  const [savingUserId, setSavingUserId] = useState<string | null>(null);
  const [removingUserId, setRemovingUserId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [newUserId, setNewUserId] = useState("");
  const [newRole, setNewRole] = useState<WorkspaceRole>("RESPONDER");
  const [roleDrafts, setRoleDrafts] = useState<Record<string, WorkspaceRole>>({});

  const workspace = auth.workspaces.find(
    (candidate) => candidate.id === auth.workspaceId,
  );
  const canAdminister = workspace?.role === "ADMIN";
  const enabledAdminCount = useMemo(
    () =>
      members.filter((member) => member.enabled && member.role === "ADMIN").length,
    [members],
  );
  const enabledMemberCount = useMemo(
    () => members.filter((member) => member.enabled).length,
    [members],
  );

  useEffect(() => {
    if (!auth.workspaceId) {
      return;
    }
    let active = true;
    api
      .getMembers(auth.workspaceId)
      .then((items) => {
        if (!active) return;
        setMembers(items);
        setRoleDrafts(Object.fromEntries(items.map((member) => [member.userId, member.role])));
        setError(null);
      })
      .catch((caught: unknown) => {
        if (active) {
          setError(caught instanceof Error ? caught.message : "Unable to load members.");
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [api, auth.workspaceId]);

  if (!auth.workspaceId) {
    return (
      <EmptyState
        title="No workspace membership"
        message="Workspace members can only be shown after an authorized workspace is selected."
      />
    );
  }

  async function addMember(event: React.FormEvent) {
    event.preventDefault();
    if (!auth.workspaceId || !canAdminister) return;
    const trimmedUserId = newUserId.trim();
    const nextErrors: Record<string, string> = {};
    if (!trimmedUserId) {
      nextErrors.userId = "Enter the existing user's UUID.";
    } else if (!UUID_PATTERN.test(trimmedUserId)) {
      nextErrors.userId = "Use a valid UUID for an existing enabled user.";
    }
    if (Object.keys(nextErrors).length) {
      setFieldErrors(nextErrors);
      return;
    }
    setAdding(true);
    setError(null);
    setSuccess(null);
    setFieldErrors({});
    try {
      const added = await api.addMember(auth.workspaceId, {
        userId: trimmedUserId,
        role: newRole,
      });
      setMembers((current) => [...current, added].sort(compareMembers));
      setRoleDrafts((current) => ({ ...current, [added.userId]: added.role }));
      setNewUserId("");
      setNewRole("RESPONDER");
      setSuccess(`${added.displayName} was added as ${humanize(added.role)}.`);
    } catch (caught) {
      if (caught instanceof ApiError) {
        setError(caught.message);
        setFieldErrors(caught.fieldErrors);
      } else {
        setError("The member could not be added.");
      }
    } finally {
      setAdding(false);
    }
  }

  async function saveRole(member: WorkspaceMember) {
    if (!auth.workspaceId || !canAdminister) return;
    const role = roleDrafts[member.userId] ?? member.role;
    setSavingUserId(member.userId);
    setError(null);
    setSuccess(null);
    try {
      const updated = await api.changeMemberRole(auth.workspaceId, member.userId, {
        role,
      });
      setMembers((current) =>
        current.map((item) => (item.userId === updated.userId ? updated : item))
          .sort(compareMembers),
      );
      setRoleDrafts((current) => ({ ...current, [updated.userId]: updated.role }));
      setSuccess(`${updated.displayName} role changed to ${humanize(updated.role)}.`);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "The member role could not be saved.");
    } finally {
      setSavingUserId(null);
    }
  }

  async function removeMember(member: WorkspaceMember) {
    if (!auth.workspaceId || !canAdminister) return;
    setRemovingUserId(member.userId);
    setError(null);
    setSuccess(null);
    try {
      await api.removeMember(auth.workspaceId, member.userId);
      setMembers((current) => current.filter((item) => item.userId !== member.userId));
      setRoleDrafts((current) => {
        const next = { ...current };
        delete next[member.userId];
        return next;
      });
      setSuccess(`${member.displayName} was removed from the workspace.`);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "The member could not be removed.");
    } finally {
      setRemovingUserId(null);
    }
  }

  return (
    <div className="page-stack">
      <header className="page-header">
        <div>
          <span className="eyebrow">Workspace access</span>
          <h1 tabIndex={-1}>Members and roles</h1>
          <p>
            Review who can act inside <strong>{workspace?.name}</strong>. The
            API keeps role changes tenant-scoped and prevents removing the last
            enabled administrator.
          </p>
        </div>
      </header>

      {error ? <ErrorNotice message={error} /> : null}
      {success ? (
        <div className="notice notice-success" role="status">
          <strong>{success}</strong>
        </div>
      ) : null}

      {loading ? <LoadingPanel label="Loading members" /> : null}

      {!loading ? (
        <section className="summary-grid" aria-label="Membership summary">
          <article>
            <span>Total members</span>
            <strong>{members.length}</strong>
            <small>Workspace records</small>
          </article>
          <article>
            <span>Enabled members</span>
            <strong>{enabledMemberCount}</strong>
            <small>Can be used for active authorization decisions</small>
          </article>
          <article>
            <span>Enabled admins</span>
            <strong>{enabledAdminCount}</strong>
            <small>At least one must remain</small>
          </article>
          <article className="severity-card">
            <span>Invite policy</span>
            <strong>Existing</strong>
            <small>Adds require an enabled user ID; invitations are future work</small>
          </article>
        </section>
      ) : null}

      {canAdminister ? (
        <section className="content-card" aria-labelledby="new-member-heading">
          <div className="section-heading">
            <div>
              <span className="eyebrow">Administrator action</span>
              <h2 id="new-member-heading">Add an existing user</h2>
            </div>
          </div>
          <form className="form-stack" onSubmit={addMember} noValidate>
            <div className="form-grid">
              <label>
                Existing user UUID
                <input
                  value={newUserId}
                  onChange={(event) => setNewUserId(event.target.value)}
                  aria-describedby={
                    fieldErrors.userId ? "member-user-id-error" : "member-user-id-hint"
                  }
                />
                <small id="member-user-id-hint">
                  Registration and invitations are not implemented; add only a
                  known enabled user.
                </small>
                {fieldErrors.userId ? (
                  <small className="field-error" id="member-user-id-error">
                    {fieldErrors.userId}
                  </small>
                ) : null}
              </label>
              <label>
                Role
                <select
                  value={newRole}
                  onChange={(event) => setNewRole(event.target.value as WorkspaceRole)}
                >
                  {ROLE_OPTIONS.map((role) => (
                    <option key={role} value={role}>
                      {humanize(role)}
                    </option>
                  ))}
                </select>
              </label>
            </div>
            <div className="form-actions">
              <button className="button button-primary" type="submit" disabled={adding}>
                {adding ? "Adding member..." : "Add member"}
              </button>
              <span className="form-hint">
                The backend rejects disabled, missing, duplicate, or lockout-causing changes.
              </span>
            </div>
          </form>
        </section>
      ) : null}

      <section className="content-card" aria-labelledby="members-heading">
        <div className="section-heading">
          <div>
            <span className="eyebrow">Workspace directory</span>
            <h2 id="members-heading">Members</h2>
          </div>
          <span>{members.length} member(s)</span>
        </div>

        {!loading && members.length === 0 ? (
          <EmptyState
            title="No members found"
            message="This should not happen for an active workspace; verify the selected workspace."
          />
        ) : null}

        {members.length > 0 ? (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th scope="col">Member</th>
                  <th scope="col">Role</th>
                  <th scope="col">Status</th>
                  <th scope="col">Joined</th>
                  {canAdminister ? <th scope="col">Actions</th> : null}
                </tr>
              </thead>
              <tbody>
                {members.map((member) => {
                  const draft = roleDrafts[member.userId] ?? member.role;
                  const isOnlyEnabledAdmin =
                    member.enabled && member.role === "ADMIN" && enabledAdminCount <= 1;
                  return (
                    <tr key={member.userId}>
                      <th scope="row">
                        {member.displayName}
                        <small>{member.email}</small>
                        <small>
                          <code>{member.userId}</code>
                        </small>
                      </th>
                      <td>
                        {canAdminister ? (
                          <label className="table-control">
                            <span className="sr-only">Role for {member.displayName}</span>
                            <select
                              value={draft}
                              onChange={(event) =>
                                setRoleDrafts((current) => ({
                                  ...current,
                                  [member.userId]: event.target.value as WorkspaceRole,
                                }))
                              }
                              disabled={isOnlyEnabledAdmin}
                            >
                              {ROLE_OPTIONS.map((role) => (
                                <option key={role} value={role}>
                                  {humanize(role)}
                                </option>
                              ))}
                            </select>
                          </label>
                        ) : (
                          <span className="badge status-monitoring">
                            {humanize(member.role)}
                          </span>
                        )}
                      </td>
                      <td>
                        <span className={`badge ${member.enabled ? "status-resolved" : "status-open"}`}>
                          {member.enabled ? "Enabled" : "Disabled"}
                        </span>
                      </td>
                      <td>{formatDateTime(member.createdAt)}</td>
                      {canAdminister ? (
                        <td>
                          <div className="table-actions">
                            <button
                              className="button button-secondary"
                              type="button"
                              disabled={
                                savingUserId === member.userId
                                || draft === member.role
                                || isOnlyEnabledAdmin
                              }
                              onClick={() => void saveRole(member)}
                            >
                              {savingUserId === member.userId ? "Saving..." : "Save"}
                            </button>
                            <button
                              className="button button-ghost"
                              type="button"
                              disabled={removingUserId === member.userId || isOnlyEnabledAdmin}
                              onClick={() => void removeMember(member)}
                            >
                              {removingUserId === member.userId ? "Removing..." : "Remove"}
                            </button>
                          </div>
                        </td>
                      ) : null}
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ) : null}
      </section>
    </div>
  );
}

function compareMembers(left: WorkspaceMember, right: WorkspaceMember): number {
  const roleOrder: Record<WorkspaceRole, number> = {
    ADMIN: 0,
    RESPONDER: 1,
    VIEWER: 2,
  };
  return (
    roleOrder[left.role] - roleOrder[right.role]
    || left.displayName.localeCompare(right.displayName, "en", { sensitivity: "base" })
    || left.userId.localeCompare(right.userId)
  );
}
