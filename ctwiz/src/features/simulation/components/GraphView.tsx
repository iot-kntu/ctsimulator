"use client";

import { motion } from "motion/react";
import { memo, useCallback, useEffect, useMemo, useRef, useState, useId } from "react";

import type { Scenario, ScenarioLink, ScenarioNode } from "@/lib/scenario";
import type { SimulationSnapshot } from "@/lib/simulation-engine";
import { cn } from "@/lib/utils";

type GraphViewProps = {
  scenario: Scenario;
  snapshot: SimulationSnapshot;
  className?: string;
};

type PositionedLink = ScenarioLink & {
  sourceNode?: ScenarioNode;
  targetNode?: ScenarioNode;
};

const NODE_RADIUS = 18;

type NodeVisualStyle = {
  fill: string;
  stroke: string;
  aura: string;
  text: string;
};

const IDLE_NODE_STYLE: NodeVisualStyle = {
  fill: '#f4f4f5',
  stroke: '#71717a',
  aura: 'rgba(113,113,122,0.18)',
  text: '#0f172a',
};

const PRESET_STATE_OVERRIDES: Record<string, NodeVisualStyle> = {
  idle: IDLE_NODE_STYLE,
  alert: {
    fill: '#fdf4c4',
    stroke: '#d97706',
    aura: 'rgba(217,119,6,0.28)',
    text: '#7c2d12',
  },
  error: {
    fill: '#fee2e2',
    stroke: '#b91c1c',
    aura: 'rgba(185,28,28,0.32)',
    text: '#7f1d1d',
  },
};

const COLOR_PALETTE: NodeVisualStyle[] = [
  { fill: '#bfdbfe', stroke: '#1d4ed8', aura: 'rgba(29,78,216,0.25)', text: '#0f172a' },
  { fill: '#bbf7d0', stroke: '#047857', aura: 'rgba(4,120,87,0.25)', text: '#064e3b' },
  { fill: '#fde68a', stroke: '#b45309', aura: 'rgba(180,83,9,0.25)', text: '#78350f' },
  { fill: '#ddd6fe', stroke: '#6d28d9', aura: 'rgba(109,40,217,0.25)', text: '#4c1d95' },
  { fill: '#bae6fd', stroke: '#0369a1', aura: 'rgba(3,105,161,0.25)', text: '#0c4a6e' },
  { fill: '#fbcfe8', stroke: '#db2777', aura: 'rgba(219,39,119,0.25)', text: '#831843' },
  { fill: '#ccfbf1', stroke: '#0f766e', aura: 'rgba(15,118,110,0.25)', text: '#115e59' },
  { fill: '#e0e7ff', stroke: '#4338ca', aura: 'rgba(67,56,202,0.25)', text: '#312e81' },
  { fill: '#fef3c7', stroke: '#d97706', aura: 'rgba(217,119,6,0.25)', text: '#92400e' },
  { fill: '#fcd5ce', stroke: '#b91d47', aura: 'rgba(185,29,71,0.25)', text: '#7a162f' },
  { fill: '#fde2ff', stroke: '#a855f7', aura: 'rgba(168,85,247,0.25)', text: '#6b21a8' },
];

const LINK_STATE_COLORS: Record<string, string> = {
  idle: '#d4d4d8',
  active: '#6366f1',
};

const TRANSMISSION_COLORS = {
  success: '#22c55e',
  default: '#2563eb',
  failure: '#ef4444',
};

type ViewBox = {
  x: number;
  y: number;
  width: number;
  height: number;
};

const PATTERN_BASE_SIZE = 48;
const ZOOM_FACTOR = 1.1;
const MIN_ZOOM = 0.4;
const MAX_ZOOM = 4;

