# Pareto Apply Behavior And Dedicated Pareto Chart Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `PARETO_FRONT` optimization apply the selected reference plan into live inputs/results like scalar optimization, and add a dedicated Pareto chart section inside `Charts`.

**Architecture:** Extend `FinancialViewModel` with explicit Pareto selection/applied state, then route Pareto optimize/apply/reselection through a single state owner. Keep the Pareto front cached, use the existing chart infrastructure for the new objective-space scatter, and add TDD coverage for apply-vs-select semantics and cached-front weight reselection.

**Tech Stack:** Kotlin, Jetpack Compose, Android ViewModel, existing Plotly/WebView chart stack, JUnit4

---

## File Map

**Create:**
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\test\java\com\example\daysurpopt\ui\screens\ParetoChartStateTest.kt`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\test\java\com\example\daysurpopt\logic\ParetoChartModelBuilderTest.kt`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\logic\ParetoChartModelBuilder.kt`

**Modify:**
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\domain\ParetoModels.kt`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\FinancialViewModel.kt`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\FinancialCalculatorScreen.kt`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\ChartsScreen.kt`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\res\values\strings.xml`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\res\values-it\strings.xml`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\res\values-es\strings.xml`

### Task 1: Add Pareto Selection/Applied State Model

**Files:**
- Create: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\test\java\com\example\daysurpopt\ui\screens\ParetoChartStateTest.kt`
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\domain\ParetoModels.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.daysurpopt.ui.screens

import com.example.daysurpopt.domain.OptimizationMarkerSnapshot
import com.example.daysurpopt.domain.OptimizationMode
import com.example.daysurpopt.domain.ParamsCandidate
import com.example.daysurpopt.domain.ParetoPoint
import com.example.daysurpopt.domain.toOptimizationMarkerSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class ParetoChartStateTest {

    @Test
    fun paretoPoint_toAppliedSnapshot_preserves_params_metrics_and_weight() {
        val point = ParetoPoint(
            params = ParamsCandidate(0.40, 62, 0.35, 68),
            avgUtility = 0.31,
            stdDevUtility = 0.09,
            isFeasible = true,
            finalCapital = 80000.0,
            legacyGap = 12000.0,
            compromiseScore = 0.07
        )

        val snapshot = point.toOptimizationMarkerSnapshot(
            mode = OptimizationMode.PARETO_FRONT,
            objectiveValue = 0.44,
            stabilityIndex = 0.18,
            weightUsed = 0.75
        )

        assertEquals(OptimizationMode.PARETO_FRONT, snapshot.mode)
        assertEquals(62, snapshot.params.p2)
        assertEquals(0.44, snapshot.objectiveValue, 1e-9)
        assertEquals(0.75, snapshot.weightUsed, 1e-9)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.ui.screens.ParetoChartStateTest"`
Expected: FAIL because the test file/class is new or required supporting model fields/helpers are missing.

- [ ] **Step 3: Write minimal implementation**

Add or keep the helper/model in `ParetoModels.kt` in this form:

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

