# Pareto Apply Behavior And Dedicated Pareto Chart Design

## Goal

Fix the current Pareto optimization flow so it behaves consistently with scalar/best-compromise optimization in the main user workflow, and add a dedicated Pareto visualization section inside the existing `Charts` screen.

The user-facing goals are:

1. Running optimization in `PARETO_FRONT` mode must update the live `P1..P4` inputs just like scalar optimization does.
2. After Pareto optimization, simulation results, objective summaries, and charts must reflect the applied Pareto reference plan immediately.
3. The chart screen must include a dedicated Pareto visualization for inspecting the front, reference point, applied point, and knee-like trade-off behavior.
4. Clicking a Pareto point must select it for inspection only; applying it to the live plan must require an explicit action.

## Problem

The current implementation preserves the Pareto front and updates a chart-side Pareto reference snapshot, but it still treats Pareto optimization as a mostly reference-only mode.

This creates several user-visible problems:

- running Pareto optimize does not update the visible `P1..P4` input boxes
- the main simulation flow remains tied to old live inputs unless the user separately reapplies values
- results and chart markers can reflect a Pareto reference while the editable input state still reflects another plan
- there is no dedicated Pareto chart to inspect trade-offs, selected points, or the current compromise/reference point clearly

This mismatch makes Pareto mode hard to understand and inconsistent with scalar optimization.

## Decision

Adopt an apply-on-optimize Pareto workflow plus an inspect-first Pareto chart.

### Main Rule

When the user presses optimization in `PARETO_FRONT` mode:

- compute the feasible Pareto front
- select the current reference point using the weight-aware compromise selector
- apply that selected point into the live `inputs`
- update `uiInputs`
- recompute and publish simulation/objective state
- persist both the full front and the currently applied/reference snapshots

This makes Pareto mode behave like scalar optimization in the main workflow.

### Chart Rule

Inside the dedicated Pareto chart:

- clicking a point selects it for inspection
- selection alone does not modify live `P1..P4`
- applying the selected point happens through an explicit `Apply selected Pareto point` action

This gives the user a safe exploration workflow without accidental overwrites.

## Behavioral Design

### 1. Pareto Optimize Action

`runOptimization()` in `PARETO_FRONT` mode must:

1. compute the Pareto front
2. select the current reference point from the front using `bonusStdWeight`
3. apply the selected point to live inputs
4. update the visible input boxes
5. rerun simulation/objective state from the applied point
6. update `optimizationResult`
7. update Pareto snapshots and chart markers

The result is that:

- the current plan shown in the input boxes is the selected Pareto reference plan
- the results card reflects that same applied plan
- pressing `Simulation` afterwards uses the same plan

### 2. Weight Changes In Pareto Mode

Changing `w` on the chart page must not regenerate the Pareto front.

On slider release:

- recompute the reference point from the cached front using the new weight
- update the Pareto reference snapshot
- if there is no manual chart selection, align the selected chart point with the new reference
- leave the front itself unchanged

If the user has manually selected a different Pareto point in the chart, that manual selection remains highlighted until reset or apply.

### 3. Manual Selection Versus Applied Plan

The system must explicitly distinguish:

- `applied Pareto point`: the plan currently loaded in live `P1..P4`
- `reference Pareto point`: the current compromise/reference selected from the front under the active `w`
- `selected Pareto point`: the point the user is currently inspecting in the dedicated Pareto chart

These may match, but they are not always the same.

## State Design

Add explicit Pareto chart state in `FinancialViewModel`.

Suggested state:

```kotlin
var selectedParetoPoint by mutableStateOf<ParetoPoint?>(null)
    private set
var appliedParetoSnapshot by mutableStateOf<OptimizationMarkerSnapshot?>(null)
    private set
```

State rules:

- `paretoFrontResult.selectedCompromise` remains the current weight-driven reference point
- `selectedParetoPoint` is the point selected by chart interaction
- `appliedParetoSnapshot` represents the point currently loaded into live `inputs`
- when Pareto optimize runs, all three align to the new reference point
- when the user clicks another Pareto point in the chart, only `selectedParetoPoint` changes
- when the user applies the selected point, `inputs`, `uiInputs`, simulation state, and `appliedParetoSnapshot` update

Existing snapshots remain useful:

- `lastBestCompromiseSnapshot`
- `lastParetoReferenceSnapshot`

`lastParetoReferenceSnapshot` continues to represent the current reference point for marker rendering.

## Dedicated Pareto Chart

Add a new section inside `ChartsScreen`.

### Layout