export const GraphView = memo(function GraphView({ scenario, snapshot, className }: GraphViewProps) {
  const svgRef = useRef<SVGSVGElement | null>(null);
  const patternId = useId();
  const [patternSize, setPatternSize] = useState({ width: PATTERN_BASE_SIZE, height: PATTERN_BASE_SIZE });
  const [viewportAspectRatio, setViewportAspectRatio] = useState<number | null>(null);
  const viewBoxRef = useRef<ViewBox | null>(null);
  const svgSizeRef = useRef<{ width: number; height: number } | null>(null);
  const nodePositions = useMemo(() => {
    const map = new Map<number, ScenarioNode>();
    scenario.nodes.forEach((node) => {
      map.set(node.id, node);
    });
    return map;
  }, [scenario.nodes]);

  const positionedLinks: PositionedLink[] = useMemo(
    () =>
      scenario.links.map((link) => {
        const enriched: PositionedLink = { ...link };
        const sourceNode = nodePositions.get(link.source);
        if (sourceNode) {
          enriched.sourceNode = sourceNode;
        }
        const targetNode = nodePositions.get(link.target);
        if (targetNode) {
          enriched.targetNode = targetNode;
        }
        return enriched;
      }),
    [scenario.links, nodePositions],
  );

  const stateStyles = useMemo(() => createStateStyleMap(scenario), [scenario]);

  const baseViewBox = useMemo(
    () => calculateViewBox(scenario.nodes, viewportAspectRatio ?? undefined),
    [scenario.nodes, viewportAspectRatio],
  );
  const highlightedNodes = useMemo(() => {
    const set = new Set(snapshot.highlightedNodes);
    Object.entries(snapshot.nodeStates).forEach(([nodeId, state]) => {
      if (state && state !== 'idle') {
        set.add(+nodeId);
      }
    });
    return set;
  }, [snapshot.highlightedNodes, snapshot.nodeStates]);

  const [viewBox, setViewBox] = useState<ViewBox>(baseViewBox);
  if (viewBoxRef.current === null) {
    viewBoxRef.current = baseViewBox;
  }

  useEffect(() => {
    setViewBox(baseViewBox);
  }, [baseViewBox]);
  const updatePatternFromRect = useCallback(
    (rect: { width: number; height: number }) => {
      const { width: pixelWidth, height: pixelHeight } = rect;
      if (pixelWidth === 0 || pixelHeight === 0) {
        return;
      }
      const currentViewBox = viewBoxRef.current;
      if (!currentViewBox) {
        return;
      }

      const widthUnits = (currentViewBox.width / pixelWidth) * PATTERN_BASE_SIZE;
      const heightUnits = (currentViewBox.height / pixelHeight) * PATTERN_BASE_SIZE;

      setPatternSize((prev) => {
        const deltaWidth = Math.abs(prev.width - widthUnits);
        const deltaHeight = Math.abs(prev.height - heightUnits);
        if (deltaWidth < 0.5 && deltaHeight < 0.5) {
          return prev;
        }
        return { width: widthUnits, height: heightUnits };
      });
    },
    []
  );
  useEffect(() => {
    viewBoxRef.current = viewBox;
  }, [viewBox]);
  useEffect(() => {
    if (svgSizeRef.current) {
      updatePatternFromRect(svgSizeRef.current);
    }
  }, [updatePatternFromRect, viewBox.height, viewBox.width]);
  useEffect(() => {
    const svg = svgRef.current;
    if (!svg) {
      return;
    }
    const observer = new ResizeObserver((entries) => {
      const entry = entries[0];
      if (!entry) {
        return;
      }
      const { width, height } = entry.contentRect;
      svgSizeRef.current = { width, height };
      if (width > 0 && height > 0) {
        const newRatio = width / height;

        setViewportAspectRatio((prevRatio) => {
          if (prevRatio && Math.abs(prevRatio - newRatio) < 0.01) {
            return prevRatio;
          }
          return newRatio;
        });

        // Adjust viewBox to match new aspect ratio while preserving center and width (zoom)
        setViewBox((prevViewBox) => {
          const currentCenter = {
            x: prevViewBox.x + prevViewBox.width / 2,
            y: prevViewBox.y + prevViewBox.height / 2,
          };

          const newHeight = prevViewBox.width / newRatio;

          return {
            x: currentCenter.x - prevViewBox.width / 2,
            y: currentCenter.y - newHeight / 2,
            width: prevViewBox.width,
            height: newHeight,
          };
        });
      }
      updatePatternFromRect(svgSizeRef.current);
    });

    observer.observe(svg);

    return () => {
      observer.disconnect();
    };
  }, [updatePatternFromRect]);

  const isPanningRef = useRef(false);
  const panStartRef = useRef<{ x: number; y: number }>({ x: 0, y: 0 });
  const viewBoxStartRef = useRef<ViewBox>(baseViewBox);

  const clampViewBox = useCallback(
    (next: ViewBox) => {
      const minWidth = baseViewBox.width * MIN_ZOOM;
      const maxWidth = baseViewBox.width * MAX_ZOOM;
      const aspect =
        viewportAspectRatio && viewportAspectRatio > 0
          ? viewportAspectRatio
          : baseViewBox.width / baseViewBox.height;
      const width = Math.min(Math.max(next.width, minWidth), maxWidth);
      const height = width / aspect;
      return { ...next, width, height };
    },
    [baseViewBox.height, baseViewBox.width, viewportAspectRatio],
  );

  const handleWheel = useCallback(
    (event: React.WheelEvent<SVGSVGElement>) => {
      event.preventDefault();
      const svg = svgRef.current;
      if (!svg) {
        return;
      }

      const rect = svg.getBoundingClientRect();
      const pointerX = event.clientX - rect.left;
      const pointerY = event.clientY - rect.top;

      setViewBox((prev) => {
        const scale = event.deltaY < 0 ? 1 / ZOOM_FACTOR : ZOOM_FACTOR;
        const nextWidth = prev.width * scale;
        const nextHeight = prev.height * scale;

        const clamped = clampViewBox({
          x: prev.x,
          y: prev.y,
          width: nextWidth,
          height: nextHeight,
        });

        const widthRatio = clamped.width / prev.width;
        const heightRatio = clamped.height / prev.height;

        const pointerGraphX = prev.x + (pointerX / rect.width) * prev.width;
        const pointerGraphY = prev.y + (pointerY / rect.height) * prev.height;

        const newX = pointerGraphX - (pointerGraphX - prev.x) * widthRatio;
        const newY = pointerGraphY - (pointerGraphY - prev.y) * heightRatio;

        return clampViewBox({
          x: newX,
          y: newY,
          width: clamped.width,
          height: clamped.height,
        });
      });
    },
    [clampViewBox],
  );

  const handlePointerDown = useCallback((event: React.PointerEvent<SVGSVGElement>) => {
    if (event.button !== 0) {
      return;
    }
    const svg = svgRef.current;
    if (!svg) {
      return;
    }
    isPanningRef.current = true;
    panStartRef.current = { x: event.clientX, y: event.clientY };
    viewBoxStartRef.current = viewBox;
    svg.setPointerCapture(event.pointerId);
  }, [viewBox]);

  const handlePointerMove = useCallback((event: React.PointerEvent<SVGSVGElement>) => {
    if (!isPanningRef.current) {
      return;
    }
    const svg = svgRef.current;
    if (!svg) {
      return;
    }
    const rect = svg.getBoundingClientRect();
    const deltaX = event.clientX - panStartRef.current.x;
    const deltaY = event.clientY - panStartRef.current.y;
    const widthScale = viewBoxStartRef.current.width / rect.width;
    const heightScale = viewBoxStartRef.current.height / rect.height;

    setViewBox((prev) => ({
      ...prev,
      x: viewBoxStartRef.current.x - deltaX * widthScale,
      y: viewBoxStartRef.current.y - deltaY * heightScale,
    }));
  }, []);

  const handlePointerUp = useCallback((event: React.PointerEvent<SVGSVGElement>) => {
    if (!isPanningRef.current) {
      return;
    }
    const svg = svgRef.current;
    if (svg && svg.hasPointerCapture(event.pointerId)) {
      svg.releasePointerCapture(event.pointerId);
    }
    isPanningRef.current = false;
  }, []);

  return (
    <div className={cn("h-full w-full", className)}>
      <svg
        ref={svgRef}
        viewBox={`${viewBox.x} ${viewBox.y} ${viewBox.width} ${viewBox.height}`}
        preserveAspectRatio="xMidYMid slice"
        className="h-full w-full touch-pan-y"
        role="presentation"
        onWheel={handleWheel}
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onPointerLeave={handlePointerUp}
      >
        <defs>
          <pattern
            id={patternId}
            x="0"
            y="0"
            width={patternSize.width}
            height={patternSize.height}
            patternUnits="userSpaceOnUse"
          >
            <rect width={patternSize.width} height={patternSize.height} fill="#f8fafc" />
            <circle cx={patternSize.width * 0.125} cy={patternSize.height * 0.125} r={Math.max(patternSize.width, patternSize.height) * 0.025} fill="#cbd5f5" />
            <circle cx={patternSize.width * 0.125} cy={patternSize.height * 0.625} r={Math.max(patternSize.width, patternSize.height) * 0.02} fill="#dbeAFE" />
            <circle cx={patternSize.width * 0.625} cy={patternSize.height * 0.125} r={Math.max(patternSize.width, patternSize.height) * 0.02} fill="#dbeAFE" />
            <circle cx={patternSize.width * 0.625} cy={patternSize.height * 0.625} r={Math.max(patternSize.width, patternSize.height) * 0.023} fill="#bfdbfe" />
          </pattern>
        </defs>
        <rect
          x={viewBox.x}
          y={viewBox.y}
          width={viewBox.width}
          height={viewBox.height}
          fill={`url(#${patternId})`}
        />
        <g>
          {positionedLinks.map((link, index) =>
            renderLink(link, snapshot.transmissions, index, snapshot.step),
          )}
        </g>
        <g>
          {scenario.nodes.map((node) => (
            <GraphNode
              key={`node-${node.id}`}
              node={node}
              state={snapshot.nodeStates[node.id] ?? 'idle'}
              highlighted={highlightedNodes.has(node.id)}
              stateStyles={stateStyles}
            />
          ))}
        </g>
      </svg>
    </div>
  );
});

