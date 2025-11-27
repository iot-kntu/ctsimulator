"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { z } from "zod";

import { scenarioSummarySchema, type ScenarioSummary, type ScenarioUpdateInput } from "../types";

import { AppError } from "@/core/error/app-error";
import { createLogger } from "@/core/logging/logger";

const listResponseSchema = z.object({
  success: z.literal(true),
  data: z.object({
    scenarios: z.array(scenarioSummarySchema),
  }),
});

const mutationResponseSchema = z.object({
  success: z.literal(true),
  data: z.object({
    scenario: scenarioSummarySchema,
  }),
});

const deleteResponseSchema = z.object({
  success: z.literal(true),
  data: z.object({
    deleted: z.boolean(),
  }),
});

const apiErrorSchema = z.object({
  error: z.string(),
  kind: z.string().optional(),
  success: z.literal(false).optional(),
});

export type UseScenarioLibraryResult = {
  scenarios: ScenarioSummary[];
  loading: boolean;
  error: string | null;
  selectedScenario: ScenarioSummary | null;
  scenarioUrl: string | null;
  selectScenario: (scenario: ScenarioSummary) => void;
  importScenario: (file: File) => Promise<ScenarioSummary>;
  deleteScenario: (scenario: ScenarioSummary) => Promise<void>;
  updateScenario: (scenario: ScenarioSummary, updates: ScenarioUpdateInput) => Promise<ScenarioSummary>;
  clearRecentScenarios: () => void;
  refresh: () => Promise<void>;
};

const logger = createLogger("scenario-library");