fun ParetoPoint.toOptimizationMarkerSnapshot(
    mode: OptimizationMode,
    objectiveValue: Double,
    stabilityIndex: Double,
    weightUsed: Double
): OptimizationMarkerSnapshot {
    return OptimizationMarkerSnapshot(
        mode = mode,
        params = params,
        objectiveValue = objectiveValue,
        avgUtility = avgUtility,
        stdDevUtility = stdDevUtility,
        stabilityIndex = stabilityIndex,
        weightUsed = weightUsed,
        compromiseScore = compromiseScore
    )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.ui.screens.ParetoChartStateTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/example/daysurpopt/ui/screens/ParetoChartStateTest.kt app/src/main/java/com/example/daysurpopt/domain/ParetoModels.kt
git commit -m "feat: add pareto chart state model helpers"
```

### Task 2: Add Pareto Chart Model Builder

**Files:**
- Create: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\test\java\com\example\daysurpopt\logic\ParetoChartModelBuilderTest.kt`
- Create: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\logic\ParetoChartModelBuilder.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.daysurpopt.logic

import com.example.daysurpopt.domain.ParetoPoint
import com.example.daysurpopt.domain.ParamsCandidate
import org.junit.Assert.assertEquals
import org.junit.Test

class ParetoChartModelBuilderTest {

    @Test
    fun buildMarkers_returns_front_reference_applied_and_selected_roles() {
        val p1 = ParetoPoint(
            params = ParamsCandidate(0.20, 60, 0.20, 66),
            avgUtility = 0.20,
            stdDevUtility = 0.30,
            isFeasible = true,
            finalCapital = 70000.0,
            legacyGap = 5000.0
        )
        val p2 = ParetoPoint(
            params = ParamsCandidate(0.35, 62, 0.25, 68),
            avgUtility = 0.30,
            stdDevUtility = 0.15,
            isFeasible = true,
            finalCapital = 76000.0,
            legacyGap = 9000.0
        )

        val model = ParetoChartModelBuilder.build(
            points = listOf(p1, p2),
            referencePoint = p2,
            appliedPoint = p1,
            selectedPoint = p2
        )

        assertEquals(2, model.basePoints.size)
        assertEquals(0.30, model.referenceMarker!!.y, 1e-9)
        assertEquals(0.20, model.appliedMarker!!.y, 1e-9)
        assertEquals(0.15, model.selectedMarker!!.x, 1e-9)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.logic.ParetoChartModelBuilderTest"`
Expected: FAIL because `ParetoChartModelBuilder` does not exist.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.example.daysurpopt.logic

import com.example.daysurpopt.domain.ParetoPoint

data class ParetoScatterPoint(
    val x: Double,
    val y: Double,
    val point: ParetoPoint
)

data class ParetoChartModel(
    val basePoints: List<ParetoScatterPoint>,
    val referenceMarker: ParetoScatterPoint?,
    val appliedMarker: ParetoScatterPoint?,
    val selectedMarker: ParetoScatterPoint?
)

object ParetoChartModelBuilder {

    fun build(
        points: List<ParetoPoint>,
        referencePoint: ParetoPoint?,
        appliedPoint: ParetoPoint?,
        selectedPoint: ParetoPoint?
    ): ParetoChartModel {
        fun ParetoPoint.toScatter() = ParetoScatterPoint(
            x = stdDevUtility,
            y = avgUtility,
            point = this
        )

        return ParetoChartModel(
            basePoints = points.map { it.toScatter() },
            referenceMarker = referencePoint?.toScatter(),
            appliedMarker = appliedPoint?.toScatter(),
            selectedMarker = selectedPoint?.toScatter()
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.logic.ParetoChartModelBuilderTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/example/daysurpopt/logic/ParetoChartModelBuilderTest.kt app/src/main/java/com/example/daysurpopt/logic/ParetoChartModelBuilder.kt
git commit -m "feat: add pareto chart model builder"
```

### Task 3: Make Pareto Optimization Apply Live Inputs

**Files:**
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\FinancialViewModel.kt`

- [ ] **Step 1: Write the failing test**

Add this test to `ParetoChartStateTest.kt`:

```kotlin
@Test
fun selectedParetoPoint_can_be_applied_to_live_inputs_consistently() {
    val point = ParetoPoint(
        params = ParamsCandidate(0.45, 63, 0.30, 69),
        avgUtility = 0.32,
        stdDevUtility = 0.11,
        isFeasible = true,
        finalCapital = 82000.0,
        legacyGap = 10000.0
    )

    val updated = applyOptimizationParamsForTest(
        baseInputs = com.example.daysurpopt.domain.FinancialInput(),
        point = point
    )

    assertEquals(0.45, updated.p1SavingRatioSurplus, 1e-9)
    assertEquals(63, updated.p2EtaFineRisparmioNoCapitale)
    assertEquals(0.30, updated.p3PercentualeCapitaleDaSpendereAnnualmente, 1e-9)
    assertEquals(69, updated.p4EtaAnticipataInizioSpesaCapitale)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.ui.screens.ParetoChartStateTest.selectedParetoPoint_can_be_applied_to_live_inputs_consistently"`
Expected: FAIL because `applyOptimizationParamsForTest(...)` does not exist.

- [ ] **Step 3: Write minimal implementation**

In `FinancialViewModel.kt`, extract and reuse a pure helper:

```kotlin
internal fun applyOptimizationParamsForTest(
    baseInputs: FinancialInput,
    point: ParetoPoint
): FinancialInput {
    val p2 = point.params.p2
    val p4 = maxOf(point.params.p4, p2)
    return baseInputs.copy(
        p1SavingRatioSurplus = point.params.p1,
        p2EtaFineRisparmioNoCapitale = p2,
        p3PercentualeCapitaleDaSpendereAnnualmente = point.params.p3,
        p4EtaAnticipataInizioSpesaCapitale = p4
    )
}
```

Then make the existing private helper delegate to it:

```kotlin
private fun applyOptimizationParams(baseInputs: FinancialInput, params: ParamsCandidate): FinancialInput {
    val point = ParetoPoint(
        params = params,
        avgUtility = 0.0,
        stdDevUtility = 0.0,
        isFeasible = true,
        finalCapital = 0.0,
        legacyGap = 0.0
    )
    return applyOptimizationParamsForTest(baseInputs, point)
}
```

- [ ] **Step 4: Update Pareto optimize flow**

In `runOptimization()`, replace the `PARETO_FRONT` branch behavior so it also applies the selected compromise/reference to live state:

```kotlin
if (optimizationMode == OptimizationMode.PARETO_FRONT && selectedCompromise != null) {
    inputs = applyOptimizationParams(inputs, selectedCompromise.params)
    saveInputs()
    uiInputs = FinancialInputUI.from(inputs)

    val (objective, years, objectives) = evaluateFinancialInput(inputs)
    publishSimulationResults(objective, years, objectives)

    selectedParetoPoint = selectedCompromise
    appliedParetoSnapshot = selectedCompromise.toOptimizationMarkerSnapshot(
        mode = OptimizationMode.PARETO_FRONT,
        objectiveValue = objectives.fObjW,
        stabilityIndex = objectives.stabilityIndex,
        weightUsed = inputs.bonusStdWeight
    )
    lastParetoReferenceSnapshot = appliedParetoSnapshot
}
```

- [ ] **Step 5: Add new ViewModel state**

Add these fields near the existing optimization snapshot state:

```kotlin
var selectedParetoPoint by mutableStateOf<ParetoPoint?>(null)
    private set
var appliedParetoSnapshot by mutableStateOf<OptimizationMarkerSnapshot?>(null)
    private set
```

Also clear them when optimization artifacts are reset:

```kotlin
private fun clearOptimizationArtifacts() {
    paretoFrontResult = null
    optimizationResult = null
    selectedParetoPoint = null
    appliedParetoSnapshot = null
}
```

- [ ] **Step 6: Run focused tests**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.ui.screens.ParetoChartStateTest"`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add app/src/test/java/com/example/daysurpopt/ui/screens/ParetoChartStateTest.kt app/src/main/java/com/example/daysurpopt/ui/screens/FinancialViewModel.kt
git commit -m "fix: apply pareto optimization to live inputs"
```

### Task 4: Add Explicit Pareto Selection And Apply Actions

**Files:**
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\FinancialViewModel.kt`

- [ ] **Step 1: Write the failing test**

Add this test to `ParetoChartStateTest.kt`:

```kotlin
@Test
fun paretoSelection_does_not_change_applied_snapshot_until_explicit_apply() {
    val applied = ParetoPoint(
        params = ParamsCandidate(0.25, 61, 0.20, 67),
        avgUtility = 0.24,
        stdDevUtility = 0.20,
        isFeasible = true,
        finalCapital = 72000.0,
        legacyGap = 7000.0
    )
    val selected = ParetoPoint(
        params = ParamsCandidate(0.45, 63, 0.35, 69),
        avgUtility = 0.32,
        stdDevUtility = 0.10,
        isFeasible = true,
        finalCapital = 84000.0,
        legacyGap = 13000.0
    )

    val state = FakeParetoSelectionState(appliedPoint = applied, selectedPoint = applied)
    state.select(selected)

    assertEquals(0.25, state.appliedPoint.params.p1, 1e-9)
    assertEquals(0.45, state.selectedPoint.params.p1, 1e-9)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.ui.screens.ParetoChartStateTest.paretoSelection_does_not_change_applied_snapshot_until_explicit_apply"`
Expected: FAIL because `FakeParetoSelectionState` does not exist.

- [ ] **Step 3: Write minimal test helper**

At the bottom of `ParetoChartStateTest.kt`, add:

```kotlin
private class FakeParetoSelectionState(
    var appliedPoint: ParetoPoint,
    var selectedPoint: ParetoPoint
) {
    fun select(point: ParetoPoint) {
        selectedPoint = point
    }
}
```

- [ ] **Step 4: Add ViewModel actions**

In `FinancialViewModel.kt`, add:

```kotlin
fun selectParetoPoint(point: ParetoPoint) {
    selectedParetoPoint = point
}

fun resetParetoSelectionToReference() {
    selectedParetoPoint = paretoFrontResult?.selectedCompromise
}

fun applySelectedParetoPoint() {
    val point = selectedParetoPoint ?: return
    viewModelScope.launch {
        inputs = applyOptimizationParams(inputs, point.params)
        saveInputs()
        uiInputs = FinancialInputUI.from(inputs)

        val (objective, years, objectives) = evaluateFinancialInput(inputs)
        publishSimulationResults(objective, years, objectives)

        appliedParetoSnapshot = point.toOptimizationMarkerSnapshot(
            mode = OptimizationMode.PARETO_FRONT,
            objectiveValue = objectives.fObjW,
            stabilityIndex = objectives.stabilityIndex,
            weightUsed = inputs.bonusStdWeight
        )
    }
}
```

- [ ] **Step 5: Keep reference selection synced on weight change**

In `onChartWeightChangeFinished()` Pareto branch, after recomputing `selected`, add:

```kotlin
val previousSelection = selectedParetoPoint
val wasReferenceSelection = previousSelection == null || previousSelection.params == front.selectedCompromise?.params

if (front != null && selected != null) {
    paretoFrontResult = front.copy(selectedCompromise = selected)
    if (wasReferenceSelection) {
        selectedParetoPoint = selected
    }
    // keep existing snapshot update logic
}
```

- [ ] **Step 6: Run focused tests**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.ui.screens.ParetoChartStateTest"`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add app/src/test/java/com/example/daysurpopt/ui/screens/ParetoChartStateTest.kt app/src/main/java/com/example/daysurpopt/ui/screens/FinancialViewModel.kt
git commit -m "feat: add explicit pareto select and apply actions"
```

### Task 5: Add Dedicated Pareto Chart Section In Charts Screen

**Files:**
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\ChartsScreen.kt`
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\logic\ParetoChartModelBuilder.kt`

- [ ] **Step 1: Add chart-specific labels and state plumbing**

In `ChartsScreen.kt`, read:

```kotlin
val selectedParetoPoint = viewModel.selectedParetoPoint
val appliedParetoSnapshot = viewModel.appliedParetoSnapshot
```

Build the chart model:

```kotlin
val paretoChartModel = remember(
    paretoFrontResult,
    selectedParetoPoint,
    appliedParetoSnapshot
) {
    ParetoChartModelBuilder.build(
        points = paretoFrontResult?.points ?: emptyList(),
        referencePoint = paretoFrontResult?.selectedCompromise,
        appliedPoint = appliedParetoSnapshot?.let { snapshot ->
            paretoFrontResult?.points?.firstOrNull { it.params == snapshot.params }
        },
        selectedPoint = selectedParetoPoint
    )
}
```

- [ ] **Step 2: Add the Pareto section UI**

Inside `ChartsContent`, add a new card before the current scalar surfaces:

```kotlin
paretoFrontResult?.takeIf { it.points.isNotEmpty() }?.let { front ->
    ParetoChartSection(
        front = front,
        chartModel = paretoChartModel,
        selectedPoint = selectedParetoPoint,
        onSelectPoint = { viewModel.selectParetoPoint(it) },
        onApplySelected = { viewModel.applySelectedParetoPoint() },
        onResetSelection = { viewModel.resetParetoSelectionToReference() }
    )
}
```

- [ ] **Step 3: Add minimal section composable**

In `ChartsScreen.kt`, add:

```kotlin
@Composable
private fun ParetoChartSection(
    front: ParetoFrontResult,
    chartModel: ParetoChartModel,
    selectedPoint: ParetoPoint?,
    onSelectPoint: (ParetoPoint) -> Unit,
    onApplySelected: () -> Unit,
    onResetSelection: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.pareto_chart_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.pareto_chart_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ParetoScatterSummary(chartModel = chartModel)
            ParetoSelectedPointCard(selectedPoint = selectedPoint, front = front)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onApplySelected,
                    enabled = selectedPoint != null
                ) {
                    Text(stringResource(R.string.pareto_apply_selected))
                }
                OutlinedButton(onClick = onResetSelection) {
                    Text(stringResource(R.string.pareto_reset_selection))
                }
            }
        }
    }
}
```

- [ ] **Step 4: Add minimal visual stub for first green step**

Also in `ChartsScreen.kt`, add:

```kotlin
@Composable
private fun ParetoScatterSummary(chartModel: ParetoChartModel) {
    Text(
        text = "Pareto points: ${chartModel.basePoints.size} | " +
            "Reference: ${chartModel.referenceMarker != null} | " +
            "Applied: ${chartModel.appliedMarker != null} | " +
            "Selected: ${chartModel.selectedMarker != null}",
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun ParetoSelectedPointCard(
    selectedPoint: ParetoPoint?,
    front: ParetoFrontResult
) {
    val point = selectedPoint ?: front.selectedCompromise
    if (point == null) return
    Text(
        text = "Selected P1=${point.params.p1} P2=${point.params.p2} " +
            "P3=${point.params.p3} P4=${point.params.p4} " +
            "Avg=${point.avgUtility} Std=${point.stdDevUtility}",
        style = MaterialTheme.typography.bodySmall
    )
}
```

- [ ] **Step 5: Run build to verify UI compiles**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/daysurpopt/ui/screens/ChartsScreen.kt app/src/main/java/com/example/daysurpopt/logic/ParetoChartModelBuilder.kt
git commit -m "feat: add dedicated pareto chart section"
```

### Task 6: Replace Pareto Stub With Real Plotly Scatter Traces

**Files:**
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\ChartsScreen.kt`

- [ ] **Step 1: Write the failing test**

Add to `ParetoChartModelBuilderTest.kt`:

```kotlin
@Test
fun buildMarkers_keeps_objective_space_coordinates_stddev_on_x_avg_on_y() {
    val point = ParetoPoint(
        params = ParamsCandidate(0.20, 60, 0.20, 66),
        avgUtility = 0.25,
        stdDevUtility = 0.12,
        isFeasible = true,
        finalCapital = 70000.0,
        legacyGap = 5000.0
    )

    val model = ParetoChartModelBuilder.build(
        points = listOf(point),
        referencePoint = point,
        appliedPoint = point,
        selectedPoint = point
    )

    assertEquals(0.12, model.basePoints.first().x, 1e-9)
    assertEquals(0.25, model.basePoints.first().y, 1e-9)
}
```

- [ ] **Step 2: Run test to verify it fails if needed**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.logic.ParetoChartModelBuilderTest"`
Expected: PASS or fail only if coordinates were changed incorrectly. If already PASS, keep it as regression coverage and continue.

- [ ] **Step 3: Implement real scatter rendering**

Replace the text stub with a real scatter using the existing web chart rendering path. Reuse `SurfaceWebView` or the same Plotly injection approach, but build a scatter-only grid/spec for:

```kotlin
// Base Pareto front points
// Reference marker
// Applied marker
// Selected marker
// X axis = StdDev, Y axis = AvgUtility
```

Keep the minimal UI API:

```kotlin
ParetoScatterPlot(
    chartModel = chartModel,
    onPointSelected = onSelectPoint
)
```

- [ ] **Step 4: Show details panel with semantic badges**

Update `ParetoSelectedPointCard` so it includes:

```kotlin
Text("Avg Utility: ...")
Text("Std Dev: ...")
Text("Final Capital: ...")
Text("Legacy Gap: ...")
Text("Compromise Score: ...")
Text("Selected / Applied / Reference")
```

- [ ] **Step 5: Run focused test/build**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.logic.ParetoChartModelBuilderTest"`
Expected: PASS

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/test/java/com/example/daysurpopt/logic/ParetoChartModelBuilderTest.kt app/src/main/java/com/example/daysurpopt/ui/screens/ChartsScreen.kt
git commit -m "feat: render pareto objective-space scatter"
```

### Task 7: Update Main Results Screen For Pareto Applied Semantics

**Files:**
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\FinancialCalculatorScreen.kt`

- [ ] **Step 1: Update Pareto summary copy**

In the optimization results card, change the Pareto summary from front-only wording to applied-plan wording:

```kotlin
val summaryText = if (res.mode == OptimizationMode.PARETO_FRONT) {
    stringResource(
        R.string.optimization_mode_pareto_summary_applied,
        res.paretoPointCount,
        res.p1,
        res.p2,
        res.p3,
        res.p4
    )
} else {
    ...
}
```

- [ ] **Step 2: Keep reference compromise line**

Retain the explicit front reference line:

```kotlin
Text(
    text = stringResource(
        R.string.pareto_reference_compromise,
        paretoFrontResult.selectedCompromise!!.compromiseScore ?: 0.0,
        paretoFrontResult.selectedCompromise!!.params.p1,
        paretoFrontResult.selectedCompromise!!.params.p2,
        paretoFrontResult.selectedCompromise!!.params.p3,
        paretoFrontResult.selectedCompromise!!.params.p4
    )
)
```

- [ ] **Step 3: Verify applied inputs are what the results card reports**

No new code block here: read the card code carefully and ensure `res.p1..p4` comes from the applied selected point after the Task 3 wiring change. If not, fix `optimizationResult` assignment in `FinancialViewModel` so it uses the applied point consistently.

- [ ] **Step 4: Run build**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/daysurpopt/ui/screens/FinancialCalculatorScreen.kt app/src/main/java/com/example/daysurpopt/ui/screens/FinancialViewModel.kt
git commit -m "fix: clarify pareto applied-plan results"
```

### Task 8: Add Strings For Dedicated Pareto Chart

**Files:**
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\res\values\strings.xml`
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\res\values-it\strings.xml`
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\res\values-es\strings.xml`

- [ ] **Step 1: Add English strings**

```xml
<string name="pareto_chart_title">Pareto Front Explorer</string>
<string name="pareto_chart_subtitle">Inspect the feasible trade-off frontier in objective space. Tap a point to inspect it, then apply explicitly if desired.</string>
<string name="pareto_apply_selected">Apply selected Pareto point</string>
<string name="pareto_reset_selection">Reset selection to reference</string>
<string name="optimization_mode_pareto_summary_applied">Pareto front computed with %1$d feasible non-dominated plans.\nApplied reference plan: P1=%2$.4f P2=%3$d P3=%4$.4f P4=%5$d</string>
```

- [ ] **Step 2: Add Italian strings**

```xml
<string name="pareto_chart_title">Esploratore Frontiera di Pareto</string>
<string name="pareto_chart_subtitle">Ispeziona la frontiera fattibile dei trade-off nello spazio obiettivi. Tocca un punto per analizzarlo e applicalo solo in modo esplicito.</string>
<string name="pareto_apply_selected">Applica punto Pareto selezionato</string>
<string name="pareto_reset_selection">Reset selezione al riferimento</string>
<string name="optimization_mode_pareto_summary_applied">Frontiera di Pareto calcolata con %1$d piani fattibili non dominati.\nPiano di riferimento applicato: P1=%2$.4f P2=%3$d P3=%4$.4f P4=%5$d</string>
```

- [ ] **Step 3: Add Spanish strings**

```xml
<string name="pareto_chart_title">Explorador del Frente de Pareto</string>
<string name="pareto_chart_subtitle">Inspecciona la frontera factible de trade-offs en el espacio objetivo. Toca un punto para inspeccionarlo y aplícalo solo de forma explícita.</string>
<string name="pareto_apply_selected">Aplicar punto Pareto seleccionado</string>
<string name="pareto_reset_selection">Restablecer selección a referencia</string>
<string name="optimization_mode_pareto_summary_applied">Frente de Pareto calculado con %1$d planes factibles no dominados.\nPlan de referencia aplicado: P1=%2$.4f P2=%3$d P3=%4$.4f P4=%5$d</string>
```

- [ ] **Step 4: Run build**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-it/strings.xml app/src/main/res/values-es/strings.xml
git commit -m "feat: add pareto explorer strings"
```

### Task 9: Final Verification

**Files:**
- Check: all touched files above

- [ ] **Step 1: Run focused regression tests**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.ui.screens.ParetoChartStateTest" --tests "com.example.daysurpopt.logic.ParetoChartModelBuilderTest" --tests "com.example.daysurpopt.logic.CompromiseSelectionLogicTest"`
Expected: PASS

- [ ] **Step 2: Run full unit tests**

Run: `./gradlew.bat testDebugUnitTest`
Expected: PASS

- [ ] **Step 3: Run debug build**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Run diagnostics on touched Kotlin files**

Use IDE diagnostics and confirm no new errors in:

```text
app/src/main/java/com/example/daysurpopt/ui/screens/FinancialViewModel.kt
app/src/main/java/com/example/daysurpopt/ui/screens/FinancialCalculatorScreen.kt
app/src/main/java/com/example/daysurpopt/ui/screens/ChartsScreen.kt
app/src/main/java/com/example/daysurpopt/logic/ParetoChartModelBuilder.kt
app/src/main/java/com/example/daysurpopt/domain/ParetoModels.kt
```

- [ ] **Step 5: Update workflow documentation**

Append the implementation details to:

```text
F:\MCP\TRADING\WORKFLOW.md
```

Include:

- Pareto optimize now applies live `P1..P4`
- selected vs applied vs reference state distinction
- dedicated Pareto explorer section in charts
- cached-front reselection on weight change

- [ ] **Step 6: Commit final pass**

```bash
git add app/src/main/java/com/example/daysurpopt app/src/test/java/com/example/daysurpopt docs/superpowers/specs/2026-06-26-pareto-apply-and-chart-design.md docs/superpowers/plans/2026-06-26-pareto-apply-and-chart.md F:/MCP/TRADING/WORKFLOW.md
git commit -m "feat: add pareto explorer and applied pareto workflow"
```