function renderLink(
  link: PositionedLink,
  transmissions: SimulationSnapshot['transmissions'],
  index: number,
  frameStep: number,
) {
  const { sourceNode, targetNode } = link;
  if (!sourceNode || !targetNode) {
    return null;
  }

  const relatedTransmissions: SimulationSnapshot['transmissions'] = [];
  const forwardTransmissions: SimulationSnapshot['transmissions'] = [];
  const reverseTransmissions: SimulationSnapshot['transmissions'] = [];

  transmissions.forEach((tx) => {
    const matchesForward = tx.source === link.source && tx.target === link.target;
    const matchesReverse = tx.source === link.target && tx.target === link.source;

    if (!matchesForward && !matchesReverse) {
      return;
    }

    relatedTransmissions.push(tx);

    if (matchesForward) {
      forwardTransmissions.push(tx);
    }

    if (matchesReverse) {
      reverseTransmissions.push(tx);
    }
  });

  const hasTransmission = relatedTransmissions.length > 0;
  const hasFailure = relatedTransmissions.some((tx) => tx.success === false);
  const hasSuccess = relatedTransmissions.some((tx) => tx.success !== false);

  let color = LINK_STATE_COLORS.idle;
  if (hasFailure) {
    color = TRANSMISSION_COLORS.failure;
  } else if (hasSuccess) {
    color = TRANSMISSION_COLORS.success;
  } else if (hasTransmission) {
    color = TRANSMISSION_COLORS.default;
  }

  return (
    <g key={`${link.source}-${link.target}-${index}`}>
      <motion.line
        x1={sourceNode.position.x}
        y1={sourceNode.position.y}
        x2={targetNode.position.x}
        y2={targetNode.position.y}
        stroke={color}
        strokeWidth={hasTransmission ? 4 : 2}
        strokeLinecap="round"
        strokeDasharray={hasFailure ? '6 6' : undefined}
        initial={{ opacity: hasTransmission ? 0.7 : 0.3 }}
        animate={{ opacity: hasTransmission ? 1 : 0.45 }}
        transition={{ duration: 0.4 }}
      />
      {forwardTransmissions.map((tx, txIndex) =>
        renderTransmissionParticle({
          tx,
          startNode: sourceNode,
          endNode: targetNode,
          frameStep,
          order: txIndex,
          direction: 'forward',
        }),
      )}
      {reverseTransmissions.map((tx, txIndex) =>
        renderTransmissionParticle({
          tx,
          startNode: targetNode,
          endNode: sourceNode,
          frameStep,
          order: txIndex,
          direction: 'reverse',
        }),
      )}
    </g>
  );
}

