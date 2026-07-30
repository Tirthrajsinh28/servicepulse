import { humanize } from "../lib/format";
import type { IncidentSeverity, IncidentStatus } from "../lib/types";

export function StatusBadge({ status }: { status: IncidentStatus }) {
  return <span className={`badge status-${status.toLowerCase()}`}>{humanize(status)}</span>;
}

export function SeverityBadge({ severity }: { severity: IncidentSeverity }) {
  return <span className={`badge severity-${severity.toLowerCase()}`}>{severity}</span>;
}
