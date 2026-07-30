/* eslint-disable react-refresh/only-export-components -- Router primitives intentionally colocate hooks and components; they do not hold component-local Fast Refresh state. */
import {
  createContext,
  type AnchorHTMLAttributes,
  type MouseEvent,
  type ReactNode,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";

type NavigationOptions = {
  replace?: boolean;
  state?: unknown;
};

type LocationState = {
  pathname: string;
  state: unknown;
};

type RouterContextValue = {
  location: LocationState;
  navigate: (to: string, options?: NavigationOptions) => void;
};

const RouterContext = createContext<RouterContextValue | null>(null);
const ParamsContext = createContext<Record<string, string>>({});

function readLocation(): LocationState {
  return {
    pathname: window.location.pathname || "/",
    state: (window.history.state as { appState?: unknown } | null)?.appState ?? null,
  };
}

function normalizePath(to: string) {
  if (to.startsWith("http://") || to.startsWith("https://")) {
    return to;
  }
  return to.startsWith("/") ? to : `/${to}`;
}

export function RouterProvider({ children }: { children: ReactNode }) {
  const [location, setLocation] = useState(readLocation);

  useEffect(() => {
    const onPopState = () => setLocation(readLocation());
    window.addEventListener("popstate", onPopState);
    return () => window.removeEventListener("popstate", onPopState);
  }, []);

  const value = useMemo<RouterContextValue>(
    () => ({
      location,
      navigate(to, options = {}) {
        const pathname = normalizePath(to);
        const historyState = { appState: options.state ?? null };
        if (options.replace) {
          window.history.replaceState(historyState, "", pathname);
        } else {
          window.history.pushState(historyState, "", pathname);
        }
        setLocation(readLocation());
      },
    }),
    [location],
  );

  return <RouterContext.Provider value={value}>{children}</RouterContext.Provider>;
}

function useRouter() {
  const router = useContext(RouterContext);
  if (!router) {
    throw new Error("Router hooks must be used inside RouterProvider.");
  }
  return router;
}

export function useLocation() {
  return useRouter().location;
}

export function useNavigate() {
  return useRouter().navigate;
}

export function RouteParamsProvider({
  children,
  value,
}: {
  children: ReactNode;
  value: Record<string, string>;
}) {
  return <ParamsContext.Provider value={value}>{children}</ParamsContext.Provider>;
}

export function useParams() {
  return useContext(ParamsContext);
}

export function Navigate({
  replace,
  state,
  to,
}: {
  replace?: boolean;
  state?: unknown;
  to: string;
}) {
  const navigate = useNavigate();

  useEffect(() => {
    navigate(to, { replace, state });
  }, [navigate, replace, state, to]);

  return null;
}

type LinkProps = Omit<AnchorHTMLAttributes<HTMLAnchorElement>, "href"> & {
  to: string;
};

export function Link({ children, onClick, to, ...props }: LinkProps) {
  const navigate = useNavigate();
  const href = normalizePath(to);

  function click(event: MouseEvent<HTMLAnchorElement>) {
    onClick?.(event);
    if (
      event.defaultPrevented ||
      event.button !== 0 ||
      event.metaKey ||
      event.altKey ||
      event.ctrlKey ||
      event.shiftKey ||
      props.target
    ) {
      return;
    }
    event.preventDefault();
    navigate(href);
  }

  return (
    <a href={href} onClick={click} {...props}>
      {children}
    </a>
  );
}

type NavLinkProps = LinkProps & {
  end?: boolean;
};

export function NavLink({ className, end, to, ...props }: NavLinkProps) {
  const { pathname } = useLocation();
  const href = normalizePath(to);
  const active = end ? pathname === href : pathname === href || pathname.startsWith(`${href}/`);
  const activeClass = active ? "active" : "";
  const resolvedClassName =
    typeof className === "string"
      ? [className, activeClass].filter(Boolean).join(" ")
      : activeClass || undefined;

  return (
    <Link
      aria-current={active ? "page" : undefined}
      className={resolvedClassName}
      to={href}
      {...props}
    />
  );
}