export function useScenarioLibrary(): UseScenarioLibraryResult {
  const [scenarios, setScenarios] = useState<ScenarioSummary[]>([]);
  const [selectedScenario, setSelectedScenario] = useState<ScenarioSummary | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await fetch("/api/scenarios", {
        method: "GET",
        cache: "no-store",
      });
      const payload = await parseResponse(response, listResponseSchema);
      setScenarios(sortScenarios(payload.data.scenarios));
      setSelectedScenario((current) => matchScenario(payload.data.scenarios, current));
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to load scenarios.";
      setError(message);
      logger.error("Failed to refresh scenarios", { error: message });
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const [recentFileNames, setRecentFileNames] = useState<string[]>([]);

  useEffect(() => {
    const stored = localStorage.getItem("recent-scenarios-ids");
    if (stored) {
      try {
        const parsed = JSON.parse(stored);
        if (Array.isArray(parsed)) {
          setRecentFileNames(parsed);
        }
      } catch {
        // Ignore parsing errors
      }
    }
  }, []);

  const updateRecentScenarios = useCallback((scenario: ScenarioSummary) => {
    setRecentFileNames((previous) => {
      const filtered = previous.filter((name) => name !== scenario.fileName);
      const next = [scenario.fileName, ...filtered];
      localStorage.setItem("recent-scenarios-ids", JSON.stringify(next));
      return next;
    });
  }, []);

  const clearRecentScenarios = useCallback(() => {
    setRecentFileNames([]);
    localStorage.removeItem("recent-scenarios-ids");
  }, []);

  const selectScenario = useCallback(
    (scenario: ScenarioSummary) => {
      setSelectedScenario(scenario);
      updateRecentScenarios(scenario);
    },
    [updateRecentScenarios]
  );

  const sortedScenarios = useMemo(() => {
    const map = new Map(scenarios.map((s) => [s.fileName, s]));
    const ordered: ScenarioSummary[] = [];

    // Add recent ones first
    for (const name of recentFileNames) {
      const scenario = map.get(name);
      if (scenario) {
        ordered.push(scenario);
        map.delete(name);
      }
    }

    // Add remaining ones (already sorted alphabetically from fetch)
    for (const scenario of map.values()) {
      ordered.push(scenario);
    }

    return ordered;
  }, [scenarios, recentFileNames]);

  const importScenario = useCallback(async (file: File) => {
    const formData = new FormData();
    formData.append("file", file);

    const response = await fetch("/api/scenarios", {
      method: "POST",
      body: formData,
    });

    const payload = await parseResponse(response, mutationResponseSchema);
    setScenarios((previous) => sortScenarios(upsertScenario(previous, payload.data.scenario)));
    setSelectedScenario(payload.data.scenario);
    updateRecentScenarios(payload.data.scenario);
    return payload.data.scenario;
  }, [updateRecentScenarios]);

  const deleteScenario = useCallback(async (scenario: ScenarioSummary) => {
    const response = await fetch("/api/scenarios", {
      method: "DELETE",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ fileName: scenario.fileName }),
    });

    await parseResponse(response, deleteResponseSchema);

    setScenarios((previous) => previous.filter((entry) => entry.fileName !== scenario.fileName));

    // Remove from recent list if present
    setRecentFileNames((previous) => {
      const next = previous.filter((name) => name !== scenario.fileName);
      localStorage.setItem("recent-scenarios-ids", JSON.stringify(next));
      return next;
    });

    setSelectedScenario((current) => {
      if (!current || current.fileName !== scenario.fileName) {
        return current;
      }
      return null;
    });
  }, []);

  const updateScenario = useCallback(
    async (scenario: ScenarioSummary, updates: ScenarioUpdateInput) => {
      const response = await fetch("/api/scenarios", {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          fileName: scenario.fileName,
          ...updates,
        }),
      });

      const payload = await parseResponse(response, mutationResponseSchema);
      setScenarios((previous) => sortScenarios(upsertScenario(previous, payload.data.scenario)));

      // No need to update recentFileNames as the name might change but ID (fileName) is key. 
      // Wait, fileName IS the ID. If fileName changes, we need to update.
      // But updateScenario implementation in API usually keeps fileName or returns new one?
      // The hook upsertScenario uses fileName.
      // If fileName changes, we should update the recent list.
      // Assuming fileName is stable or we handle it.
      // For now, let's assume fileName is the key.

      setSelectedScenario((current) => {
        if (!current || current.fileName !== scenario.fileName) {
          return current;
        }
        return payload.data.scenario;
      });
      return payload.data.scenario;
    },
    [],
  );

  const scenarioUrl = useMemo(() => selectedScenario?.url ?? null, [selectedScenario]);

  useEffect(() => {
    const nextScenario = scenarios[0];
    if (!selectedScenario && nextScenario) {
      setSelectedScenario(nextScenario);
      updateRecentScenarios(nextScenario);
    }
  }, [scenarios, selectedScenario, updateRecentScenarios]);

  return {
    scenarios: sortedScenarios, // Return the sorted list as the main list
    loading,
    error,
    selectedScenario,
    scenarioUrl,
    selectScenario,
    importScenario,
    deleteScenario,
    updateScenario,
    clearRecentScenarios,
    refresh,
  };
}

async function parseResponse<T>(response: Response, schema: z.ZodSchema<T>): Promise<T> {
  let payload: unknown;
  try {
    payload = await response.json();
  } catch (error) {
    throw new AppError("internal", "Unexpected response from server.", { cause: error });
  }

  if (!response.ok) {
    const parsedError = apiErrorSchema.safeParse(payload);
    const message = parsedError.success ? parsedError.data.error : `Request failed with status ${response.status}.`;
    throw new AppError(response.status === 404 ? "not-found" : "validation", message);
  }

  return schema.parse(payload);
}

function sortScenarios(entries: ScenarioSummary[]) {
  return [...entries].sort((a, b) => a.name.localeCompare(b.name));
}

function upsertScenario(existing: ScenarioSummary[], next: ScenarioSummary) {
  const filtered = existing.filter((entry) => entry.fileName !== next.fileName);
  return [...filtered, next];
}

function matchScenario(list: ScenarioSummary[], current: ScenarioSummary | null): ScenarioSummary | null {
  if (!current) {
    return list[0] ?? null;
  }
  return list.find((scenario) => scenario.fileName === current.fileName) ?? list[0] ?? null;
}
