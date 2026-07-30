import type { ReactNode } from "react";

import { useAuth } from "../auth/auth-context";
import { Navigate, useLocation } from "../router";
import { LoadingPanel } from "./Feedback";

export function ProtectedRoute({ children }: { children: ReactNode }) {
  const auth = useAuth();
  const location = useLocation();

  if (auth.status === "loading") {
    return <LoadingPanel label="Restoring your session" />;
  }
  if (auth.status === "anonymous") {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  return <>{children}</>;
}
