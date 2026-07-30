import { useEffect, useRef } from "react";

import { useLocation } from "../router";

export function RouteFocus() {
  const location = useLocation();
  const previousPath = useRef(location.pathname);

  useEffect(() => {
    if (previousPath.current === location.pathname) {
      return;
    }
    previousPath.current = location.pathname;
    window.scrollTo({ top: 0, behavior: "instant" });
    requestAnimationFrame(() => {
      document.querySelector<HTMLElement>("#main-content h1")?.focus();
    });
  }, [location.pathname]);

  return null;
}
