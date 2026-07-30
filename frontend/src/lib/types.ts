export type WorkspaceRole = "ADMIN" | "RESPONDER" | "VIEWER";
export type IncidentSeverity = "SEV1" | "SEV2" | "SEV3" | "SEV4";
export type IncidentStatus =
  | "OPEN"
  | "INVESTIGATING"
  | "IDENTIFIED"
  | "MONITORING"
  | "RESOLVED";
export type ServiceLifecycleStatus = "ACTIVE" | "MAINTENANCE" | "RETIRED";

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: "Bearer";
  expiresIn: number;
}

export interface CurrentUser {
  id: string;
}

export interface Workspace {
  id: string;
  name: string;
  slug: string;
  role: WorkspaceRole;
  memberSince: string;
}

export interface ManagedService {
  id: string;
  workspaceId: string;
  name: string;
  slug: string;
  description: string | null;
  lifecycleStatus: ServiceLifecycleStatus;
  createdAt: string;
  updatedAt: string;
}

export interface ManagedServicePage {
  items: ManagedService[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CreateManagedServiceInput {
  name: string;
  slug: string;
  description?: string | null;
}

export interface UpdateManagedServiceInput {
  name: string;
  description?: string | null;
  lifecycleStatus: ServiceLifecycleStatus;
}

export interface Incident {
  id: string;
  workspaceId: string;
  serviceId: string;
  title: string;
  summary: string;
  severity: IncidentSeverity;
  status: IncidentStatus;
  assigneeId: string | null;
  declaredAt: string;
  resolvedAt: string | null;
  version: number;
}

export interface IncidentPage {
  items: Incident[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface IncidentEvent {
  id: string;
  actorId: string | null;
  eventType: string;
  fromStatus: IncidentStatus | null;
  toStatus: IncidentStatus | null;
  detail: string | null;
  occurredAt: string;
}

export interface IncidentComment {
  id: string;
  incidentId: string;
  authorId: string;
  body: string;
  createdAt: string;
  updatedAt: string;
}

export interface WorkspaceMember {
  userId: string;
  email: string;
  displayName: string;
  enabled: boolean;
  role: WorkspaceRole;
  createdAt: string;
}

export interface AddWorkspaceMemberInput {
  userId: string;
  role: WorkspaceRole;
}

export interface ChangeWorkspaceMemberRoleInput {
  role: WorkspaceRole;
}

export interface FailedNotificationJob {
  id: string;
  incidentId: string;
  eventType: string;
  attemptCount: number;
  lastError: string;
  createdAt: string;
  failedAt: string;
}

export interface FailedNotificationJobPage {
  items: FailedNotificationJob[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ReplayedNotificationJob {
  id: string;
  incidentId: string;
  eventType: string;
  status: "PENDING";
  attemptCount: number;
  nextAttemptAt: string;
  previousAttemptCount: number;
}

export interface DashboardSummary {
  totalIncidents: number;
  activeIncidents: number;
  unassignedActiveIncidents: number;
  byStatus: Record<IncidentStatus, number>;
  bySeverity: Record<IncidentSeverity, number>;
}

export interface IncidentSearch {
  workspaceId: string;
  query?: string;
  serviceId?: string;
  status?: IncidentStatus;
  severity?: IncidentSeverity;
  page?: number;
  size?: number;
}

export interface DeclareIncidentInput {
  workspaceId: string;
  serviceId: string;
  title: string;
  summary: string;
  severity: IncidentSeverity;
}

export interface AuthBootstrap {
  user: CurrentUser;
  workspaces: Workspace[];
}
