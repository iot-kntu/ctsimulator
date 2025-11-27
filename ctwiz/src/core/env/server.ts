import { z } from "zod";

import { logLevelSchema, type LogLevel } from "./shared";

const serverEnvSchema = z.object({
  NODE_ENV: z.enum(["development", "production", "test"]).default("development"),
  SCENARIOS_DIR: z.string().min(1).default("public/scenarios"),
  NEXT_PUBLIC_LOG_LEVEL: logLevelSchema.optional(),
});

export type ServerEnv = z.infer<typeof serverEnvSchema>;

export const serverEnv: ServerEnv = serverEnvSchema.parse({
  NODE_ENV: process.env.NODE_ENV,
  SCENARIOS_DIR: process.env.SCENARIOS_DIR,
  NEXT_PUBLIC_LOG_LEVEL: process.env.NEXT_PUBLIC_LOG_LEVEL,
});

export const serverLogLevel: LogLevel =
  serverEnv.NEXT_PUBLIC_LOG_LEVEL ?? (serverEnv.NODE_ENV === "development" ? "debug" : "info");
