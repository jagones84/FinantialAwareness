## Goal

Replace the current ad-hoc scalar multiobjective optimization with a mathematically sound dual-mode design where the user can choose between:

1. `Pareto Front` mode: return a set of non-dominated solutions.
2. `Best Compromise` mode: return one automatically selected Pareto-optimal solution.

The new design must preserve the current simulation engine and profile logic as much as possible while making the optimization math cleaner, more interpretable, and more extensible.

## Why Change

The current scalar objective in `SimulationLogic.computeObjective(...)` is:

```text
(AvgUtility + (w/100) * (AvgUtility / StdDev)) / (1 + w/100)
```

This has three issues:

1. It mixes two terms with unstable relative scales:
   - `AvgUtility`
   - `AvgUtility / StdDev`
2. The meaning of `w` is hard to interpret consistently across scenarios.
3. The current "single best" strategy is not explicitly tied to a proper Pareto framework.

The result is usable but mathematically awkward, especially for a user-facing "weight of stability" control.

## Decision

Adopt a unified multiobjective architecture with two explicit user-selectable decision modes:

### Mode A: Pareto Front

- Optimize two objectives directly:
  - maximize `AvgUtility`
  - minimize `StdDevUtility`
- Treat legacy/final-capital feasibility as a hard constraint, not as a third objective.
- Return a non-dominated approximation set using an NSGA-II style evolutionary process.

### Mode B: Best Compromise

- Do not use the current scalar formula.
- First generate a Pareto set using the same multiobjective engine.
- Then select a single Pareto-optimal point using normalized ideal-point compromise logic.
- Use an augmented Tchebycheff / achievement scalarizing function (ASF) over normalized objectives as the default selector.
- Keep knee-point distance available as an explanation metric and optional future tie-break / alternate selector.

## Mathematical Model

### Objectives

For every feasible candidate `x`:

- `f1(x) = AvgUtility(x)` to maximize
- `f2(x) = StdDevUtility(x)` to minimize

Feasibility condition:

- final net worth at death must satisfy the legacy target
- any existing hard-invalid simulation condition remains infeasible

### Constraint Handling

Legacy feasibility stays a hard constraint because it reflects a non-negotiable plan requirement rather than a preference tradeoff.

Implication:

- infeasible solutions do not appear in the Pareto front
- infeasible solutions are dominated by feasible ones in selection
- if all sampled solutions are infeasible, the optimizer returns a structured failure state rather than a misleading "best" score

### Pareto Dominance

For feasible solutions `a` and `b`:

- `a` dominates `b` if:
  - `AvgUtility(a) >= AvgUtility(b)`
  - `StdDev(a) <= StdDev(b)`
  - and at least one inequality is strict

### Best Compromise Selector

For the Pareto set, define normalized objectives:

```text
u_norm(x) = (u_max - AvgUtility(x)) / max(eps, u_max - u_min)
s_norm(x) = (StdDev(x) - s_min) / max(eps, s_max - s_min)
```

This converts the compromise problem into minimizing distance to the ideal point `(0, 0)` in normalized objective space.

Recommended selector:

```text
ASF(x) = max(alpha * u_norm(x), beta * s_norm(x)) + rho * (alpha * u_norm(x) + beta * s_norm(x))
```

Where:

- `alpha`, `beta` are user preference weights on normalized objectives
- default is `alpha = beta = 1`
- `rho` is a small augmentation constant such as `1e-6` to break ties

Interpretation:

- the max term enforces balanced compromise rather than allowing one objective to dominate the other
- normalization makes weights interpretable
- the chosen point is guaranteed to come from the Pareto set

### Knee Point

For bi-objective visualization, define a knee metric based on distance from the line connecting the two extreme Pareto points in normalized objective space.

This is not the main selector by default, but it is valuable because:

- users intuitively understand "the elbow" of the tradeoff curve
- it provides explainable UI messaging for why the compromise point was chosen
- it can be exposed in a subsequent UX enhancement as an alternate single-solution strategy

## Why This Family

### Why not keep weighted sum

Simple weighted sums are sensitive to objective scaling and can miss non-convex parts of the Pareto front. This is a known limitation in multiobjective optimization discussions and practical engineering guidance. See:

