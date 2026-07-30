import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useLocation, useParams } from "../router";

import { useAuth } from "../auth/auth-context";
import { SeverityBadge, StatusBadge } from "../components/Badges";
import { ErrorNotice, LoadingPanel } from "../components/Feedback";
import { ApiError } from "../lib/api";
import { useApi } from "../lib/api-context";
import { formatDateTime, humanize } from "../lib/format";
import type {
  Incident,
  IncidentComment,
  IncidentEvent,
  IncidentStatus,
  ManagedService,
  WorkspaceMember,
} from "../lib/types";

const NEXT_STATUSES: Record<IncidentStatus, IncidentStatus[]> = {
  OPEN: ["INVESTIGATING", "RESOLVED"],
  INVESTIGATING: ["IDENTIFIED", "MONITORING"],
  IDENTIFIED: ["MONITORING", "RESOLVED"],
  MONITORING: ["INVESTIGATING", "RESOLVED"],
  RESOLVED: [],
};

function formatEventDetail(
  event: IncidentEvent,
  memberNames: ReadonlyMap<string, string>,
) {
  if (event.eventType !== "ASSIGNEE_CHANGED" || !event.detail) {
    return event.detail ?? "No additional detail.";
  }

  const assignees = event.detail.split(/\s*->\s*/);
  if (assignees.length !== 2) return event.detail;

  const displayAssignee = (value: string) =>
    value.toLowerCase() === "unassigned"
      ? "Unassigned"
      : memberNames.get(value) ?? "Workspace member";

  return `${displayAssignee(assignees[0])} → ${displayAssignee(assignees[1])}`;
}

