"use client";

import {
  ArrowLeftIcon,
  ArrowRightIcon,
  BookOpenCheckIcon,
  CommandIcon,
  PanelsTopLeftIcon,
  PauseIcon,
  PlayIcon,
  Redo2Icon,
  RotateCcwIcon,
  UploadIcon,
  Undo2Icon,
  PencilIcon,
  Trash2Icon,
} from "lucide-react";
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ChangeEvent,
} from "react";

import { Button } from "@/components/ui/button";
import {
  CommandDialog,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
  CommandSeparator,
  CommandShortcut,
} from "@/components/ui/command";
import {
  Menubar,
  MenubarCheckboxItem,
  MenubarContent,
  MenubarItem,
  MenubarMenu,
  MenubarSeparator,
  MenubarShortcut,
  MenubarTrigger,
} from "@/components/ui/menubar";
import { ScenarioEditDialog } from "@/features/scenarios/components/ScenarioEditDialog";
import type {
  ScenarioSummary,
  ScenarioUpdateInput,
} from "@/features/scenarios/types";
import { useSimulation } from "@/features/simulation/providers/SimulationProvider";
import { type PanelKey, type PanelVisibilityState } from "@/lib/panels";

type AppBarProps = {
  panelVisibility: PanelVisibilityState;
  onTogglePanel: (panel: PanelKey) => void;
  scenarios: ScenarioSummary[];
  selectedScenario: ScenarioSummary | null;
  onScenarioSelect: (scenario: ScenarioSummary) => void;
  onImportScenario: (file: File) => Promise<ScenarioSummary>;
  onUpdateScenario: (
    scenario: ScenarioSummary,
    updates: ScenarioUpdateInput
  ) => Promise<ScenarioSummary>;
  onDeleteScenario: (scenario: ScenarioSummary) => Promise<void>;
};

const PANEL_ORDER: PanelKey[] = ["library", "viewer", "controller"];
const PANEL_LABELS: Record<PanelKey, string> = {
  library: "Scenario Library",
  viewer: "Simulation View",
  controller: "Controller",
};

