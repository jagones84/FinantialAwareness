# Pareto Compromise Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add user-selectable `Pareto Front` and `Best Compromise` optimization modes, replacing the current scalar compromise objective with a Pareto-based workflow.

**Architecture:** Keep the yearly simulation engine as the source of truth, then layer a new objective-evaluation API, an NSGA-II style Pareto optimizer, and a compromise selector on top. Migrate `FinancialViewModel`, charts, compare-mode deltas, and reporting in phases so old scalar-only semantics are removed only after the new pipeline is working end-to-end.

**Tech Stack:** Kotlin, Android ViewModel, Jetpack Compose, JUnit4, existing genetic algorithm code, SharedPreferences-backed UI state

---

## File Map

**Create:**
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\domain\ParetoModels.kt`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\logic\ParetoOptimizationLogic.kt`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\logic\CompromiseSelectionLogic.kt`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\test\java\com\example\daysurpopt\logic\ParetoOptimizationLogicTest.kt`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\test\java\com\example\daysurpopt\logic\CompromiseSelectionLogicTest.kt`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\test\java\com\example\daysurpopt\domain\ParetoModelsTest.kt`

**Modify:**
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\domain\SimulationModels.kt`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\domain\ComparisonModels.kt`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\logic\SimulationLogic.kt`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\logic\OptimizationLogic.kt`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\FinancialViewModel.kt`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\ChartsViewModel.kt`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\logic\ChartLogic.kt`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\ChartsScreen.kt`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\FinancialCalculatorScreen.kt`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\logic\PdfExporter.kt`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\agent\AgentPrompts.kt`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\res\values\strings.xml`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\res\values-it\strings.xml`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\res\values-es\strings.xml`

### Task 1: Add New Objective And Pareto Domain Models

