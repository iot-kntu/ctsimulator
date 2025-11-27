"use client";

import {
  Loader2Icon,
  MoreVerticalIcon,
  PencilIcon,
  PlusIcon,
  Trash2Icon,
  XIcon,
} from "lucide-react";
import {
  useCallback,
  useMemo,
  useRef,
  useState,
  type ChangeEvent,
} from "react";

import type {
  ScenarioSummary,
  ScenarioUpdateInput,
} from "../types";
import { RESERVED_SCENARIO_METADATA_KEYS } from "../types";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  Empty,
  EmptyContent,
  EmptyDescription,
  EmptyHeader,
  EmptyTitle,
} from "@/components/ui/empty";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";

type ScenarioLibraryPanelProps = {
  scenarios: ScenarioSummary[];
  loading: boolean;
  error: string | null;
  selectedScenarioFileName: string | null;
  onScenarioSelect: (scenario: ScenarioSummary) => void;
  onImportScenario: (file: File) => Promise<ScenarioSummary>;
  onDeleteScenario: (scenario: ScenarioSummary) => Promise<void>;
  onUpdateScenario: (
    scenario: ScenarioSummary,
    updates: ScenarioUpdateInput
  ) => Promise<ScenarioSummary>;
  className?: string;
};

const RESERVED_METADATA_KEY_SET = new Set<string>(RESERVED_SCENARIO_METADATA_KEYS);

type ScenarioMetadataEntry = {
  id: string;
  key: string;
  value: string;
};

type ScenarioEditFormState = {
  name: string;
  description: string;
  author: string;
  metadataEntries: ScenarioMetadataEntry[];
};

let metadataEntryCounter = 0;

function nextMetadataEntryId() {
  metadataEntryCounter += 1;
  return `metadata-entry-${metadataEntryCounter}`;
}

function createMetadataEntry(key = "", value = ""): ScenarioMetadataEntry {
  return {
    id: nextMetadataEntryId(),
    key,
    value,
  };
}

