# True Scalar Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a real `True Scalar` optimization mode, rename the current compromise mode to `Pareto Compromise`, preserve `Pareto Front`, and keep charts/results/exports mathematically consistent across all three modes.

**Architecture:** Extend `OptimizationMode` to three values and route each mode through an explicit engine in `FinancialViewModel`: scalar optimizer for `TRUE_SCALAR`, Pareto front plus selector for `PARETO_COMPROMISE`, and full front/reference workflow for `PARETO_FRONT`. Update UI/chart/export labels exhaustively, keep mode-specific marker snapshots separate, and verify with TDD that chart `fObjW` alignment is restored for scalar mode without regressing Pareto flows.

**Tech Stack:** Kotlin, Jetpack Compose, Android ViewModel, existing scalar optimizer, Pareto logic, Plotly/WebView chart layer, JUnit4

---

## File Map

**Create:**
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\test\java\com\example\daysurpopt\ui\screens\OptimizationModeFlowTest.kt`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\test\java\com\example\daysurpopt\logic\OptimizationModeLabelTest.kt`

**Modify:**
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\domain\ParetoModels.kt`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\FinancialViewModel.kt`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\FinancialCalculatorScreen.kt`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\ChartsScreen.kt`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\logic\PdfExporter.kt`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\res\values\strings.xml`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\res\values-it\strings.xml`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\res\values-es\strings.xml`
- `F:\MCP\TRADING\WORKFLOW.md`

### Task 1: Expand Optimization Mode Model

**Files:**
- Create: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\test\java\com\example\daysurpopt\logic\OptimizationModeLabelTest.kt`
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\domain\ParetoModels.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.daysurpopt.logic

import com.example.daysurpopt.domain.OptimizationMode
import org.junit.Assert.assertEquals
import org.junit.Test

class OptimizationModeLabelTest {

    @Test
    fun optimizationMode_contains_true_scalar_pareto_compromise_and_pareto_front() {
        val modes = OptimizationMode.entries.map { it.name }

        assertEquals(
            listOf("TRUE_SCALAR", "PARETO_COMPROMISE", "PARETO_FRONT"),
            modes
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.logic.OptimizationModeLabelTest"`
Expected: FAIL because the enum still has only two values.

- [ ] **Step 3: Write minimal implementation**

Update `ParetoModels.kt`:

```kotlin
enum class OptimizationMode {
    TRUE_SCALAR,
    PARETO_COMPROMISE,
    PARETO_FRONT
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.logic.OptimizationModeLabelTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/example/daysurpopt/logic/OptimizationModeLabelTest.kt app/src/main/java/com/example/daysurpopt/domain/ParetoModels.kt
git commit -m "feat: expand optimization modes to three explicit states"
```

### Task 2: Add Pure Helpers For Mode-Specific Weight Behavior

**Files:**
- Create: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\test\java\com\example\daysurpopt\ui\screens\OptimizationModeFlowTest.kt`
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\FinancialViewModel.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.daysurpopt.ui.screens

import com.example.daysurpopt.domain.OptimizationMode
import org.junit.Assert.assertEquals
import org.junit.Test

class OptimizationModeFlowTest {

    @Test
    fun chartWeightReleaseAction_is_scalar_for_true_scalar_mode() {
        assertEquals(
            "rerun_scalar",
            chartWeightReleaseActionForMode(OptimizationMode.TRUE_SCALAR)
        )
    }

    @Test
    fun chartWeightReleaseAction_is_compromise_for_pareto_compromise_mode() {
        assertEquals(
            "rerun_compromise",
            chartWeightReleaseActionForMode(OptimizationMode.PARETO_COMPROMISE)
        )
    }

    @Test
    fun chartWeightReleaseAction_is_reselect_front_for_pareto_front_mode() {
        assertEquals(
            "reselect_front",
            chartWeightReleaseActionForMode(OptimizationMode.PARETO_FRONT)
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.ui.screens.OptimizationModeFlowTest"`
Expected: FAIL because `chartWeightReleaseActionForMode(...)` does not exist.

- [ ] **Step 3: Write minimal implementation**

Add this helper near the other internal pure helpers in `FinancialViewModel.kt`:

```kotlin
internal fun chartWeightReleaseActionForMode(mode: OptimizationMode): String {
    return when (mode) {
        OptimizationMode.TRUE_SCALAR -> "rerun_scalar"
        OptimizationMode.PARETO_COMPROMISE -> "rerun_compromise"
        OptimizationMode.PARETO_FRONT -> "reselect_front"
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.ui.screens.OptimizationModeFlowTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/example/daysurpopt/ui/screens/OptimizationModeFlowTest.kt app/src/main/java/com/example/daysurpopt/ui/screens/FinancialViewModel.kt
git commit -m "test: encode mode-specific weight release behavior"
```

