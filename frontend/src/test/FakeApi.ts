import type { ServicePulseApi } from "../lib/api";
import type {
  AddWorkspaceMemberInput,
  AuthBootstrap,
  ChangeWorkspaceMemberRoleInput,
  CreateManagedServiceInput,
  DashboardSummary,
  DeclareIncidentInput,
  FailedNotificationJob,
  FailedNotificationJobPage,
  Incident,
  IncidentComment,
  IncidentEvent,
  IncidentPage,
  IncidentSearch,
  IncidentStatus,
  ManagedService,
  ReplayedNotificationJob,
  UpdateManagedServiceInput,
  Workspace,
  WorkspaceMember,
} from "../lib/types";

const NOW = "2026-07-01T16:00:00Z";

export class FakeApi implements ServicePulseApi {
  session: AuthBootstrap | null;
  searches: IncidentSearch[] = [];
  loginCalls: Array<{ email: string; password: string }> = [];
  incidents: Incident[];
  comments: IncidentComment[] = [];
  events: IncidentEvent[];
  services: ManagedService[];
  members: WorkspaceMember[];
  failedNotificationJobs: FailedNotificationJob[];

  constructor(role: Workspace["role"] = "ADMIN", authenticated = true) {
    const workspace: Workspace = {
      id: "workspace-1",
      name: "Northstar Labs",
      slug: "northstar-labs",
      role,
      memberSince: NOW,
    };
    this.session = authenticated
      ? { user: { id: "user-1" }, workspaces: [workspace] }
      : null;
    this.services = [
      {
        id: "service-1",
        workspaceId: workspace.id,
        name: "Checkout API",
        slug: "checkout-api",
        description: "Synthetic checkout service",
        lifecycleStatus: "ACTIVE",
        createdAt: NOW,
        updatedAt: NOW,
      },
    ];
    this.incidents = [
      {
        id: "incident-1",
        workspaceId: workspace.id,
        serviceId: "service-1",
        title: "Elevated checkout latency",
        summary: "Synthetic latency exceeded the demonstration threshold.",
        severity: "SEV2",
        status: "OPEN",
        assigneeId: null,
        declaredAt: NOW,
        resolvedAt: null,
        version: 0,
      },
    ];
    this.events = [
      {
        id: "event-1",
        actorId: "user-1",
        eventType: "DECLARED",
        fromStatus: null,
        toStatus: "OPEN",
        detail: "Incident declared",
        occurredAt: NOW,
      },
    ];
    this.members = [
      {
        userId: "user-1",
        email: "admin@example.test",
        displayName: "Synthetic Administrator",
        enabled: true,
        role,
        createdAt: NOW,
      },
      {
        userId: "user-2",
        email: "responder@example.test",
        displayName: "Synthetic Responder",
        enabled: true,
        role: "RESPONDER",
        createdAt: NOW,
      },
    ];
    this.failedNotificationJobs = [
      {
        id: "job-1",
        incidentId: "incident-1",
        eventType: "STATUS_CHANGED",
        attemptCount: 5,
        lastError: "IllegalStateException",
        createdAt: NOW,
        failedAt: "2026-07-01T16:05:00Z",
      },
    ];
  }

  restoreSession(): Promise<AuthBootstrap | null> {
    return Promise.resolve(this.session);
  }

  login(email: string, password: string): Promise<AuthBootstrap> {
    this.loginCalls.push({ email, password });
    this.session = {
      user: { id: "user-1" },
      workspaces: [
        {
          id: "workspace-1",
          name: "Northstar Labs",
          slug: "northstar-labs",
          role: "ADMIN",
          memberSince: NOW,
        },
      ],
    };
    return Promise.resolve(this.session);
  }

  logout(): Promise<void> {
    this.session = null;
    return Promise.resolve();
  }

