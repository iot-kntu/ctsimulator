export type AppErrorKind =
  | "validation"
  | "not-found"
  | "conflict"
  | "unauthorized"
  | "forbidden"
  | "internal";

const STATUS_MAP: Record<AppErrorKind, number> = {
  validation: 400,
  "not-found": 404,
  conflict: 409,
  unauthorized: 401,
  forbidden: 403,
  internal: 500,
};

export type AppErrorDetails = Record<string, unknown>;

export type AppErrorOptions = {
  cause?: unknown;
  details?: AppErrorDetails;
  statusCode?: number;
};

export class AppError extends Error {
  readonly kind: AppErrorKind;
  readonly statusCode: number;
  readonly details?: AppErrorDetails;

  constructor(kind: AppErrorKind, message: string, options: AppErrorOptions = {}) {
    super(message);
    this.name = "AppError";
    this.kind = kind;
    this.statusCode = options.statusCode ?? STATUS_MAP[kind];
    if (options.details) {
      this.details = options.details;
    }
    if (options.cause) {
      this.cause = options.cause;
    }
  }
}

export function isAppError(error: unknown): error is AppError {
  return error instanceof AppError;
}

export function toAppError(error: unknown, fallbackKind: AppErrorKind = "internal"): AppError {
  if (isAppError(error)) {
    return error;
  }
  if (error instanceof Error) {
    return new AppError(fallbackKind, error.message, { cause: error });
  }
  return new AppError(fallbackKind, "An unexpected error occurred.", { details: { error } });
}

export function getAppErrorStatus(error: unknown): number {
  return isAppError(error) ? error.statusCode : STATUS_MAP.internal;
}

export function formatAppError(error: unknown): Record<string, unknown> {
  if (isAppError(error)) {
    return {
      kind: error.kind,
      message: error.message,
      statusCode: error.statusCode,
      details: error.details,
    };
  }
  if (error instanceof Error) {
    return { kind: "unknown", message: error.message };
  }
  return { kind: "unknown", message: String(error) };
}
