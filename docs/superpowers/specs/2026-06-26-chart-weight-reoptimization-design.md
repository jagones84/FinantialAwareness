## Goal

Fix chart-page optimization markers so they remain consistent with the last optimization run, and make weight changes on the chart page update the correct optimization-derived marker behavior.

The user-facing goals are:

1. When `w` changes from the chart slider, the chart surfaces refresh as they do now.
2. In `Best Compromise` mode, slider release reruns optimization and relocates the optimized marker.
3. In `Pareto Front` mode, the Pareto front itself remains independent of `w`, but the chart may refresh scalar overlays and the selected Pareto reference point may move if the compromise selector uses `w`.
4. If the user has run both optimization modes at different times, both optimized markers remain visible together with different colors so the user can compare scalar/best-compromise behavior against Pareto behavior.

## Problem

Current chart behavior mixes:

- chart-surface recomputation
- current-input marker placement
- optimization-summary values

without a clear separation between:

- the current editable inputs
- the last optimized point for `Best Compromise`
- the last Pareto-derived reference point

This causes a stale-marker problem:

- surfaces recompute after `w` changes
- but the "optimal" marker does not necessarily move to a newly optimized location
- and chart-side `optimalObjW` currently represents the objective of current inputs rather than a persisted optimized point snapshot

## Decision

Adopt a snapshot-based chart marker design.

The chart must display three categories of points:

1. `Current Inputs`
2. `Last Best Compromise`
3. `Last Pareto Reference`

Each optimized category stores its own last-known snapshot. They are not derived implicitly from whatever happens to be in current inputs.

## Behavioral Rules

### Weight Slider

Weight slider changes are handled in two stages:

1. While dragging:
   - update local/current `w`
   - do not rerun optimization continuously
2. On slider release:
   - refresh charts
   - trigger mode-specific optimization handling

This keeps the UI responsive and avoids repeated expensive optimization while sliding.

### Best Compromise Mode

On slider release:

- rerun optimization using the current `Best Compromise` mode
- update current inputs to the new compromise-selected parameters
- update the `Last Best Compromise` snapshot
- move the best-compromise marker on the chart

This is mathematically valid because in compromise mode the selected point depends on user preference weighting.

### Pareto Front Mode

On slider release:

- do not regenerate the Pareto front because the front itself is independent of `w`
- reuse the last computed Pareto front
- if the Pareto compromise/reference selector uses `w` as a user preference weight, recompute only the selected reference point from that existing front
- update the `Last Pareto Reference` snapshot

If no Pareto front has been computed yet, slider release in Pareto mode should not fabricate one. It should only refresh scalar surfaces and leave the Pareto snapshot absent.

### Marker Visibility

Markers shown on charts:

- `Current Inputs`
- `Last Best Compromise` if available
- `Last Pareto Reference` if available

If both optimized snapshots exist, both remain visible simultaneously.

## Marker Design

Recommended colors:

- `Current Inputs`: existing blue
- `Last Best Compromise`: green
- `Last Pareto Reference`: orange or purple

The label text must make the distinction explicit. Avoid generic labels like "Optimal".

Recommended labels:

- `Current Inputs`
- `Best Compromise`
- `Pareto Reference`

In compare-profile mode, existing compare markers can remain, but optimized marker labels must still distinguish mode/source clearly.

## State Design

Introduce explicit optimization snapshot state in `FinancialViewModel`.

Suggested model:

```kotlin
data class OptimizationMarkerSnapshot(
    val mode: OptimizationMode,
    val params: ParamsCandidate,
    val objectiveValue: Double,
    val avgUtility: Double,
    val stdDevUtility: Double,
    val stabilityIndex: Double,
    val weightUsed: Double,
    val compromiseScore: Double? = null
)
```

State fields:

- `lastBestCompromiseSnapshot: OptimizationMarkerSnapshot?`
- `lastParetoReferenceSnapshot: OptimizationMarkerSnapshot?`

Rules:

- `Best Compromise` optimization updates only `lastBestCompromiseSnapshot`
- `Pareto Front` optimization updates only `lastParetoReferenceSnapshot`
- changing generic inputs manually should not erase historical comparison markers unless the user explicitly resets or runs a new optimization

## Chart Data Flow

Current chart markers should stop deriving optimized positions from raw `inputsSnapshot`.

Instead:

- `Current Inputs` marker uses current inputs
- `Best Compromise` marker uses `lastBestCompromiseSnapshot`
- `Pareto Reference` marker uses `lastParetoReferenceSnapshot`

This ensures the chart can show two optimization outcomes from different runs at the same time.

## Reoptimization Hook

Add a dedicated entry point in `FinancialViewModel` for chart slider completion, for example:

```kotlin
fun onChartWeightChangeFinished()
```

Responsibilities:

1. trigger simulation refresh
2. if mode is `Best Compromise`, rerun full optimization
3. if mode is `Pareto Front`, recompute reference selection only from cached front
4. update marker snapshots accordingly

This avoids duplicating optimization policy inside `ChartsScreen`.

## What Stays Unchanged

- the yearly simulation engine
- scalar chart surfaces as exploratory surfaces
- Pareto front generation being independent of `w`

## Error Handling

- if no feasible Pareto front exists, `lastParetoReferenceSnapshot` remains null
- if chart slider changes `w` in Pareto mode before any Pareto optimization has been run, do not synthesize a Pareto marker
- if optimization fails to find a feasible best compromise, keep the previous snapshot until a successful replacement exists

## Testing

Add focused tests for:

1. `Best Compromise` chart weight release updates the best-compromise snapshot
2. `Pareto Front` chart weight release does not regenerate the front, but can update the reference selection from cached front
3. marker-building logic shows both stored optimized snapshots simultaneously
4. current inputs marker remains separate from stored optimized snapshots

## Acceptance Criteria

- chart slider release reruns optimization only when appropriate
- best-compromise marker relocates after `w` change
- Pareto front is not rebuilt just because `w` changed
- if both modes have been run, both optimized markers are visible together with distinct labels and colors
- chart summaries and marker semantics match actual optimization behavior
