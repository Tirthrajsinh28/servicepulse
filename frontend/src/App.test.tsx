import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { axe } from "jest-axe";

import { App } from "./App";
import { ApiContext } from "./lib/api-context";
import { FakeApi } from "./test/FakeApi";

function renderApp(api: FakeApi, path = "/") {
  window.history.pushState({}, "", path);
  return render(
    <ApiContext.Provider value={api}>
      <App />
    </ApiContext.Provider>,
  );
}

beforeEach(() => {
  sessionStorage.clear();
  window.history.pushState({}, "", "/");
});

test("signs in and loads the authorized workspace overview", async () => {
  const api = new FakeApi("ADMIN", false);
  const user = userEvent.setup();
  const { container } = renderApp(api, "/login");

  expect(await screen.findByRole("heading", { name: /sign in/i })).toBeVisible();
  expect(await axe(container)).toHaveNoViolations();
  await user.type(screen.getByLabelText(/email address/i), " admin@example.test ");
  await user.type(screen.getByLabelText(/^password$/i), "local-password");
  await user.click(screen.getByRole("button", { name: /sign in/i }));

  expect(await screen.findByRole("heading", { name: /incident overview/i })).toBeVisible();
  expect(screen.getAllByText("Northstar Labs")).not.toHaveLength(0);
  expect(api.loginCalls).toEqual([
    { email: "admin@example.test", password: "local-password" },
  ]);
});

test("renders an accessible dashboard and applies text filtering", async () => {
  const api = new FakeApi();
  const user = userEvent.setup();
  const { container } = renderApp(api);

  expect(await screen.findByText("Elevated checkout latency")).toBeVisible();
  const results = await axe(container);
  expect(results).toHaveNoViolations();

  await user.type(screen.getByLabelText(/search title or summary/i), "missing");
  await user.click(screen.getByRole("button", { name: /apply filters/i }));
  expect(await screen.findByRole("heading", { name: /no incidents match/i })).toBeVisible();
  expect(api.searches.at(-1)?.query).toBe("missing");
});

test("exposes a skip link wired to the main landmark", async () => {
  const user = userEvent.setup();
  renderApp(new FakeApi());

  expect(await screen.findByRole("heading", { name: /incident overview/i }))
    .toBeVisible();

  const skipLink = screen.getByRole("link", { name: /skip to main content/i });
  const main = screen.getByRole("main");

  expect(skipLink).toHaveAttribute("href", "#main-content");
  expect(main).toHaveAttribute("id", "main-content");
  expect(main).toHaveAttribute("tabindex", "-1");

  if (document.activeElement instanceof HTMLElement) {
    document.activeElement.blur();
  }
  await user.tab();
  expect(skipLink).toHaveFocus();
});

test("administrator creates and updates service catalog entries", async () => {
  const api = new FakeApi();
  const user = userEvent.setup();
  const { container } = renderApp(api, "/services");

  expect(await screen.findByRole("heading", { name: /managed services/i })).toBeVisible();
  expect(await screen.findByText("Checkout API")).toBeVisible();
  expect(await axe(container)).toHaveNoViolations();

  await user.type(screen.getByLabelText(/service name/i), "Inventory API");
  expect(screen.getByLabelText(/service slug/i)).toHaveValue("inventory-api");
  await user.type(
    screen.getByLabelText(/^description$/i),
    "Synthetic inventory service",
  );
  await user.click(screen.getByRole("button", { name: /add service/i }));

  expect(await screen.findByText(/Inventory API was added/i)).toBeVisible();
  expect(api.services.some((service) => service.slug === "inventory-api")).toBe(true);

  await user.selectOptions(
    screen.getByLabelText(/lifecycle for inventory api/i),
    "RETIRED",
  );
  const inventoryRow = screen.getByText("Inventory API").closest("tr");
  expect(inventoryRow).not.toBeNull();
  await user.click(
    within(inventoryRow as HTMLTableRowElement).getByRole("button", { name: /^save$/i }),
  );

  expect(await screen.findByText(/lifecycle changed to retired/i)).toBeVisible();
  expect(api.services.find((service) => service.slug === "inventory-api")?.lifecycleStatus)
    .toBe("RETIRED");
});

