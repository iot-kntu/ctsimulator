"use client";

import dynamic from "next/dynamic";
import { memo } from "react";

import { useSimulation } from "../providers/SimulationProvider";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Spinner } from "@/components/ui/spinner";
import { cn } from "@/lib/utils";

const GraphView = dynamic(() => import("./GraphView").then((mod) => mod.GraphView), {
  ssr: false,
  loading: () => (
    <div className="flex h-full w-full items-center justify-center">
      <Spinner className="size-6 text-primary" />
    </div>
  ),
});

type SimulationViewportProps = {
  className?: string;
};

export function SimulationViewport({ className }: SimulationViewportProps) {
  const { loading, error, scenario, snapshot, warnings } = useSimulation();

  if (loading) {
    return (
      <div className="flex h-full w-full items-center justify-center">
        <Spinner className="size-6 text-primary" />
      </div>
    );
  }

  if (error) {
    return (
      <Alert variant="destructive" className="m-4 max-w-md">
        <AlertTitle>Failed to load scenario</AlertTitle>
        <AlertDescription>{error}</AlertDescription>
      </Alert>
    );
  }

  if (!scenario || !snapshot) {
    return (
      <div className="flex h-full items-center justify-center text-sm text-muted-foreground">
        Select a scenario to begin.
      </div>
    );
  }

  return (
    <div className={cn("relative flex h-full w-full flex-col overflow-hidden", className)}>
      <GraphView scenario={scenario} snapshot={snapshot} className="h-full w-full bg-transparent" />
      {warnings.length > 0 ? (
        <div className="pointer-events-none absolute left-4 top-4 max-w-sm">
          <WarningBanner warnings={warnings} />
        </div>
      ) : null}
    </div>
  );
}

const WarningBanner = memo(function WarningBanner({ warnings }: { warnings: string[] }) {
  return (
    <Alert className="pointer-events-auto bg-amber-50 text-amber-900 shadow-lg dark:bg-amber-950">
      <AlertTitle className="text-sm font-semibold">Scenario warnings</AlertTitle>
      <AlertDescription>
        <ul className="mt-2 list-disc space-y-1 pl-4 text-xs">
          {warnings.map((warning) => (
            <li key={warning}>{warning}</li>
          ))}
        </ul>
      </AlertDescription>
    </Alert>
  );
});
