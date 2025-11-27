import { promises as fs } from "fs";
import path from "path";

import { z } from "zod";
import { parse as parseYaml, stringify as stringifyYaml } from "yaml";

import { serverEnv } from "@/core/env/server";
import { AppError } from "@/core/error/app-error";
import { createLogger } from "@/core/logging/logger";
import {
  RESERVED_SCENARIO_METADATA_KEYS,
  scenarioMetadataSchema,
  scenarioSummarySchema,
  type ScenarioMetadata,
  type ScenarioSummary,
} from "@/features/scenarios/types";
import { parseScenarioYaml, type Scenario, type ScenarioRaw } from "@/lib/scenario";
import { errorResponse, successResponse } from "@/server/api/responses";

const SCENARIOS_DIR = path.resolve(process.cwd(), serverEnv.SCENARIOS_DIR);
const SUPPORTED_EXTENSIONS = [".yaml", ".yml"];
const scenarioLogger = createLogger("api:scenarios");
const RESERVED_METADATA_KEYS = new Set<string>(RESERVED_SCENARIO_METADATA_KEYS);

const deletePayloadSchema = z.object({
  fileName: z.string().min(1, "Scenario file name is required."),
});

const updatePayloadSchema = z.object({
  fileName: z.string().min(1, "Scenario file name is required."),
  name: z.string().min(1, "Scenario name is required."),
  description: z.string().optional().nullable(),
  author: z.string().optional().nullable(),
  metadata: scenarioMetadataSchema.optional(),
});

export async function GET() {
  try {
    const scenarios = await readScenarioSummaries();
    return successResponse({ scenarios });
  } catch (error) {
    return errorResponse(error);
  }
}

export async function POST(request: Request) {
  try {
    const formData = await request.formData();
    const file = formData.get("file");

    if (!file || !(file instanceof File)) {
      throw new AppError("validation", "Missing scenario file upload.");
    }

    const originalName = file.name?.trim() || "scenario.yaml";
    const extension = path.extname(originalName).toLowerCase() || ".yaml";

    if (!SUPPORTED_EXTENSIONS.includes(extension)) {
      throw new AppError("validation", "Only YAML files are supported.");
    }

    const textContent = await file.text();
    const summary = await persistScenarioFile({
      originalName,
      extension,
      contents: textContent,
    });

    return successResponse({ scenario: summary }, { status: 201 });
  } catch (error) {
    return errorResponse(error);
  }
}

export async function DELETE(request: Request) {
  try {
    const payload = deletePayloadSchema.parse(await request.json());
    await deleteScenarioFile(payload.fileName);
    return successResponse({ deleted: true });
  } catch (error) {
    return errorResponse(error);
  }
}

export async function PATCH(request: Request) {
  try {
    const payload = updatePayloadSchema.parse(await request.json());
    const scenario = await updateScenarioFile(payload);
    return successResponse({ scenario });
  } catch (error) {
    return errorResponse(error);
  }
}

async function readScenarioSummaries(): Promise<ScenarioSummary[]> {
  await ensureScenarioDir();
  const entries = await fs.readdir(SCENARIOS_DIR);
  const summaries: ScenarioSummary[] = [];

  for (const fileName of entries) {
    if (!SUPPORTED_EXTENSIONS.some((ext) => fileName.endsWith(ext))) {
      continue;
    }

    const filePath = path.join(SCENARIOS_DIR, fileName);

    try {
      const content = await fs.readFile(filePath, "utf-8");
      const { scenario } = parseScenarioYaml(content, fileName);
      summaries.push(toSummary(scenario, fileName));
    } catch (error) {
      scenarioLogger.warn(`Failed to parse scenario file ${fileName}`, { error });
    }
  }

  summaries.sort((a, b) => a.name.localeCompare(b.name));
  return summaries;
}

async function persistScenarioFile({
  originalName,
  extension,
  contents,
}: {
  originalName: string;
  extension: string;
  contents: string;
}): Promise<ScenarioSummary> {
  let scenarioData;
  try {
    scenarioData = parseScenarioYaml(contents, originalName).scenario;
  } catch (error) {
    throw new AppError(
      "validation",
      error instanceof Error ? error.message : "Unable to parse scenario YAML.",
      { cause: error },
    );
  }

  const parsedSummary = toSummary(scenarioData, originalName);

  await ensureScenarioDir();
  const baseName = sanitizeBaseName(path.parse(originalName).name) || "scenario";

  let candidateName = `${baseName}${extension}`;
  let counter = 1;

  while (await fileExists(path.join(SCENARIOS_DIR, candidateName))) {
    candidateName = `${baseName}-${counter}${extension}`;
    counter += 1;
  }

  await fs.writeFile(path.join(SCENARIOS_DIR, candidateName), contents, "utf-8");

  return scenarioSummarySchema.parse({
    ...parsedSummary,
    fileName: candidateName,
    url: `/scenarios/${candidateName}`,
  });
}