- [Multi-objective optimization overview](https://en.wikipedia.org/wiki/Multi-objective_optimization)
- [OpenMDAO practical note on weighted sums and scaling](https://openmdao.github.io/PracticalMDO/Notebooks/Optimization/multiobjective.html)

### Why NSGA-II style Pareto search

NSGA-II remains the standard practical choice for two-objective evolutionary Pareto approximation because it combines:

- nondominated sorting
- diversity preservation
- good usability for existing GA-style codebases

It fits the app because the current optimizer is already evolutionary and works over compact parameter vectors `P1..P4`.

### Why ideal-point / ASF compromise selection

Selecting the best compromise from the Pareto set using ideal-point or ASF/Tchebycheff logic is standard practice because it:

- preserves the Pareto nature of the underlying solution
- avoids unstable raw-magnitude weighting
- gives interpretable normalized tradeoff control

Useful reference points:

- [Multi-objective optimization overview](https://en.wikipedia.org/wiki/Multi-objective_optimization)
- [Example of ideal-point based compromise selection on Pareto sets](https://www.mdpi.com/2073-4441/18/12/1391)
- [Practical knee-point explanation](https://github.com/equinor/neqsim/blob/master/docs/process/optimization/multi-objective-optimization.md)

## Scope

### In Scope

- Add user-selectable optimization mode:
  - `Best Compromise`
  - `Pareto Front`
- Replace the current scalar compromise objective with normalized Pareto-based selection.
- Introduce Pareto result models and compromise-selection logic.
- Update charts and optimization UI to work with the new objective semantics.
- Keep reporting/agent descriptions aligned with the new math.

### Out of Scope

- No redesign of the underlying yearly simulation cashflow logic.
- No change to profile persistence format unless needed for new optimization mode preferences.
- No introduction of many-objective optimization beyond the two selected objectives.
- No attempt to optimize `w` as an objective-space dimension.

## Architecture

### 1. Simulation Metrics Layer

Keep simulation as the source of truth.

Responsibilities:

- run yearly simulation
- compute `AvgUtility`
- compute `StdDevUtility`
- determine feasibility

This is an evolution of the current `ObjectiveResults` flow, but the metrics become more explicit and less tied to the old scalar score.

### 2. Pareto Evaluation Layer

Add a dedicated evaluator that maps a candidate `(P1..P4)` to:

- objective vector
- feasibility state
- possibly auxiliary metrics such as final capital

This layer is independent from UI decisions like "front vs best compromise".

### 3. Pareto Optimizer Layer

Introduce an NSGA-II style engine over the current candidate representation:

- same decision variables: `P1..P4`
- same bounds from `GAConfig`
- same mutation/crossover spirit as the current GA

Outputs:

- Pareto approximation set
- ranked population summary for debugging and chart integration

### 4. Compromise Selection Layer

A separate selector consumes the Pareto set and returns:

- selected compromise point
- normalized objective coordinates
- selector score
- optional knee metric / explanatory data

This separation is important so compromise policy can evolve without changing the Pareto search engine.

### 5. UI / ViewModel Integration Layer

Current code paths that rely on `fObjW` need to be migrated carefully:

- optimization execution in `FinancialViewModel`
- charts in `ChartsViewModel` and `ChartLogic`
- deltas/comparison models
- PDF/report summaries
- agent explanations

The design goal is backward-compatible semantics at the UX level, but with new labels and metrics under the hood.

## Data Model Changes

### Replace / Extend Objective Results

Current `ObjectiveResults` is too tied to the old scalar weight-based function.

Recommended future shape:

- `avgUtility`
- `stdDevUtility`
- `isFeasible`
- `legacyGap` or final-capital feasibility margin
- `compromiseScore` only when applicable
- `normalizedUtilityLoss`
- `normalizedStabilityLoss`

Keep any temporary compatibility fields only during migration.

### New Pareto Models

Need a dedicated Pareto point model containing:

- candidate parameters `P1..P4`
- objective metrics
- feasibility
- normalized coordinates
- selection metadata for compromise mode

Need a Pareto result container containing:

- list of non-dominated points
- selected compromise point if mode requires it
- extremes / ideal point / nadir approximations

## User Experience Design

### User Choice

User selects optimization mode:

- `Best Compromise`
- `Pareto Front`

This selection should live near optimization controls, not hidden in charts.

### Best Compromise Mode UX

Show:

- selected plan
- `AvgUtility`
- `StdDevUtility`
- reason for selection:
  - "closest normalized balanced compromise"
  - optionally "near the knee of the Pareto curve"

Do not expose the old `w` as the primary selector for this mode.

If preference weights are kept, relabel them as normalized preference weights between utility and stability, not as the current opaque standard-deviation multiplier.

### Pareto Front UX

Show:

- scatter plot of Pareto points:
  - x = `StdDevUtility`
  - y = `AvgUtility`
- tap/select one point to inspect its parameters and simulation details
- clearly mark the compromise-selected point when relevant

## Compatibility and Migration

### `bonusStdWeight`

Current meaning of `bonusStdWeight` is mathematically tied to the old scalar objective.

Decision:

- do not keep its current semantics as-is
- migrate it into one of these roles:
  1. deprecated legacy scalar weight, hidden from new optimization mode
  2. replaced by normalized preference weights for compromise selection

Recommended path:

- replace the old meaning in optimization UI
- preserve stored value only for backward compatibility during transition
- avoid mixing old and new semantics under the same label

## Error Handling

- If no feasible Pareto points are found:
  - return structured failure
  - surface clear UI text: no feasible plan found within current bounds
- If the Pareto set collapses to one point:
  - that point is both the front and the compromise
- If objective spread is near zero:
  - use epsilon-safe normalization
  - avoid divide-by-zero and misleading "perfect compromise" labels

## Testing Strategy

Use TDD for the new math and the migration.

### Unit Tests

- dominance logic
- feasibility-first ordering
- nondominated sorting
- crowding-distance or chosen diversity mechanism
- normalized objective computation
- ASF / augmented Tchebycheff selection
- knee-point metric on synthetic fronts

### Regression Tests

- legacy constraint still enforced
- unchanged simulation metrics for fixed parameter sets
- compromise point must be one of the Pareto points
- compare mode deltas still compute correctly with new metrics

### UI / Integration Checks

- switching optimization mode updates displayed metrics correctly
- charts still render for old and new result structures
- agent/report text reflects the new semantics

## Risks

### 1. Integration Breadth

The old scalar objective is deeply referenced in:

- `OptimizationLogic`
- `FinancialViewModel`
- `ChartsViewModel`
- `ChartLogic`
- comparison/delta models
- report/agent copy

Mitigation:

- introduce new models in parallel first
- migrate consumers in phases
- only remove old scalar fields after coverage is in place

### 2. Performance

Pareto evolutionary search can be more expensive than a scalar GA.

Mitigation:

- keep objective count at two
- keep the current compact decision space `P1..P4`
- start with moderate population sizes and generation counts
- reuse existing simulation and local-search ideas when appropriate

### 3. User Confusion

Users may not understand the difference between front mode and compromise mode.

Mitigation:

- use explicit labels and short explanations
- show the compromise point on the Pareto chart
- explain that Pareto mode gives choices, compromise mode picks one balanced choice automatically

## Acceptance Criteria

- User can choose `Best Compromise` or `Pareto Front`.
- Pareto mode returns a nondominated set over:
  - max `AvgUtility`
  - min `StdDevUtility`
- Legacy target remains a hard feasibility constraint.
- Best compromise no longer uses the old scalar formula.
- Best compromise is selected from the Pareto set using normalized ideal-point / augmented Tchebycheff logic.
- UI and reporting reflect the new optimization semantics clearly.
- Tests cover the core mathematical machinery and migration-sensitive integrations.

## Recommended Implementation Order

1. Introduce new objective metric models without deleting old ones.
2. Implement Pareto evaluation and synthetic tests.
3. Implement NSGA-II style Pareto search.
4. Implement compromise selector from Pareto set.
5. Integrate into `FinancialViewModel`.
6. Migrate charts and compare/delta flows.
7. Update agent/report language.
8. Remove or deprecate old scalar-only semantics.
