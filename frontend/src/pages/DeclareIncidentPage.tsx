import { useEffect, useState } from "react";
import { Link, Navigate, useNavigate } from "../router";

import { useAuth } from "../auth/auth-context";
import { EmptyState, ErrorNotice, LoadingPanel } from "../components/Feedback";
import { ApiError } from "../lib/api";
import { useApi } from "../lib/api-context";
import type { IncidentSeverity, ManagedService } from "../lib/types";

export function DeclareIncidentPage() {
  const api = useApi();
  const auth = useAuth();
  const navigate = useNavigate();
  const [services, setServices] = useState<ManagedService[]>([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [serviceId, setServiceId] = useState("");
  const [title, setTitle] = useState("");
  const [summary, setSummary] = useState("");
  const [severity, setSeverity] = useState<IncidentSeverity>("SEV2");

  const workspace = auth.workspaces.find(
    (candidate) => candidate.id === auth.workspaceId,
  );

  useEffect(() => {
    if (!auth.workspaceId) {
      return;
    }
    let active = true;
    api
      .getServices(auth.workspaceId)
      .then((items) => {
        if (!active) return;
        const activeServices = items.filter(
          (service) => service.lifecycleStatus !== "RETIRED",
        );
        setServices(activeServices);
        setServiceId((current) => current || activeServices[0]?.id || "");
      })
      .catch((caught: unknown) => {
        if (active) {
          setError(caught instanceof Error ? caught.message : "Unable to load services.");
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [api, auth.workspaceId]);

  if (workspace?.role === "VIEWER") {
    return <Navigate to="/" replace />;
  }

  if (!auth.workspaceId) {
    return (
      <EmptyState
        title="No workspace membership"
        message="An incident needs an authorized workspace."
      />
    );
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (!auth.workspaceId) return;
    setError(null);
    setFieldErrors({});
    const clientErrors: Record<string, string> = {};
    if (!serviceId) clientErrors.serviceId = "Choose a service.";
    if (!title.trim()) clientErrors.title = "Enter a title.";
    if (!summary.trim()) clientErrors.summary = "Enter a summary.";
    if (Object.keys(clientErrors).length) {
      setFieldErrors(clientErrors);
      return;
    }
    setSubmitting(true);
    try {
      const incident = await api.declareIncident({
        workspaceId: auth.workspaceId,
        serviceId,
        title: title.trim(),
        summary: summary.trim(),
        severity,
      });
      navigate(`/incidents/${incident.id}`, {
        replace: true,
        state: { flash: "Incident declared and recorded in the timeline." },
      });
    } catch (caught) {
      if (caught instanceof ApiError) {
        setError(caught.message);
        setFieldErrors(caught.fieldErrors);
      } else {
        setError("The incident could not be declared.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="page-stack page-narrow">
      <header className="page-header">
        <div>
          <span className="eyebrow">New operational record</span>
          <h1 tabIndex={-1}>Declare an incident</h1>
          <p>Capture the initial signal. Status changes and discussion follow on the timeline.</p>
        </div>
        <Link className="button button-ghost" to="/">
          Cancel
        </Link>
      </header>

      {loading ? <LoadingPanel label="Loading services" /> : null}
      {error ? <ErrorNotice message={error} /> : null}
      {!loading && services.length === 0 ? (
        <EmptyState
          title="No active services"
          message="An administrator must create an active service before an incident can be declared."
          action={
            <Link className="button button-secondary" to="/">
              Return to overview
            </Link>
          }
        />
      ) : null}

      {!loading && services.length > 0 ? (
        <form className="content-card form-stack" onSubmit={submit} noValidate>
          <label>
            Service
            <select
              value={serviceId}
              onChange={(event) => setServiceId(event.target.value)}
              aria-describedby={fieldErrors.serviceId ? "service-error" : undefined}
            >
              {services.map((service) => (
                <option key={service.id} value={service.id}>
                  {service.name}
                </option>
              ))}
            </select>
            {fieldErrors.serviceId ? (
              <small className="field-error" id="service-error">
                {fieldErrors.serviceId}
              </small>
            ) : null}
          </label>
          <div className="form-grid">
            <label>
              Severity
              <select
                value={severity}
                onChange={(event) => setSeverity(event.target.value as IncidentSeverity)}
              >
                <option value="SEV1">SEV1 — Critical impact</option>
                <option value="SEV2">SEV2 — Major impact</option>
                <option value="SEV3">SEV3 — Limited impact</option>
                <option value="SEV4">SEV4 — Minor impact</option>
              </select>
            </label>
            <label>
              Title
              <input
                maxLength={160}
                value={title}
                onChange={(event) => setTitle(event.target.value)}
                aria-describedby={fieldErrors.title ? "title-error" : "title-hint"}
              />
              <small id="title-hint">{title.length}/160 characters</small>
              {fieldErrors.title ? (
                <small className="field-error" id="title-error">
                  {fieldErrors.title}
                </small>
              ) : null}
            </label>
          </div>
          <label>
            Initial summary
            <textarea
              rows={7}
              maxLength={4000}
              value={summary}
              onChange={(event) => setSummary(event.target.value)}
              aria-describedby={fieldErrors.summary ? "summary-error" : "summary-hint"}
            />
            <small id="summary-hint">{summary.length}/4,000 characters</small>
            {fieldErrors.summary ? (
              <small className="field-error" id="summary-error">
                {fieldErrors.summary}
              </small>
            ) : null}
          </label>
          <div className="form-actions">
            <button className="button button-primary" type="submit" disabled={submitting}>
              {submitting ? "Declaring…" : "Declare incident"}
            </button>
            <span className="form-hint">
              This creates an audit entry, timeline event, and notification job.
            </span>
          </div>
        </form>
      ) : null}
    </div>
  );
}
