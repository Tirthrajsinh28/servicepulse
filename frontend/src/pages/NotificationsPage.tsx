import { useEffect, useMemo, useState } from "react";
import { Link } from "../router";

import { useAuth } from "../auth/auth-context";
import { EmptyState, ErrorNotice, LoadingPanel } from "../components/Feedback";
import { useApi } from "../lib/api-context";
import { formatDateTime, humanize } from "../lib/format";
import type { FailedNotificationJob, FailedNotificationJobPage } from "../lib/types";

export function NotificationsPage() {
  const api = useApi();
  const auth = useAuth();
  const [jobs, setJobs] = useState<FailedNotificationJobPage | null>(null);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [replayingJobId, setReplayingJobId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [reload, setReload] = useState(0);

  const workspace = auth.workspaces.find(
    (candidate) => candidate.id === auth.workspaceId,
  );
  const canAdminister = workspace?.role === "ADMIN";
  const canLoadOperations = Boolean(auth.workspaceId && canAdminister);
  const totalAttempts = useMemo(
    () => jobs?.items.reduce((total, job) => total + job.attemptCount, 0) ?? 0,
    [jobs],
  );

  useEffect(() => {
    if (!auth.workspaceId || !canLoadOperations) {
      return;
    }
    let active = true;
    api
      .getFailedNotificationJobs(auth.workspaceId, page)
      .then((nextJobs) => {
        if (!active) return;
        setJobs(nextJobs);
        setError(null);
      })
      .catch((caught: unknown) => {
        if (active) {
          setError(caught instanceof Error ? caught.message : "Unable to load failed jobs.");
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [api, auth.workspaceId, canLoadOperations, page, reload]);

  if (!auth.workspaceId) {
    return (
      <EmptyState
        title="No workspace membership"
        message="Notification operations need an authorized workspace."
      />
    );
  }

  if (!canAdminister) {
    return (
      <EmptyState
        title="Administrator access required"
        message="Failed notification inspection and replay are restricted to workspace administrators."
      />
    );
  }

  async function replay(job: FailedNotificationJob) {
    if (!auth.workspaceId) return;
    setReplayingJobId(job.id);
    setError(null);
    setSuccess(null);
    try {
      const replayed = await api.replayFailedNotificationJob(auth.workspaceId, job.id);
      setSuccess(
        `${humanize(replayed.eventType)} was reset to ${humanize(replayed.status)} after ${replayed.previousAttemptCount} failed attempt(s).`,
      );
      setReload((value) => value + 1);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "The failed job could not be replayed.");
    } finally {
      setReplayingJobId(null);
    }
  }

  function goToPage(nextPage: number) {
    setLoading(true);
    setError(null);
    setSuccess(null);
    setPage(nextPage);
  }

  return (
    <div className="page-stack">
      <header className="page-header">
        <div>
          <span className="eyebrow">Delivery operations</span>
          <h1 tabIndex={-1}>Failed notifications</h1>
          <p>
            Inspect retained failed notification jobs for <strong>{workspace?.name}</strong>{" "}
            and replay them through the transactional outbox. The current demo
            adapter logs locally; no external delivery provider is claimed.
          </p>
        </div>
      </header>

      {error ? (
        <ErrorNotice
          message={error}
          onRetry={() => {
            setLoading(true);
            setError(null);
            setReload((value) => value + 1);
          }}
        />
      ) : null}
      {success ? (
        <div className="notice notice-success" role="status">
          <strong>{success}</strong>
        </div>
      ) : null}

      {canLoadOperations && loading && !jobs ? (
        <LoadingPanel label="Loading failed notification jobs" />
      ) : null}

      {jobs ? (
        <section className="summary-grid" aria-label="Failed notification summary">
          <article>
            <span>Failed jobs</span>
            <strong>{jobs.totalElements}</strong>
            <small>Retained for operator review</small>
          </article>
          <article>
            <span>Current page</span>
            <strong>{jobs.items.length}</strong>
            <small>Visible records</small>
          </article>
          <article>
            <span>Attempts shown</span>
            <strong>{totalAttempts}</strong>
            <small>Before replay</small>
          </article>
          <article className="severity-card">
            <span>Delivery adapter</span>
            <strong>Local</strong>
            <small>Structured log only</small>
          </article>
        </section>
      ) : null}

      <section className="content-card" aria-labelledby="failed-jobs-heading">
        <div className="section-heading">
          <div>
            <span className="eyebrow">Outbox operations</span>
            <h2 id="failed-jobs-heading">Failed jobs</h2>
          </div>
          {jobs ? <span>{jobs.totalElements} failed job(s)</span> : null}
        </div>

        {jobs && jobs.items.length === 0 ? (
          <EmptyState
            title="No failed notification jobs"
            message="The worker has no retained failures for this workspace."
          />
        ) : null}

        {jobs && jobs.items.length > 0 ? (
          <>
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th scope="col">Job</th>
                    <th scope="col">Incident</th>
                    <th scope="col">Attempts</th>
                    <th scope="col">Last error</th>
                    <th scope="col">Failed</th>
                    <th scope="col">Action</th>
                  </tr>
                </thead>
                <tbody>
                  {jobs.items.map((job) => (
                    <tr key={job.id}>
                      <th scope="row">
                        {humanize(job.eventType)}
                        <small>
                          <code>{job.id}</code>
                        </small>
                        <small>Created {formatDateTime(job.createdAt)}</small>
                      </th>
                      <td>
                        <Link to={`/incidents/${job.incidentId}`}>Open incident</Link>
                      </td>
                      <td>{job.attemptCount}</td>
                      <td>{job.lastError}</td>
                      <td>{formatDateTime(job.failedAt)}</td>
                      <td>
                        <button
                          className="button button-secondary"
                          type="button"
                          disabled={replayingJobId === job.id}
                          onClick={() => void replay(job)}
                        >
                          {replayingJobId === job.id ? "Replaying..." : "Replay"}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="pagination" aria-label="Failed notification pagination">
              <button
                className="button button-ghost"
                type="button"
                disabled={jobs.page === 0 || loading}
                onClick={() => goToPage(jobs.page - 1)}
              >
                Previous
              </button>
              <span>
                Page {jobs.page + 1} of {Math.max(jobs.totalPages, 1)}
              </span>
              <button
                className="button button-ghost"
                type="button"
                disabled={jobs.page + 1 >= jobs.totalPages || loading}
                onClick={() => goToPage(jobs.page + 1)}
              >
                Next
              </button>
            </div>
          </>
        ) : null}
      </section>
    </div>
  );
}
