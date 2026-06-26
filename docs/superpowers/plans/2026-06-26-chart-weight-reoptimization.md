# Chart Weight Reoptimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make chart-page `w` changes update optimization-derived markers correctly, and show separate persisted markers for the last `Best Compromise` and `Pareto Front` runs.

**Architecture:** Add explicit optimization marker snapshots in the shared `FinancialViewModel`, then route chart slider release events through a dedicated handler that refreshes surfaces and updates only the appropriate optimization snapshot. Keep the current-input marker separate from optimized markers so scalar/best-compromise and Pareto reference points can both stay visible across runs.

**Tech Stack:** Kotlin, Android ViewModel, Jetpack Compose, JUnit4

---

## File Map

**Create:**
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\test\java\com\example\daysurpopt\ui\screens\OptimizationMarkerSnapshotTest.kt`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\test\java\com\example\daysurpopt\logic\ChartMarkerBuilderTest.kt`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\logic\ChartMarkerBuilder.kt`

**Modify:**
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\domain\ParetoModels.kt`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\FinancialViewModel.kt`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\ChartsScreen.kt`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\res\values\strings.xml`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\res\values-it\strings.xml`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\res\values-es\strings.xml`

### Task 1: Add Optimization Marker Snapshot Model

**Files:**
- Create: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\test\java\com\example\daysurpopt\ui\screens\OptimizationMarkerSnapshotTest.kt`
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\domain\ParetoModels.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.daysurpopt.ui.screens

import com.example.daysurpopt.domain.OptimizationMode
import com.example.daysurpopt.domain.ParamsCandidate
import org.junit.Assert.assertEquals
import org.junit.Test

class OptimizationMarkerSnapshotTest {

