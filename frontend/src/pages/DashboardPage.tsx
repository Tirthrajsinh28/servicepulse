import { useEffect, useMemo, useState } from "react";
import { Link } from "../router";

import { useAuth } from "../auth/auth-context";
import { SeverityBadge, StatusBadge } from "../components/Badges";
import { EmptyState, ErrorNotice, LoadingPanel } from "../components/Feedback";
import { useApi } from "../lib/api-context";
import { formatDateTime } from "../lib/format";
import type {
  DashboardSummary,
  IncidentPage,
  IncidentSeverity,
  IncidentStatus,
  ManagedService,
} from "../lib/types";

interface Filters {
  query: string;
  serviceId: string;
  status: string;
  severity: string;
}

const EMPTY_FILTERS: Filters = {
  query: "",
  serviceId: "",
  status: "",
  severity: "",
};

export function DashboardPage() {
  const api = useApi();
  const auth = useAuth();
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [services, setServices] = useState<ManagedService[]>([]);
  const [incidents, setIncidents] = useState<IncidentPage | null>(null);
  const [draftFilters, setDraftFilters] = useState<Filters>(EMPTY_FILTERS);
  const [filters, setFilters] = useState<Filters>(EMPTY_FILTERS);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadedWorkspaceId, setLoadedWorkspaceId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reload, setReload] = useState(0);

  useEffect(() => {
    if (!auth.workspaceId) {
      return;
    }
    let active = true;
    Promise.all([
      api.getDashboard(auth.workspaceId),
      api.getServices(auth.workspaceId),
      api.getIncidents({
        workspaceId: auth.workspaceId,
        query: filters.query || undefined,
        serviceId: filters.serviceId || undefined,
        status: (filters.status || undefined) as IncidentStatus | undefined,
        severity: (filters.severity || undefined) as IncidentSeverity | undefined,
        page,
        size: 10,
      }),
    ])
      .then(([nextSummary, nextServices, nextIncidents]) => {
        if (!active) return;
        setSummary(nextSummary);
        setServices(nextServices);
        setIncidents(nextIncidents);
        setLoadedWorkspaceId(auth.workspaceId);
        setError(null);
      })
      .catch((caught: unknown) => {
        if (!active) return;
        setError(caught instanceof Error ? caught.message : "Unable to load operations data.");
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [api, auth.workspaceId, filters, page, reload]);

  const serviceNames = useMemo(
    () => new Map(services.map((service) => [service.id, service.name])),
    [services],
  );
  const workspace = auth.workspaces.find(
    (candidate) => candidate.id === auth.workspaceId,
  );
  const canRespond = workspace?.role !== "VIEWER";
  const showingStaleWorkspace = loadedWorkspaceId !== auth.workspaceId;

  function applyFilters(event: React.FormEvent) {
    event.preventDefault();
    setLoading(true);
    setError(null);
    setPage(0);
    setFilters(draftFilters);
  }

  function clearFilters() {
    setLoading(true);
    setError(null);
    setDraftFilters(EMPTY_FILTERS);
    setFilters(EMPTY_FILTERS);
    setPage(0);
  }

  if (!auth.workspaceId) {
    return (
      <EmptyState
        title="No workspace membership"
        message="This account does not currently belong to a ServicePulse workspace."
      />
    );
  }

  return (
    <div className="page-stack">
      <header className="page-header">
        <div>
          <span className="eyebrow">Live workspace view</span>
          <h1 tabIndex={-1}>Incident overview</h1>
          <p>
            Current operational state for <strong>{workspace?.name}</strong>.
          </p>
        </div>
        {canRespond ? (
          <Link className="button button-primary" to="/incidents/new">
            Declare incident
          </Link>
        ) : null}
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
      {(loading || showingStaleWorkspace) && (!summary || showingStaleWorkspace) ? (
        <LoadingPanel label="Loading incident overview" />
      ) : null}

      {summary && !showingStaleWorkspace ? (
        <section className="summary-grid" aria-label="Incident summary">
          <article>
            <span>Total incidents</span>
            <strong>{summary.totalIncidents}</strong>
            <small>All recorded incidents</small>
          </article>
          <article>
            <span>Active</span>
            <strong>{summary.activeIncidents}</strong>
            <small>Not yet resolved</small>
          </article>
          <article>
            <span>Unassigned active</span>
            <strong>{summary.unassignedActiveIncidents}</strong>
            <small>Needs an owner</small>
          </article>
          <article className="severity-card">
            <span>Highest urgency</span>
            <strong>
              {summary.bySeverity.SEV1 > 0
                ? `${summary.bySeverity.SEV1} SEV1`
                : `${summary.bySeverity.SEV2} SEV2`}
            </strong>
            <small>Across recorded incidents</small>
          </article>
        </section>
      ) : null}

      <section className="content-card" aria-labelledby="incidents-heading">
        <div className="section-heading">
          <div>
            <span className="eyebrow">Incident register</span>
            <h2 id="incidents-heading">Incidents</h2>
          </div>
          {incidents && !showingStaleWorkspace ? (
            <span>{incidents.totalElements} result(s)</span>
          ) : null}
        </div>
        <form className="filter-grid" onSubmit={applyFilters}>
          <label className="filter-search">
            Search title or summary
            <input
              type="search"
              maxLength={160}
              value={draftFilters.query}
              onChange={(event) =>
                setDraftFilters((current) => ({ ...current, query: event.target.value }))
              }
            />
          </label>
          <label>
            Service
            <select
              value={draftFilters.serviceId}
              onChange={(event) =>
                setDraftFilters((current) => ({
                  ...current,
                  serviceId: event.target.value,
                }))
              }
            >
              <option value="">All services</option>
              {services.map((service) => (
                <option key={service.id} value={service.id}>
                  {service.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            Status
            <select
              value={draftFilters.status}
              onChange={(event) =>
                setDraftFilters((current) => ({ ...current, status: event.target.value }))
              }
            >
              <option value="">All statuses</option>
              {["OPEN", "INVESTIGATING", "IDENTIFIED", "MONITORING", "RESOLVED"].map(
                (status) => (
                  <option key={status} value={status}>
                    {status.replace("_", " ")}
                  </option>
                ),
              )}
            </select>
          </label>
          <label>
            Severity
            <select
              value={draftFilters.severity}
              onChange={(event) =>
                setDraftFilters((current) => ({
                  ...current,
                  severity: event.target.value,
                }))
              }
            >
              <option value="">All severities</option>
              {["SEV1", "SEV2", "SEV3", "SEV4"].map((severity) => (
                <option key={severity} value={severity}>
                  {severity}
                </option>
              ))}
            </select>
          </label>
          <div className="filter-actions">
            <button className="button button-secondary" type="submit">
              Apply filters
            </button>
            <button className="button button-ghost" type="button" onClick={clearFilters}>
              Clear
            </button>
          </div>
        </form>

        {loading && incidents && !showingStaleWorkspace ? (
          <p className="inline-status">Refreshing incidents…</p>
        ) : null}
        {!showingStaleWorkspace && incidents?.items.length ? (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th scope="col">Incident</th>
                  <th scope="col">Service</th>
                  <th scope="col">Severity</th>
                  <th scope="col">Status</th>
                  <th scope="col">Declared</th>
                </tr>
              </thead>
              <tbody>
                {incidents.items.map((incident) => (
                  <tr key={incident.id}>
                    <th scope="row">
                      <Link to={`/incidents/${incident.id}`}>{incident.title}</Link>
                      <small>{incident.summary}</small>
                    </th>
                    <td>{serviceNames.get(incident.serviceId) ?? "Unknown service"}</td>
                    <td>
                      <SeverityBadge severity={incident.severity} />
                    </td>
                    <td>
                      <StatusBadge status={incident.status} />
                    </td>
                    <td>{formatDateTime(incident.declaredAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : !showingStaleWorkspace && incidents && !loading ? (
          <EmptyState
            title="No incidents match"
            message="Change the filters or declare the workspace's first incident."
            action={
              canRespond ? (
                <Link className="button button-secondary" to="/incidents/new">
                  Declare incident
                </Link>
              ) : undefined
            }
          />
        ) : null}

        {!showingStaleWorkspace && incidents && incidents.totalPages > 1 ? (
          <nav className="pagination" aria-label="Incident pages">
            <button
              className="button button-ghost"
              type="button"
              disabled={incidents.page === 0}
              onClick={() => {
                setLoading(true);
                setPage((value) => Math.max(0, value - 1));
              }}
            >
              Previous
            </button>
            <span>
              Page {incidents.page + 1} of {incidents.totalPages}
            </span>
            <button
              className="button button-ghost"
              type="button"
              disabled={incidents.page + 1 >= incidents.totalPages}
              onClick={() => {
                setLoading(true);
                setPage((value) => value + 1);
              }}
            >
              Next
            </button>
          </nav>
        ) : null}
      </section>
    </div>
  );
}
