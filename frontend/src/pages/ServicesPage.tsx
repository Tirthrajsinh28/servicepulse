import { useEffect, useMemo, useState } from "react";

import { useAuth } from "../auth/auth-context";
import { EmptyState, ErrorNotice, LoadingPanel } from "../components/Feedback";
import { ApiError } from "../lib/api";
import { useApi } from "../lib/api-context";
import { formatDateTime, humanize } from "../lib/format";
import type { ManagedService, ServiceLifecycleStatus } from "../lib/types";

const LIFECYCLE_OPTIONS: ServiceLifecycleStatus[] = [
  "ACTIVE",
  "MAINTENANCE",
  "RETIRED",
];

function slugify(value: string): string {
  return value
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .replace(/-{2,}/g, "-");
}

export function ServicesPage() {
  const api = useApi();
  const auth = useAuth();
  const [services, setServices] = useState<ManagedService[]>([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [savingServiceId, setSavingServiceId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [name, setName] = useState("");
  const [slug, setSlug] = useState("");
  const [slugTouched, setSlugTouched] = useState(false);
  const [description, setDescription] = useState("");
  const [lifecycleDrafts, setLifecycleDrafts] = useState<
    Record<string, ServiceLifecycleStatus>
  >({});

  const workspace = auth.workspaces.find(
    (candidate) => candidate.id === auth.workspaceId,
  );
  const canAdminister = workspace?.role === "ADMIN";
  const activeCount = useMemo(
    () => services.filter((service) => service.lifecycleStatus === "ACTIVE").length,
    [services],
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
        setServices(items);
        setLifecycleDrafts(
          Object.fromEntries(
            items.map((service) => [service.id, service.lifecycleStatus]),
          ),
        );
        setError(null);
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

  if (!auth.workspaceId) {
    return (
      <EmptyState
        title="No workspace membership"
        message="A service catalog needs an authorized workspace."
      />
    );
  }

  async function createService(event: React.FormEvent) {
    event.preventDefault();
    if (!auth.workspaceId || !canAdminister) return;
    setError(null);
    setSuccess(null);
    setFieldErrors({});
    const nextErrors: Record<string, string> = {};
    const trimmedName = name.trim();
    const trimmedSlug = slug.trim();
    if (!trimmedName) nextErrors.name = "Enter a service name.";
    if (!trimmedSlug) nextErrors.slug = "Enter a URL-safe slug.";
    if (Object.keys(nextErrors).length) {
      setFieldErrors(nextErrors);
      return;
    }
    setSubmitting(true);
    try {
      const created = await api.createService(auth.workspaceId, {
        name: trimmedName,
        slug: trimmedSlug,
        description: description.trim() || null,
      });
      setServices((current) => [...current, created].sort(compareServices));
      setLifecycleDrafts((current) => ({
        ...current,
        [created.id]: created.lifecycleStatus,
      }));
      setName("");
      setSlug("");
      setSlugTouched(false);
      setDescription("");
      setSuccess(`${created.name} was added to the service catalog.`);
    } catch (caught) {
      if (caught instanceof ApiError) {
        setError(caught.message);
        setFieldErrors(caught.fieldErrors);
      } else {
        setError("The service could not be created.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  async function saveLifecycle(service: ManagedService) {
    if (!auth.workspaceId || !canAdminister) return;
    const lifecycleStatus = lifecycleDrafts[service.id] ?? service.lifecycleStatus;
    setSavingServiceId(service.id);
    setError(null);
    setSuccess(null);
    try {
      const updated = await api.updateService(auth.workspaceId, service.id, {
        name: service.name,
        description: service.description,
        lifecycleStatus,
      });
      setServices((current) =>
        current.map((item) => (item.id === updated.id ? updated : item)),
      );
      setLifecycleDrafts((current) => ({
        ...current,
        [updated.id]: updated.lifecycleStatus,
      }));
      setSuccess(`${updated.name} lifecycle changed to ${humanize(updated.lifecycleStatus)}.`);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "The service could not be updated.");
    } finally {
      setSavingServiceId(null);
    }
  }

  return (
    <div className="page-stack">
      <header className="page-header">
        <div>
          <span className="eyebrow">Service catalog</span>
          <h1 tabIndex={-1}>Managed services</h1>
          <p>
            Maintain the fictional systems that incidents attach to in{" "}
            <strong>{workspace?.name}</strong>.
          </p>
        </div>
      </header>

      {error ? <ErrorNotice message={error} /> : null}
      {success ? (
        <div className="notice notice-success" role="status">
          <strong>{success}</strong>
        </div>
      ) : null}

      {loading ? <LoadingPanel label="Loading services" /> : null}

      {!loading ? (
        <section className="summary-grid" aria-label="Service summary">
          <article>
            <span>Total services</span>
            <strong>{services.length}</strong>
            <small>Catalog records</small>
          </article>
          <article>
            <span>Active</span>
            <strong>{activeCount}</strong>
            <small>Available for incident declaration</small>
          </article>
          <article>
            <span>Read model</span>
            <strong>100</strong>
            <small>Maximum services per request</small>
          </article>
          <article className="severity-card">
            <span>Delete policy</span>
            <strong>Retain</strong>
            <small>Service history stays attached to incidents</small>
          </article>
        </section>
      ) : null}

      {canAdminister ? (
        <section className="content-card" aria-labelledby="new-service-heading">
          <div className="section-heading">
            <div>
              <span className="eyebrow">Administrator action</span>
              <h2 id="new-service-heading">Add a service</h2>
            </div>
          </div>
          <form className="form-stack" onSubmit={createService} noValidate>
            <div className="form-grid">
              <label>
                Service name
                <input
                  maxLength={120}
                  value={name}
                  onChange={(event) => {
                    const nextName = event.target.value;
                    setName(nextName);
                    if (!slugTouched) setSlug(slugify(nextName));
                  }}
                  aria-describedby={fieldErrors.name ? "service-name-error" : undefined}
                />
                {fieldErrors.name ? (
                  <small className="field-error" id="service-name-error">
                    {fieldErrors.name}
                  </small>
                ) : null}
              </label>
              <label>
                Service slug
                <input
                  maxLength={80}
                  value={slug}
                  onChange={(event) => {
                    setSlugTouched(true);
                    setSlug(slugify(event.target.value));
                  }}
                  aria-describedby={fieldErrors.slug ? "service-slug-error" : "service-slug-hint"}
                />
                <small id="service-slug-hint">Lowercase letters, numbers, and hyphens.</small>
                {fieldErrors.slug ? (
                  <small className="field-error" id="service-slug-error">
                    {fieldErrors.slug}
                  </small>
                ) : null}
              </label>
            </div>
            <label>
              Description
              <textarea
                rows={4}
                maxLength={1000}
                value={description}
                onChange={(event) => setDescription(event.target.value)}
              />
            </label>
            <div className="form-actions">
              <button className="button button-primary" type="submit" disabled={submitting}>
                {submitting ? "Adding service..." : "Add service"}
              </button>
              <span className="form-hint">
                Slugs are immutable after creation; lifecycle can change later.
              </span>
            </div>
          </form>
        </section>
      ) : null}

      <section className="content-card" aria-labelledby="services-heading">
        <div className="section-heading">
          <div>
            <span className="eyebrow">Workspace catalog</span>
            <h2 id="services-heading">Services</h2>
          </div>
          <span>{services.length} service(s)</span>
        </div>

        {!loading && services.length === 0 ? (
          <EmptyState
            title="No services yet"
            message="An administrator can add a service before incidents are declared."
          />
        ) : null}

        {services.length > 0 ? (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th scope="col">Service</th>
                  <th scope="col">Slug</th>
                  <th scope="col">Lifecycle</th>
                  <th scope="col">Updated</th>
                  {canAdminister ? <th scope="col">Action</th> : null}
                </tr>
              </thead>
              <tbody>
                {services.map((service) => {
                  const draft = lifecycleDrafts[service.id] ?? service.lifecycleStatus;
                  return (
                    <tr key={service.id}>
                      <th scope="row">
                        {service.name}
                        {service.description ? <small>{service.description}</small> : null}
                      </th>
                      <td>
                        <code>{service.slug}</code>
                      </td>
                      <td>
                        {canAdminister ? (
                          <label className="table-control">
                            <span className="sr-only">Lifecycle for {service.name}</span>
                            <select
                              value={draft}
                              onChange={(event) =>
                                setLifecycleDrafts((current) => ({
                                  ...current,
                                  [service.id]: event.target.value as ServiceLifecycleStatus,
                                }))
                              }
                            >
                              {LIFECYCLE_OPTIONS.map((status) => (
                                <option key={status} value={status}>
                                  {humanize(status)}
                                </option>
                              ))}
                            </select>
                          </label>
                        ) : (
                          <span className={`badge lifecycle-${service.lifecycleStatus.toLowerCase()}`}>
                            {humanize(service.lifecycleStatus)}
                          </span>
                        )}
                      </td>
                      <td>{formatDateTime(service.updatedAt)}</td>
                      {canAdminister ? (
                        <td>
                          <button
                            className="button button-secondary"
                            type="button"
                            disabled={savingServiceId === service.id || draft === service.lifecycleStatus}
                            onClick={() => void saveLifecycle(service)}
                          >
                            {savingServiceId === service.id ? "Saving..." : "Save"}
                          </button>
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

function compareServices(left: ManagedService, right: ManagedService) {
  const nameCompare = left.name.localeCompare(right.name, "en", { sensitivity: "base" });
  return nameCompare || left.id.localeCompare(right.id);
}