test("viewer can inspect services without mutation controls", async () => {
  const { container } = renderApp(new FakeApi("VIEWER"), "/services");

  expect(await screen.findByRole("heading", { name: /managed services/i })).toBeVisible();
  expect(await screen.findByText("Checkout API")).toBeVisible();
  expect(screen.queryByRole("heading", { name: /add a service/i })).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: /^save$/i })).not.toBeInTheDocument();
  expect(await axe(container)).toHaveNoViolations();
});

test("administrator manages existing workspace members", async () => {
  const api = new FakeApi();
  const user = userEvent.setup();
  const { container } = renderApp(api, "/members");

  expect(await screen.findByRole("heading", { name: /members and roles/i }))
    .toBeVisible();
  expect(await screen.findByText("Synthetic Administrator")).toBeVisible();
  expect(await screen.findByText("Synthetic Responder")).toBeVisible();
  expect(await axe(container)).toHaveNoViolations();

  await user.type(
    screen.getByLabelText(/existing user uuid/i),
    "33333333-3333-4333-8333-333333333333",
  );
  await user.selectOptions(screen.getByLabelText(/^role$/i), "VIEWER");
  await user.click(screen.getByRole("button", { name: /add member/i }));

  expect(await screen.findByText(/Synthetic 33333333-3333-4333-8333-333333333333 was added as viewer/i))
    .toBeVisible();
  expect(api.members.some((member) => member.userId.startsWith("33333333")))
    .toBe(true);

  await user.selectOptions(
    screen.getByLabelText(/role for synthetic responder/i),
    "VIEWER",
  );
  const responderRow = screen.getByText("Synthetic Responder").closest("tr");
  expect(responderRow).not.toBeNull();
  await user.click(
    within(responderRow as HTMLTableRowElement).getByRole("button", { name: /^save$/i }),
  );

  expect(await screen.findByText(/Synthetic Responder role changed to viewer/i))
    .toBeVisible();
  expect(api.members.find((member) => member.userId === "user-2")?.role)
    .toBe("VIEWER");

  const addedRow = screen
    .getByText("Synthetic 33333333-3333-4333-8333-333333333333")
    .closest("tr");
  expect(addedRow).not.toBeNull();
  await user.click(
    within(addedRow as HTMLTableRowElement).getByRole("button", { name: /^remove$/i }),
  );

  expect(await screen.findByText(/was removed from the workspace/i)).toBeVisible();
  expect(api.members.some((member) => member.userId.startsWith("33333333")))
    .toBe(false);
});

test("viewer can inspect members without membership mutation controls", async () => {
  const { container } = renderApp(new FakeApi("VIEWER"), "/members");

  expect(await screen.findByRole("heading", { name: /members and roles/i }))
    .toBeVisible();
  expect(await screen.findByText("Synthetic Responder")).toBeVisible();
  expect(screen.queryByRole("heading", { name: /add an existing user/i }))
    .not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: /^save$/i })).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: /^remove$/i })).not.toBeInTheDocument();
  expect(await axe(container)).toHaveNoViolations();
});

test("administrator inspects and replays failed notification jobs", async () => {
  const api = new FakeApi();
  const user = userEvent.setup();
  const { container } = renderApp(api, "/notifications");

  expect(await screen.findByRole("heading", { name: /failed notifications/i }))
    .toBeVisible();
  expect(await screen.findByText("Status Changed")).toBeVisible();
  expect(screen.getByText("IllegalStateException")).toBeVisible();
  expect(await axe(container)).toHaveNoViolations();

  await user.click(screen.getByRole("button", { name: /^replay$/i }));

  expect(await screen.findByText(/Status Changed was reset to pending after 5 failed attempt/i))
    .toBeVisible();
  expect(api.failedNotificationJobs).toHaveLength(0);
  expect(await screen.findByRole("heading", { name: /no failed notification jobs/i }))
    .toBeVisible();
});

