export type PanelKey = "library" | "viewer" | "controller";

export type PanelVisibilityState = Record<PanelKey, boolean>;

export const DEFAULT_PANEL_VISIBILITY: PanelVisibilityState = {
  library: true,
  viewer: true,
  controller: true,
};
