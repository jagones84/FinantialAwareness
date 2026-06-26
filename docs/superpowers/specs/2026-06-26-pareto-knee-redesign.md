# Pareto Knee Redesign

**Goal**

Separate Pareto generation from preference articulation so the app is mathematically coherent:

- `True Scalar` remains weight-driven and optimizes the scalar objective directly.
- `Pareto Knee` becomes a weight-free auto-selection mode.
- `Pareto Front` remains a full-front explorer with an auto-selected reference point that is also weight-free.

**Problem**

- The current app uses `w` to choose a single point from the Pareto front.
- That makes the displayed Pareto reference a preference-selected compromise rather than a canonical Pareto-intrinsic point.
- The current naming (`Pareto Compromise`, `selectedCompromise`, `Reference compromise`) obscures that distinction and makes the Pareto behavior look mathematically confused.

**Approved Behavior**

- Pareto front generation uses no `w`.
- Single-point automatic selection on the Pareto front uses a knee-based rule, not a weight-based rule.
- `True Scalar` is the only mode whose optimization meaning depends on `w`.
- `Pareto Front` still supports click-to-inspect and explicit apply, but its default reference is the knee point.

**Selection Rule**

- Use a knee-point selector in 2D objective space:
  - X axis: `StdDevUtility` (minimize)
  - Y axis: `AvgUtility` (maximize)
- Compute the line joining the two Pareto extremes:
  - minimum `StdDevUtility`
  - maximum `AvgUtility`
- Select the Pareto point with the largest perpendicular distance from that chord after range normalization.
- Store the resulting score in `kneeScore`.

**Renaming**

- Rename `PARETO_COMPROMISE` to `PARETO_KNEE`.
- Rename user-facing text from `Pareto Compromise` to `Pareto Knee`.
- Rename chart/reference wording so it no longer says `compromise` where the math is knee-based.

**Testing**

- Add regression coverage for:
  - new enum and display labels
  - knee selection behavior independent of `w`
  - mode routing updates
  - view-model usage of the knee selector in both Pareto modes