export function AppBar({
  panelVisibility,
  onTogglePanel,
  scenarios,
  selectedScenario,
  onScenarioSelect,
  onImportScenario,
  onUpdateScenario,
  onDeleteScenario,
}: AppBarProps) {
  const {
    canStepBackward,
    canStepForward,
    canRedo,
    isPlaying,
    pause,
    play,
    redo,
    reset,
    stepBackward,
    stepForward,
    undo,
  } = useSimulation();

  const [commandOpen, setCommandOpen] = useState(false);
  const [importing, setImporting] = useState(false);
  const [importError, setImportError] = useState<string | null>(null);
  const [editDialogOpen, setEditDialogOpen] = useState(false);

  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const scenarioLabel = selectedScenario?.name ?? "";

  const handleImportRequest = useCallback(() => {
    fileInputRef.current?.click();
  }, []);

  const handleFileChange = useCallback(
    async (event: ChangeEvent<HTMLInputElement>) => {
      const file = event.target.files?.[0];
      event.target.value = "";
      if (!file) {
        return;
      }

      setImporting(true);
      setImportError(null);
      try {
        const scenario = await onImportScenario(file);
        onScenarioSelect(scenario);
      } catch (error) {
        setImportError(
          error instanceof Error ? error.message : "Failed to import scenario."
        );
      } finally {
        setImporting(false);
      }
    },
    [onImportScenario, onScenarioSelect]
  );

  const handleDeleteRequest = useCallback(async () => {
    if (!selectedScenario) return;

    const confirmed = window.confirm(
      `Are you sure you want to delete "${selectedScenario.name}"?`
    );
    if (!confirmed) return;

    try {
      await onDeleteScenario(selectedScenario);
    } catch (error) {
      alert(
        error instanceof Error ? error.message : "Failed to delete scenario."
      );
    }
  }, [selectedScenario, onDeleteScenario]);

  useEffect(() => {
    function handleKeydown(event: KeyboardEvent) {
      if (
        event.target instanceof HTMLElement &&
        (event.target.tagName === "INPUT" ||
          event.target.tagName === "TEXTAREA" ||
          event.target.isContentEditable)
      ) {
        return;
      }

      const isModifier = event.metaKey || event.ctrlKey;
      if (isModifier && !event.shiftKey && event.key.toLowerCase() === "o") {
        event.preventDefault();
        handleImportRequest();
      } else if (isModifier && event.key.toLowerCase() === "k") {
        event.preventDefault();
        setCommandOpen((previous) => !previous);
      }
    }

    window.addEventListener("keydown", handleKeydown);
    return () => window.removeEventListener("keydown", handleKeydown);
  }, [handleImportRequest]);

  const commandQuickActions = useMemo(() => {
    const playPauseAction = {
      label: isPlaying ? "Pause Simulation" : "Play Simulation",
      icon: isPlaying ? PauseIcon : PlayIcon,
      action: isPlaying ? pause : play,
      disabled: !isPlaying && !canStepForward,
      shortcut: "Space",
    };

    return [
      {
        label: "Import Scenario",
        icon: UploadIcon,
        action: handleImportRequest,
        disabled: importing,
        shortcut: "⌘O",
      },
      {
        label: "Edit Active Scenario",
        icon: PencilIcon,
        action: () => setEditDialogOpen(true),
        disabled: !selectedScenario,
      },
      {
        label: "Delete Active Scenario",
        icon: Trash2Icon,
        action: handleDeleteRequest,
        disabled: !selectedScenario,
      },
      playPauseAction,
      {
        label: "Step Forward",
        icon: ArrowRightIcon,
        action: stepForward,
        disabled: !canStepForward,
        shortcut: "→",
      },
      {
        label: "Step Backward",
        icon: ArrowLeftIcon,
        action: stepBackward,
        disabled: !canStepBackward,
        shortcut: "←",
      },
      {
        label: "Undo",
        icon: Undo2Icon,
        action: undo,
        disabled: !canStepBackward,
        shortcut: "⌘Z",
      },
      {
        label: "Redo",
        icon: Redo2Icon,
        action: redo,
        disabled: !canRedo,
        shortcut: "⇧⌘Z",
      },
      {
        label: "Reset Simulation",
        icon: RotateCcwIcon,
        action: reset,
      },
    ];
  }, [
    canRedo,
    canStepBackward,
    canStepForward,
    handleImportRequest,
    handleDeleteRequest,
    importing,
    isPlaying,
    pause,
    play,
    redo,
    reset,
    stepBackward,
    stepForward,
    undo,
    selectedScenario,
  ]);

  const handleScenarioActivate = useCallback(
    (scenario: ScenarioSummary) => {
      onScenarioSelect(scenario);
      setCommandOpen(false);
    },
    [onScenarioSelect]
  );

  const handleQuickActionSelect = useCallback((action: () => void) => {
    action();
    setCommandOpen(false);
  }, []);

  return (
    <div className="border-b border-border bg-background/90 px-4 py-1 text-sm backdrop-blur supports-backdrop-filter:bg-background/70">
      <div className="flex items-center gap-3">
        <div className="hidden min-w-0 flex-col text-xs sm:flex">
          <span
            className="truncate text-sm font-semibold text-primary"
            title={scenarioLabel}
          >
            {scenarioLabel}
          </span>
          {importError ? (
            <span className="text-[11px] text-red-500">{importError}</span>
          ) : null}
        </div>
        <Menubar className="flex-1 rounded-none border-none bg-transparent p-0 shadow-none">
          <MenubarMenu>
            <MenubarTrigger>File</MenubarTrigger>
            <MenubarContent>
              <MenubarItem disabled>New Scenario</MenubarItem>
              <MenubarItem disabled={importing} onSelect={handleImportRequest}>
                Open New Scenario <MenubarShortcut>⌘O</MenubarShortcut>
              </MenubarItem>
              <MenubarSeparator />
              <MenubarItem
                disabled={!selectedScenario}
                onSelect={() => setEditDialogOpen(true)}
              >
                Edit Scenario
              </MenubarItem>
              <MenubarItem
                disabled={!selectedScenario}
                onSelect={handleDeleteRequest}
                className="text-red-600 focus:text-red-600"
              >
                Delete Scenario
              </MenubarItem>
              <MenubarSeparator />
              <MenubarItem onSelect={() => setCommandOpen(true)}>
                Command Palette <MenubarShortcut>⌘K</MenubarShortcut>
              </MenubarItem>
              <MenubarSeparator />
              <MenubarItem disabled>Share</MenubarItem>
            </MenubarContent>
          </MenubarMenu>

          <MenubarMenu>
            <MenubarTrigger>Scenarios</MenubarTrigger>
            <MenubarContent>
              {scenarios.length === 0 ? (
                <MenubarItem disabled>No scenarios available</MenubarItem>
              ) : (
                scenarios.map((scenario) => (
                  <MenubarItem
                    key={scenario.fileName}
                    onSelect={() => onScenarioSelect(scenario)}
                  >
                    {scenario.name}
                  </MenubarItem>
                ))
              )}
            </MenubarContent>
          </MenubarMenu>

          <MenubarMenu>
            <MenubarTrigger>Edit</MenubarTrigger>
            <MenubarContent>
              <MenubarItem disabled={!canStepBackward} onSelect={undo}>
                Undo <MenubarShortcut>⌘Z</MenubarShortcut>
              </MenubarItem>
              <MenubarItem disabled={!canRedo} onSelect={redo}>
                Redo <MenubarShortcut>⇧⌘Z</MenubarShortcut>
              </MenubarItem>
            </MenubarContent>
          </MenubarMenu>
          <MenubarMenu>
            <MenubarTrigger>View</MenubarTrigger>
            <MenubarContent>
              <MenubarItem
                disabled
                inset
                className="text-xs uppercase tracking-wide text-muted-foreground"
              >
                Panels
              </MenubarItem>
              <MenubarSeparator />
              {PANEL_ORDER.map((panel) => (
                <MenubarCheckboxItem
                  key={panel}
                  checked={panelVisibility[panel]}
                  onCheckedChange={() => onTogglePanel(panel)}
                >
                  {PANEL_LABELS[panel]}
                </MenubarCheckboxItem>
              ))}
            </MenubarContent>
          </MenubarMenu>
        </Menubar>

        <Button
          type="button"
          variant="ghost"
          size="sm"
          onClick={() => setCommandOpen(true)}
          className="inline-flex items-center gap-2 text-xs font-medium text-muted-foreground"
        >
          <CommandIcon className="h-4 w-4" />
          <span className="hidden sm:inline">Command</span>
          <kbd className="rounded bg-muted px-1.5 py-0.5 text-[10px] font-semibold text-muted-foreground">
            ⌘K
          </kbd>
        </Button>
      </div>

      <CommandDialog open={commandOpen} onOpenChange={setCommandOpen}>
        <CommandInput placeholder="Search scenarios, panels, or actions" />
        <CommandList>
          <CommandEmpty>No commands match your search.</CommandEmpty>
          {scenarios.length > 0 ? (
            <CommandGroup heading="Scenarios">
              {scenarios.map((scenario) => (
                <CommandItem
                  key={scenario.fileName}
                  value={`${scenario.name} ${scenario.fileName}`}
                  onSelect={() => handleScenarioActivate(scenario)}
                >
                  <BookOpenCheckIcon className="h-4 w-4" />
                  <div className="flex flex-col text-left">
                    <span className="text-sm font-medium text-foreground">
                      {scenario.name}
                    </span>
                    <span className="text-xs text-muted-foreground">
                      {scenario.fileName}
                    </span>
                  </div>
                  {selectedScenario?.fileName === scenario.fileName ? (
                    <CommandShortcut>Active</CommandShortcut>
                  ) : null}
                </CommandItem>
              ))}
            </CommandGroup>
          ) : null}

          <CommandGroup heading="Quick Actions">
            {commandQuickActions.map((action) => {
              const Icon = action.icon;
              return (
                <CommandItem
                  key={action.label}
                  disabled={action.disabled ?? false}
                  onSelect={() => handleQuickActionSelect(action.action)}
                >
                  <Icon className="h-4 w-4" />
                  <span>{action.label}</span>
                  {action.shortcut ? (
                    <CommandShortcut>{action.shortcut}</CommandShortcut>
                  ) : null}
                </CommandItem>
              );
            })}
          </CommandGroup>

          <CommandSeparator />
          <CommandGroup heading="Panels">
            {PANEL_ORDER.map((panel) => (
              <CommandItem
                key={panel}
                onSelect={() => {
                  onTogglePanel(panel);
                  setCommandOpen(false);
                }}
              >
                <PanelsTopLeftIcon className="h-4 w-4" />
                <span>{PANEL_LABELS[panel]}</span>
                <CommandShortcut>
                  {panelVisibility[panel] ? "Visible" : "Hidden"}
                </CommandShortcut>
              </CommandItem>
            ))}
          </CommandGroup>
        </CommandList>
      </CommandDialog>

      <input
        ref={fileInputRef}
        type="file"
        accept=".yaml,.yml"
        className="hidden"
        onChange={handleFileChange}
      />

      <ScenarioEditDialog
        open={editDialogOpen}
        onOpenChange={setEditDialogOpen}
        scenario={selectedScenario}
        onUpdate={onUpdateScenario}
      />
    </div>
  );
}