test("viewer cannot access failed notification operations", async () => {
  const { container } = renderApp(new FakeApi("VIEWER"), "/notifications");

  expect(await screen.findByRole("heading", { name: /administrator access required/i }))
    .toBeVisible();
  expect(screen.queryByRole("button", { name: /^replay$/i })).not.toBeInTheDocument();
  expect(screen.queryByRole("link", { name: /notifications/i })).not.toBeInTheDocument();
  expect(await axe(container)).toHaveNoViolations();
});

test("declares an incident and lands on its append-only record", async () => {
  const api = new FakeApi();
  const user = userEvent.setup();
  const { container } = renderApp(api, "/incidents/new");

  expect(await screen.findByRole("heading", { name: /declare an incident/i })).toBeVisible();
  expect(await axe(container)).toHaveNoViolations();
  await user.type(
    await screen.findByLabelText(/^title/i),
    "Inventory cache saturation",
  );
  await user.type(
    screen.getByLabelText(/initial summary/i),
    "Synthetic cache saturation affects inventory reads.",
  );
  await user.selectOptions(screen.getByLabelText(/severity/i), "SEV3");
  await user.click(screen.getByRole("button", { name: /declare incident/i }));

  expect(await screen.findByRole("heading", { name: "Inventory cache saturation" }))
    .toBeVisible();
  expect(screen.getByText(/incident declared and recorded/i)).toBeVisible();
  expect(api.incidents.at(-1)?.severity).toBe("SEV3");
});

test("updates status, assignment, and comments from incident detail", async () => {
  const api = new FakeApi();
  const user = userEvent.setup();
  const { container } = renderApp(api, "/incidents/incident-1");

  expect(await screen.findByRole("heading", { name: /elevated checkout latency/i }))
    .toBeVisible();
  expect(await axe(container)).toHaveNoViolations();
  await user.type(screen.getByLabelText(/operational note/i), "Investigation started.");
  await user.click(screen.getByRole("button", { name: /update status/i }));
  expect(await screen.findByText(/incident moved to investigating/i)).toBeVisible();

  await user.selectOptions(screen.getByLabelText(/^responder$/i), "user-2");
  await user.click(screen.getByRole("button", { name: /save assignment/i }));
  expect(await screen.findByText(/incident assignment updated/i)).toBeVisible();
  expect(screen.getByText("Unassigned → Synthetic Responder")).toBeVisible();

  await user.type(screen.getByLabelText(/add a comment/i), "Synthetic logs reviewed.");
  await user.click(screen.getByRole("button", { name: /^add comment$/i }));
  expect(await screen.findByText("Synthetic logs reviewed.")).toBeVisible();
});

test("viewer receives read-only incident detail", async () => {
  const api = new FakeApi("VIEWER");
  const { container } = renderApp(api, "/incidents/incident-1");

  expect(await screen.findByRole("heading", { name: /elevated checkout latency/i }))
    .toBeVisible();
  expect(await axe(container)).toHaveNoViolations();
  expect(screen.queryByRole("heading", { name: /change status/i })).not.toBeInTheDocument();
  expect(screen.queryByLabelText(/add a comment/i)).not.toBeInTheDocument();
  expect(screen.queryByRole("link", { name: /declare incident/i })).not.toBeInTheDocument();
});

test("renders the custom not-found route", async () => {
  const { container } = renderApp(new FakeApi(), "/not-a-route");
  expect(
    await screen.findByRole("heading", {
      name: /operational path does not exist/i,
    }),
  ).toBeVisible();
  expect(await axe(container)).toHaveNoViolations();
});

test("redirects anonymous users to sign in", async () => {
  renderApp(new FakeApi("ADMIN", false), "/");
  await waitFor(() =>
    expect(screen.getByRole("heading", { name: /return to steady state/i })).toBeVisible(),
  );
});
