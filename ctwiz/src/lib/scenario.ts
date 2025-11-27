import { parse } from "yaml";
import { z } from "zod";

const POSITION_SCALE = 50;
const FAILURE_STATES = ["fail", "failed", "failure", "error", "timeout", "offline"];
const SUCCESS_STATES = ["success", "ok", "delivered", "ready"];

const nodePositionSchema = z.object({
  x: z.number(),
  y: z.number(),
});

const nodeIdSchema = z
  .preprocess((value) => {
    if (typeof value === "string") {
      const trimmed = value.trim();
      return trimmed.length === 0 ? undefined : Number(trimmed);
    }
    return value;
  }, z.number())
  .optional();

const scenarioNodeSchema = z.object({
  id: z.number(),
  label: z.string().optional(),
  position: nodePositionSchema.optional(),
  meta: z.record(z.string(), z.unknown()).optional(),
});

const scenarioLinkSchema = z.object({
  id: z.string().optional(),
  source: z.number(),
  target: z.number(),
  label: z.string().optional(),
  meta: z.record(z.string(), z.unknown()).optional(),
});

const eventPacketSchema = z.record(z.string(), z.unknown()).optional();

const scenarioSlotEventSchema = z.object({
  id: z.string().optional(),
  type: z.string(),
  title: z.string().optional(),
  description: z.string().optional(),
  from: nodeIdSchema,
  to: nodeIdSchema,
  state: z.string().optional(),
  content: z.string().optional(),
  success: z.boolean().optional(),
  direction: z.string().optional(),
  packet: eventPacketSchema,
  meta: z.record(z.string(), z.unknown()).optional(),
});

const nodeStatesSchema = z.record(z.union([z.string(), z.number()]), z.string());

const scenarioSlotSchema = z.object({
  id: z.number(),
  label: z.string().optional(),
  description: z.string().optional(),
  duration: z.number().optional(),
  nodeStates: nodeStatesSchema.optional(),
  events: z.array(scenarioSlotEventSchema).optional(),
});

const scenarioRoundSchema = z.object({
  id: z.number(),
  label: z.string().optional(),
  description: z.string().optional(),
  slots: z.array(scenarioSlotSchema).min(1),
});

const scenarioSchema = z.object({
  id: z.string().optional(),
  name: z.string().optional(),
  metadata: z.record(z.string(), z.unknown()).optional(),
  nodes: z.array(scenarioNodeSchema).min(1),
  links: z.array(scenarioLinkSchema).optional(),
  rounds: z.array(scenarioRoundSchema).min(1),
});

// eslint-disable-next-line @typescript-eslint/no-unused-vars
const KNOWN_STATUSES = [
  "idle",
  "listen",
  "flood",
  "sleep",
  "standby",
  "transmitting",
  "receiving",
  "forwarding",
  "alert",
  "error",
] as const;

export type NodeStatus = (typeof KNOWN_STATUSES)[number] | (string & {});

export type ScenarioNode = {
  id: number;
  label?: string;
  position: {
    x: number;
    y: number;
  };
  meta?: Record<string, unknown>;
};

export type ScenarioLink = {
  id?: string;
  source: number;
  target: number;
  label?: string;
  meta?: Record<string, unknown>;
};

export type ScenarioEventPacket = {
  time?: number;
  initiatorId?: number;
  messageNo?: number;
  content?: string;
  [key: string]: unknown;
};

export type ScenarioSlotEvent = {
  id: string;
  type: string;
  title?: string;
  description?: string;
  from?: number;
  to?: number;
  state?: NodeStatus;
  content?: string;
  success?: boolean;
  direction?: "send" | "receive" | "broadcast" | "forward" | string;
  packet?: ScenarioEventPacket;
  meta?: Record<string, unknown>;
};

export type ScenarioSlot = {
  id: number;
  index: number;
  label?: string;
  description?: string;
  duration?: number;
  nodeStates?: Record<number, NodeStatus>;
  events: ScenarioSlotEvent[];
};

export type ScenarioRound = {
  id: number;
  index: number;
  label?: string;
  description?: string;
  slots: ScenarioSlot[];
};