  getDashboard(): Promise<DashboardSummary> {
    return Promise.resolve({
      totalIncidents: this.incidents.length,
      activeIncidents: this.incidents.filter((item) => item.status !== "RESOLVED").length,
      unassignedActiveIncidents: this.incidents.filter(
        (item) => item.status !== "RESOLVED" && !item.assigneeId,
      ).length,
      byStatus: {
        OPEN: 1,
        INVESTIGATING: 0,
        IDENTIFIED: 0,
        MONITORING: 0,
        RESOLVED: 0,
      },
      bySeverity: { SEV1: 0, SEV2: 1, SEV3: 0, SEV4: 0 },
    });
  }

  getServices(): Promise<ManagedService[]> {
    return Promise.resolve(this.services.map(cloneService));
  }

  createService(_workspaceId: string, input: CreateManagedServiceInput): Promise<ManagedService> {
    const service: ManagedService = {
      id: `service-${this.services.length + 1}`,
      workspaceId: "workspace-1",
      name: input.name,
      slug: input.slug,
      description: input.description ?? null,
      lifecycleStatus: "ACTIVE",
      createdAt: NOW,
      updatedAt: NOW,
    };
    this.services.push(service);
    this.services.sort((left, right) => left.name.localeCompare(right.name));
    return Promise.resolve(cloneService(service));
  }

  updateService(
    _workspaceId: string,
    serviceId: string,
    input: UpdateManagedServiceInput,
  ): Promise<ManagedService> {
    const service = this.services.find((item) => item.id === serviceId);
    if (!service) return Promise.reject(new Error("Service not found"));
    service.name = input.name;
    service.description = input.description ?? null;
    service.lifecycleStatus = input.lifecycleStatus;
    service.updatedAt = NOW;
    return Promise.resolve(cloneService(service));
  }

  getIncidents(search: IncidentSearch): Promise<IncidentPage> {
    this.searches.push(search);
    const items = search.query
      ? this.incidents.filter((item) =>
          item.title.toLowerCase().includes(search.query!.toLowerCase()),
        )
      : this.incidents;
    return Promise.resolve({
      items,
      page: search.page ?? 0,
      size: search.size ?? 10,
      totalElements: items.length,
      totalPages: items.length ? 1 : 0,
    });
  }

  declareIncident(input: DeclareIncidentInput): Promise<Incident> {
    const incident: Incident = {
      id: "incident-new",
      ...input,
      status: "OPEN",
      assigneeId: null,
      declaredAt: NOW,
      resolvedAt: null,
      version: 0,
    };
    this.incidents.push(incident);
    this.events = [
      {
        id: "event-new",
        actorId: "user-1",
        eventType: "DECLARED",
        fromStatus: null,
        toStatus: "OPEN",
        detail: "Incident declared",
        occurredAt: NOW,
      },
    ];
    return Promise.resolve(incident);
  }

  getIncident(id: string): Promise<Incident> {
    const incident = this.incidents.find((item) => item.id === id);
    if (!incident) return Promise.reject(new Error("Incident not found"));
    return Promise.resolve(incident);
  }

  getIncidentEvents(): Promise<IncidentEvent[]> {
    return Promise.resolve(this.events);
  }

  getIncidentComments(): Promise<IncidentComment[]> {
    return Promise.resolve(this.comments);
  }

  getMembers(): Promise<WorkspaceMember[]> {
    return Promise.resolve(this.members.map(cloneMember));
  }

  addMember(
    _workspaceId: string,
    input: AddWorkspaceMemberInput,
  ): Promise<WorkspaceMember> {
    const member: WorkspaceMember = {
      userId: input.userId,
      email: `${input.userId}@example.test`,
      displayName: `Synthetic ${input.userId}`,
      enabled: true,
      role: input.role,
      createdAt: NOW,
    };
    this.members.push(member);
    this.members.sort(compareMembers);
    return Promise.resolve(cloneMember(member));
  }

