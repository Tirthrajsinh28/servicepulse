import { useState } from "react";
import { Navigate, useLocation, useNavigate } from "../router";

import { useAuth } from "../auth/auth-context";
import { ApiError } from "../lib/api";
import { ErrorNotice } from "../components/Feedback";

export function LoginPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (auth.status === "authenticated") {
    return <Navigate to="/" replace />;
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    if (!email.trim() || !password) {
      setError("Enter both your email address and password.");
      return;
    }
    setSubmitting(true);
    try {
      await auth.login(email, password);
      const from = (location.state as { from?: string } | null)?.from ?? "/";
      navigate(from, { replace: true });
    } catch (caught) {
      setError(
        caught instanceof ApiError
          ? caught.message
          : "The API could not be reached. Confirm the backend is running.",
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="login-page">
      <section className="login-panel" aria-labelledby="login-title">
        <div className="login-intro">
          <span className="eyebrow">Incident operations</span>
          <h1 id="login-title" tabIndex={-1}>
            Return to steady state.
          </h1>
          <p>
            Track service incidents, decisions, ownership, and recovery without
            losing the operational thread.
          </p>
          <ul className="feature-list">
            <li>Workspace-scoped access</li>
            <li>Append-only timelines</li>
            <li>Audited operational changes</li>
          </ul>
        </div>
        <form className="login-form" onSubmit={submit} noValidate>
          <div>
            <span className="eyebrow">ServicePulse</span>
            <h2>Sign in</h2>
            <p>Use a development seed account configured by the operator.</p>
          </div>
          {error ? <ErrorNotice title="Sign-in failed" message={error} /> : null}
          <label>
            Email address
            <input
              autoComplete="username"
              inputMode="email"
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              required
            />
          </label>
          <label>
            Password
            <input
              autoComplete="current-password"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
            />
          </label>
          <button className="button button-primary" type="submit" disabled={submitting}>
            {submitting ? "Signing in…" : "Sign in"}
          </button>
          <small className="form-hint">
            No default credential is embedded in this application.
          </small>
        </form>
      </section>
    </main>
  );
}