**Files:**
- Create: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\test\java\com\example\daysurpopt\domain\ParetoModelsTest.kt`
- Create: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\domain\ParetoModels.kt`
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\domain\SimulationModels.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.daysurpopt.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParetoModelsTest {

    @Test
    fun dominates_requires_better_or_equal_on_all_objectives_and_strict_improvement_on_one() {
        val a = ParetoPoint(
            params = ParamsCandidate(0.2, 60, 0.1, 65),
            avgUtility = 0.30,
            stdDevUtility = 0.10,
            isFeasible = true,
            finalCapital = 80000.0,
            legacyGap = 30000.0
        )
        val b = ParetoPoint(
            params = ParamsCandidate(0.3, 61, 0.2, 67),
            avgUtility = 0.25,
            stdDevUtility = 0.15,
            isFeasible = true,
            finalCapital = 78000.0,
            legacyGap = 28000.0
        )

        assertTrue(a.dominates(b))
        assertFalse(b.dominates(a))
    }

    @Test
    fun infeasible_point_never_dominates_feasible_point() {
        val feasible = ParetoPoint(
            params = ParamsCandidate(0.2, 60, 0.1, 65),
            avgUtility = 0.20,
            stdDevUtility = 0.30,
            isFeasible = true,
            finalCapital = 50000.0,
            legacyGap = 0.0
        )
        val infeasible = feasible.copy(isFeasible = false, legacyGap = -1000.0)

        assertFalse(infeasible.dominates(feasible))
        assertTrue(feasible.constraintDominates(infeasible))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.domain.ParetoModelsTest"`
Expected: FAIL with unresolved references for `ParetoPoint`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.example.daysurpopt.domain

data class ParetoPoint(
    val params: ParamsCandidate,
    val avgUtility: Double,
    val stdDevUtility: Double,
    val isFeasible: Boolean,
    val finalCapital: Double,
    val legacyGap: Double,
    val normalizedUtilityLoss: Double = 0.0,
    val normalizedStabilityLoss: Double = 0.0,
    val compromiseScore: Double? = null,
    val kneeScore: Double? = null,
    val rank: Int = 0,
    val crowdingDistance: Double = 0.0
) {
    fun dominates(other: ParetoPoint): Boolean {
        if (!isFeasible || !other.isFeasible) return false
        val noWorseUtility = avgUtility >= other.avgUtility
        val noWorseStability = stdDevUtility <= other.stdDevUtility
        val strictlyBetter = avgUtility > other.avgUtility || stdDevUtility < other.stdDevUtility
        return noWorseUtility && noWorseStability && strictlyBetter
    }

    fun constraintDominates(other: ParetoPoint): Boolean {
        return isFeasible && !other.isFeasible
    }
}

data class ParetoFrontResult(
    val points: List<ParetoPoint>,
    val selectedCompromise: ParetoPoint? = null,
    val idealAvgUtility: Double = 0.0,
    val idealStdDevUtility: Double = 0.0
)

enum class OptimizationMode {
    BEST_COMPROMISE,
    PARETO_FRONT
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.domain.ParetoModelsTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/example/daysurpopt/domain/ParetoModelsTest.kt app/src/main/java/com/example/daysurpopt/domain/ParetoModels.kt
git commit -m "feat: add pareto domain models"
```

### Task 2: Expose Feasibility-Centric Objective Metrics From Simulation

**Files:**
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\test\java\com\example\daysurpopt\logic\SimulationLogicTest.kt`
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\logic\SimulationLogic.kt`
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\domain\SimulationModels.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun calculateObjectivesFromYears_reports_feasibility_and_legacy_gap() {
    val years = listOf(
        SimulationYear(eta = 65, funzioneUtilita = 0.2, capitaleFineAnno = 60000.0, violazioneLascito = false),
        SimulationYear(eta = 66, funzioneUtilita = 0.3, capitaleFineAnno = 55000.0, violazioneLascito = false)
    )

    val result = calculateObjectivesFromYears(years, bonusStdWeight = 0.5, legacyTarget = 50000.0)

    assertEquals(0.25, result.avgUtility, 1e-9)
    assertTrue(result.isFeasible)
    assertEquals(5000.0, result.legacyGap, 1e-9)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.logic.SimulationLogicTest.calculateObjectivesFromYears_reports_feasibility_and_legacy_gap"`
Expected: FAIL because `calculateObjectivesFromYears(...)` does not yet accept `legacyTarget` or expose feasibility fields.

- [ ] **Step 3: Write minimal implementation**

```kotlin
data class ObjectiveResults(
    val fObjW: Double,
    val fObj0: Double,
    val stabilityIndex: Double,
    val stdDev: Double,
    val avgUtilita: Double,
    val isFeasible: Boolean = false,
    val finalCapital: Double = 0.0,
    val legacyGap: Double = 0.0
)
```

```kotlin
fun calculateObjectivesFromYears(
    years: List<SimulationYear>,
    bonusStdWeight: Double,
    legacyTarget: Double? = null
): ObjectiveResults {
    if (years.isEmpty()) return ObjectiveResults(0.0, 0.0, 0.0, 0.0, 0.0)

    val finalCapital = years.lastOrNull()?.capitaleFineAnno ?: 0.0
    val inferredLegacyTarget = legacyTarget ?: 0.0
    val legacyGap = finalCapital - inferredLegacyTarget
    val isFeasible = !years.any { it.violazioneLascito } && years.none { it.funzioneUtilita < 0 || !it.funzioneUtilita.isFinite() }

    if (!isFeasible) {
        return ObjectiveResults(0.0, 0.0, 0.0, 0.0, 0.0, false, finalCapital, legacyGap)
    }

    // keep existing average/stdDev/scalar calculations unchanged for migration
}
```

- [ ] **Step 4: Run focused tests**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.logic.SimulationLogicTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/daysurpopt/domain/SimulationModels.kt app/src/main/java/com/example/daysurpopt/logic/SimulationLogic.kt app/src/test/java/com/example/daysurpopt/logic/SimulationLogicTest.kt
git commit -m "feat: expose feasibility-aware objective metrics"
```

### Task 3: Add Compromise Selection Logic

**Files:**
- Create: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\test\java\com\example\daysurpopt\logic\CompromiseSelectionLogicTest.kt`
- Create: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\logic\CompromiseSelectionLogic.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.daysurpopt.logic

import com.example.daysurpopt.domain.ParamsCandidate
import com.example.daysurpopt.domain.ParetoPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class CompromiseSelectionLogicTest {

    @Test
    fun selectBestCompromise_picks_balanced_point_by_normalized_asf() {
        val points = listOf(
            ParetoPoint(ParamsCandidate(0.1, 60, 0.1, 65), avgUtility = 0.10, stdDevUtility = 0.05, isFeasible = true, finalCapital = 70000.0, legacyGap = 20000.0),
            ParetoPoint(ParamsCandidate(0.2, 60, 0.2, 65), avgUtility = 0.20, stdDevUtility = 0.20, isFeasible = true, finalCapital = 65000.0, legacyGap = 15000.0),
            ParetoPoint(ParamsCandidate(0.3, 60, 0.3, 65), avgUtility = 0.30, stdDevUtility = 0.40, isFeasible = true, finalCapital = 60000.0, legacyGap = 10000.0)
        )

        val selected = CompromiseSelectionLogic.selectBestCompromise(points)

        assertEquals(0.20, selected.avgUtility, 1e-9)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.logic.CompromiseSelectionLogicTest"`
Expected: FAIL because `CompromiseSelectionLogic` does not exist.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.example.daysurpopt.logic

import com.example.daysurpopt.domain.ParetoPoint
import kotlin.math.abs
import kotlin.math.max

object CompromiseSelectionLogic {

    fun selectBestCompromise(points: List<ParetoPoint>, alpha: Double = 1.0, beta: Double = 1.0, rho: Double = 1e-6): ParetoPoint {
        require(points.isNotEmpty()) { "Pareto set cannot be empty" }

        val uMax = points.maxOf { it.avgUtility }
        val uMin = points.minOf { it.avgUtility }
        val sMin = points.minOf { it.stdDevUtility }
        val sMax = points.maxOf { it.stdDevUtility }

        return points
            .map { point ->
                val uNorm = (uMax - point.avgUtility) / max(1e-9, uMax - uMin)
                val sNorm = (point.stdDevUtility - sMin) / max(1e-9, sMax - sMin)
                val asf = max(alpha * uNorm, beta * sNorm) + rho * (alpha * uNorm + beta * sNorm)
                val knee = abs((uNorm + sNorm) - 1.0)
                point.copy(
                    normalizedUtilityLoss = uNorm,
                    normalizedStabilityLoss = sNorm,
                    compromiseScore = asf,
                    kneeScore = knee
                )
            }
            .minBy { it.compromiseScore ?: Double.POSITIVE_INFINITY }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.logic.CompromiseSelectionLogicTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/example/daysurpopt/logic/CompromiseSelectionLogicTest.kt app/src/main/java/com/example/daysurpopt/logic/CompromiseSelectionLogic.kt
git commit -m "feat: add compromise selector from pareto set"
```

### Task 4: Add NSGA-II Style Pareto Search

**Files:**
- Create: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\test\java\com\example\daysurpopt\logic\ParetoOptimizationLogicTest.kt`
- Create: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\logic\ParetoOptimizationLogic.kt`
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\logic\OptimizationLogic.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.daysurpopt.logic

import com.example.daysurpopt.domain.ParamsCandidate
import com.example.daysurpopt.domain.ParetoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParetoOptimizationLogicTest {

    @Test
    fun nonDominatedFront_filters_dominated_points() {
        val points = listOf(
            ParetoPoint(ParamsCandidate(0.1, 60, 0.1, 65), 0.30, 0.10, true, 70000.0, 20000.0),
            ParetoPoint(ParamsCandidate(0.2, 60, 0.2, 65), 0.25, 0.15, true, 68000.0, 18000.0),
            ParetoPoint(ParamsCandidate(0.3, 60, 0.3, 65), 0.20, 0.25, true, 65000.0, 15000.0)
        )

        val front = ParetoOptimizationLogic.extractNonDominatedFront(points)

        assertEquals(1, front.size)
        assertTrue(front.first().avgUtility == 0.30)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.logic.ParetoOptimizationLogicTest"`
Expected: FAIL because `ParetoOptimizationLogic` does not exist.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.example.daysurpopt.logic

import com.example.daysurpopt.domain.*

object ParetoOptimizationLogic {

    fun extractNonDominatedFront(points: List<ParetoPoint>): List<ParetoPoint> {
        return points.filter { candidate ->
            points.none { other ->
                other !== candidate && (other.constraintDominates(candidate) || other.dominates(candidate))
            }
        }
    }
}
```

- [ ] **Step 4: Expand to optimizer entry point**

```kotlin
suspend fun optimizeParetoParameters(
    baseInputs: FinancialInput,
    config: GAConfig,
    specificExpenses: List<SpecificExpense>,
    surplusData: SurplusInput
): ParetoFrontResult
```

Implementation notes:
- reuse current `ParamsCandidate` bounds
- reuse mutation/crossover ideas from `OptimizationLogic`
- evaluate candidates through `calculateSimulation(...)` + `calculateObjectivesFromYears(..., legacyTarget = baseInputs.soldiDaConservare)`
- keep only feasible Pareto points in final front

- [ ] **Step 5: Run focused tests**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.logic.ParetoOptimizationLogicTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/test/java/com/example/daysurpopt/logic/ParetoOptimizationLogicTest.kt app/src/main/java/com/example/daysurpopt/logic/ParetoOptimizationLogic.kt app/src/main/java/com/example/daysurpopt/logic/OptimizationLogic.kt
git commit -m "feat: add pareto optimizer"
```

### Task 5: Integrate Optimization Mode Into ViewModel

**Files:**
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\FinancialViewModel.kt`
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\domain\ComparisonModels.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun compareDeltaObjectives_uses_new_feasibility_aware_metrics() {
    val p1 = ObjectiveResults(0.0, 0.0, 0.0, 0.10, 0.20, true, 60000.0, 10000.0)
    val p2 = ObjectiveResults(0.0, 0.0, 0.0, 0.08, 0.18, true, 70000.0, 20000.0)

    val delta = DeltaCalculator.computeDeltaObjectives(p1, p2)

    assertEquals(10000.0, delta.deltaLegacyGap, 1e-9)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.logic.*" --tests "com.example.daysurpopt.domain.*"`
Expected: FAIL because delta models do not yet expose the new fields.

- [ ] **Step 3: Write minimal implementation**

```kotlin
data class DeltaObjectiveResults(
    val deltaFObjW: Double,
    val deltaFObj0: Double,
    val deltaStabilityIndex: Double,
    val deltaStdDev: Double,
    val deltaAvgUtilita: Double,
    val deltaLegacyGap: Double = 0.0,
    val deltaFinalCapital: Double = 0.0
)
```

```kotlin
fun computeDeltaObjectives(results1: ObjectiveResults, results2: ObjectiveResults): DeltaObjectiveResults {
    return DeltaObjectiveResults(
        deltaFObjW = results2.fObjW - results1.fObjW,
        deltaFObj0 = results2.fObj0 - results1.fObj0,
        deltaStabilityIndex = results2.stabilityIndex - results1.stabilityIndex,
        deltaStdDev = results2.stdDev - results1.stdDev,
        deltaAvgUtilita = results2.avgUtilita - results1.avgUtilita,
        deltaLegacyGap = results2.legacyGap - results1.legacyGap,
        deltaFinalCapital = results2.finalCapital - results1.finalCapital
    )
}
```

- [ ] **Step 4: Add ViewModel state**

```kotlin
var optimizationMode by mutableStateOf(OptimizationMode.BEST_COMPROMISE)
    private set

var paretoFrontResult by mutableStateOf<ParetoFrontResult?>(null)
    private set

fun updateOptimizationMode(mode: OptimizationMode) {
    optimizationMode = mode
}
```

- [ ] **Step 5: Route optimization execution**

```kotlin
if (optimizationMode == OptimizationMode.PARETO_FRONT) {
    paretoFrontResult = ParetoOptimizationLogic.optimizeParetoParameters(inputs, gaConfig, specificExpenses, surplusData)
} else {
    val front = ParetoOptimizationLogic.optimizeParetoParameters(inputs, gaConfig, specificExpenses, surplusData)
    paretoFrontResult = front.copy(selectedCompromise = CompromiseSelectionLogic.selectBestCompromise(front.points))
}
```

- [ ] **Step 6: Run focused tests**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.logic.*" --tests "com.example.daysurpopt.domain.*"`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/daysurpopt/ui/screens/FinancialViewModel.kt app/src/main/java/com/example/daysurpopt/domain/ComparisonModels.kt
git commit -m "feat: add optimization mode state and pareto integration"
```

### Task 6: Update UI Controls And Labels

**Files:**
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\FinancialCalculatorScreen.kt`
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\res\values\strings.xml`
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\res\values-it\strings.xml`
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\res\values-es\strings.xml`

- [ ] **Step 1: Add strings**

```xml
<string name="optimization_mode_title">Optimization Mode</string>
<string name="optimization_mode_best_compromise">Best Compromise</string>
<string name="optimization_mode_pareto_front">Pareto Front</string>
<string name="optimization_mode_best_compromise_desc">Select one balanced Pareto-optimal plan automatically</string>
<string name="optimization_mode_pareto_front_desc">Return the full non-dominated tradeoff set</string>
```

- [ ] **Step 2: Add toggle UI**

```kotlin
SegmentedButtonRow {
    SegmentedButton(
        selected = viewModel.optimizationMode == OptimizationMode.BEST_COMPROMISE,
        onClick = { viewModel.updateOptimizationMode(OptimizationMode.BEST_COMPROMISE) },
        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
    ) { Text(stringResource(R.string.optimization_mode_best_compromise)) }

    SegmentedButton(
        selected = viewModel.optimizationMode == OptimizationMode.PARETO_FRONT,
        onClick = { viewModel.updateOptimizationMode(OptimizationMode.PARETO_FRONT) },
        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
    ) { Text(stringResource(R.string.optimization_mode_pareto_front)) }
}
```

- [ ] **Step 3: Remove or relabel old scalar weight text**

```kotlin
Text(
    text = stringResource(R.string.optimization_mode_best_compromise_desc),
    style = MaterialTheme.typography.bodySmall
)
```

- [ ] **Step 4: Run build-level verification**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/daysurpopt/ui/screens/FinancialCalculatorScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-it/strings.xml app/src/main/res/values-es/strings.xml
git commit -m "feat: add optimization mode selector"
```

### Task 7: Migrate Charts To Pareto Metrics

**Files:**
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\ChartsViewModel.kt`
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\logic\ChartLogic.kt`
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\ChartsScreen.kt`

- [ ] **Step 1: Replace chart z-values in compromise mode**

```kotlin
val compromise = paretoFrontResult?.selectedCompromise
optimalObjW = compromise?.compromiseScore ?: 0.0
optimalStabilityIndex = objectiveResults.stabilityIndex
```

- [ ] **Step 2: Add Pareto scatter support**

```kotlin
val paretoXs = paretoFrontResult?.points?.map { it.stdDevUtility } ?: emptyList()
val paretoYs = paretoFrontResult?.points?.map { it.avgUtility } ?: emptyList()
```

- [ ] **Step 3: Keep existing heatmap/surface behavior during migration**

```kotlin
val res1 = calculateObjectivesFromYears(years1, w, legacyTarget = in1.soldiDaConservare)
z[iy][ix] = if (res1.isFeasible) res1.avgUtilita else null
```

- [ ] **Step 4: Run targeted tests and manual chart smoke build**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.logic.*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/daysurpopt/ui/screens/ChartsViewModel.kt app/src/main/java/com/example/daysurpopt/logic/ChartLogic.kt app/src/main/java/com/example/daysurpopt/ui/screens/ChartsScreen.kt
git commit -m "feat: adapt charts to pareto metrics"
```

### Task 8: Update PDF And Agent Reporting

**Files:**
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\logic\PdfExporter.kt`
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\agent\AgentPrompts.kt`
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\FinancialViewModel.kt`

- [ ] **Step 1: Update report language**

```kotlin
val optimizationSummary = if (optimizationMode == OptimizationMode.PARETO_FRONT) {
    "Pareto optimization returned ${paretoFrontResult?.points?.size ?: 0} non-dominated plans."
} else {
    "Best compromise selected from Pareto front using normalized ideal-point ASF."
}
```

- [ ] **Step 2: Update agent/report prompt wording**

```kotlin
- Pareto objectives: maximize AvgUtility, minimize StdDevUtility.
- Best compromise selector: normalized ideal-point augmented Tchebycheff over Pareto-optimal solutions.
```

- [ ] **Step 3: Run focused verification**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.agent.*" --tests "com.example.daysurpopt.logic.*"`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/daysurpopt/logic/PdfExporter.kt app/src/main/java/com/example/daysurpopt/agent/AgentPrompts.kt app/src/main/java/com/example/daysurpopt/ui/screens/FinancialViewModel.kt
git commit -m "fix: align reporting with pareto optimization"
```

### Task 9: Final Safety Pass And Deprecation Cleanup

**Files:**
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\logic\SimulationLogic.kt`
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\logic\OptimizationLogic.kt`
- Check: all files touched above

- [ ] **Step 1: Remove direct dependence on old scalar compromise formula from optimizer entry points**

```kotlin
@Deprecated("Use ParetoOptimizationLogic + CompromiseSelectionLogic")
fun computeObjective(avgUtilita: Double, stdDevUtilita: Double, bonusStdWeight: Double): Double { ... }
```

- [ ] **Step 2: Run full unit test suite**

Run: `./gradlew.bat testDebugUnitTest`
Expected: PASS

- [ ] **Step 3: Run diagnostics on edited Kotlin files**

Use IDE diagnostics and confirm no new errors.

- [ ] **Step 4: Run debug build**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit final migration**

```bash
git add app/src/main/java/com/example/daysurpopt app/src/test/java/com/example/daysurpopt docs/superpowers/specs/2026-06-26-pareto-compromise-optimization-design.md docs/superpowers/plans/2026-06-26-pareto-compromise-optimization.md
git commit -m "feat: add pareto and best compromise optimization modes"
```
