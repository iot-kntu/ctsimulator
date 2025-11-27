"use client";

import {
  ArrowLeftIcon,
  ArrowRightIcon,
  PauseIcon,
  PlayIcon,
  RotateCcw,
} from "lucide-react";
import { memo, useEffect } from "react";

import { Button } from "@/components/ui/button";
import { ButtonGroup } from "@/components/ui/button-group";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import type { SimulationSnapshot } from "@/lib/simulation-engine";
import { cn } from "@/lib/utils";

type SimulationControllerProps = {
  isPlaying: boolean;
  snapshot: SimulationSnapshot;
  nextSnapshot?: SimulationSnapshot;
  totalSteps: number;
  canStepForward: boolean;
  canStepBackward: boolean;
  canRedo: boolean;
  onPlay: () => void;
  onPause: () => void;
  onStepForward: () => void;
  onStepBackward: () => void;
  onReset: () => void;
  onUndo: () => void;
  onRedo: () => void;
};

function SimulationControllerComponent({
  isPlaying,
  snapshot,
  nextSnapshot,
  totalSteps,
  canStepForward,
  canStepBackward,
  onPlay,
  onPause,
  onStepForward,
  onStepBackward,
  onReset,
  onUndo,
  onRedo,
}: SimulationControllerProps) {
  useKeyboardShortcuts({
    isPlaying,
    canStepForward,
    canStepBackward,
    onPlay,
    onPause,
    onStepForward,
    onStepBackward,
    onUndo,
    onRedo,
    onReset,
  });

  const progress =
    totalSteps === 0 ? 0 : Math.min(1, snapshot.step / totalSteps);
  const isInitial = snapshot.roundIndex < 0 || snapshot.slotIndex < 0;
  const roundLabel = isInitial
    ? "Ready"
    : (snapshot.roundLabel ?? `Round ${snapshot.roundIndex + 1}`);
  const slotLabel = isInitial
    ? "Awaiting start"
    : `Slot ${snapshot.slotIndex + 1}`;
  const nodeChangeCount = Object.keys(snapshot.nodeStateChanges).length;
  const knowledgeChangeCount = Object.keys(
    snapshot.nodeKnowledgeChanges ?? {}
  ).length;

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex flex-col gap-1">
          <span className="text-xs font-semibold uppercase tracking-wide text-primary">
            {isInitial ? "Simulation" : `Round ${snapshot.roundIndex + 1}`}
          </span>
          <span className="text-sm font-semibold text-foreground">
            {roundLabel}
          </span>
          <span className="text-xs text-muted-foreground">{slotLabel}</span>
          <span className="text-[11px] font-mono text-muted-foreground">
            Step {snapshot.step}/{totalSteps} · nodes {nodeChangeCount} ·
            knowledge {knowledgeChangeCount} · events {snapshot.events.length} ·
            tx {snapshot.transmissions.length}
          </span>
        </div>
      </div>
      <div className="flex flex-col items-center gap-3 p-4">
        <ButtonGroup className="flex flex-wrap justify-center gap-2">
          <ButtonGroup className="hidden sm:flex">
            <Button
              variant="outline"
              size="icon"
              aria-label="Step back"
              onClick={onStepBackward}
              disabled={!canStepBackward}
            >
              <ArrowLeftIcon className="h-4 w-4" />
            </Button>
          </ButtonGroup>
          <ButtonGroup>
            <Button
              onClick={isPlaying ? onPause : onPlay}
              disabled={!isPlaying && !canStepForward}
            >
              {isPlaying ? (
                <>
                  <PauseIcon className="h-4 w-4" /> Pause
                </>
              ) : (
                <>
                  <PlayIcon className="h-4 w-4" /> Play
                </>
              )}
            </Button>
            <Button
              variant="outline"
              onClick={onReset}
              disabled={isPlaying && !canStepBackward}
            >
              <RotateCcw className="h-4 w-4" /> Reset
            </Button>
          </ButtonGroup>
          <ButtonGroup className="hidden sm:flex">
            <Button
              variant="outline"
              size="icon"
              aria-label="Step forward"
              onClick={onStepForward}
              disabled={!canStepForward}
            >
              <ArrowRightIcon className="h-4 w-4" />
            </Button>
          </ButtonGroup>
        </ButtonGroup>
        <Progress value={progress * 100} className="h-2 w-full" />
      </div>
      <SlotDetails
        frame={snapshot}
        {...(nextSnapshot ? { nextFrame: nextSnapshot } : {})}
      />
    </div>
  );
}

export const SimulationController = memo(SimulationControllerComponent);

type UseKeyboardShortcuts = {
  isPlaying: boolean;
  canStepForward: boolean;
  canStepBackward: boolean;
  onPlay: () => void;
  onPause: () => void;
  onStepForward: () => void;
  onStepBackward: () => void;
  onUndo: () => void;
  onRedo: () => void;
  onReset: () => void;
};

