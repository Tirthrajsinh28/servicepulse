import type {
  AuthBootstrap,
  AddWorkspaceMemberInput,
  ChangeWorkspaceMemberRoleInput,
  CreateManagedServiceInput,
  CurrentUser,
  DashboardSummary,
  DeclareIncidentInput,
  FailedNotificationJobPage,
  Incident,
  IncidentComment,
  IncidentEvent,
  IncidentPage,
  IncidentSearch,
  IncidentStatus,
  ManagedService,
  ManagedServicePage,
  ReplayedNotificationJob,
  UpdateManagedServiceInput,
  TokenResponse,
  Workspace,
  WorkspaceMember,
} from "./types";

export class ApiError extends Error {
  readonly status: number;
  readonly title: string;
  readonly fieldErrors: Record<string, string>;

  constructor(
    status: number,
    title: string,
    detail: string,
    fieldErrors: Record<string, string> = {},
  ) {
    super(detail);
    this.name = "ApiError";
    this.status = status;
    this.title = title;
    this.fieldErrors = fieldErrors;
  }
}

export interface ServicePulseApi {
  restoreSession(): Promise<AuthBootstrap | null>;
  login(email: string, password: string): Promise<AuthBootstrap>;
  logout(): Promise<void>;
  getDashboard(workspaceId: string): Promise<DashboardSummary>;
  getServices(workspaceId: string): Promise<ManagedService[]>;
  createService(workspaceId: string, input: CreateManagedServiceInput): Promise<ManagedService>;
  updateService(
    workspaceId: string,
    serviceId: string,
    input: UpdateManagedServiceInput,
  ): Promise<ManagedService>;
  getIncidents(search: IncidentSearch): Promise<IncidentPage>;
  declareIncident(input: DeclareIncidentInput): Promise<Incident>;
  getIncident(id: string): Promise<Incident>;
  getIncidentEvents(id: string): Promise<IncidentEvent[]>;
  getIncidentComments(id: string): Promise<IncidentComment[]>;
  getMembers(workspaceId: string): Promise<WorkspaceMember[]>;
  addMember(
    workspaceId: string,
    input: AddWorkspaceMemberInput,
  ): Promise<WorkspaceMember>;
  changeMemberRole(
    workspaceId: string,
    userId: string,
    input: ChangeWorkspaceMemberRoleInput,
  ): Promise<WorkspaceMember>;
  removeMember(workspaceId: string, userId: string): Promise<void>;
  getFailedNotificationJobs(
    workspaceId: string,
    page?: number,
  ): Promise<FailedNotificationJobPage>;
  replayFailedNotificationJob(
    workspaceId: string,
    jobId: string,
  ): Promise<ReplayedNotificationJob>;
  addComment(id: string, body: string): Promise<IncidentComment>;
  transitionIncident(
    id: string,
    status: IncidentStatus,
    detail?: string,
  ): Promise<Incident>;
  assignIncident(id: string, assigneeId: string): Promise<Incident>;
  clearAssignee(id: string): Promise<Incident>;
}

interface ProblemResponse {
  title?: string;
  detail?: string;
  errors?: Record<string, string>;
}

export const REFRESH_TOKEN_KEY = "servicepulse.refresh-token";

export class FetchServicePulseApi implements ServicePulseApi {
  private readonly baseUrl: string;
  private accessToken: string | null = null;
  private refreshPromise: Promise<void> | null = null;

  constructor(baseUrl: string) {
    this.baseUrl = baseUrl.replace(/\/$/, "");
  }

  async restoreSession(): Promise<AuthBootstrap | null> {
    if (!this.storedRefreshToken()) {
      return null;
    }
    try {
      await this.refresh();
      return await this.bootstrap();
    } catch {
      this.clearSession();
      return null;
    }
  }

  async login(email: string, password: string): Promise<AuthBootstrap> {
    const tokens = await this.request<TokenResponse>(
      "/api/v1/auth/login",
      {
        method: "POST",
        body: JSON.stringify({ email: email.trim(), password }),
      },
      false,
    );
    this.saveTokens(tokens);
    return this.bootstrap();
  }