export function IncidentDetailPage() {
  const { id = "" } = useParams();
  const api = useApi();
  const auth = useAuth();
  const location = useLocation();
  const [incident, setIncident] = useState<Incident | null>(null);
  const [events, setEvents] = useState<IncidentEvent[]>([]);
  const [comments, setComments] = useState<IncidentComment[]>([]);
  const [services, setServices] = useState<ManagedService[]>([]);
  const [members, setMembers] = useState<WorkspaceMember[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadedIncidentId, setLoadedIncidentId] = useState<string | null>(null);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [commentBody, setCommentBody] = useState("");
  const [transitionStatus, setTransitionStatus] = useState<IncidentStatus | "">("");
  const [transitionDetail, setTransitionDetail] = useState("");
  const [assigneeId, setAssigneeId] = useState("");
  const [flash, setFlash] = useState(
    (location.state as { flash?: string } | null)?.flash ?? "",
  );

  const fetchData = useCallback(() => {
    if (!id || !auth.workspaceId) return Promise.resolve(null);
    return Promise.all([
      api.getIncident(id),
      api.getIncidentEvents(id),
      api.getIncidentComments(id),
      api.getServices(auth.workspaceId),
      api.getMembers(auth.workspaceId),
    ]);
  }, [api, auth.workspaceId, id]);

  const applyData = useCallback(
    (
      data: [
        Incident,
        IncidentEvent[],
        IncidentComment[],
        ManagedService[],
        WorkspaceMember[],
      ],
    ) => {
      const [nextIncident, nextEvents, nextComments, nextServices, nextMembers] =
        data;
      setError(null);
      setIncident(nextIncident);
      setEvents(nextEvents);
      setComments(nextComments);
      setServices(nextServices);
      setMembers(nextMembers);
      setAssigneeId(nextIncident.assigneeId ?? "");
      setTransitionStatus(NEXT_STATUSES[nextIncident.status][0] ?? "");
      setLoadedIncidentId(nextIncident.id);
    },
    [],
  );

  const load = useCallback(async () => {
    const data = await fetchData();
    if (!data) return;
    applyData(data);
  }, [applyData, fetchData]);

  useEffect(() => {
    let active = true;
    fetchData()
      .then((data) => {
        if (active && data) applyData(data);
      })
      .catch((caught: unknown) => {
        if (active) {
          setError(caught instanceof Error ? caught.message : "Unable to load incident.");
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [applyData, fetchData]);

  const workspace = auth.workspaces.find(
    (candidate) => candidate.id === auth.workspaceId,
  );
  const canRespond = workspace?.role !== "VIEWER";
  const service = services.find((candidate) => candidate.id === incident?.serviceId);
  const eligibleMembers = useMemo(
    () =>
      members.filter(
        (member) =>
          member.enabled && (member.role === "ADMIN" || member.role === "RESPONDER"),
      ),
    [members],
  );
  const memberNames = useMemo(
    () => new Map(members.map((member) => [member.userId, member.displayName])),
    [members],
  );

  async function runAction(action: () => Promise<unknown>, success: string) {
    setWorking(true);
    setError(null);
    try {
      await action();
      await load();
      setFlash(success);
    } catch (caught) {
      setError(
        caught instanceof ApiError
          ? caught.message
          : "The operation could not be completed.",
      );
    } finally {
      setWorking(false);
    }
  }

  async function submitComment(event: React.FormEvent) {
    event.preventDefault();
    const body = commentBody.trim();
    if (!body) {
      setError("Write a comment before submitting.");
      return;
    }
    await runAction(
      () => api.addComment(id, body),
      "Comment added to the incident record.",
    );
    setCommentBody("");
  }

  async function submitTransition(event: React.FormEvent) {
    event.preventDefault();
    if (!transitionStatus) return;
    await runAction(
      () => api.transitionIncident(id, transitionStatus, transitionDetail.trim()),
      `Incident moved to ${humanize(transitionStatus)}.`,
    );
    setTransitionDetail("");
  }

  async function submitAssignment(event: React.FormEvent) {
    event.preventDefault();
    await runAction(
      () =>
        assigneeId
          ? api.assignIncident(id, assigneeId)
          : api.clearAssignee(id),
      assigneeId ? "Incident assignment updated." : "Incident is now unassigned.",
    );
  }

  if (loading || loadedIncidentId !== id) {
    return <LoadingPanel label="Loading incident record" />;
  }

  if (!incident) {
    return (
      <div className="page-stack">
        <ErrorNotice message={error ?? "The incident was not found."} />
        <Link className="button button-secondary" to="/">
          Return to overview
        </Link>
      </div>
    );
  }

  return (
    <div className="page-stack">
      <header className="page-header incident-header">
        <div>
          <Link className="back-link" to="/">
            ← Incident overview
          </Link>
          <div className="badge-row">
            <SeverityBadge severity={incident.severity} />
            <StatusBadge status={incident.status} />
          </div>
          <h1 tabIndex={-1}>{incident.title}</h1>
          <p>{incident.summary}</p>
        </div>
        <dl className="incident-meta">
          <div>
            <dt>Service</dt>
            <dd>{service?.name ?? "Unknown service"}</dd>
          </div>
          <div>
            <dt>Declared</dt>
            <dd>{formatDateTime(incident.declaredAt)}</dd>
          </div>
          <div>
            <dt>Assigned to</dt>
            <dd>
              {incident.assigneeId
                ? memberNames.get(incident.assigneeId) ?? incident.assigneeId
                : "Unassigned"}
            </dd>
          </div>
        </dl>
      </header>

      {flash ? (
        <div className="notice notice-success" role="status">
          <strong>Record updated</strong>
          <p>{flash}</p>
          <button className="button button-ghost" type="button" onClick={() => setFlash("")}>
            Dismiss
          </button>
        </div>
      ) : null}
      {error ? <ErrorNotice message={error} onRetry={() => void load()} /> : null}

      {canRespond ? (
        <section className="action-grid" aria-label="Incident actions">
          <form className="content-card form-stack" onSubmit={submitTransition}>
            <div>
              <span className="eyebrow">Workflow</span>
              <h2>Change status</h2>
            </div>
            {NEXT_STATUSES[incident.status].length ? (
              <>
                <label>
                  Next status
                  <select
                    value={transitionStatus}
                    onChange={(event) =>
                      setTransitionStatus(event.target.value as IncidentStatus)
                    }
                  >
                    {NEXT_STATUSES[incident.status].map((status) => (
                      <option key={status} value={status}>
                        {humanize(status)}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Operational note
                  <textarea
                    rows={3}
                    maxLength={2000}
                    value={transitionDetail}
                    onChange={(event) => setTransitionDetail(event.target.value)}
                  />
                </label>
                <button className="button button-secondary" type="submit" disabled={working}>
                  Update status
                </button>
              </>
            ) : (
              <p className="form-hint">Resolved incidents are terminal in this release.</p>
            )}
          </form>

          <form className="content-card form-stack" onSubmit={submitAssignment}>
            <div>
              <span className="eyebrow">Ownership</span>
              <h2>Assignment</h2>
            </div>
            <label>
              Responder
              <select
                value={assigneeId}
                onChange={(event) => setAssigneeId(event.target.value)}
              >
                <option value="">Unassigned</option>
                {eligibleMembers.map((member) => (
                  <option key={member.userId} value={member.userId}>
                    {member.displayName} · {humanize(member.role)}
                  </option>
                ))}
              </select>
            </label>
            <button className="button button-secondary" type="submit" disabled={working}>
              Save assignment
            </button>
          </form>
        </section>
      ) : null}

      <div className="detail-grid">
        <section className="content-card" aria-labelledby="timeline-heading">
          <div className="section-heading">
            <div>
              <span className="eyebrow">Append-only history</span>
              <h2 id="timeline-heading">Timeline</h2>
            </div>
            <span>{events.length} event(s)</span>
          </div>
          <ol className="timeline">
            {events.map((event) => (
              <li key={event.id}>
                <span className="timeline-dot" aria-hidden="true" />
                <div>
                  <strong>{humanize(event.eventType)}</strong>
                  <p>{formatEventDetail(event, memberNames)}</p>
                  <small>
                    {formatDateTime(event.occurredAt)} ·{" "}
                    {event.actorId
                      ? memberNames.get(event.actorId) ?? "Workspace member"
                      : "System"}
                  </small>
                </div>
              </li>
            ))}
          </ol>
        </section>

        <section className="content-card" aria-labelledby="comments-heading">
          <div className="section-heading">
            <div>
              <span className="eyebrow">Team discussion</span>
              <h2 id="comments-heading">Comments</h2>
            </div>
            <span>{comments.length}</span>
          </div>
          {comments.length ? (
            <ul className="comment-list">
              {comments.map((comment) => (
                <li key={comment.id}>
                  <div>
                    <strong>
                      {memberNames.get(comment.authorId) ?? "Workspace member"}
                    </strong>
                    <small>{formatDateTime(comment.createdAt)}</small>
                  </div>
                  <p>{comment.body}</p>
                </li>
              ))}
            </ul>
          ) : (
            <p className="form-hint">No comments have been added.</p>
          )}
          {canRespond ? (
            <form className="comment-form" onSubmit={submitComment}>
              <label>
                Add a comment
                <textarea
                  rows={4}
                  maxLength={4000}
                  value={commentBody}
                  onChange={(event) => setCommentBody(event.target.value)}
                />
              </label>
              <button className="button button-primary" type="submit" disabled={working}>
                Add comment
              </button>
            </form>
          ) : null}
        </section>
      </div>
    </div>
  );
}