function useKeyboardShortcuts({
  isPlaying,
  canStepForward,
  canStepBackward,
  onPlay,
  onPause,
  onStepForward,
  onStepBackward,
  onUndo,
  onRedo,
  onReset,
}: UseKeyboardShortcuts) {
  useEffect(() => {
    function handleKey(event: KeyboardEvent) {
      if (
        event.target instanceof HTMLElement &&
        (event.target.tagName === "INPUT" ||
          event.target.tagName === "TEXTAREA" ||
          event.target.isContentEditable)
      ) {
        return;
      }

      if (event.code === "Space") {
        event.preventDefault();
        if (isPlaying) {
          onPause();
        } else if (canStepForward) {
          onPlay();
        }
      } else if (event.key === "ArrowRight") {
        event.preventDefault();
        if (canStepForward) {
          onStepForward();
        }
      } else if (event.key === "ArrowLeft") {
        event.preventDefault();
        if (canStepBackward) {
          onStepBackward();
        }
      } else if (
        (event.metaKey || event.ctrlKey) &&
        !event.shiftKey &&
        event.key.toLowerCase() === "z"
      ) {
        event.preventDefault();
        onUndo();
      } else if (
        (event.metaKey || event.ctrlKey) &&
        event.shiftKey &&
        event.key.toLowerCase() === "z"
      ) {
        event.preventDefault();
        onRedo();
      } else if (event.key.toLowerCase() === "r") {
        event.preventDefault();
        onReset();
      }
    }

    window.addEventListener("keydown", handleKey);
    return () => window.removeEventListener("keydown", handleKey);
  }, [
    canStepBackward,
    canStepForward,
    isPlaying,
    onPause,
    onPlay,
    onRedo,
    onReset,
    onStepBackward,
    onStepForward,
    onUndo,
  ]);
}

type SlotDetailsProps = {
  frame: SimulationSnapshot;
  nextFrame?: SimulationSnapshot;
};

function SlotDetails({ frame, nextFrame }: SlotDetailsProps) {
  const hasCurrent = frame.roundIndex >= 0 && frame.slotIndex >= 0;
  const hasNext =
    nextFrame && nextFrame.roundIndex >= 0 && nextFrame.slotIndex >= 0;

  if (!hasCurrent && !hasNext) {
    return (
      <Card>
        <CardContent className="p-4 text-sm text-muted-foreground">
          Load a scenario to begin stepping through rounds and slots.
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="grid gap-3">
      <SlotCard
        title="Current Slot"
        {...(hasCurrent ? { frame } : {})}
        placeholder="Awaiting slot execution."
      />
      <SlotCard
        title="Next Slot"
        {...(hasNext && nextFrame ? { frame: nextFrame } : {})}
        placeholder="Simulation complete."
      />
    </div>
  );
}

type SlotCardProps = {
  title: string;
  frame?: SimulationSnapshot;
  placeholder: string;
};

function SlotCard({ title, frame, placeholder }: SlotCardProps) {
  return (
    <Card className={cn("py-4 gap-0 rounded-lg")}>
      <CardHeader className="px-4">
        <CardTitle className="text-xs font-semibold uppercase tracking-wide text-primary">
          {title}
        </CardTitle>
      </CardHeader>
      <CardContent className="px-4 space-y-2 pt-0 text-sm">
        {frame ? (
          <>
            <div className="flex flex-col">
              <span className="text-sm font-semibold text-foreground">
                Round {frame.roundIndex + 1}
                {frame.roundLabel ? ` · ${frame.roundLabel}` : ""}
              </span>
              <span className="text-xs text-muted-foreground">
                Slot {frame.slotIndex + 1}
                {frame.slotLabel ? ` · ${frame.slotLabel}` : ""}
              </span>
            </div>
            {frame.slotDescription ? (
              <p className="text-xs text-muted-foreground">
                {frame.slotDescription}
              </p>
            ) : null}
            {Object.keys(frame.nodeStateChanges ?? {}).length > 0 ? (
              <div className="text-xs">
                <p className="font-semibold text-foreground">Node states</p>
                <ul className="mt-1 space-y-1">
                  {Object.entries(frame.nodeStateChanges).map(
                    ([nodeId, state]) => (
                      <li
                        key={nodeId}
                        className="flex items-center justify-between rounded-md bg-muted/40 px-2 py-1 text-muted-foreground"
                      >
                        <span className="font-medium">{nodeId}</span>
                        <span className="font-mono text-[11px] uppercase tracking-wide">
                          {state}
                        </span>
                      </li>
                    )
                  )}
                </ul>
              </div>
            ) : null}
            <SlotEvents events={frame.events} />
          </>
        ) : (
          <span className="text-xs text-muted-foreground">{placeholder}</span>
        )}
      </CardContent>
    </Card>
  );
}

type SlotEventsProps = {
  events: SimulationSnapshot["events"];
};

function SlotEvents({ events }: SlotEventsProps) {
  if (!events.length) {
    return (
      <p className="text-xs text-muted-foreground">
        No events defined for this slot.
      </p>
    );
  }

  return (
    <div className="text-xs">
      <p className="font-semibold text-foreground">Events</p>
      <ul className="mt-1 space-y-1">
        {events.map((event) => (
          <li
            key={event.id}
            className="rounded-md border border-border/60 px-2 py-1"
          >
            <p className="text-xs font-semibold text-foreground">
              {event.title ?? event.type}
            </p>
            {event.description ? (
              <p className="text-[11px] text-muted-foreground">
                {event.description}
              </p>
            ) : null}
            <EventMeta event={event} />
          </li>
        ))}
      </ul>
    </div>
  );
}

type EventMetaProps = {
  event: SimulationSnapshot["events"][number];
};

function EventMeta({ event }: EventMetaProps) {
  const lines: string[] = [];

  if (event.from !== undefined && event.to !== undefined) {
    const status =
      event.success === false
        ? "failed"
        : event.success
          ? "delivered"
          : "pending";
    lines.push(`${event.from} → ${event.to} · ${status}`);
  }

  if (event.content) {
    lines.push(`payload: ${event.content}`);
  }

  if (event.meta && Object.keys(event.meta).length > 0) {
    lines.push(`meta: ${JSON.stringify(event.meta)}`);
  }

  if (!lines.length) {
    return null;
  }

  return (
    <ul className="mt-1 space-y-0.5 text-[11px] text-muted-foreground">
      {lines.map((line) => (
        <li key={`${event.id}-${line}`}>{line}</li>
      ))}
    </ul>
  );
}