  async logout(): Promise<void> {
    const refreshToken = this.storedRefreshToken();
    try {
      if (refreshToken) {
        await this.request<void>(
          "/api/v1/auth/logout",
          {
            method: "POST",
            body: JSON.stringify({ refreshToken }),
          },
          false,
        );
      }
    } finally {
      this.clearSession();
    }
  }

  getDashboard(workspaceId: string) {
    return this.request<DashboardSummary>(
      `/api/v1/dashboard/summary?workspaceId=${encodeURIComponent(workspaceId)}`,
    );
  }

  getServices(workspaceId: string) {
    const parameters = new URLSearchParams({
      page: "0",
      size: "100",
    });
    return this.request<ManagedServicePage>(
      `/api/v1/workspaces/${encodeURIComponent(workspaceId)}/services?${parameters}`,
    ).then((page) => page.items);
  }

  createService(workspaceId: string, input: CreateManagedServiceInput) {
    return this.request<ManagedService>(
      `/api/v1/workspaces/${encodeURIComponent(workspaceId)}/services`,
      {
        method: "POST",
        body: JSON.stringify(input),
      },
    );
  }

  updateService(
    workspaceId: string,
    serviceId: string,
    input: UpdateManagedServiceInput,
  ) {
    return this.request<ManagedService>(
      `/api/v1/workspaces/${encodeURIComponent(workspaceId)}/services/${encodeURIComponent(serviceId)}`,
      {
        method: "PUT",
        body: JSON.stringify(input),
      },
    );
  }

  getIncidents(search: IncidentSearch) {
    const parameters = new URLSearchParams({
      workspaceId: search.workspaceId,
      page: String(search.page ?? 0),
      size: String(search.size ?? 20),
    });
    if (search.query) parameters.set("query", search.query);
    if (search.serviceId) parameters.set("serviceId", search.serviceId);
    if (search.status) parameters.set("status", search.status);
    if (search.severity) parameters.set("severity", search.severity);
    return this.request<IncidentPage>(`/api/v1/incidents?${parameters}`);
  }

  declareIncident(input: DeclareIncidentInput) {
    return this.request<Incident>("/api/v1/incidents", {
      method: "POST",
      body: JSON.stringify(input),
    });
  }

  getIncident(id: string) {
    return this.request<Incident>(`/api/v1/incidents/${encodeURIComponent(id)}`);
  }

  getIncidentEvents(id: string) {
    return this.request<IncidentEvent[]>(
      `/api/v1/incidents/${encodeURIComponent(id)}/events`,
    );
  }

  getIncidentComments(id: string) {
    return this.request<IncidentComment[]>(
      `/api/v1/incidents/${encodeURIComponent(id)}/comments`,
    );
  }

  getMembers(workspaceId: string) {
    return this.request<WorkspaceMember[]>(
      `/api/v1/workspaces/${encodeURIComponent(workspaceId)}/members`,
    );
  }

  addMember(workspaceId: string, input: AddWorkspaceMemberInput) {
    return this.request<WorkspaceMember>(
      `/api/v1/workspaces/${encodeURIComponent(workspaceId)}/members`,
      {
        method: "POST",
        body: JSON.stringify(input),
      },
    );
  }

  changeMemberRole(
    workspaceId: string,
    userId: string,
    input: ChangeWorkspaceMemberRoleInput,
  ) {
    return this.request<WorkspaceMember>(
      `/api/v1/workspaces/${encodeURIComponent(workspaceId)}/members/${encodeURIComponent(userId)}`,
      {
        method: "PUT",
        body: JSON.stringify(input),
      },
    );
  }

  removeMember(workspaceId: string, userId: string) {
    return this.request<void>(
      `/api/v1/workspaces/${encodeURIComponent(workspaceId)}/members/${encodeURIComponent(userId)}`,
      { method: "DELETE" },
    );
  }

  getFailedNotificationJobs(workspaceId: string, page = 0) {
    const parameters = new URLSearchParams({
      page: String(page),
      size: "20",
    });
    return this.request<FailedNotificationJobPage>(
      `/api/v1/workspaces/${encodeURIComponent(workspaceId)}/notification-jobs/failed?${parameters}`,
    );
  }

