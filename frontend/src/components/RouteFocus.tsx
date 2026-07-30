import { useEffect } from "react";

import { useLocation } from "../router";

export function RouteFocus() {
  const location = useLocation();

  useEffect(() => {
    window.scrollTo({ top: 0, behavior: "instant" });
    requestAnimationFrame(() => {
      document.querySelector<HTMLElement>("#main-content h1")?.focus();
    });
  }, [location.pathname]);

  return null;
}
