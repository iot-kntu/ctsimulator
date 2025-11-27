import { clientEnv } from "@/core/env/client";
import { serverLogLevel } from "@/core/env/server";
import type { LogLevel } from "@/core/env/shared";

const levelRank: Record<LogLevel, number> = {
  debug: 10,
  info: 20,
  warn: 30,
  error: 40,
};

type LogPayload = Record<string, unknown> | undefined;

export type Logger = {
  debug: (message: string, payload?: LogPayload) => void;
  info: (message: string, payload?: LogPayload) => void;
  warn: (message: string, payload?: LogPayload) => void;
  error: (message: string, payload?: LogPayload) => void;
};

const isServer = typeof window === "undefined";

const resolvedLogLevel: LogLevel = isServer ? serverLogLevel : clientEnv.logLevel;

export function createLogger(namespace: string): Logger {
  return {
    debug: (message, payload) => logMessage("debug", namespace, message, payload),
    info: (message, payload) => logMessage("info", namespace, message, payload),
    warn: (message, payload) => logMessage("warn", namespace, message, payload),
    error: (message, payload) => logMessage("error", namespace, message, payload),
  };
}

function logMessage(level: LogLevel, namespace: string, message: string, payload?: LogPayload) {
  if (levelRank[level] < levelRank[resolvedLogLevel]) {
    return;
  }

  const formatted = payload ? `${message} ${JSON.stringify(payload)}` : message;

  switch (level) {
    case "debug":
      console.debug(`[${namespace}] ${formatted}`);
      break;
    case "info":
      console.info(`[${namespace}] ${formatted}`);
      break;
    case "warn":
      console.warn(`[${namespace}] ${formatted}`);
      break;
    case "error":
    default:
      console.error(`[${namespace}] ${formatted}`);
      break;
  }
}
