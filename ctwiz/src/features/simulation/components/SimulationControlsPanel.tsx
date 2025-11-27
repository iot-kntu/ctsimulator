"use client";

import { useSimulation } from "../providers/SimulationProvider";

import { SimulationController } from "./SimulationController";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";

export function SimulationControlsPanel() {
  const {
    loading,
    error,
    scenario,
    snapshot,
    nextSnapshot,
    totalSteps,
    canStepForward,
    canStepBackward,
    canRedo,
    isPlaying,
    play,
    pause,
    stepForward,
    stepBackward,
    reset,
    undo,
    redo,
  } = useSimulation();

  if (loading) {
    return <StatusBanner title="Loading scenario" description="Preparing simulation frames…" />;
  }

  if (error) {
    return <StatusBanner title="Unable to load scenario" description={error} variant="destructive" />;
  }

  if (!scenario || !snapshot) {
    return (
      <StatusBanner
        title="No scenario selected"
        description="Choose a scenario from the library or import a YAML file to begin."
      />
    );
  }

  return (
    <SimulationController
      isPlaying={isPlaying}
      snapshot={snapshot}
      totalSteps={totalSteps}
      canStepForward={canStepForward}
      canStepBackward={canStepBackward}
      canRedo={canRedo}
      onPlay={play}
      onPause={pause}
      onStepForward={stepForward}
      onStepBackward={stepBackward}
      onReset={reset}
      onUndo={undo}
      onRedo={redo}
      {...(nextSnapshot ? { nextSnapshot } : {})}
    />
  );
}

type StatusBannerProps = {
  title: string;
  description: string;
  variant?: "default" | "destructive";
};

function StatusBanner({ title, description, variant = "default" }: StatusBannerProps) {
  return (
    <Alert variant={variant} className="text-sm">
      <AlertTitle>{title}</AlertTitle>
      <AlertDescription>{description}</AlertDescription>
    </Alert>
  );
}
