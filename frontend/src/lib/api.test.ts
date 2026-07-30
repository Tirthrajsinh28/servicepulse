import { ApiError, FetchServicePulseApi, REFRESH_TOKEN_KEY } from "./api";

beforeEach(() => {
  sessionStorage.clear();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

test("restores a session through refresh and sends bearer/request-id headers", async () => {
  sessionStorage.setItem(REFRESH_TOKEN_KEY, "refresh-old");
  const requests: Request[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const request = new Request(input, init);
      requests.push(request);
      if (request.url.endsWith("/api/v1/auth/refresh")) {
        return Response.json({
          accessToken: "access-new",
          refreshToken: "refresh-new",
          tokenType: "Bearer",
          expiresIn: 300,
        });
      }
      if (request.url.endsWith("/api/v1/auth/me")) {
        return Response.json({ id: "user-1" });
      }
      if (request.url.endsWith("/api/v1/workspaces")) {
        return Response.json([
          {
            id: "workspace-1",
            name: "Northstar Labs",
            slug: "northstar-labs",
            role: "ADMIN",
            memberSince: "2026-07-01T16:00:00Z",
          },
        ]);
      }
      throw new Error(`Unexpected URL: ${request.url}`);
    }),
  );

  const api = new FetchServicePulseApi("https://servicepulse.test");
  const session = await api.restoreSession();

  expect(session?.workspaces[0].id).toBe("workspace-1");
  expect(sessionStorage.getItem(REFRESH_TOKEN_KEY)).toBe("refresh-new");
  const meRequest = requests.find((request) => request.url.endsWith("/auth/me"))!;
  expect(meRequest.headers.get("Authorization")).toBe("Bearer access-new");
  expect(meRequest.headers.get("X-Request-ID")).toMatch(/^[0-9a-f-]{36}$/);
});

test("refreshes once after a protected request returns 401", async () => {
  sessionStorage.setItem(REFRESH_TOKEN_KEY, "refresh-old");
  let dashboardCalls = 0;
  let refreshCalls = 0;
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const request = new Request(input, init);
      if (request.url.includes("/dashboard/summary")) {
        dashboardCalls += 1;
        if (dashboardCalls === 1) return new Response(null, { status: 401 });
        expect(request.headers.get("Authorization")).toBe("Bearer access-new");
        return Response.json({
          totalIncidents: 0,
          activeIncidents: 0,
          unassignedActiveIncidents: 0,
          byStatus: {},
          bySeverity: {},
        });
      }
      if (request.url.endsWith("/auth/refresh")) {
        refreshCalls += 1;
        return Response.json({
          accessToken: "access-new",
          refreshToken: "refresh-new",
          tokenType: "Bearer",
          expiresIn: 300,
        });
      }
      throw new Error(`Unexpected URL: ${request.url}`);
    }),
  );

  const api = new FetchServicePulseApi("https://servicepulse.test");
  await api.getDashboard("workspace-1");

  expect(dashboardCalls).toBe(2);
  expect(refreshCalls).toBe(1);
});

test("unwraps paginated service responses for existing screens", async () => {
  const requests: Request[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const request = new Request(input, init);
      requests.push(request);
      return Response.json({
        items: [
          {
            id: "service-1",
            workspaceId: "workspace-1",
            name: "Checkout API",
            slug: "checkout-api",
            description: null,
            lifecycleStatus: "ACTIVE",
            createdAt: "2026-07-01T16:00:00Z",
            updatedAt: "2026-07-01T16:00:00Z",
          },
        ],
        page: 0,
        size: 100,
        totalElements: 1,
        totalPages: 1,
      });
    }),
  );

  const api = new FetchServicePulseApi("https://servicepulse.test");
  const services = await api.getServices("workspace-1");

  expect(services).toHaveLength(1);
  expect(services[0].name).toBe("Checkout API");
  expect(requests[0].url).toContain("/api/v1/workspaces/workspace-1/services?");
  expect(requests[0].url).toContain("page=0");
  expect(requests[0].url).toContain("size=100");
});

