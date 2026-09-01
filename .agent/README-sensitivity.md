# README — Parameter Sensitivity (average utility metric)

Frozen, tested semantics of the sensitivity analysis. All green as of 2026-08-05.

## What it answers

"Which input moves my HAPPINESS the most?" — the sensitivity of the **AVERAGE UTILITY**
(average of all monthly utility samples; falls back to yearly aggregates) with respect to
each input, one-at-a-time, ranked by absolute impact. It is NOT the sensitivity of the
scalarized objective `fObjW = Avg × ((1−w) + w·Stability)`.

## Architecture

- `SimulationLogic.calculateAverageUtilityFromYears(years)` — the metric (mirrors the
  objective's sampling).
- `OptimizationLogic.runSensitivityAnalysis(baseInputs, specificExpenses, surplusData)` —
  finite differences through `calculateSimulation` (the official engine). If the response is
  flat it retries with the opposite delta sign; base average utility <= 0 or non-finite →
  empty list (GUI shows `sensitivity_calculation_failed`).
- Agent tool `RUN_SENSITIVITY {overrides}` wraps the same function; header is
  "**Sensitivity Analysis (impact on average utility):**"; same JSON overrides as
  RUN_SIMULATION.

## Unit steps (golden rules)

| Row | Unit | Perturbation |
|---|---|---|
| P1 Saving Ratio | pt / 10% | +0.10 |
| P2 End Savings Age | pt / year | +1 year |
| P3 Capital Spending Share | pt / 10% | +0.10 |
| P4 Capital Spending Start | pt / year | +1 year |
| Inheritance / Keep / TFR / Initial Capital | pt / 10k€ | +1% of `eredita` (min 100) |
| Interest / Debt rate | pt / 1pp | +0.1pp (absolute 0.001) |
| Utility Threshold | pt / 0.01 | +0.01 |
| Max Utility Spending | pt / 100€ month | +1 €/day scaled by 100·12/365.25 |
| Daily Surplus | pt / 100€ month | `surplusOffset` +1 €/day, same scaling |

1. **Rate rows report per +1 PERCENTAGE POINT**: the code perturbs by +0.1pp (0.001 absolute)
   and divides by the delta 0.1 — mathematically `dU/d(rate) × 0.01`. Do NOT "fix" it to the
   absolute step (that was a test bug, not a code bug).
2. **No "Bonus Weight (w)" row**: `bonusStdWeight` defines the objective itself; it never
   moves the average utility. Re-adding it would produce a meaningless 0.00 row.
3. Daily Surplus measures "per +100 €/month of extra earnings" — the user-facing question.
4. "pt" = points of average utility (0..1 scale), so typical values are small decimals.

## Diagnosis commands

```powershell
.\gradlew testDebugUnitTest --tests "com.example.daysurpopt.logic.SensitivityAvgUtilityTest" --tests "com.example.daysurpopt.agent.AgentSensitivityToolTest"
```

Key locks: `interest_rate_row_measures_average_utility_finite_difference` (equals
`(U(rate+0.1pp) − U(rate)) × 10`), `surplus_row_is_positive_and_expressed_per_100eur_month`,
`bonus_weight_row_is_not_reported`, agent-tool parity at 6% interest.

## Rollback

The metric lives in one function (`runSensitivityAnalysis`): swapping
`calculateAverageUtilityFromYears` back to `calculateSimulationWithWeight` restores the old
fObjW metric. Keep the test file in sync either way.