export function ScenarioLibraryPanel({
  scenarios,
  loading,
  error,
  selectedScenarioFileName,
  onScenarioSelect,
  onImportScenario,
  onDeleteScenario,
  onUpdateScenario,
  className,
}: ScenarioLibraryPanelProps) {
  const [uploading, setUploading] = useState(false);
  const [importError, setImportError] = useState<string | null>(null);
  const [deletingFileName, setDeletingFileName] = useState<string | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [editingScenario, setEditingScenario] = useState<ScenarioSummary | null>(null);
  const [editForm, setEditForm] = useState<ScenarioEditFormState | null>(null);
  const [editError, setEditError] = useState<string | null>(null);
  const [savingScenario, setSavingScenario] = useState(false);

  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const handleUpload = useCallback(
    async (file: File | undefined | null) => {
      if (!file) {
        return;
      }
      setUploading(true);
      setImportError(null);
      try {
        await onImportScenario(file);
      } catch (err) {
        setImportError(
          err instanceof Error ? err.message : "Failed to import scenario."
        );
      } finally {
        setUploading(false);
      }
    },
    [onImportScenario]
  );

  const handleFileInputChange = useCallback(
    (event: ChangeEvent<HTMLInputElement>) => {
      const file = event.target.files?.[0];
      event.target.value = "";
      void handleUpload(file);
    },
    [handleUpload]
  );


  const handleImportClick = useCallback(() => {
    fileInputRef.current?.click();
  }, []);

  const handleScenarioDelete = useCallback(
    async (scenario: ScenarioSummary) => {
      const confirmed = window.confirm(
        `Are you sure you want to delete "${scenario.name}"?`
      );
      if (!confirmed) {
        return;
      }

      setDeletingFileName(scenario.fileName);
      setDeleteError(null);
      try {
        await onDeleteScenario(scenario);
      } catch (err) {
        setDeleteError(
          err instanceof Error ? err.message : "Failed to delete scenario."
        );
      } finally {
        setDeletingFileName(null);
      }
    },
    [onDeleteScenario]
  );

  const handleScenarioEdit = useCallback((scenario: ScenarioSummary) => {
    setEditingScenario(scenario);
    setEditForm({
      name: scenario.name,
      description: scenario.description ?? "",
      author: scenario.author ?? "",
      metadataEntries: metadataEntriesFromRecord(scenario.metadata),
    });
    setEditError(null);
  }, []);

  const handleEditDialogClose = useCallback(() => {
    setEditingScenario(null);
    setEditForm(null);
    setEditError(null);
    setSavingScenario(false);
  }, []);

  const handleFieldChange = useCallback(
    (field: keyof Pick<ScenarioEditFormState, "name" | "description" | "author">, value: string) => {
      setEditForm((previous) => (previous ? { ...previous, [field]: value } : previous));
    },
    [],
  );

  const handleMetadataEntryChange = useCallback(
    (entryId: string, field: keyof Pick<ScenarioMetadataEntry, "key" | "value">, value: string) => {
      setEditForm((previous) => {
        if (!previous) {
          return previous;
        }
        return {
          ...previous,
          metadataEntries: previous.metadataEntries.map((entry) =>
            entry.id === entryId ? { ...entry, [field]: value } : entry,
          ),
        };
      });
    },
    [],
  );

  const handleMetadataEntryAdd = useCallback(() => {
    setEditForm((previous) => {
      if (!previous) {
        return previous;
      }
      return {
        ...previous,
        metadataEntries: [...previous.metadataEntries, createMetadataEntry()],
      };
    });
  }, []);

  const handleMetadataEntryRemove = useCallback((entryId: string) => {
    setEditForm((previous) => {
      if (!previous) {
        return previous;
      }
      return {
        ...previous,
        metadataEntries: previous.metadataEntries.filter((entry) => entry.id !== entryId),
      };
    });
  }, []);

  const handleEditSubmit = useCallback(async () => {
    if (!editingScenario || !editForm) {
      return;
    }

    const trimmedName = editForm.name.trim();
    if (!trimmedName) {
      setEditError("Scenario name is required.");
      return;
    }

    const metadataPayload = buildMetadataPayload(editForm.metadataEntries);
    const updates: ScenarioUpdateInput = {
      name: trimmedName,
      description: editForm.description.trim() === "" ? null : editForm.description,
      author: editForm.author.trim() === "" ? null : editForm.author,
      metadata: metadataPayload,
    };

    setSavingScenario(true);
    setEditError(null);
    try {
      await onUpdateScenario(editingScenario, updates);
      handleEditDialogClose();
    } catch (err) {
      setEditError(err instanceof Error ? err.message : "Failed to update scenario.");
    } finally {
      setSavingScenario(false);
    }
  }, [editForm, editingScenario, handleEditDialogClose, onUpdateScenario]);

  const handleDialogOpenChange = useCallback(
    (open: boolean) => {
      if (!open) {
        handleEditDialogClose();
      }
    },
    [handleEditDialogClose],
  );

  const activeMap = useMemo(() => {
    const map = new Map<string, boolean>();
    if (selectedScenarioFileName) {
      map.set(selectedScenarioFileName, true);
    }
    return map;
  }, [selectedScenarioFileName]);

  const renderScenarioList = () => {
    if (loading) {
      return (
        <div className="rounded-lg border border-muted/60 bg-muted/20 px-4 py-6 text-center text-sm text-muted-foreground">
          Loading scenarios…
        </div>
      );
    }

    if (scenarios.length === 0) {
      return (
        <Empty className="border border-dashed border-muted/70 bg-transparent text-foreground">
          <EmptyHeader>
            <EmptyTitle>No scenarios yet</EmptyTitle>
            <EmptyDescription>
              Import a YAML file to get started.
            </EmptyDescription>
          </EmptyHeader>
          <EmptyContent>
            <Button onClick={handleImportClick} className="w-full" disabled={uploading}>
              Import Scenario
            </Button>
          </EmptyContent>
        </Empty>
      );
    }

    return (
      <div className="divide-y divide-muted/40 rounded-lg border border-muted/60">
        {scenarios.map((scenario) => {
          const isActive = activeMap.get(scenario.fileName);
          const isDeleting = deletingFileName === scenario.fileName;
          return (
            <div
              key={scenario.fileName}
              role="button"
              tabIndex={0}
              onClick={() => onScenarioSelect(scenario)}
              onKeyDown={(event) => {
                if (event.key === "Enter" || event.key === " ") {
                  event.preventDefault();
                  onScenarioSelect(scenario);
                }
              }}
              className={cn(
                "flex w-full items-start gap-3 p-3 text-left transition hover:bg-muted/40 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary",
                isActive &&
                  "bg-primary/10 rounded border border-primary hover:bg-primary/20"
              )}
            >
              <div className="flex flex-1 flex-col gap-1 min-w-0">
                <div className="flex items-center justify-between gap-3">
                  <span
                    className={cn(
                      "text-sm font-semibold text-foreground",
                      isActive && "text-primary"
                    )}
                  >
                    {scenario.name}
                  </span>
                  {scenario.author ? (
                    <span
                      className={cn(
                        "text-xs font-medium uppercase tracking-wide text-muted-foreground",
                        isActive && "text-primary/60"
                      )}
                    >
                      {scenario.author}
                    </span>
                  ) : null}
                </div>
                <span className="text-xs text-muted-foreground">
                  {scenario.description ?? "No description provided."}
                </span>
                <span className="text-[11px] font-mono uppercase tracking-wide text-muted-foreground/80">
                  {scenario.fileName}
                </span>
              </div>
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button
                    variant="ghost"
                    size="icon"
                    onClick={(event) => event.stopPropagation()}
                    disabled={isDeleting}
                    aria-label={`Scenario actions for ${scenario.name}`}
                  >
                    {isDeleting ? (
                      <Loader2Icon className="h-4 w-4 animate-spin text-muted-foreground" />
                    ) : (
                      <MoreVerticalIcon className="h-4 w-4" />
                    )}
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" className="w-44">
                  <DropdownMenuItem
                    onSelect={() => {
                      handleScenarioEdit(scenario);
                    }}
                  >
                    <PencilIcon className="h-4 w-4" />
                    Edit details
                  </DropdownMenuItem>
                  <DropdownMenuItem
                    variant="destructive"
                    onSelect={() => {
                      void handleScenarioDelete(scenario);
                    }}
                  >
                    <Trash2Icon className="h-4 w-4" />
                    Delete scenario
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          );
        })}
      </div>
    );
  };

  const isEditDialogOpen = Boolean(editingScenario && editForm);

  return (
    <>
      <ScrollArea className="h-full">
        <div
          className={cn(
            "flex h-full flex-col gap-4 bg-background p-4",
            className
          )}
        >
          <div className="space-y-2">
            {error ? <StatusAlert message={error} title="Unable to load scenarios" /> : null}
            {importError ? <StatusAlert message={importError} title="Import failed" /> : null}
            {deleteError ? <StatusAlert message={deleteError} title="Delete failed" /> : null}
          </div>

          {renderScenarioList()}


          <input
            ref={fileInputRef}
            type="file"
            accept=".yaml,.yml"
            className="hidden"
            onChange={handleFileInputChange}
          />
        </div>
      </ScrollArea>
      <Dialog open={isEditDialogOpen} onOpenChange={handleDialogOpenChange}>
        {isEditDialogOpen && editForm && editingScenario ? (
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Edit scenario</DialogTitle>
              <DialogDescription>
                Update details for{" "}
                <span className="font-semibold text-foreground">
                  {editingScenario.name}
                </span>{" "}
                ({editingScenario.fileName})
              </DialogDescription>
            </DialogHeader>
            <div className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="scenario-name">Name</Label>
                <Input
                  id="scenario-name"
                  value={editForm.name}
                  onChange={(event) => handleFieldChange("name", event.target.value)}
                  autoFocus
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="scenario-author">Author</Label>
                <Input
                  id="scenario-author"
                  value={editForm.author}
                  placeholder="Optional"
                  onChange={(event) => handleFieldChange("author", event.target.value)}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="scenario-description">Description</Label>
                <Textarea
                  id="scenario-description"
                  value={editForm.description}
                  onChange={(event) => handleFieldChange("description", event.target.value)}
                  rows={3}
                  placeholder="Optional"
                />
              </div>
              <div className="space-y-2">
                <div className="space-y-1">
                  <Label>Metadata</Label>
                  <p className="text-xs text-muted-foreground">
                    Custom key/value pairs stored on the scenario. Values parse as JSON when possible.
                  </p>
                </div>
                {editForm.metadataEntries.length === 0 ? (
                  <p className="text-xs text-muted-foreground">
                    No metadata entries yet. Add one to store extra context.
                  </p>
                ) : (
                  <div className="space-y-2">
                    {editForm.metadataEntries.map((entry) => (
                      <div key={entry.id} className="flex flex-wrap items-center gap-2">
                        <Input
                          className="min-w-[120px] flex-1"
                          placeholder="Key"
                          value={entry.key}
                          onChange={(event) =>
                            handleMetadataEntryChange(entry.id, "key", event.target.value)
                          }
                        />
                        <Input
                          className="min-w-[160px] flex-1"
                          placeholder='Value (e.g. beta or {"flag":true})'
                          value={entry.value}
                          onChange={(event) =>
                            handleMetadataEntryChange(entry.id, "value", event.target.value)
                          }
                        />
                        <Button
                          type="button"
                          variant="ghost"
                          size="icon"
                          className="flex-shrink-0"
                          onClick={() => handleMetadataEntryRemove(entry.id)}
                          aria-label="Remove metadata entry"
                        >
                          <XIcon className="h-4 w-4" />
                        </Button>
                      </div>
                    ))}
                  </div>
                )}
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={handleMetadataEntryAdd}
                  className="w-full sm:w-auto"
                >
                  <PlusIcon className="mr-2 h-4 w-4" />
                  Add metadata entry
                </Button>
              </div>
              {editError ? (
                <Alert variant="destructive" className="text-xs">
                  <AlertTitle>Update failed</AlertTitle>
                  <AlertDescription>{editError}</AlertDescription>
                </Alert>
              ) : null}
            </div>
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={handleEditDialogClose}
                disabled={savingScenario}
              >
                Cancel
              </Button>
              <Button
                type="button"
                onClick={handleEditSubmit}
                disabled={savingScenario || editForm.name.trim() === ""}
              >
                {savingScenario ? (
                  <Loader2Icon className="mr-2 h-4 w-4 animate-spin" />
                ) : null}
                Save changes
              </Button>
            </DialogFooter>
          </DialogContent>
        ) : null}
      </Dialog>
    </>
  );
}

