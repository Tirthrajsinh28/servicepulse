import "@testing-library/jest-dom/vitest";
import { toHaveNoViolations } from "jest-axe";
import { expect, vi } from "vitest";

expect.extend(toHaveNoViolations);
window.scrollTo = vi.fn();
