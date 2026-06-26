# True Scalar Mode Design

## Goal

Add a mathematically explicit `True Scalar` optimization mode to the app, keep a separate `Pareto Compromise` mode, keep `Pareto Front`, and propagate the distinction consistently across optimization flow, charts, result summaries, reports, and exported artifacts.

This fixes the current semantic mismatch where the scalar surface charts display `fObjW` but the default optimization mode no longer maximizes that same scalar objective.

## Problem Statement

The app currently exposes two optimization modes:

- `BEST_COMPROMISE`
- `PARETO_FRONT`

However, `BEST_COMPROMISE` is not a true scalar optimizer anymore. It computes a Pareto front and then selects a compromise point from that front using a weight-aware compromise selector. This means:

- the mode name is misleading
- the selected point is not guaranteed to maximize the plotted scalar surface `fObjW`
- the scalar chart marker can legitimately differ from the visible surface peak
- reports/results can be misunderstood by the user as "the scalar optimum"

The app therefore lacks a real single-objective mode aligned with the visible scalar chart.

## Design Summary

The app will expose exactly three optimization modes:

- `TRUE_SCALAR`
- `PARETO_COMPROMISE`
- `PARETO_FRONT`

Default mode becomes `TRUE_SCALAR`.

Mode semantics:

- `TRUE_SCALAR`
  - directly optimize the scalar objective `fObjW`
  - apply the winning `P1..P4` to live inputs
  - refresh simulation/results/charts
  - store a dedicated scalar snapshot/marker
- `PARETO_COMPROMISE`
  - compute the Pareto front
  - select one compromise point from it using the existing weight-aware selector
  - apply that plan to live inputs
  - refresh simulation/results/charts
  - store a dedicated compromise snapshot/marker
- `PARETO_FRONT`
  - compute the full feasible front
  - compute the current reference point from the cached front
  - apply the current reference point to live inputs for consistency with simulation/results
  - keep the full front explorable in the Pareto explorer
  - allow inspect first, explicit apply second inside the explorer

This makes the scalar surface mathematically aligned with the scalar optimizer again, while preserving the multiobjective decision-support workflow.

## Architecture

### 1. Optimization Mode Model

`OptimizationMode` will be expanded from two states to three:

- `TRUE_SCALAR`
- `PARETO_COMPROMISE`
- `PARETO_FRONT`

All UI, summaries, chart marker labels, report exports, and preview/sample code that switch on `OptimizationMode` must be updated exhaustively.

`BEST_COMPROMISE` is not kept as a visible label because it is mathematically ambiguous. The user-facing name becomes `Pareto Compromise`.

### 2. Optimization Engines

The app will explicitly own two different optimization mechanisms:

- scalar optimization engine
- Pareto optimization engine

The existing scalar optimizer path in `OptimizationLogic.kt` becomes the engine for `TRUE_SCALAR`.

The existing Pareto path in `ParetoOptimizationLogic.kt` remains the engine for:

- `PARETO_COMPROMISE`
- `PARETO_FRONT`

The selection logic in `CompromiseSelectionLogic.kt` remains used only for:

- `PARETO_COMPROMISE`
- weight-driven reference reselection in `PARETO_FRONT`

It is not used in `TRUE_SCALAR`.

### 3. ViewModel Ownership

`FinancialViewModel` remains the single state owner for:

- active optimization mode
- latest optimization result summary
- latest Pareto front
- latest scalar snapshot
- latest Pareto compromise snapshot
- latest Pareto reference snapshot
- current selected Pareto point
- current applied Pareto point/snapshot

Snapshot ownership must remain mode-specific. Running one mode must not silently destroy the last meaningful snapshot from another mode unless that artifact is structurally invalidated.

## Detailed Behavior

### 1. Optimize Button

#### `TRUE_SCALAR`

When the user presses optimize:

- run the scalar optimizer against `fObjW`
- obtain the scalar-optimal `P1..P4`
- apply them to live `inputs`
- update `uiInputs`
- rerun simulation/objective state
- refresh charts and markers
- save an optimization result tagged as `TRUE_SCALAR`
- update the scalar marker snapshot used by charts

The resulting marker is expected to match the scalar objective, subject only to chart grid discretization and slicing effects.

#### `PARETO_COMPROMISE`

When the user presses optimize:

- compute the Pareto front
- choose one compromise point using the weight-aware selector
- apply that compromise plan to live `inputs`
- update `uiInputs`
- rerun simulation/objective state
- refresh charts and markers
- save an optimization result tagged as `PARETO_COMPROMISE`
- keep the front available for summaries/explorer use

#### `PARETO_FRONT`

When the user presses optimize:

- compute the Pareto front
- compute the current reference point from that front
- apply the reference point to live `inputs`
- update `uiInputs`
- rerun simulation/objective state
- refresh charts and markers
- save an optimization result tagged as `PARETO_FRONT`
- keep the full front available in the explorer

This preserves consistency with the rest of the app: after optimize, visible `P1..P4` and subsequent `Simulation` runs use the same live plan.

### 2. Chart Weight Slider

The scalar and Pareto modes react differently by design.

#### `TRUE_SCALAR`

On chart slider release:

- update the current weight `w`
- rerun scalar optimization
- reapply the new scalar-optimal plan
- refresh simulation/results/charts

This keeps the scalar marker tied to the scalar objective being plotted.

#### `PARETO_COMPROMISE`

On chart slider release:

- update the current weight `w`
- rerun the compromise-selection workflow
- if the front must be recomputed due to the current implementation structure, do so
- apply the selected compromise
- refresh simulation/results/charts

