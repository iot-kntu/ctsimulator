"use client";

import { Loader2Icon, PlusIcon, XIcon } from "lucide-react";
import { useCallback, useEffect, useState } from "react";

import type { ScenarioSummary, ScenarioUpdateInput } from "../types";
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
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";

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

type ScenarioEditDialogProps = {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    scenario: ScenarioSummary | null;
    onUpdate: (scenario: ScenarioSummary, updates: ScenarioUpdateInput) => Promise<ScenarioSummary>;
};

export function ScenarioEditDialog({
    open,
    onOpenChange,
    scenario,
    onUpdate,
}: ScenarioEditDialogProps) {
    const [editForm, setEditForm] = useState<ScenarioEditFormState | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        if (open && scenario) {
            setEditForm({
                name: scenario.name,
                description: scenario.description ?? "",
                author: scenario.author ?? "",
                metadataEntries: metadataEntriesFromRecord(scenario.metadata),
            });
            setError(null);
        } else {
            setEditForm(null);
        }
    }, [open, scenario]);

    const handleFieldChange = useCallback(
        (field: keyof Pick<ScenarioEditFormState, "name" | "description" | "author">, value: string) => {
            setEditForm((previous) => (previous ? { ...previous, [field]: value } : previous));
        },
        []
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
                        entry.id === entryId ? { ...entry, [field]: value } : entry
                    ),
                };
            });
        },
        []
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

    const handleSubmit = useCallback(async () => {
        if (!scenario || !editForm) {
            return;
        }

        const trimmedName = editForm.name.trim();
        if (!trimmedName) {
            setError("Scenario name is required.");
            return;
        }

        const metadataPayload = buildMetadataPayload(editForm.metadataEntries);
        const updates: ScenarioUpdateInput = {
            name: trimmedName,
            description: editForm.description.trim() === "" ? null : editForm.description,
            author: editForm.author.trim() === "" ? null : editForm.author,
            metadata: metadataPayload,
        };

        setSaving(true);
        setError(null);
        try {
            await onUpdate(scenario, updates);
            onOpenChange(false);
        } catch (err) {
            setError(err instanceof Error ? err.message : "Failed to update scenario.");
        } finally {
            setSaving(false);
        }
    }, [editForm, scenario, onUpdate, onOpenChange]);

    if (!scenario || !editForm) {
        return null;
    }

    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent>
                <DialogHeader>
                    <DialogTitle>Edit scenario</DialogTitle>
                    <DialogDescription>
                        Update details for{" "}
                        <span className="font-semibold text-foreground">{scenario.name}</span> (
                        {scenario.fileName})
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
                    {error ? (
                        <Alert variant="destructive" className="text-xs">
                            <AlertTitle>Update failed</AlertTitle>
                            <AlertDescription>{error}</AlertDescription>
                        </Alert>
                    ) : null}
                </div>
                <DialogFooter>
                    <Button
                        type="button"
                        variant="outline"
                        onClick={() => onOpenChange(false)}
                        disabled={saving}
                    >
                        Cancel
                    </Button>
                    <Button
                        type="button"
                        onClick={handleSubmit}
                        disabled={saving || editForm.name.trim() === ""}
                    >
                        {saving ? <Loader2Icon className="mr-2 h-4 w-4 animate-spin" /> : null}
                        Save changes
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}

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
