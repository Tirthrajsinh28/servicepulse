import { Link } from "../router";

export function NotFoundPage() {
  return (
    <div className="state-panel not-found">
      <span className="eyebrow">404 · Route not found</span>
      <h1 tabIndex={-1}>This operational path does not exist.</h1>
      <p>Return to the incident overview and continue from a known state.</p>
      <Link className="button button-primary" to="/">
        Return to overview
      </Link>
    </div>
  );
}