  changeMemberRole(
    _workspaceId: string,
    userId: string,
    input: ChangeWorkspaceMemberRoleInput,
  ): Promise<WorkspaceMember> {
    const member = this.members.find((item) => item.userId === userId);
    if (!member) return Promise.reject(new Error("Member not found"));
    member.role = input.role;
    return Promise.resolve(cloneMember(member));
  }

  removeMember(_workspaceId: string, userId: string): Promise<void> {
    this.members = this.members.filter((item) => item.userId !== userId);
    return Promise.resolve();
  }

  getFailedNotificationJobs(
    _workspaceId: string,
    page = 0,
  ): Promise<FailedNotificationJobPage> {
    return Promise.resolve({
      items: this.failedNotificationJobs.map(cloneFailedNotificationJob),
      page,
      size: 20,
      totalElements: this.failedNotificationJobs.length,
      totalPages: this.failedNotificationJobs.length ? 1 : 0,
    });
  }

  replayFailedNotificationJob(
    _workspaceId: string,
    jobId: string,
  ): Promise<ReplayedNotificationJob> {
    const job = this.failedNotificationJobs.find((item) => item.id === jobId);
    if (!job) return Promise.reject(new Error("Failed notification job not found"));
    this.failedNotificationJobs = this.failedNotificationJobs.filter(
      (item) => item.id !== jobId,
    );
    return Promise.resolve({
      id: job.id,
      incidentId: job.incidentId,
      eventType: job.eventType,
      status: "PENDING",
      attemptCount: 0,
      nextAttemptAt: NOW,
      previousAttemptCount: job.attemptCount,
    });
  }

  addComment(id: string, body: string): Promise<IncidentComment> {
    const comment: IncidentComment = {
      id: `comment-${this.comments.length + 1}`,
      incidentId: id,
      authorId: "user-1",
      body,
      createdAt: NOW,
      updatedAt: NOW,
    };
    this.comments.push(comment);
    return Promise.resolve(comment);
  }

  transitionIncident(
    id: string,
    status: IncidentStatus,
    detail?: string,
  ): Promise<Incident> {
    const incident = this.incidents.find((item) => item.id === id)!;
    const previous = incident.status;
    incident.status = status;
    this.events.push({
      id: `event-${this.events.length + 1}`,
      actorId: "user-1",
      eventType: "STATUS_CHANGED",
      fromStatus: previous,
      toStatus: status,
      detail: detail ?? null,
      occurredAt: NOW,
    });
    return Promise.resolve(incident);
  }

  assignIncident(id: string, assigneeId: string): Promise<Incident> {
    const incident = this.incidents.find((item) => item.id === id)!;
    const previousAssignee = incident.assigneeId ?? "unassigned";
    incident.assigneeId = assigneeId;
    this.events.push({
      id: `event-${this.events.length + 1}`,
      actorId: "user-1",
      eventType: "ASSIGNEE_CHANGED",
      fromStatus: null,
      toStatus: null,
      detail: `${previousAssignee} -> ${assigneeId}`,
      occurredAt: NOW,
    });
    return Promise.resolve(incident);
  }

  clearAssignee(id: string): Promise<Incident> {
    const incident = this.incidents.find((item) => item.id === id)!;
    incident.assigneeId = null;
    return Promise.resolve(incident);
  }
}

function cloneService(service: ManagedService): ManagedService {
  return { ...service };
}

function cloneMember(member: WorkspaceMember): WorkspaceMember {
  return { ...member };
}

function cloneFailedNotificationJob(
  job: FailedNotificationJob,
): FailedNotificationJob {
  return { ...job };
}

function compareMembers(left: WorkspaceMember, right: WorkspaceMember): number {
  const roleOrder = { ADMIN: 0, RESPONDER: 1, VIEWER: 2 };
  return (
    roleOrder[left.role] - roleOrder[right.role]
    || left.displayName.localeCompare(right.displayName, "en", { sensitivity: "base" })
    || left.userId.localeCompare(right.userId)
  );
}