async function updateScenarioFile(payload: z.infer<typeof updatePayloadSchema>): Promise<ScenarioSummary> {
  const { fileName, name, description, author, metadata } = payload;
  const filePath = await resolveScenarioFilePath(fileName);
  let rawContents: string;

  try {
    rawContents = await fs.readFile(filePath, "utf-8");
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code === "ENOENT") {
      throw new AppError("not-found", "Scenario not found.", { cause: error });
    }
    throw error;
  }

  let scenarioRaw: ScenarioRaw;
  try {
    scenarioRaw = parseYaml(rawContents) as ScenarioRaw;
  } catch (error) {
    throw new AppError("validation", "Unable to parse scenario YAML.", { cause: error });
  }

  const updatedScenario: ScenarioRaw = {
    ...scenarioRaw,
    name,
  };

  const currentMetadata = extractMetadataRecord(scenarioRaw.metadata);
  const nextMetadata = applyMetadataUpdates(currentMetadata, metadata, author, description);

  if (Object.keys(nextMetadata).length > 0) {
    updatedScenario.metadata = nextMetadata;
  } else {
    delete updatedScenario.metadata;
  }

  const updatedYaml = stringifyYaml(updatedScenario);
  await fs.writeFile(filePath, updatedYaml, "utf-8");

  const { scenario } = parseScenarioYaml(updatedYaml, fileName);
  return toSummary(scenario, fileName);
}

async function deleteScenarioFile(fileName: string) {
  const filePath = await resolveScenarioFilePath(fileName);

  try {
    await fs.unlink(filePath);
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code === "ENOENT") {
      throw new AppError("not-found", "Scenario not found.", { cause: error });
    }
    throw error;
  }
}

async function resolveScenarioFilePath(fileName: string) {
  await ensureScenarioDir();
  const safeName = sanitizeBaseName(path.basename(fileName));

  if (!safeName || !SUPPORTED_EXTENSIONS.some((ext) => safeName.endsWith(ext))) {
    throw new AppError("validation", "Invalid scenario file name.");
  }

  const filePath = path.join(SCENARIOS_DIR, safeName);
  const relativePath = path.relative(SCENARIOS_DIR, filePath);

  if (relativePath.startsWith("..") || path.isAbsolute(relativePath)) {
    throw new AppError("forbidden", "Invalid scenario file path.");
  }

  return filePath;
}

function extractMetadataRecord(value: unknown): ScenarioMetadata {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return {};
  }
  return { ...(value as ScenarioMetadata) };
}

function applyMetadataUpdates(
  current: ScenarioMetadata,
  updates: ScenarioMetadata | undefined,
  author?: string | null,
  description?: string | null,
): ScenarioMetadata {
  const next: ScenarioMetadata = { ...current };

  if (updates) {
    for (const key of Object.keys(next)) {
      if (RESERVED_METADATA_KEYS.has(key)) {
        continue;
      }
      if (!(key in updates)) {
        delete next[key];
      }
    }

    for (const [key, value] of Object.entries(updates)) {
      next[key] = value;
    }
  }

  if (author !== undefined) {
    if (author === null || author.trim() === "") {
      delete next.author;
    } else {
      next.author = author;
    }
  }

  if (description !== undefined) {
    if (description === null || description.trim() === "") {
      delete next.description;
    } else {
      next.description = description;
    }
  }

  return next;
}

async function ensureScenarioDir() {
  await fs.mkdir(SCENARIOS_DIR, { recursive: true });
}

async function fileExists(filePath: string) {
  try {
    await fs.access(filePath);
    return true;
  } catch {
    return false;
  }
}

function sanitizeBaseName(name: string) {
  return name.replace(/[^a-zA-Z0-9._-]/g, "-");
}

function toSummary(scenario: Scenario, fileName: string): ScenarioSummary {
  const metadata = scenario.metadata ?? {};
  const description = typeof metadata["description"] === "string" ? (metadata["description"] as string) : undefined;
  const author = typeof metadata["author"] === "string" ? (metadata["author"] as string) : undefined;

  return {
    id: scenario.id,
    name: scenario.name,
    description,
    author,
    fileName,
    url: `/scenarios/${fileName}`,
    ...(scenario.metadata ? { metadata: scenario.metadata } : {}),
  };
}
