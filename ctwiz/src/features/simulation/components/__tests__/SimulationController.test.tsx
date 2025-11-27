import { render, screen } from "@testing-library/react";

import { SimulationController } from "../SimulationController";

import type { SimulationSnapshot } from "@/lib/simulation-engine";

const baseSnapshot: SimulationSnapshot = {
  step: 1,
  roundIndex: 0,
  slotIndex: 0,
  roundId: "round-1",
  roundLabel: "Round 1",
  roundDescription: "demo round",
  slotId: "round-1-slot-1",
  slotLabel: "Slot 1",
  slotDescription: "demo slot",
  nodeStates: { 1: "idle" },
  nodeStateChanges: { 1: "alert" },
  highlightedNodes: [1],
  transmissions: [],
  events: [
    {
      id: "evt-1",
      type: "tx",
      from: 1,
      to: 2,
      title: "Ping",
      description: "Sends ping",
      success: true,
    },
  ],
};

function createSnapshot(overrides: Partial<SimulationSnapshot> = {}): SimulationSnapshot {
  return { ...baseSnapshot, ...overrides };
}

describe("SimulationController", () => {
  it("renders the current snapshot details and enables controls", () => {
    const handlers = {
      onPlay: vi.fn(),
      onPause: vi.fn(),
      onStepForward: vi.fn(),
      onStepBackward: vi.fn(),
      onReset: vi.fn(),
      onUndo: vi.fn(),
      onRedo: vi.fn(),
    };

    render(
      <SimulationController
        isPlaying={false}
        snapshot={createSnapshot()}
        totalSteps={10}
        canStepForward
        canStepBackward={false}
        canRedo={false}
        {...handlers}
      />,
    );

    expect(screen.getAllByText("Round 1").length).toBeGreaterThan(0);
    expect(screen.getByText("Slot 1")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /play/i })).toBeEnabled();
    expect(screen.getByText(/Node states/i)).toBeInTheDocument();
    expect(screen.getAllByText(/Events/i).length).toBeGreaterThan(0);
    expect(screen.getByRole("progressbar")).toBeInTheDocument();
  });

  it("shows fallback text when there are no frames yet", () => {
    render(
      <SimulationController
        isPlaying={false}
        snapshot={createSnapshot({ roundIndex: -1, slotIndex: -1 })}
        totalSteps={0}
        canStepForward={false}
        canStepBackward={false}
        canRedo={false}
        onPlay={vi.fn()}
        onPause={vi.fn()}
        onStepForward={vi.fn()}
        onStepBackward={vi.fn()}
        onReset={vi.fn()}
        onUndo={vi.fn()}
        onRedo={vi.fn()}
      />,
    );

    expect(screen.getByText(/Load a scenario/i)).toBeInTheDocument();
  });
});
