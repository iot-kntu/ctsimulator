import { logLevelSchema, type LogLevel } from "./shared";

const fallbackLevel: LogLevel =
  process.env.NODE_ENV === "development" ? "debug" : "warn";

const parsed = logLevelSchema.safeParse(process.env.NEXT_PUBLIC_LOG_LEVEL);

export const clientEnv = {
  logLevel: parsed.success ? parsed.data : fallbackLevel,
};