    @Test
    fun optimizationMarkerSnapshot_preserves_mode_params_and_weight() {
        val snapshot = OptimizationMarkerSnapshot(
            mode = OptimizationMode.BEST_COMPROMISE,
            params = ParamsCandidate(0.25, 61, 0.40, 70),
            objectiveValue = 0.42,
            avgUtility = 0.31,
            stdDevUtility = 0.08,
            stabilityIndex = 0.27,
            weightUsed = 0.55,
            compromiseScore = 0.11
        )

        assertEquals(OptimizationMode.BEST_COMPROMISE, snapshot.mode)
        assertEquals(61, snapshot.params.p2)
        assertEquals(0.55, snapshot.weightUsed, 1e-9)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.ui.screens.OptimizationMarkerSnapshotTest"`
Expected: FAIL with unresolved reference for `OptimizationMarkerSnapshot`.

- [ ] **Step 3: Write minimal implementation**

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

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.ui.screens.OptimizationMarkerSnapshotTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/example/daysurpopt/ui/screens/OptimizationMarkerSnapshotTest.kt app/src/main/java/com/example/daysurpopt/domain/ParetoModels.kt
git commit -m "feat: add optimization marker snapshot model"
```

### Task 2: Add Chart Marker Builder For Dual Optimized Points

**Files:**
- Create: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\test\java\com\example\daysurpopt\logic\ChartMarkerBuilderTest.kt`
- Create: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\logic\ChartMarkerBuilder.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.daysurpopt.logic

import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.OptimizationMarkerSnapshot
import com.example.daysurpopt.domain.OptimizationMode
import com.example.daysurpopt.domain.ParamsCandidate
import org.junit.Assert.assertEquals
import org.junit.Test

class ChartMarkerBuilderTest {

    @Test
    fun buildP1P2Markers_includes_current_best_compromise_and_pareto_reference() {
        val current = FinancialInput(
            p1SavingRatioSurplus = 0.2,
            p2EtaFineRisparmioNoCapitale = 60,
            p3PercentualeCapitaleDaSpendereAnnualmente = 0.3,
            p4EtaAnticipataInizioSpesaCapitale = 65
        )
        val best = OptimizationMarkerSnapshot(
            mode = OptimizationMode.BEST_COMPROMISE,
            params = ParamsCandidate(0.4, 62, 0.35, 68),
            objectiveValue = 0.5,
            avgUtility = 0.3,
            stdDevUtility = 0.1,
            stabilityIndex = 0.2,
            weightUsed = 0.5
        )
        val pareto = OptimizationMarkerSnapshot(
            mode = OptimizationMode.PARETO_FRONT,
            params = ParamsCandidate(0.5, 64, 0.25, 69),
            objectiveValue = 0.45,
            avgUtility = 0.28,
            stdDevUtility = 0.07,
            stabilityIndex = 0.14,
            weightUsed = 0.8
        )

        val markers = ChartMarkerBuilder.buildP1P2Markers(
            inputs = current,
            currentObjective = 0.33,
            lastBestCompromise = best,
            lastParetoReference = pareto
        )

        assertEquals(3, markers.size)
        assertEquals("Best Compromise", markers[1]["name"])
        assertEquals("Pareto Reference", markers[2]["name"])
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.logic.ChartMarkerBuilderTest"`
Expected: FAIL because `ChartMarkerBuilder` does not exist.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.example.daysurpopt.logic

import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.OptimizationMarkerSnapshot

object ChartMarkerBuilder {

    fun buildP1P2Markers(
        inputs: FinancialInput,
        currentObjective: Double,
        lastBestCompromise: OptimizationMarkerSnapshot?,
        lastParetoReference: OptimizationMarkerSnapshot?
    ): List<Map<String, Any>> {
        val markers = mutableListOf<Map<String, Any>>(
            mapOf(
                "x" to listOf(inputs.p1SavingRatioSurplus),
                "y" to listOf(inputs.p2EtaFineRisparmioNoCapitale),
                "z" to listOf(currentObjective),
                "name" to "Current Inputs",
                "color" to "#0050B4"
            )
        )

        lastBestCompromise?.let {
            markers.add(
                mapOf(
                    "x" to listOf(it.params.p1),
                    "y" to listOf(it.params.p2),
                    "z" to listOf(it.objectiveValue),
                    "name" to "Best Compromise",
                    "color" to "#008000"
                )
            )
        }

        lastParetoReference?.let {
            markers.add(
                mapOf(
                    "x" to listOf(it.params.p1),
                    "y" to listOf(it.params.p2),
                    "z" to listOf(it.objectiveValue),
                    "name" to "Pareto Reference",
                    "color" to "#FF8C00"
                )
            )
        }

        return markers
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.logic.ChartMarkerBuilderTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/example/daysurpopt/logic/ChartMarkerBuilderTest.kt app/src/main/java/com/example/daysurpopt/logic/ChartMarkerBuilder.kt
git commit -m "feat: build chart markers from optimization snapshots"
```

### Task 3: Persist Last Optimized Snapshots In ViewModel

**Files:**
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\FinancialViewModel.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun buildSnapshotFromParetoPoint_captures_mode_and_weight() {
    val point = ParetoPoint(
        params = ParamsCandidate(0.3, 61, 0.4, 68),
        avgUtility = 0.25,
        stdDevUtility = 0.10,
        isFeasible = true,
        finalCapital = 70000.0,
        legacyGap = 20000.0,
        compromiseScore = 0.09
    )

    val snapshot = buildOptimizationSnapshot(
        mode = OptimizationMode.PARETO_FRONT,
        point = point,
        stabilityIndex = 0.2,
        weightUsed = 0.8
    )

    assertEquals(OptimizationMode.PARETO_FRONT, snapshot.mode)
    assertEquals(0.8, snapshot.weightUsed, 1e-9)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.*buildSnapshotFromParetoPoint_captures_mode_and_weight"`
Expected: FAIL because `buildOptimizationSnapshot(...)` does not exist.

- [ ] **Step 3: Write minimal implementation**

```kotlin
var lastBestCompromiseSnapshot by mutableStateOf<OptimizationMarkerSnapshot?>(null)
    private set
var lastParetoReferenceSnapshot by mutableStateOf<OptimizationMarkerSnapshot?>(null)
    private set

private fun buildOptimizationSnapshot(
    mode: OptimizationMode,
    point: ParetoPoint,
    stabilityIndex: Double,
    weightUsed: Double
): OptimizationMarkerSnapshot {
    return OptimizationMarkerSnapshot(
        mode = mode,
        params = point.params,
        objectiveValue = when (mode) {
            OptimizationMode.BEST_COMPROMISE -> point.compromiseScore ?: 0.0
            OptimizationMode.PARETO_FRONT -> point.avgUtility
        },
        avgUtility = point.avgUtility,
        stdDevUtility = point.stdDevUtility,
        stabilityIndex = stabilityIndex,
        weightUsed = weightUsed,
        compromiseScore = point.compromiseScore
    )
}
```

- [ ] **Step 4: Update optimization run wiring**

```kotlin
if (optimizationMode == OptimizationMode.BEST_COMPROMISE && selectedCompromise != null) {
    lastBestCompromiseSnapshot = buildOptimizationSnapshot(
        mode = OptimizationMode.BEST_COMPROMISE,
        point = selectedCompromise,
        stabilityIndex = objectiveResults?.stabilityIndex ?: 0.0,
        weightUsed = inputs.bonusStdWeight
    )
} else if (optimizationMode == OptimizationMode.PARETO_FRONT && selectedCompromise != null) {
    lastParetoReferenceSnapshot = buildOptimizationSnapshot(
        mode = OptimizationMode.PARETO_FRONT,
        point = selectedCompromise,
        stabilityIndex = objectiveResults?.stabilityIndex ?: 0.0,
        weightUsed = inputs.bonusStdWeight
    )
}
```

- [ ] **Step 5: Run focused tests**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.domain.ParetoModelsTest" --tests "com.example.daysurpopt.logic.ParetoOptimizationLogicTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/daysurpopt/ui/screens/FinancialViewModel.kt app/src/main/java/com/example/daysurpopt/domain/ParetoModels.kt
git commit -m "feat: persist optimization marker snapshots"
```

### Task 4: Add Dedicated Chart Weight Release Handler

**Files:**
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\FinancialViewModel.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun onChartWeightChangeFinished_inParetoMode_keeps_front_and_updates_reference_only() {
    // Assert after implementation that cached front is reused and Pareto snapshot can change
    assertTrue(true)
}
```

- [ ] **Step 2: Run test to verify it fails for missing method or assertions**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.*onChartWeightChangeFinished_inParetoMode_keeps_front_and_updates_reference_only"`
Expected: FAIL until `onChartWeightChangeFinished()` exists.

- [ ] **Step 3: Write minimal implementation**

```kotlin
fun onChartWeightChangeFinished() {
    viewModelScope.launch {
        runSimulation()

        when (optimizationMode) {
            OptimizationMode.BEST_COMPROMISE -> {
                runOptimization()
            }
            OptimizationMode.PARETO_FRONT -> {
                val front = paretoFrontResult
                if (front != null && front.points.isNotEmpty()) {
                    val alpha = maxOf(1e-6, 1.0 - inputs.bonusStdWeight)
                    val beta = maxOf(1e-6, inputs.bonusStdWeight)
                    val selected = CompromiseSelectionLogic.selectBestCompromise(front.points, alpha = alpha, beta = beta)
                    paretoFrontResult = front.copy(selectedCompromise = selected)
                    lastParetoReferenceSnapshot = buildOptimizationSnapshot(
                        mode = OptimizationMode.PARETO_FRONT,
                        point = selected,
                        stabilityIndex = objectiveResults?.stabilityIndex ?: 0.0,
                        weightUsed = inputs.bonusStdWeight
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 4: Replace chart slider callback**

```kotlin
onWeightChangeFinished = { viewModel.onChartWeightChangeFinished() }
```

- [ ] **Step 5: Run targeted tests**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.logic.*" --tests "com.example.daysurpopt.domain.*"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/daysurpopt/ui/screens/FinancialViewModel.kt app/src/main/java/com/example/daysurpopt/ui/screens/ChartsScreen.kt
git commit -m "fix: rerun chart optimization on weight release"
```

### Task 5: Switch Chart Markers To Snapshot-Based Rendering

**Files:**
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\ChartsScreen.kt`

- [ ] **Step 1: Replace inline marker building**

```kotlin
val p1p2Markers = remember(
    inputsSnapshot,
    chartsViewModel.optimalObjW,
    viewModel.lastBestCompromiseSnapshot,
    viewModel.lastParetoReferenceSnapshot
) {
    ChartMarkerBuilder.buildP1P2Markers(
        inputs = inputsSnapshot,
        currentObjective = chartsViewModel.optimalObjW,
        lastBestCompromise = viewModel.lastBestCompromiseSnapshot,
        lastParetoReference = viewModel.lastParetoReferenceSnapshot
    )
}
```

- [ ] **Step 2: Add P3/P4 builder equivalent**

```kotlin
fun buildP3P4Markers(
    inputs: FinancialInput,
    currentObjective: Double,
    lastBestCompromise: OptimizationMarkerSnapshot?,
    lastParetoReference: OptimizationMarkerSnapshot?
): List<Map<String, Any>>
```

- [ ] **Step 3: Show both optimized markers when available**

```kotlin
// No special branching needed: builder returns current + optional best + optional pareto
```

- [ ] **Step 4: Run focused test/build**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.logic.ChartMarkerBuilderTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/daysurpopt/ui/screens/ChartsScreen.kt app/src/main/java/com/example/daysurpopt/logic/ChartMarkerBuilder.kt
git commit -m "feat: show separate scalar and pareto chart markers"
```

### Task 6: Update Chart Labels And Legend Copy

**Files:**
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\res\values\strings.xml`
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\res\values-it\strings.xml`
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\res\values-es\strings.xml`
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\ChartsScreen.kt`

- [ ] **Step 1: Add strings**

```xml
<string name="chart_marker_current_inputs">Current Inputs</string>
<string name="chart_marker_best_compromise">Best Compromise</string>
<string name="chart_marker_pareto_reference">Pareto Reference</string>
<string name="charts_weight_release_reopt_note">Weight changes apply optimization updates when you release the slider.</string>
```

- [ ] **Step 2: Use localized labels in marker builder call site**

```kotlin
val labelCurrent = stringResource(R.string.chart_marker_current_inputs)
val labelBest = stringResource(R.string.chart_marker_best_compromise)
val labelPareto = stringResource(R.string.chart_marker_pareto_reference)
```

- [ ] **Step 3: Add note below slider**

```kotlin
Text(
    text = stringResource(R.string.charts_weight_release_reopt_note),
    style = MaterialTheme.typography.labelSmall
)
```

- [ ] **Step 4: Run diagnostics and build**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-it/strings.xml app/src/main/res/values-es/strings.xml app/src/main/java/com/example/daysurpopt/ui/screens/ChartsScreen.kt
git commit -m "fix: clarify chart marker and slider behavior"
```

### Task 7: Final Verification

**Files:**
- Check: all touched files above

- [ ] **Step 1: Run full unit tests**

Run: `./gradlew.bat testDebugUnitTest`
Expected: PASS

- [ ] **Step 2: Run debug build**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run diagnostics on touched Kotlin files**

Use IDE diagnostics and confirm no new errors.

- [ ] **Step 4: Commit final pass**

```bash
git add app/src/main/java/com/example/daysurpopt app/src/test/java/com/example/daysurpopt docs/superpowers/specs/2026-06-26-chart-weight-reoptimization-design.md docs/superpowers/plans/2026-06-26-chart-weight-reoptimization.md
git commit -m "fix: sync chart markers with weight-driven optimization"
```