export type Scenario = {
  id: string;
  name: string;
  metadata?: Record<string, unknown>;
  nodes: ScenarioNode[];
  links: ScenarioLink[];
  rounds: ScenarioRound[];
};

export type ScenarioLoadResult = {
  scenario: Scenario;
  warnings: string[];
};

export type ScenarioRaw = z.input<typeof scenarioSchema>;

export function normalizeScenario(raw: ScenarioRaw, sourceId = "scenario"): ScenarioLoadResult {
  const parsedResult = scenarioSchema.safeParse(raw);
  if (!parsedResult.success) {
    const message = parsedResult.error.issues.map((issue) => issue.message).join("; ");
    throw new Error(`Invalid scenario "${sourceId}": ${message}`);
  }

  const warnings: string[] = [];
  const parsed = parsedResult.data;
  const nodes = normalizeNodes(parsed.nodes, warnings);
  const nodeLookup = new Set(nodes.map((node) => node.id));
  const links = normalizeLinks(parsed.links ?? [], nodeLookup, warnings);
  const rounds = normalizeRounds(parsed.rounds, nodeLookup, warnings);

  const scenarioId = parsed.id ?? sourceId;
  const scenarioName = parsed.name ?? scenarioId;
  const normalizedScenario: Scenario = {
    id: scenarioId,
    name: scenarioName,
    nodes,
    links,
    rounds,
  };

  if (parsed.metadata) {
    normalizedScenario.metadata = parsed.metadata;
  }

  return {
    scenario: normalizedScenario,
    warnings,
  };
}

export function parseScenarioYaml(yamlText: string, sourceId = "scenario"): ScenarioLoadResult {
  const raw = parse(yamlText) as ScenarioRaw;
  return normalizeScenario(raw, sourceId);
}

export async function loadScenarioFromUrl(url: string): Promise<ScenarioLoadResult> {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`Failed to load scenario from ${url}: ${response.status} ${response.statusText}`);
  }

  const content = await response.text();
  return parseScenarioYaml(content, url);
}

function normalizeNodes(nodes: z.infer<typeof scenarioNodeSchema>[], warnings: string[]): ScenarioNode[] {
  const seen = new Set<number>();
  return nodes.map((node, index) => {
    if (seen.has(node.id)) {
      throw new Error(`Duplicate node id "${node.id}" detected at index ${index}.`);
    }
    seen.add(node.id);

    if (!node.position) {
      warnings.push(`Node "${node.id}" is missing a position. Using {x:0,y:0}.`);
    }

    const normalized: ScenarioNode = {
      id: node.id,
      position: node.position ? scalePosition(node.position) : { x: 0, y: 0 },
    };

    if (typeof node.label === "string") {
      normalized.label = node.label;
    }
    if (node.meta) {
      normalized.meta = node.meta;
    }

    return normalized;
  });
}

function normalizeLinks(
  links: z.infer<typeof scenarioLinkSchema>[],
  nodes: Set<number>,
  warnings: string[],
): ScenarioLink[] {
  return links.map((link, index) => {
    if (!nodes.has(link.source)) {
      warnings.push(`Link ${index} references unknown source node "${link.source}".`);
    }
    if (!nodes.has(link.target)) {
      warnings.push(`Link ${index} references unknown target node "${link.target}".`);
    }

    const normalized: ScenarioLink = {
      source: link.source,
      target: link.target,
    };

    if (link.id) {
      normalized.id = link.id;
    }
    if (typeof link.label === "string") {
      normalized.label = link.label;
    }
    if (link.meta) {
      normalized.meta = link.meta;
    }

    return normalized;
  });
}

function normalizeRounds(
  rounds: z.infer<typeof scenarioRoundSchema>[],
  nodes: Set<number>,
  warnings: string[],
): ScenarioRound[] {
  return rounds.map((round, roundIndex) => {
    const roundId = round.id ?? `round-${roundIndex + 1}`;
    const slots = round.slots.map((slot, slotIndex) => {
      const nodeStates = normalizeNodeStates(slot.nodeStates, slot.id, nodes, warnings);
      const events = normalizeEvents(slot.events ?? [], slot.id, slotIndex, nodeStates, nodes, warnings);

      const normalizedSlot: ScenarioSlot = {
        id: slot.id,
        index: slotIndex,
        events,
      };

      if (nodeStates) {
        normalizedSlot.nodeStates = nodeStates;
      }

      if (slot.label) {
        normalizedSlot.label = slot.label;
      }
      if (slot.description) {
        normalizedSlot.description = slot.description;
      }
      if (typeof slot.duration === "number") {
        normalizedSlot.duration = slot.duration;
      }

      return normalizedSlot;
    });

    const normalizedRound: ScenarioRound = {
      id: roundId,
      index: roundIndex,
      label: round.label ?? `Round ${roundIndex + 1}`,
      slots,
    };

    if (round.description) {
      normalizedRound.description = round.description;
    }

    return normalizedRound;
  });
}