type TransmissionParticleArgs = {
  tx: SimulationSnapshot['transmissions'][number];
  startNode: ScenarioNode;
  endNode: ScenarioNode;
  frameStep: number;
  order: number;
  direction: 'forward' | 'reverse';
};

function renderTransmissionParticle({
  tx,
  startNode,
  endNode,
  frameStep,
  order,
  direction,
}: TransmissionParticleArgs) {
  const { start, end } = computeTransmissionSegment(startNode, endNode);
  const key = `tx-${direction}-${frameStep}-${tx.id ?? `${startNode.id}-${endNode.id}-${order}`}`;

  return (
    <motion.circle
      key={key}
      r={4}
      fill={'#000'}
      pointerEvents="none"
      initial={{ cx: start.x, cy: start.y, opacity: 0, scale: 0.65 }}
      animate={{
        cx: end.x,
        cy: end.y,
        opacity: [0.15, 0.95, 0],
        scale: [0.65, 1, 0.85],
      }}
      transition={{
        duration: 0.9,
        ease: 'easeInOut',
        delay: order * 0.12,
      }}
    />
  );
}

function computeTransmissionSegment(startNode: ScenarioNode, endNode: ScenarioNode) {
  const dx = endNode.position.x - startNode.position.x;
  const dy = endNode.position.y - startNode.position.y;
  const distance = Math.hypot(dx, dy);

  if (distance === 0) {
    return {
      start: { x: startNode.position.x, y: startNode.position.y },
      end: { x: endNode.position.x, y: endNode.position.y },
    };
  }

  const inset = Math.min(NODE_RADIUS, distance / 2);
  const offsetX = (dx / distance) * inset;
  const offsetY = (dy / distance) * inset;

  return {
    start: { x: startNode.position.x + offsetX, y: startNode.position.y + offsetY },
    end: { x: endNode.position.x - offsetX, y: endNode.position.y - offsetY },
  };
}

