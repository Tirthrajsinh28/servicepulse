import { AuthProvider } from "./auth/AuthProvider";
import { AppShell } from "./components/AppShell";
import { ProtectedRoute } from "./components/ProtectedRoute";
import { RouteFocus } from "./components/RouteFocus";
import { DashboardPage } from "./pages/DashboardPage";
import { DeclareIncidentPage } from "./pages/DeclareIncidentPage";
import { IncidentDetailPage } from "./pages/IncidentDetailPage";
import { LoginPage } from "./pages/LoginPage";
import { MembersPage } from "./pages/MembersPage";
import { NotFoundPage } from "./pages/NotFoundPage";
import { NotificationsPage } from "./pages/NotificationsPage";
import { ServicesPage } from "./pages/ServicesPage";
import { RouteParamsProvider, RouterProvider, useLocation } from "./router";

function AppRoutes() {
  const { pathname } = useLocation();

  if (pathname === "/login") {
    return <LoginPage />;
  }

  let page = <NotFoundPage />;
  let params: Record<string, string> = {};
  const incidentMatch = /^\/incidents\/([^/]+)$/.exec(pathname);

  if (pathname === "/") {
    page = <DashboardPage />;
  } else if (pathname === "/services") {
    page = <ServicesPage />;
  } else if (pathname === "/members") {
    page = <MembersPage />;
  } else if (pathname === "/notifications") {
    page = <NotificationsPage />;
  } else if (pathname === "/incidents/new") {
    page = <DeclareIncidentPage />;
  } else if (incidentMatch) {
    params = { id: decodeURIComponent(incidentMatch[1]) };
    page = <IncidentDetailPage />;
  }

  return (
    <ProtectedRoute>
      <AppShell>
        <RouteParamsProvider value={params}>{page}</RouteParamsProvider>
      </AppShell>
    </ProtectedRoute>
  );
}

export function App() {
  return (
    <RouterProvider>
      <AuthProvider>
        <RouteFocus />
        <AppRoutes />
      </AuthProvider>
    </RouterProvider>
  );
}
