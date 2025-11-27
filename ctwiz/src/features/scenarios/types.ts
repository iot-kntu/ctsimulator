import { z } from "zod";

export const scenarioMetadataSchema = z.record(z.string(), z.unknown());

export const scenarioSummarySchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string().optional(),
  author: z.string().optional(),
  fileName: z.string(),
  url: z.string(),
  metadata: scenarioMetadataSchema.optional(),
});

export type ScenarioSummary = z.infer<typeof scenarioSummarySchema>;

export type ScenarioMetadata = z.infer<typeof scenarioMetadataSchema>;

export const RESERVED_SCENARIO_METADATA_KEYS = ["description", "author"] as const;

export type ReservedScenarioMetadataKey = (typeof RESERVED_SCENARIO_METADATA_KEYS)[number];

export type ScenarioUpdateInput = {
  name: string;
  description?: string | null;
  author?: string | null;
  metadata?: ScenarioMetadata;
};