type StatusAlertProps = {
  title: string;
  message: string;
};

function metadataEntriesFromRecord(metadata: ScenarioSummary["metadata"]): ScenarioMetadataEntry[] {
  if (!metadata) {
    return [];
  }

  return Object.entries(metadata)
    .filter(([key]) => !RESERVED_METADATA_KEY_SET.has(key))
    .map(([key, value]) => createMetadataEntry(key, formatMetadataValue(value)));
}

function buildMetadataPayload(entries: ScenarioMetadataEntry[]): Record<string, unknown> {
  return entries.reduce<Record<string, unknown>>((acc, entry) => {
    const normalizedKey = entry.key.trim();
    if (!normalizedKey) {
      return acc;
    }
    acc[normalizedKey] = parseMetadataValueInput(entry.value);
    return acc;
  }, {});
}

function formatMetadataValue(value: unknown): string {
  if (typeof value === "string") {
    return value;
  }
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
}

function parseMetadataValueInput(value: string): unknown {
  const trimmed = value.trim();
  if (!trimmed) {
    return "";
  }
  try {
    return JSON.parse(trimmed);
  } catch {
    return value;
  }
}

function StatusAlert({ title, message }: StatusAlertProps) {
  return (
    <Alert variant="destructive" className="text-xs">
      <AlertTitle>{title}</AlertTitle>
      <AlertDescription>{message}</AlertDescription>
    </Alert>
  );
}
