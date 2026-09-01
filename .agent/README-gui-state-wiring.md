# README — GUI state wiring (live inputs rule)

Frozen architecture rule after the scheduled-expenses incident (2026-08-05). Applies to every
input screen (user data, surplus, scheduled expenses, assumption curves, GA config).

## The incident (root cause)

`SpecificExpensesScreen` kept its OWN local `remember { mutableStateOf }` list and persisted
it directly to SharedPreferences via a `LaunchedEffect`. `FinancialViewModel.updateSpecificExpenses`
existed but was DEAD CODE: the simulation, GA and agent kept using the stale list until app
restart / reset / profile load. Symptom: "expenses are hardcoded, not sensible to input changes".

## Golden rules (apply to any new input screen)

1. **Single source of truth**: screen state initializes FROM the ViewModel
   (`viewModel.<field>`), never from the repository.
2. **Every edit goes through the ViewModel update function** (`updateInputs`,
   `updateSpecificExpenses`, `updateSurplusData`, ...). Screens never write to
   SharedPreferences directly.
3. ViewModel update functions own the full side-effect chain: no-op skip when unchanged
   (see `expensesListsDiffer`), clear optimization artifacts + goal sweep, persist via the
   repository, `triggerRecalculation()`.
4. Analysis state (`clearAnalysisState`) is IN-MEMORY ONLY: "Erase Analysis Results" clears
   results, chart-marker snapshots (`lastTrueScalar/Compromise/Reference`), sensitivity,
   goal sweep and comparison deltas. It now asks confirmation (destructive action).
   Persisted data (inputs, profiles, curves, expenses, chats) is never touched by it.

## Diagnosis commands

```powershell
# Find screens bypassing the ViewModel (writing prefs directly):
.\gradlew --console=plain -q compileDebugKotlin
# grep for repository persistence inside ui/screens composables:
#   Select-String -Path app\src\main\java\com\example\daysurpopt\ui\screens\*.kt -Pattern "Repository\."
```

Key locks: `ScheduledExpensesFlowTest` (change detection via `expensesListsDiffer`),
`OptimizationModeFlowTest.clearAnalysisState_resets_results_front_and_markers` (erase
semantics including chart markers).

## Rollback

Not applicable (rule, not a component). Re-introducing screen-side persistence would
re-create the stale-simulation bug; do not merge such code.