function createStateStyleMap(scenario: Scenario): Map<string, NodeVisualStyle> {
  const states = collectScenarioStates(scenario);
  const styleMap = new Map<string, NodeVisualStyle>();
  let paletteIndex = 0;

  states.forEach((rawState) => {
    const state = rawState.trim();
    if (!state) {
      return;
    }
    if (styleMap.has(state)) {
      return;
    }

    const preset = PRESET_STATE_OVERRIDES[state.toLowerCase()];
    if (preset) {
      styleMap.set(state, preset);
      return;
    }

    const nextStyle = styleFromPalette(state, paletteIndex);
    styleMap.set(state, nextStyle);
    paletteIndex += 1;
  });

  if (!styleMap.has('idle')) {
    styleMap.set('idle', IDLE_NODE_STYLE);
  }

  return styleMap;
}

function collectScenarioStates(scenario: Scenario): string[] {
  const states = new Set<string>(['idle']);
  scenario.rounds.forEach((round) => {
    round.slots.forEach((slot) => {
      if (!slot.nodeStates) {
        return;
      }
      Object.values(slot.nodeStates).forEach((state) => {
        if (typeof state === 'string' && state.trim()) {
          states.add(state);
        }
      });
    });
  });
  return Array.from(states)
    .filter((state) => state.trim().length > 0)
    .sort((a, b) => {
      const aLower = a.toLowerCase();
      const bLower = b.toLowerCase();
      if (aLower === 'idle') {
        return -1;
      }
      if (bLower === 'idle') {
        return 1;
      }
      return aLower.localeCompare(bLower);
    });
}

function styleFromPalette(state: string, index: number): NodeVisualStyle {
  if (index < COLOR_PALETTE.length) {
    return COLOR_PALETTE[index]!;
  }
  return generateStyleFromState(state, index);
}

function generateStyleFromState(state: string, offset: number): NodeVisualStyle {
  let hash = 0;
  for (let i = 0; i < state.length; i += 1) {
    hash = (hash * 131 + state.charCodeAt(i)) % 360;
  }
  const hue = (hash + offset * 47) % 360;
  const fill = `hsl(${hue}, 70%, 78%)`;
  const stroke = `hsl(${hue}, 68%, 45%)`;
  const aura = `hsla(${hue}, 72%, 60%, 0.3)`;
  const text = `hsl(${hue}, 68%, 22%)`;
  return { fill, stroke, aura, text };
}