function normalizeNodeStates(
  input: Record<string, string> | undefined,
  slotId: number,
  nodes: Set<number>,
  warnings: string[],
): Record<number, NodeStatus> | undefined {
  if (!input) {
    return undefined;
  }

  const normalized: Record<number, NodeStatus> = {};
  Object.entries(input).forEach(([rawId, rawStatus]) => {
    const nodeId = Number(rawId);
    if (!Number.isFinite(nodeId)) {
      warnings.push(`Slot "${slotId}" has invalid node id "${rawId}" in nodeStates.`);
      return;
    }
    if (!nodes.has(nodeId)) {
      warnings.push(`Slot "${slotId}" references unknown node "${rawId}" in nodeStates.`);
      return;
    }
    normalized[nodeId] = normalizeStatus(rawStatus);
  });

  return Object.keys(normalized).length ? normalized : undefined;
}

function normalizeEvents(
  events: z.infer<typeof scenarioSlotEventSchema>[],
  slotId: number,
  slotIndex: number,
  nodeStates: Record<number, NodeStatus> | undefined,
  nodes: Set<number>,
  warnings: string[],
): ScenarioSlotEvent[] {
  return events.map((event, index) => {
    const eventId = event.id ?? `${slotId}-event-${slotIndex + 1}-${index + 1}`;

    if (event.from !== undefined && !nodes.has(event.from)) {
      warnings.push(`Event "${eventId}" references unknown source node "${event.from}".`);
    }
    if (event.to !== undefined && !nodes.has(event.to)) {
      warnings.push(`Event "${eventId}" references unknown target node "${event.to}".`);
    }

    const inferredSuccess =
      event.success ??
      inferStateOutcome(event.to !== undefined ? nodeStates?.[event.to] : undefined) ??
      inferStateOutcome(event.from !== undefined ? nodeStates?.[event.from] : undefined);

    const normalizedEvent: ScenarioSlotEvent = {
      id: eventId,
      type: event.type,
    };

    if (event.title) {
      normalizedEvent.title = event.title;
    }
    if (event.description) {
      normalizedEvent.description = event.description;
    }
    if (event.from !== undefined) {
      normalizedEvent.from = event.from;
    }
    if (event.to !== undefined) {
      normalizedEvent.to = event.to;
    }
    if (event.state) {
      normalizedEvent.state = normalizeStatus(event.state);
    }
    const content = event.content ?? (event.packet?.content as string | undefined);
    if (content) {
      normalizedEvent.content = content;
    }
    if (inferredSuccess !== undefined) {
      normalizedEvent.success = inferredSuccess;
    }
    if (event.direction) {
      normalizedEvent.direction = event.direction;
    }
    if (event.packet) {
      normalizedEvent.packet = event.packet as ScenarioEventPacket;
    }
    if (event.meta) {
      normalizedEvent.meta = event.meta;
    }

    return normalizedEvent;
  });
}

function normalizeStatus(status?: string): NodeStatus {
  if (!status) {
    return "idle";
  }
  return status.trim();
}

function inferStateOutcome(state?: NodeStatus): boolean | undefined {
  if (!state) {
    return undefined;
  }

  const normalized = state.toString().trim().toLowerCase();
  if (FAILURE_STATES.includes(normalized)) {
    return false;
  }
  if (SUCCESS_STATES.includes(normalized)) {
    return true;
  }

  return undefined;
}

function scalePosition(position: { x: number; y: number }) {
  return {
    x: position.x * POSITION_SCALE,
    y: position.y * POSITION_SCALE,
  };
}
