import { NextResponse } from "next/server";

import { AppError, formatAppError, getAppErrorStatus, toAppError } from "@/core/error/app-error";
import { createLogger } from "@/core/logging/logger";

const apiLogger = createLogger("api");

export function successResponse<T>(data: T, init?: ResponseInit) {
  return NextResponse.json({ success: true, data }, init);
}

export function errorResponse(error: unknown, init?: ResponseInit) {
  const appError = error instanceof AppError ? error : toAppError(error);
  apiLogger.error(appError.message, formatAppError(appError));
  return NextResponse.json(
    {
      success: false,
      error: appError.message,
      kind: appError.kind,
      details: appError.details,
    },
    { status: getAppErrorStatus(appError), ...init },
  );
}