#### `PARETO_FRONT`

On chart slider release:

- update the current weight `w`
- do not rebuild the front if only the preference weight changed
- reselect the reference point from the cached front
- update the reference marker
- if the currently applied plan was still the reference-driven plan, reapply the new reference
- if the user had manually selected/applied another Pareto point, preserve that distinction

This keeps Pareto mathematically independent from preference reweighting while preserving understandable UI behavior.

## Charts

### 1. Scalar Surface Charts

Scalar surfaces continue to plot `fObjW`.

Markers must remain visually distinct by mode:

- current inputs
- true scalar optimum
- Pareto compromise
- Pareto reference

The chart must not imply that all markers optimize the same target. Labels and legend entries must make the role explicit.

The `TRUE_SCALAR` marker is the only one that claims to maximize the plotted scalar objective.

### 2. Pareto Explorer

The existing dedicated Pareto section inside `Charts` remains.

It continues to show:

- Pareto front points
- reference marker
- applied marker
- selected marker

The explorer remains inspect-first:

- clicking a point selects it
- applying requires explicit user action

This behavior remains specific to `PARETO_FRONT`.

`PARETO_COMPROMISE` may still expose the front summary/reference info, but it does not become a front-exploration mode.

## Results And Exports

### 1. Main Results Card

Result summaries must explicitly state what happened:

- `TRUE_SCALAR`: scalar optimum applied
- `PARETO_COMPROMISE`: Pareto compromise applied
- `PARETO_FRONT`: Pareto reference applied, full front available

No summary text should imply that a Pareto compromise is a scalar optimum.

### 2. PDF Export / Reports

Exports must reflect the new modes consistently:

- mode title
- descriptive text
- interpretation of the selected plan

Expected semantics:

- `TRUE_SCALAR`: "optimized directly for scalar objective `fObjW`"
- `PARETO_COMPROMISE`: "selected from the Pareto front using the compromise rule"
- `PARETO_FRONT`: "reference point from the Pareto front applied; full front retained for analysis"

Agent/report prompts that mention optimization mode must be updated to use the new terminology where relevant.

## State Persistence

The live source of truth remains the currently applied `inputs`.

Profiles continue to save/load the editable financial state, not the entire optimization artifact set unless already designed elsewhere.

Optimization mode should remain consistent with the current app settings behavior. If mode is already stored or restored anywhere, the new enum values must be handled safely and exhaustively.

Snapshot persistence across a running session should remain mode-specific:

- scalar snapshot
- compromise snapshot
- Pareto reference snapshot

These snapshots are chart/report helpers and must not become the primary state source over live inputs.

## Error Handling

### 1. No Feasible Pareto Front

If the Pareto front is empty:

- do not overwrite live inputs
- show explicit no-feasible-front feedback
- clear only Pareto-specific derived artifacts that are no longer valid
- keep scalar artifacts intact

### 2. Scalar Optimization Failure

If scalar optimization fails or returns no valid candidate:

- do not overwrite live inputs
- do not fabricate a scalar snapshot
- show a mode-specific failure message

### 3. Structural Input Changes

If inputs change in a way that invalidates cached fronts or snapshots:

- invalidate only the affected optimization artifacts
- avoid wiping unrelated valid state from other modes unless required

## Testing Strategy

The implementation must follow TDD.

### Required Regression Coverage

1. `OptimizationMode` supports all three cases exhaustively.
2. `TRUE_SCALAR` uses the scalar optimizer path, not Pareto compromise selection.
3. `PARETO_COMPROMISE` uses the Pareto front plus compromise selector.
4. `PARETO_FRONT` retains full-front behavior and reference application.
5. Chart weight release:
   - reruns scalar optimization in `TRUE_SCALAR`
   - reruns compromise logic in `PARETO_COMPROMISE`
   - only reselects cached front in `PARETO_FRONT` when appropriate
6. Results summaries and exported mode labels reflect the new names and semantics.
7. Scalar marker snapshots remain separate from Pareto compromise/reference snapshots.

### Verification

At minimum:

- targeted unit tests for new mode semantics
- full `testDebugUnitTest`
- `assembleDebug`
- IDE diagnostics on touched files

## Files Expected To Change

Likely touched files include:

- `app/src/main/java/com/example/daysurpopt/domain/ParetoModels.kt`
- `app/src/main/java/com/example/daysurpopt/ui/screens/FinancialViewModel.kt`
- `app/src/main/java/com/example/daysurpopt/ui/screens/FinancialCalculatorScreen.kt`
- `app/src/main/java/com/example/daysurpopt/ui/screens/ChartsScreen.kt`
- `app/src/main/java/com/example/daysurpopt/logic/OptimizationLogic.kt`
- `app/src/main/java/com/example/daysurpopt/logic/CompromiseSelectionLogic.kt`
- `app/src/main/java/com/example/daysurpopt/logic/PdfExporter.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-it/strings.xml`
- `app/src/main/res/values-es/strings.xml`
- targeted unit test files under `app/src/test/java/com/example/daysurpopt/...`

## Chosen Design Decisions

The following decisions are fixed for implementation:

- visible modes are exactly:
  - `True Scalar`
  - `Pareto Compromise`
  - `Pareto Front`
- default mode is `True Scalar`
- this is an additive mode split, not a hidden sub-toggle
- `Pareto Compromise` is kept as a first-class mode
- `PARETO_FRONT` still applies the current reference point to live inputs after optimize
- chart legends/labels/results/exports must distinguish the three modes explicitly

