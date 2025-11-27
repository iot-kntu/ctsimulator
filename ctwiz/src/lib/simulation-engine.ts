import type {
  NodeStatus,
  Scenario,
  ScenarioRound,
  ScenarioSlot,
  ScenarioSlotEvent,
} from "./scenario";

export type TransmissionEvent = {
  id: string;
  type: string;
  source: number;
  target: number;
  success?: boolean;
  title?: string;
  description?: string;
  payload?: string;
};

export type SimulationFrame = {
  step: number;
  roundIndex: number;
  slotIndex: number;
  roundId?: number;
  roundLabel?: string;
  roundDescription?: string;
  slotId?: number;
  slotLabel?: string;
  slotDescription?: string;
  nodeStates: Record<number, NodeStatus>;
  nodeStateChanges: Record<number, NodeStatus>;
  highlightedNodes: number[];
  transmissions: TransmissionEvent[];
  events: ScenarioSlotEvent[];
};

export type SimulationSnapshot = SimulationFrame;

export type SimulationEngineState = {
  scenario: Scenario;
  frames: SimulationFrame[];
  pointer: number;
};

export class SimulationEngine {
  private readonly scenario: Scenario;
  private readonly frames: SimulationFrame[];
  private pointer = 0;

  constructor(scenario: Scenario) {
    this.scenario = scenario;
    this.frames = buildSimulationFrames(scenario);
  }

  getState(): SimulationEngineState {
    return {
      scenario: this.scenario,
      frames: this.frames,
      pointer: this.pointer,
    };
  }

  get currentSnapshot(): SimulationSnapshot {
    return this.frames[this.pointer]!;
  }

  get nextSnapshot(): SimulationSnapshot | undefined {
    return this.frames[this.pointer + 1];
  }

  canStepForward(): boolean {
    return this.pointer < this.frames.length - 1;
  }

  canStepBackward(): boolean {
    return this.pointer > 0;
  }

  stepForward(): SimulationSnapshot {
    if (this.canStepForward()) {
      this.pointer += 1;
    }
    return this.currentSnapshot;
  }

  stepBackward(): SimulationSnapshot {
    if (this.canStepBackward()) {
      this.pointer -= 1;
    }
    return this.currentSnapshot;
  }

  undo(): SimulationSnapshot {
    return this.stepBackward();
  }

  redo(): SimulationSnapshot {
    return this.stepForward();
  }

  reset(): SimulationSnapshot {
    this.pointer = 0;
    return this.currentSnapshot;
  }

  seek(step: number): SimulationSnapshot {
    const clamped = Math.max(0, Math.min(step, this.frames.length - 1));
    this.pointer = clamped;
    return this.currentSnapshot;
  }

  exportSnapshots(): SimulationSnapshot[] {
    return [...this.frames];
  }
}

function buildSimulationFrames(scenario: Scenario): SimulationFrame[] {
  const frames: SimulationFrame[] = [];
  const baseNodeStates: Record<number, NodeStatus> = {};
  scenario.nodes.forEach((node) => {
    baseNodeStates[node.id] = "idle";
  });

  const initialFrame: SimulationFrame = {
    step: 0,
    roundIndex: -1,
    slotIndex: -1,
    nodeStates: { ...baseNodeStates },
    nodeStateChanges: {},
    highlightedNodes: [],
    transmissions: [],
    events: [],
    roundLabel: "Ready",
    slotLabel: "Initial state",
  };

  if (typeof scenario.metadata?.description === "string") {
    initialFrame.roundDescription = scenario.metadata.description;
  }

  frames.push(initialFrame);

  let step = 1;
  let currentNodeStates = { ...baseNodeStates };

  scenario.rounds.forEach((round, roundIndex) => {
    appendRoundFrames({
      frames,
      round,
      roundIndex,
      stepState: { step, currentNodeStates },
    });
    const lastFrame = frames[frames.length - 1];
    if (lastFrame) {
      step = lastFrame.step + 1;
      currentNodeStates = {
        ...lastFrame.nodeStates,
      };
    }
  });

  // Correct step numbers after building frames so they are contiguous.
  frames.forEach((frame, idx) => {
    frame.step = idx;
  });

  return frames;
}

type AppendRoundFramesArgs = {
  frames: SimulationFrame[];
  round: ScenarioRound;
  roundIndex: number;
  stepState: {
    step: number;
    currentNodeStates: Record<number, NodeStatus>;
  };
};

function appendRoundFrames({ frames, round, roundIndex, stepState }: AppendRoundFramesArgs) {
  let { currentNodeStates } = stepState;

  round.slots.forEach((slot, slotIndex) => {
    const { updatedStates, changes: nodeStateChanges, highlightedNodes } = deriveSlotNodeStates(currentNodeStates, slot);

    currentNodeStates = updatedStates;
    const transmissions = deriveSlotTransmissions(slot);

    transmissions.forEach((tx) => {
      highlightedNodes.add(tx.source);
      highlightedNodes.add(tx.target);
    });

    const frame: SimulationFrame = {
      step: 0, // temporary, reassigned later
      roundIndex,
      slotIndex,
      roundLabel: round.label ?? `Round ${roundIndex + 1}`,
      slotLabel: slot.label ?? `Slot ${slotIndex + 1}`,
      nodeStates: updatedStates,
      nodeStateChanges,
      highlightedNodes: Array.from(highlightedNodes),
      transmissions,
      events: slot.events,
    };

    if (round.id) {
      frame.roundId = round.id;
    }
    if (round.description) {
      frame.roundDescription = round.description;
    }
    if (slot.id) {
      frame.slotId = slot.id;
    }
    if (slot.description) {
      frame.slotDescription = slot.description;
    }

    frames.push(frame);
  });
}

function deriveSlotNodeStates(
  previousStates: Record<number, NodeStatus>,
  slot: ScenarioSlot,
): {
  updatedStates: Record<number, NodeStatus>;
  changes: Record<number, NodeStatus>;
  highlightedNodes: Set<number>;
} {
  const updatedStates = { ...previousStates };
  const changes: Record<number, NodeStatus> = {};
  const highlightedNodes = new Set<number>();

  if (slot.nodeStates) {
    Object.entries(slot.nodeStates).forEach(([nodeId, state]) => {
      const numericId = Number(nodeId);
      if (updatedStates[numericId] !== state) {
        updatedStates[numericId] = state;
        changes[numericId] = state;
        highlightedNodes.add(numericId);
      }
    });
  }

  return {
    updatedStates,
    changes,
    highlightedNodes,
  };
}

function deriveSlotTransmissions(slot: ScenarioSlot): TransmissionEvent[] {
  return slot.events
    .filter((event) => event.from !== undefined && event.to !== undefined)
    .map((event) => {
      const transmission: TransmissionEvent = {
        id: event.id,
        type: event.type,
        source: event.from as number,
        target: event.to as number,
      };

      if (event.success !== undefined) {
        transmission.success = event.success;
      }
      if (event.title) {
        transmission.title = event.title;
      }
      if (event.description) {
        transmission.description = event.description;
      }
      if (event.content) {
        transmission.payload = event.content;
      }

      return transmission;
    });
}