Place it near the top of the existing charts page, before or between the current scalar exploration surfaces, with visibility gated by Pareto data availability.

### Main Visualization

Use a 2D scatter plot in objective space:

- X axis: `StdDevUtility`
- Y axis: `AvgUtility`

This is the clearest first-order Pareto trade-off view for the current two-objective formulation.

### Point Semantics

Render:

- all feasible Pareto front points
- current reference point
- currently applied point
- currently selected point

Recommended styling:

- base front points: muted neutral dots
- reference point: strong accent color
- applied point: different accent color
- selected point: larger outlined marker

If one point plays multiple roles, merge styling predictably, preferring:

`selected` > `applied` > `reference` > `base`

### Details Panel

Show a summary card for the currently selected point:

- `P1`
- `P2`
- `P3`
- `P4`
- `AvgUtility`
- `StdDev`
- `FinalCapital`
- `LegacyGap`
- `CompromiseScore`
- labels indicating whether the point is currently `Selected`, `Applied`, and/or `Reference`

### Controls

Add:

- `Apply selected Pareto point`
- `Reset selection to reference`

Optional future extension, not in current scope:

- linked decision-space view
- parallel coordinates
- raw/normalized metric toggle

## Why This Chart

Research and practical multi-objective tools consistently use objective-space scatter plots as the first view for understanding fronts, trade-offs, knees, and selected compromise points. More advanced linked views such as parallel coordinates and decision maps are powerful, but they are better added after the core objective-space view is correct and interactive.

Relevant references:

- ParetoLens visual analytics framework emphasizes coordinated decision-space and objective-space analysis for multi-objective solution sets.
- Interactive Decision Map style tools use objective scatter as a primary trade-off view with linked inspection panels.

For this app, the best first implementation is:

- one clean 2D Pareto front scatter
- explicit selected/applied/reference semantics
- explicit apply button

This gives high clarity with limited UI complexity.

## Data Flow

### Pareto Optimize

1. compute `ParetoFrontResult`
2. select weight-driven reference point
3. apply reference point to live `inputs`
4. rebuild `uiInputs`
5. recompute simulation/objectives
6. update snapshots:
   - `lastParetoReferenceSnapshot`
   - `appliedParetoSnapshot`
7. set `selectedParetoPoint` to the reference point

### Chart Selection

1. user clicks a Pareto point
2. update `selectedParetoPoint`
3. update details panel
4. do not update live inputs or simulation yet

### Apply Selected Point

1. read `selectedParetoPoint`
2. apply it to live `inputs`
3. rebuild `uiInputs`
4. recompute simulation/objectives
5. update `appliedParetoSnapshot`
6. keep `paretoFrontResult` intact

### Weight Slider In Pareto Mode

1. keep cached front
2. recompute reference point only
3. update `paretoFrontResult.selectedCompromise`
4. update `lastParetoReferenceSnapshot`
5. if current selection is still auto/reference-linked, move selection too
6. if user manually selected another point, keep manual selection unchanged

## Error Handling

- If no feasible Pareto front exists:
  - show no Pareto scatter
  - show a clear empty-state message
  - do not overwrite current live inputs
- If the front is invalidated by structural input changes:
  - clear Pareto chart selection state
  - require a new optimization run
- If the user presses apply with no selected point:
  - no-op or disable the button
- If applying a selected point fails validation:
  - keep existing live inputs unchanged
  - show a clear error message

## Testing

Add focused tests for:

1. `PARETO_FRONT` optimize applies the selected reference point into live `inputs`
2. `PARETO_FRONT` optimize updates `uiInputs`
3. `PARETO_FRONT` optimize refreshes `simulationResults` and `objectiveResults`
4. Pareto chart selection changes inspection state only
5. explicit apply action copies selected Pareto point into live inputs
6. weight changes in Pareto mode do not rebuild the front, only reselection/reference state
7. marker rendering distinguishes current inputs, best compromise, Pareto reference, and applied Pareto point correctly

## Acceptance Criteria

- Running optimize in `PARETO_FRONT` visibly updates `P1..P4` boxes
- Main results and simulation table reflect the applied Pareto reference plan immediately
- Clicking `Simulation` after Pareto optimize uses the same newly applied plan
- Chart markers update consistently after Pareto optimize
- `Charts` includes a dedicated Pareto section with objective-space scatter
- Clicking a Pareto point selects it for inspection only
- `Apply selected Pareto point` explicitly applies the selected point to live state
- Changing `w` in Pareto mode does not rebuild the front, only the current reference selection
