"use client";

import { useCallback, useState } from "react";

import { ResizableSidebar } from "@/components/ui/resizable-sidebar";
import { useScenarioLibrary } from "@/features/scenarios/hooks/use-scenario-library";
import { AppBar } from "@/features/shell/AppBar";
import { SimulationControlsPanel } from "@/features/simulation/components/SimulationControlsPanel";
import { SimulationViewport } from "@/features/simulation/components/SimulationViewport";
import { SimulationProvider } from "@/features/simulation/providers/SimulationProvider";
import {
  DEFAULT_PANEL_VISIBILITY,
  type PanelKey,
  type PanelVisibilityState,
} from "@/lib/panels";


export default function Home() {
  const [panelVisibility, setPanelVisibility] = useState<PanelVisibilityState>(
    DEFAULT_PANEL_VISIBILITY
  );
  const {
    scenarios,
    // loading: scenariosLoading,
    // error: scenariosError,
    selectedScenario,
    scenarioUrl,
    selectScenario,
    importScenario,
    deleteScenario,
    updateScenario
  } = useScenarioLibrary();

  const handleTogglePanel = useCallback((panel: PanelKey) => {
    setPanelVisibility((previous) => ({
      ...previous,
      [panel]: !previous[panel],
    }));
  }, []);


  const [rightPanelWidth, setRightPanelWidth] = useState(350);

  return (
    <SimulationProvider scenarioUrl={scenarioUrl}>
      <main className="relative h-screen w-screen overflow-hidden bg-background">
        {/* Graph Layer */}
        <div className="absolute inset-0 z-0">
          <SimulationViewport />
        </div>

        {/* UI Overlay Layer */}
        <div className="absolute inset-0 z-10 pointer-events-none flex flex-col h-full">
          <div className="pointer-events-auto">
            <AppBar
              panelVisibility={panelVisibility}
              onTogglePanel={handleTogglePanel}
              scenarios={scenarios}
              selectedScenario={selectedScenario}
              onScenarioSelect={selectScenario}
              onImportScenario={importScenario}
              onUpdateScenario={updateScenario}
              onDeleteScenario={deleteScenario}
            />
          </div>

          <div className="relative flex-1 w-full overflow-hidden">
            {/* Right Sidebar (Controller) */}
            <ResizableSidebar
              side="right"
              width={rightPanelWidth}
              onResize={setRightPanelWidth}
              isOpen={panelVisibility.controller}
              className="pointer-events-auto"
            >
              <div className="h-full overflow-auto bg-transparent p-4">
                <SimulationControlsPanel />
              </div>
            </ResizableSidebar>
          </div>
        </div>
      </main>
    </SimulationProvider>
  );
}