### Task 3: Make True Scalar The Default Mode

**Files:**
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\FinancialViewModel.kt`

- [ ] **Step 1: Write the failing test**

Add this test to `OptimizationModeFlowTest.kt`:

```kotlin
@Test
fun defaultOptimizationMode_is_true_scalar() {
    assertEquals(
        OptimizationMode.TRUE_SCALAR,
        defaultOptimizationModeForTest()
    )
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.ui.screens.OptimizationModeFlowTest.defaultOptimizationMode_is_true_scalar"`
Expected: FAIL because `defaultOptimizationModeForTest()` does not exist.

- [ ] **Step 3: Write minimal implementation**

Add helper:

```kotlin
internal fun defaultOptimizationModeForTest(): OptimizationMode {
    return OptimizationMode.TRUE_SCALAR
}
```

Then update the property:

```kotlin
var optimizationMode by mutableStateOf(defaultOptimizationModeForTest())
    private set
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.ui.screens.OptimizationModeFlowTest.defaultOptimizationMode_is_true_scalar"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/example/daysurpopt/ui/screens/OptimizationModeFlowTest.kt app/src/main/java/com/example/daysurpopt/ui/screens/FinancialViewModel.kt
git commit -m "feat: make true scalar the default optimization mode"
```

### Task 4: Route Optimize Button Through Three Explicit Engines

**Files:**
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\FinancialViewModel.kt`

- [ ] **Step 1: Write the failing test**

Add this test to `OptimizationModeFlowTest.kt`:

```kotlin
@Test
fun optimizationExecutionPath_is_distinct_for_each_mode() {
    assertEquals("scalar_optimizer", optimizationExecutionPathForMode(OptimizationMode.TRUE_SCALAR))
    assertEquals("pareto_compromise", optimizationExecutionPathForMode(OptimizationMode.PARETO_COMPROMISE))
    assertEquals("pareto_front", optimizationExecutionPathForMode(OptimizationMode.PARETO_FRONT))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.ui.screens.OptimizationModeFlowTest.optimizationExecutionPath_is_distinct_for_each_mode"`
Expected: FAIL because `optimizationExecutionPathForMode(...)` does not exist.

- [ ] **Step 3: Write minimal implementation**

Add helper:

```kotlin
internal fun optimizationExecutionPathForMode(mode: OptimizationMode): String {
    return when (mode) {
        OptimizationMode.TRUE_SCALAR -> "scalar_optimizer"
        OptimizationMode.PARETO_COMPROMISE -> "pareto_compromise"
        OptimizationMode.PARETO_FRONT -> "pareto_front"
    }
}
```

- [ ] **Step 4: Refactor `runOptimization()`**

In `FinancialViewModel.kt`, split the existing logic into three branches:

```kotlin
when (optimizationMode) {
    OptimizationMode.TRUE_SCALAR -> runTrueScalarOptimization()
    OptimizationMode.PARETO_COMPROMISE -> runParetoCompromiseOptimization()
    OptimizationMode.PARETO_FRONT -> runParetoFrontOptimization()
}
```

Implement or extract these methods:

```kotlin
private suspend fun runTrueScalarOptimization() { /* uses OptimizationLogic.optimizeParameters(...) */ }
private suspend fun runParetoCompromiseOptimization() { /* uses ParetoOptimizationLogic + CompromiseSelectionLogic */ }
private suspend fun runParetoFrontOptimization() { /* uses Pareto front + reference application */ }
```

`runTrueScalarOptimization()` must:

- call the scalar optimizer
- apply scalar-optimal params to live inputs
- update `uiInputs`
- publish simulation/objective state
- update a scalar snapshot, reusing `lastBestCompromiseSnapshot` only after renaming it appropriately

Before implementing, rename the scalar snapshot property to avoid confusion:

```kotlin
var lastTrueScalarSnapshot by mutableStateOf<OptimizationMarkerSnapshot?>(null)
    private set
```

- [ ] **Step 5: Run focused tests**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.ui.screens.OptimizationModeFlowTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/test/java/com/example/daysurpopt/ui/screens/OptimizationModeFlowTest.kt app/src/main/java/com/example/daysurpopt/ui/screens/FinancialViewModel.kt
git commit -m "feat: route optimization through scalar compromise and pareto engines"
```

### Task 5: Rename Best Compromise Semantics To Pareto Compromise

**Files:**
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\FinancialViewModel.kt`
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\ChartsScreen.kt`

- [ ] **Step 1: Write the failing test**

Add this test to `OptimizationModeLabelTest.kt`:

```kotlin
@Test
fun optimizationModeLabelName_for_compromise_uses_pareto_compromise_wording() {
    assertEquals(
        "Pareto Compromise",
        optimizationModeDisplayNameForTest(OptimizationMode.PARETO_COMPROMISE)
    )
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.logic.OptimizationModeLabelTest.optimizationModeLabelName_for_compromise_uses_pareto_compromise_wording"`
Expected: FAIL because `optimizationModeDisplayNameForTest(...)` does not exist.

- [ ] **Step 3: Write minimal implementation**

Add a pure helper in `FinancialViewModel.kt` or a small label helper file if preferred:

```kotlin
internal fun optimizationModeDisplayNameForTest(mode: OptimizationMode): String {
    return when (mode) {
        OptimizationMode.TRUE_SCALAR -> "True Scalar"
        OptimizationMode.PARETO_COMPROMISE -> "Pareto Compromise"
        OptimizationMode.PARETO_FRONT -> "Pareto Front"
    }
}
```

- [ ] **Step 4: Rename snapshot/state labels in code**

Update confusing scalar-vs-compromise names:

```kotlin
var lastTrueScalarSnapshot by mutableStateOf<OptimizationMarkerSnapshot?>(null)
var lastParetoCompromiseSnapshot by mutableStateOf<OptimizationMarkerSnapshot?>(null)
```

Update chart marker construction so:

- scalar marker uses `lastTrueScalarSnapshot`
- compromise marker uses `lastParetoCompromiseSnapshot`
- Pareto front reference uses `lastParetoReferenceSnapshot`

- [ ] **Step 5: Run focused tests**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.logic.OptimizationModeLabelTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/test/java/com/example/daysurpopt/logic/OptimizationModeLabelTest.kt app/src/main/java/com/example/daysurpopt/ui/screens/FinancialViewModel.kt app/src/main/java/com/example/daysurpopt/ui/screens/ChartsScreen.kt
git commit -m "refactor: rename compromise state to pareto compromise"
```

### Task 6: Fix Chart Weight Release Handling For Three Modes

**Files:**
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\FinancialViewModel.kt`

- [ ] **Step 1: Write the failing test**

Add this test to `OptimizationModeFlowTest.kt`:

```kotlin
@Test
fun scalarMode_chartWeightRelease_never_maps_to_reselect_front() {
    val action = chartWeightReleaseActionForMode(OptimizationMode.TRUE_SCALAR)
    assertEquals("rerun_scalar", action)
}
```

- [ ] **Step 2: Run test to verify it fails if behavior regressed**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.ui.screens.OptimizationModeFlowTest.scalarMode_chartWeightRelease_never_maps_to_reselect_front"`
Expected: PASS or fail only if helper logic regressed. Keep it as regression coverage either way.

- [ ] **Step 3: Update `onChartWeightChangeFinished()`**

Refactor to:

```kotlin
when (optimizationMode) {
    OptimizationMode.TRUE_SCALAR -> runOptimization()
    OptimizationMode.PARETO_COMPROMISE -> runOptimization()
    OptimizationMode.PARETO_FRONT -> { /* cached-front reselection path */ }
}
```

This explicitly preserves:

- scalar rerun for `TRUE_SCALAR`
- compromise rerun for `PARETO_COMPROMISE`
- cached-front reselection for `PARETO_FRONT`

- [ ] **Step 4: Run focused tests**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.ui.screens.OptimizationModeFlowTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/example/daysurpopt/ui/screens/OptimizationModeFlowTest.kt app/src/main/java/com/example/daysurpopt/ui/screens/FinancialViewModel.kt
git commit -m "fix: align chart weight release behavior with three optimization modes"
```

### Task 7: Update Financial Calculator UI To Three Visible Modes

**Files:**
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\FinancialCalculatorScreen.kt`
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\res\values\strings.xml`
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\res\values-it\strings.xml`
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\res\values-es\strings.xml`

- [ ] **Step 1: Add strings for three explicit modes**

Add in all locales:

```xml
<string name="optimization_mode_true_scalar">True Scalar</string>
<string name="optimization_mode_pareto_compromise">Pareto Compromise</string>
<string name="optimization_mode_true_scalar_desc">Directly maximizes the scalar objective shown on the scalar charts.</string>
<string name="optimization_mode_pareto_compromise_desc">Computes the Pareto front and applies one weight-aware compromise plan.</string>
<string name="optimization_mode_true_scalar_definition">Scalar rule: directly maximize F(w) over the parameter space.</string>
<string name="optimization_mode_pareto_compromise_definition">Compromise rule: compute the Pareto front and choose one balanced point using the weight-aware selector.</string>
```

- [ ] **Step 2: Replace the two-button selector with three buttons**

Update the mode section in `FinancialCalculatorScreen.kt`:

```kotlin
Button(onClick = { onUpdateOptimizationMode(OptimizationMode.TRUE_SCALAR) }) { ... }
OutlinedButton(onClick = { onUpdateOptimizationMode(OptimizationMode.PARETO_COMPROMISE) }) { ... }
OutlinedButton(onClick = { onUpdateOptimizationMode(OptimizationMode.PARETO_FRONT) }) { ... }
```

Use the correct selected styling for all three cases.

- [ ] **Step 3: Update descriptive copy**

Replace the binary `if` blocks with a `when`:

```kotlin
text = when (optimizationMode) {
    OptimizationMode.TRUE_SCALAR -> stringResource(R.string.optimization_mode_true_scalar_desc)
    OptimizationMode.PARETO_COMPROMISE -> stringResource(R.string.optimization_mode_pareto_compromise_desc)
    OptimizationMode.PARETO_FRONT -> stringResource(R.string.optimization_mode_pareto_front_desc)
}
```

Do the same for the mathematical definition block.

- [ ] **Step 4: Run build**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/daysurpopt/ui/screens/FinancialCalculatorScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-it/strings.xml app/src/main/res/values-es/strings.xml
git commit -m "feat: expose true scalar pareto compromise and pareto front in UI"
```

### Task 8: Update Results Summaries For Three Modes

**Files:**
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\FinancialCalculatorScreen.kt`
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\res\values\strings.xml`
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\res\values-it\strings.xml`
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\res\values-es\strings.xml`

- [ ] **Step 1: Add mode-specific summary strings**

Add in all locales:

```xml
<string name="optimization_mode_true_scalar_summary">Scalar optimum applied.\nScalar objective: %1$.4f\nApplied params: P1=%2$.4f P2=%3$d P3=%4$.4f P4=%5$d</string>
<string name="optimization_mode_pareto_compromise_summary">Pareto compromise selected from %1$d Pareto points.\nCompromise score: %2$.4f\nApplied params: P1=%3$.4f P2=%4$d P3=%5$.4f P4=%6$d</string>
```

Keep the existing `optimization_mode_pareto_summary_applied` for the front mode.

- [ ] **Step 2: Update summary selection logic**

In `FinancialCalculatorScreen.kt`, use:

```kotlin
val summaryText = when (res.mode) {
    OptimizationMode.TRUE_SCALAR -> stringResource(
        R.string.optimization_mode_true_scalar_summary,
        res.finalFitness,
        res.p1,
        res.p2,
        res.p3,
        res.p4
    )
    OptimizationMode.PARETO_COMPROMISE -> stringResource(
        R.string.optimization_mode_pareto_compromise_summary,
        res.paretoPointCount,
        res.compromiseScore ?: 0.0,
        res.p1,
        res.p2,
        res.p3,
        res.p4
    )
    OptimizationMode.PARETO_FRONT -> stringResource(
        R.string.optimization_mode_pareto_summary_applied,
        res.paretoPointCount,
        res.p1,
        res.p2,
        res.p3,
        res.p4
    )
}
```

- [ ] **Step 3: Limit front-reference extra line to front mode**

Keep:

```kotlin
if (res.mode == OptimizationMode.PARETO_FRONT && paretoFrontResult?.selectedCompromise != null) { ... }
```

Do not show that line in scalar mode or Pareto compromise mode.

- [ ] **Step 4: Run build**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/daysurpopt/ui/screens/FinancialCalculatorScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-it/strings.xml app/src/main/res/values-es/strings.xml
git commit -m "fix: make optimization summaries explicit by mode"
```

### Task 9: Update Chart Marker Semantics And Labels

**Files:**
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\ChartsScreen.kt`
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\res\values\strings.xml`
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\res\values-it\strings.xml`
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\res\values-es\strings.xml`

- [ ] **Step 1: Add/rename chart marker labels**

Add in all locales:

```xml
<string name="chart_marker_true_scalar">True Scalar</string>
<string name="chart_marker_pareto_compromise">Pareto Compromise</string>
```

Remove or stop using the misleading scalar label currently derived from `Best Compromise`.

- [ ] **Step 2: Update marker building logic**

In `ChartsScreen.kt`, the marker list must now use:

```kotlin
lastTrueScalarSnapshot
lastParetoCompromiseSnapshot
lastParetoReferenceSnapshot
```

with labels:

- current inputs
- true scalar
- Pareto compromise
- Pareto reference

- [ ] **Step 3: Update chart-side summary wording**

Replace any logic like:

```kotlin
if (optimizationMode == OptimizationMode.BEST_COMPROMISE)
```

with explicit `when` branches for:

- `TRUE_SCALAR`
- `PARETO_COMPROMISE`
- `PARETO_FRONT`

Scalar chart summary text must state that scalar mode aligns with the plotted `F(w)` surface.

- [ ] **Step 4: Run build**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/daysurpopt/ui/screens/ChartsScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-it/strings.xml app/src/main/res/values-es/strings.xml
git commit -m "fix: align chart markers and labels with three optimization modes"
```

### Task 10: Update PDF Export And Report Semantics

**Files:**
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\logic\PdfExporter.kt`
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\res\values\strings.xml`
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\res\values-it\strings.xml`
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\res\values-es\strings.xml`

- [ ] **Step 1: Add export explanation strings**

Add in all locales:

```xml
<string name="optimization_mode_true_scalar_export_desc">Direct scalar optimization of F(w).</string>
<string name="optimization_mode_pareto_compromise_export_desc">Pareto-front optimization followed by compromise-point selection.</string>
<string name="optimization_mode_pareto_front_export_desc">Pareto-front optimization with the current reference point applied.</string>
```

- [ ] **Step 2: Update `PdfExporter.kt`**

Replace binary mode text selection with:

```kotlin
when (optimizationMode) {
    OptimizationMode.TRUE_SCALAR -> context.getString(R.string.optimization_mode_true_scalar)
    OptimizationMode.PARETO_COMPROMISE -> context.getString(R.string.optimization_mode_pareto_compromise)
    OptimizationMode.PARETO_FRONT -> context.getString(R.string.optimization_mode_pareto_front)
}
```

and add the matching export description block using the new strings.

- [ ] **Step 3: Run build**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/daysurpopt/logic/PdfExporter.kt app/src/main/res/values/strings.xml app/src/main/res/values-it/strings.xml app/src/main/res/values-es/strings.xml
git commit -m "fix: update PDF export semantics for true scalar and pareto modes"
```

### Task 11: Final Verification And Workflow Update

**Files:**
- Check: all touched files above
- Modify: `F:\MCP\TRADING\WORKFLOW.md`

- [ ] **Step 1: Run focused regression tests**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.logic.OptimizationModeLabelTest" --tests "com.example.daysurpopt.ui.screens.OptimizationModeFlowTest" --tests "com.example.daysurpopt.ui.screens.ParetoChartStateTest" --tests "com.example.daysurpopt.logic.ParetoChartModelBuilderTest" --tests "com.example.daysurpopt.logic.CompromiseSelectionLogicTest"`
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
app/src/main/java/com/example/daysurpopt/domain/ParetoModels.kt
app/src/main/java/com/example/daysurpopt/ui/screens/FinancialViewModel.kt
app/src/main/java/com/example/daysurpopt/ui/screens/FinancialCalculatorScreen.kt
app/src/main/java/com/example/daysurpopt/ui/screens/ChartsScreen.kt
app/src/main/java/com/example/daysurpopt/logic/PdfExporter.kt
```

- [ ] **Step 5: Update workflow documentation**

Append to `F:\MCP\TRADING\WORKFLOW.md`:

- the app now exposes three optimization modes
- `True Scalar` is the default
- scalar chart surfaces are once again aligned with scalar optimization semantics
- compromise mode is explicitly Pareto-based
- Pareto front remains a separate exploration/reference mode

- [ ] **Step 6: Commit final pass**

```bash
git add app/src/main/java/com/example/daysurpopt app/src/test/java/com/example/daysurpopt docs/superpowers/specs/2026-06-26-true-scalar-mode-design.md docs/superpowers/plans/2026-06-26-true-scalar-mode.md F:/MCP/TRADING/WORKFLOW.md
git commit -m "feat: add true scalar optimization mode and explicit pareto modes"
```