test("creates and updates services with the backend contract", async () => {
  const requests: Request[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const request = new Request(input, init);
      requests.push(request);
      const body = (await request.clone().json()) as Record<string, unknown>;
      return Response.json({
        id: request.method === "POST" ? "service-2" : "service-1",
        workspaceId: "workspace-1",
        name: body.name,
        slug: request.method === "POST" ? body.slug : "checkout-api",
        description: body.description ?? null,
        lifecycleStatus: body.lifecycleStatus ?? "ACTIVE",
        createdAt: "2026-07-01T16:00:00Z",
        updatedAt: "2026-07-01T16:00:00Z",
      });
    }),
  );

  const api = new FetchServicePulseApi("https://servicepulse.test");
  const created = await api.createService("workspace-1", {
    name: "Inventory API",
    slug: "inventory-api",
    description: "Synthetic inventory service",
  });
  const updated = await api.updateService("workspace-1", "service-1", {
    name: "Checkout API",
    description: null,
    lifecycleStatus: "MAINTENANCE",
  });

  expect(created.slug).toBe("inventory-api");
  expect(updated.lifecycleStatus).toBe("MAINTENANCE");
  expect(requests[0].method).toBe("POST");
  expect(requests[0].url).toContain("/api/v1/workspaces/workspace-1/services");
  expect(await requests[0].clone().json()).toMatchObject({
    name: "Inventory API",
    slug: "inventory-api",
  });
  expect(requests[1].method).toBe("PUT");
  expect(requests[1].url).toContain(
    "/api/v1/workspaces/workspace-1/services/service-1",
  );
  expect(await requests[1].clone().json()).toMatchObject({
    lifecycleStatus: "MAINTENANCE",
  });
});

test("inspects and replays failed notification jobs with the backend contract", async () => {
  const requests: Request[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const request = new Request(input, init);
      requests.push(request);
      if (request.method === "GET") {
        return Response.json({
          items: [
            {
              id: "job-1",
              incidentId: "incident-1",
              eventType: "STATUS_CHANGED",
              attemptCount: 5,
              lastError: "IllegalStateException",
              createdAt: "2026-07-01T16:00:00Z",
              failedAt: "2026-07-01T16:05:00Z",
            },
          ],
          page: 1,
          size: 20,
          totalElements: 21,
          totalPages: 2,
        });
      }
      return Response.json({
        id: "job-1",
        incidentId: "incident-1",
        eventType: "STATUS_CHANGED",
        status: "PENDING",
        attemptCount: 0,
        nextAttemptAt: "2026-07-01T16:06:00Z",
        previousAttemptCount: 5,
      });
    }),
  );

  const api = new FetchServicePulseApi("https://servicepulse.test");
  const failedJobs = await api.getFailedNotificationJobs("workspace-1", 1);
  const replayed = await api.replayFailedNotificationJob("workspace-1", "job-1");

  expect(failedJobs.items[0].lastError).toBe("IllegalStateException");
  expect(failedJobs.page).toBe(1);
  expect(replayed.status).toBe("PENDING");
  expect(replayed.previousAttemptCount).toBe(5);
  expect(requests[0].method).toBe("GET");
  expect(requests[0].url).toContain(
    "/api/v1/workspaces/workspace-1/notification-jobs/failed?",
  );
  expect(requests[0].url).toContain("page=1");
  expect(requests[0].url).toContain("size=20");
  expect(requests[1].method).toBe("POST");
  expect(requests[1].url).toContain(
    "/api/v1/workspaces/workspace-1/notification-jobs/job-1/replay",
  );
});

test("maps problem details and field errors", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async () =>
      Response.json(
        {
          title: "Request validation failed",
          detail: "One or more request fields are invalid.",
          errors: { title: "must not be blank" },
        },
        { status: 400 },
      ),
    ),
  );
  const api = new FetchServicePulseApi("https://servicepulse.test");

  const error = await api
    .declareIncident({
      workspaceId: "workspace-1",
      serviceId: "service-1",
      title: "",
      summary: "Summary",
      severity: "SEV2",
    })
    .catch((caught: unknown) => caught);

  expect(error).toBeInstanceOf(ApiError);
  expect((error as ApiError).status).toBe(400);
  expect((error as ApiError).fieldErrors.title).toBe("must not be blank");
});