  replayFailedNotificationJob(workspaceId: string, jobId: string) {
    return this.request<ReplayedNotificationJob>(
      `/api/v1/workspaces/${encodeURIComponent(workspaceId)}/notification-jobs/${encodeURIComponent(jobId)}/replay`,
      { method: "POST" },
    );
  }

  addComment(id: string, body: string) {
    return this.request<IncidentComment>(
      `/api/v1/incidents/${encodeURIComponent(id)}/comments`,
      {
        method: "POST",
        body: JSON.stringify({ body }),
      },
    );
  }

  transitionIncident(id: string, status: IncidentStatus, detail?: string) {
    return this.request<Incident>(
      `/api/v1/incidents/${encodeURIComponent(id)}/transitions`,
      {
        method: "POST",
        body: JSON.stringify({ status, detail: detail || undefined }),
      },
    );
  }

  assignIncident(id: string, assigneeId: string) {
    return this.request<Incident>(
      `/api/v1/incidents/${encodeURIComponent(id)}/assignee`,
      {
        method: "PUT",
        body: JSON.stringify({ assigneeId }),
      },
    );
  }

  clearAssignee(id: string) {
    return this.request<Incident>(
      `/api/v1/incidents/${encodeURIComponent(id)}/assignee`,
      { method: "DELETE" },
    );
  }

  private async bootstrap(): Promise<AuthBootstrap> {
    const [user, workspaces] = await Promise.all([
      this.request<CurrentUser>("/api/v1/auth/me"),
      this.request<Workspace[]>("/api/v1/workspaces"),
    ]);
    return { user, workspaces };
  }

  private async refresh(): Promise<void> {
    if (this.refreshPromise) {
      return this.refreshPromise;
    }
    const refreshToken = this.storedRefreshToken();
    if (!refreshToken) {
      throw new ApiError(401, "Authentication required", "No refresh token is available.");
    }
    this.refreshPromise = this.request<TokenResponse>(
      "/api/v1/auth/refresh",
      {
        method: "POST",
        body: JSON.stringify({ refreshToken }),
      },
      false,
    )
      .then((tokens) => this.saveTokens(tokens))
      .finally(() => {
        this.refreshPromise = null;
      });
    return this.refreshPromise;
  }

  private async request<T>(
    path: string,
    init: RequestInit = {},
    retry = true,
  ): Promise<T> {
    const headers = new Headers(init.headers);
    headers.set("Accept", "application/json");
    headers.set("X-Request-ID", crypto.randomUUID());
    if (init.body) headers.set("Content-Type", "application/json");
    if (this.accessToken) {
      headers.set("Authorization", `Bearer ${this.accessToken}`);
    }
    const response = await fetch(`${this.baseUrl}${path}`, { ...init, headers });
    if (
      response.status === 401 &&
      retry &&
      !path.startsWith("/api/v1/auth/")
    ) {
      await this.refresh();
      return this.request<T>(path, init, false);
    }
    if (!response.ok) {
      let problem: ProblemResponse;
      try {
        problem = (await response.json()) as ProblemResponse;
      } catch {
        problem = {};
      }
      throw new ApiError(
        response.status,
        problem.title ?? "Request failed",
        problem.detail ?? `The server returned HTTP ${response.status}.`,
        problem.errors,
      );
    }
    if (response.status === 204) {
      return undefined as T;
    }
    return (await response.json()) as T;
  }

  private saveTokens(tokens: TokenResponse): void {
    this.accessToken = tokens.accessToken;
    sessionStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken);
  }

  private storedRefreshToken(): string | null {
    return sessionStorage.getItem(REFRESH_TOKEN_KEY);
  }

  private clearSession(): void {
    this.accessToken = null;
    sessionStorage.removeItem(REFRESH_TOKEN_KEY);
  }
}

export const servicePulseApi: ServicePulseApi = new FetchServicePulseApi(
  import.meta.env.VITE_API_BASE_URL ?? "",
);
