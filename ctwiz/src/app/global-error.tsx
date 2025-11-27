"use client";

import { useEffect } from "react";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { formatAppError } from "@/core/error/app-error";
import { createLogger } from "@/core/logging/logger";

const logger = createLogger("app:global-error");

type GlobalErrorProps = {
  error: Error & { digest?: string };
  reset: () => void;
};

export default function GlobalError({ error, reset }: GlobalErrorProps) {
  useEffect(() => {
    logger.error("Global error", formatAppError(error));
  }, [error]);

  return (
    <html>
      <body className="flex min-h-screen items-center justify-center bg-background p-6">
        <div className="w-full max-w-md space-y-4">
          <Alert variant="destructive">
            <AlertTitle>Application error</AlertTitle>
            <AlertDescription>{error.message}</AlertDescription>
          </Alert>
          <Button onClick={() => reset()}>Reload app</Button>
        </div>
      </body>
    </html>
  );
}