function resolveStateStyle(styles: Map<string, NodeVisualStyle>, state: string | undefined): NodeVisualStyle {
  if (!state) {
    return styles.get('idle') ?? IDLE_NODE_STYLE;
  }

  const trimmed = state.trim();
  if (!trimmed) {
    return styles.get('idle') ?? IDLE_NODE_STYLE;
  }

  const exact = styles.get(trimmed);
  if (exact) {
    return exact;
  }

  const lower = trimmed.toLowerCase();
  for (const [key, value] of styles.entries()) {
    if (key.toLowerCase() === lower) {
      return value;
    }
  }

  return generateStyleFromState(trimmed, 0);
}

type GraphNodeProps = {
  node: ScenarioNode;
  state: string;
  highlighted: boolean;
  stateStyles: Map<string, NodeVisualStyle>;
};

function GraphNode({ node, state, highlighted, stateStyles }: GraphNodeProps) {
  const styles = resolveStateStyle(stateStyles, state);
  const transform = `translate(${node.position.x}, ${node.position.y})`;
  const normalizedState = state?.trim() ?? '';
  const isIdle = normalizedState.length === 0 || normalizedState.toLowerCase() === 'idle';
  const shouldHighlight = highlighted || !isIdle;
  const stateLabel = normalizedState.length > 0 ? normalizedState.toUpperCase() : 'IDLE';

  return (
    <g transform={transform}>
      <title>{`${node.label ?? node.id}${normalizedState ? ` • ${normalizedState}` : ''}`}</title>
      {shouldHighlight && (
        <motion.circle
          r={NODE_RADIUS * 1.8}
          fill={styles.aura}
          initial={{ opacity: 0, scale: 0.7 }}
          animate={{ opacity: 0.45, scale: 1.1 }}
          exit={{ opacity: 0 }}
        />
      )}
      <motion.circle
        r={NODE_RADIUS}
        fill={styles.fill}
        stroke={styles.stroke}
        strokeWidth={2}
        initial={{ opacity: 0, scale: 0.9 }}
        animate={{ opacity: 1, scale: shouldHighlight ? 1.12 : 1 }}
        transition={{ type: 'spring', stiffness: 180, damping: 12 }}
      />
      <text
        x={0}
        y={2}
        textAnchor="middle"
        dominantBaseline="middle"
        className="select-none text-[11px] font-semibold uppercase"
        fill={styles.text}
      >
        {stateLabel}
      </text>
      <text
        x={0}
        y={NODE_RADIUS + 16}
        textAnchor="middle"
        className="select-none text-xs font-medium fill-zinc-800 dark:fill-zinc-200"
      >
        {node.label ?? node.id}
      </text>
    </g>
  );
}

function calculateViewBox(nodes: ScenarioNode[], targetAspectRatio?: number) {
  const padding = 80;

  let x: number;
  let y: number;
  let width: number;
  let height: number;

  if (nodes.length === 0) {
    const baseSize = 400;
    width = baseSize + padding * 2;
    height = baseSize + padding * 2;
    x = -padding;
    y = -padding;
  } else {
    let minX = Number.POSITIVE_INFINITY;
    let maxX = Number.NEGATIVE_INFINITY;
    let minY = Number.POSITIVE_INFINITY;
    let maxY = Number.NEGATIVE_INFINITY;

    nodes.forEach((node) => {
      minX = Math.min(minX, node.position.x);
      maxX = Math.max(maxX, node.position.x);
      minY = Math.min(minY, node.position.y);
      maxY = Math.max(maxY, node.position.y);
    });

    width = (maxX - minX || 200) + padding * 2;
    height = (maxY - minY || 200) + padding * 2;
    x = minX - padding;
    y = minY - padding;
  }

  if (targetAspectRatio && targetAspectRatio > 0) {
    const currentAspect = width / height;
    if (currentAspect < targetAspectRatio) {
      const desiredWidth = height * targetAspectRatio;
      const extra = desiredWidth - width;
      x -= extra / 2;
      width = desiredWidth;
    } else if (currentAspect > targetAspectRatio) {
      const desiredHeight = width / targetAspectRatio;
      const extra = desiredHeight - height;
      y -= extra / 2;
      height = desiredHeight;
    }
  }

  return { x, y, width, height };
}
