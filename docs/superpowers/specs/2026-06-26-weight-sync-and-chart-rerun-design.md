# Weight Sync And Chart Rerun Design

**Goal**

Make the stability weight `w` behave as one shared live state across `User Data` and `Charts`, and ensure chart-side `w` changes re-execute the active optimization mode and republish simulation results so no screen becomes stale.

**Problem**

- `Charts` currently updates `inputs.bonusStdWeight` directly.
- `User Data` renders `uiInputs.bonusStdWeight`, which can lag if not refreshed from the same source at the right time.
- `PARETO_FRONT` still has a special chart-release path that reselects from cached Pareto data instead of rerunning the approved active-mode flow.
- This causes visible mismatches:
  - chart slider value vs user-data `w`
  - first-open chart surface vs post-slider chart surface
  - optimization markers/results vs current simulation

**Approved Behavior**

- `w` has one live source of truth: `inputs.bonusStdWeight`.
- `uiInputs.bonusStdWeight` is always refreshed from that shared domain state when chart-side changes occur.
- Releasing the chart slider reruns the current optimization mode every time:
  - `TRUE_SCALAR`
  - `PARETO_COMPROMISE`
  - `PARETO_FRONT`
- After that rerun, simulation/objective publication is refreshed so results, parameter boxes, and charts stay aligned.

**Implementation Shape**

- Keep chart dragging lightweight:
  - update shared `inputs.bonusStdWeight`
  - refresh `uiInputs` from `FinancialInputUI.from(inputs)`
  - persist inputs
- Make chart-slider release use a single mode-to-action rule:
  - all three modes map to full rerun
- Keep chart surfaces driven from the same `inputsSnapshot.bonusStdWeight` that also feeds optimization and simulation.

**Testing**

- Add regression coverage for:
  - chart weight release action for `PARETO_FRONT` now being rerun, not cached reselection
  - chart-side weight updates synchronizing the UI string state
  - focused build/test verification for touched files
