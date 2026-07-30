import { createContext, useContext } from "react";

import { servicePulseApi, type ServicePulseApi } from "./api";

export const ApiContext = createContext<ServicePulseApi>(servicePulseApi);

export function useApi(): ServicePulseApi {
  return useContext(ApiContext);
}
