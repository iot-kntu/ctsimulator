"use client";

import {
  ReactNode,
  createContext,
  startTransition,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";

import {
  loadScenarioFromUrl,
  type Scenario,
  type ScenarioLoadResult,
} from "@/lib/scenario";
import {
  SimulationEngine,
  type SimulationEngineState,
  type SimulationSnapshot,
} from "@/lib/simulation-engine";

const PLAYBACK_INTERVAL_MS = 1600;

type SimulationProviderProps = {
  scenarioUrl?: string | null;
  children: ReactNode;
};

type SimulationContextValue = {
  loading: boolean;
  error: string | null;
  warnings: string[];
  scenario: Scenario | null;
  snapshot: SimulationSnapshot | null;
  nextSnapshot?: SimulationSnapshot;
  totalSteps: number;
  canStepForward: boolean;
  canStepBackward: boolean;
  canRedo: boolean;
  isPlaying: boolean;
  play: () => void;
  pause: () => void;
  stepForward: () => void;
  stepBackward: () => void;
  reset: () => void;
  undo: () => void;
  redo: () => void;
};

const SimulationContext = createContext<SimulationContextValue | null>(null);

export function SimulationProvider({
  scenarioUrl,
  children,
}: SimulationProviderProps) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [scenarioResult, setScenarioResult] =
    useState<ScenarioLoadResult | null>(null);

  const [snapshot, setSnapshot] = useState<SimulationSnapshot | null>(null);
  const [engineState, setEngineState] = useState<SimulationEngineState | null>(
    null
  );
  const [warnings, setWarnings] = useState<string[]>([]);
  const [isPlaying, setIsPlaying] = useState(false);

  const engineRef = useRef<SimulationEngine | null>(null);
  const stopPlayback = useCallback(() => {
    setIsPlaying(false);
  }, []);

  useEffect(() => {
    let active = true;
    startTransition(() => {
      setLoading(Boolean(scenarioUrl));
      setError(null);
      setScenarioResult(null);
      setWarnings([]);
    });

    if (!scenarioUrl) {
      return () => {
        active = false;
      };
    }

    void loadScenarioFromUrl(scenarioUrl)
      .then((result) => {
        if (!active) {
          return;
        }
        setScenarioResult(result);
        setWarnings(result.warnings);
      })
      .catch((err: unknown) => {
        if (!active) {
          return;
        }
        setError(err instanceof Error ? err.message : "Failed to load scenario");
      })
      .finally(() => {
        if (active) {
          startTransition(() => {
            setLoading(false);
          });
        }
      });

    return () => {
      active = false;
    };
  }, [scenarioUrl]);

  useEffect(() => {
    if (!scenarioResult) {
      engineRef.current = null;
      startTransition(() => {
        setSnapshot(null);
        setEngineState(null);
        setIsPlaying(false);
      });
      return;
    }

    const engine = new SimulationEngine(scenarioResult.scenario);
    engineRef.current = engine;
    startTransition(() => {
      setSnapshot(engine.currentSnapshot);
      setEngineState(engine.getState());
      setIsPlaying(false);
    });
  }, [scenarioResult]);

  const syncEngineState = useCallback(() => {
    const engine = engineRef.current;
    if (!engine) {
      return;
    }

    setSnapshot(engine.currentSnapshot);
    setEngineState(engine.getState());
  }, []);

  useEffect(() => {
    if (!isPlaying) {
      return;
    }

    const engine = engineRef.current;
    if (!engine) {
      return;
    }

    const interval = window.setInterval(() => {
      const currentEngine = engineRef.current;
      if (!currentEngine) {
        startTransition(() => {
          stopPlayback();
        });
        return;
      }

      if (!currentEngine.canStepForward()) {
        stopPlayback();
        return;
      }

      currentEngine.stepForward();
      syncEngineState();
    }, PLAYBACK_INTERVAL_MS);

    return () => {
      window.clearInterval(interval);
    };
  }, [isPlaying, stopPlayback, syncEngineState]);

  const scenario = scenarioResult?.scenario ?? null;
  const totalSteps = engineState
    ? Math.max(engineState.frames.length - 1, 0)
    : 0;
  const canStepForward = engineState
    ? engineState.pointer < engineState.frames.length - 1
    : false;
  const canStepBackward = engineState ? engineState.pointer > 0 : false;
  const canRedo = canStepForward;
  const nextSnapshot =
    engineState && engineState.pointer < engineState.frames.length - 1
      ? engineState.frames[engineState.pointer + 1]
      : undefined;

  const handleStepForward = useCallback(() => {
    const engine = engineRef.current;
    if (!engine || !engine.canStepForward()) {
      return;
    }
    engine.stepForward();
    syncEngineState();
    if (!engine.canStepForward()) {
      stopPlayback();
    }
  }, [stopPlayback, syncEngineState]);

  const handleStepBackward = useCallback(() => {
    const engine = engineRef.current;
    if (!engine || !engine.canStepBackward()) {
      return;
    }
    engine.stepBackward();
    syncEngineState();
  }, [syncEngineState]);

  const handleUndo = useCallback(() => {
    const engine = engineRef.current;
    if (!engine) {
      return;
    }
    engine.undo();
    syncEngineState();
  }, [syncEngineState]);

  const handleRedo = useCallback(() => {
    const engine = engineRef.current;
    if (!engine) {
      return;
    }
    engine.redo();
    syncEngineState();
    if (!engine.canStepForward()) {
      stopPlayback();
    }
  }, [stopPlayback, syncEngineState]);

  const handleReset = useCallback(() => {
    const engine = engineRef.current;
    if (!engine) {
      return;
    }
    engine.reset();
    stopPlayback();
    syncEngineState();
  }, [stopPlayback, syncEngineState]);

  const handlePlay = useCallback(() => {
    const engine = engineRef.current;
    if (!engine) {
      return;
    }

    if (!engine.canStepForward()) {
      return;
    }

    setIsPlaying(true);
  }, []);

  const handlePause = useCallback(() => {
    stopPlayback();
  }, [stopPlayback]);

  const value = useMemo<SimulationContextValue>(() => {
    const baseValue: SimulationContextValue = {
      loading,
      error,
      warnings,
      scenario,
      snapshot,
      totalSteps,
      canStepForward,
      canStepBackward,
      canRedo,
      isPlaying,
      play: handlePlay,
      pause: handlePause,
      stepForward: handleStepForward,
      stepBackward: handleStepBackward,
      reset: handleReset,
      undo: handleUndo,
      redo: handleRedo,
    };

    if (nextSnapshot) {
      baseValue.nextSnapshot = nextSnapshot;
    }

    return baseValue;
  },
    [
      loading,
      error,
      warnings,
      scenario,
      snapshot,
      nextSnapshot,
      totalSteps,
      canStepForward,
      canStepBackward,
      canRedo,
      isPlaying,
      handlePlay,
      handlePause,
      handleStepForward,
      handleStepBackward,
      handleReset,
      handleUndo,
      handleRedo,
    ]
  );

  return (
    <SimulationContext.Provider value={value}>
      {children}
    </SimulationContext.Provider>
  );
}

export function useSimulation() {
  const context = useContext(SimulationContext);
  if (!context) {
    throw new Error("useSimulation must be used within SimulationProvider");
  }

  return context;
}
