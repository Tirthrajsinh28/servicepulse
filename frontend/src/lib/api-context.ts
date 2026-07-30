import { createContext, useContext } from "react";

import { DemoServicePulseApi } from "../demo/DemoServicePulseApi";
import { servicePulseApi, type ServicePulseApi } from "./api";

export const demoModeEnabled =
  import.meta.env.VITE_SERVICEPULSE_DEMO_MODE === "true";

const defaultApi = demoModeEnabled
  ? new DemoServicePulseApi()
  : servicePulseApi;

export const ApiContext = createContext<ServicePulseApi>(defaultApi);

export function useApi(): ServicePulseApi {
  return useContext(ApiContext);
}
