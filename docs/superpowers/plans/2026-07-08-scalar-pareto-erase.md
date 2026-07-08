# Scalar Pareto Erase Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make true-scalar optimization converge to a refined local optimum, keep Pareto optimization semantically independent from `w`, keep charts plotted with the current scalar `w`, and expose an explicit erase/reset action for saved analysis state.

**Architecture:** Keep the current three-mode structure, but strengthen the scalar pipeline with a deterministic local refinement stage after GA. Preserve Pareto front and knee selection on `avgUtility/stdDev` only, and treat `w` only as the scalar chart/report weight. Add one clear state-clearing action for analysis artifacts and wire it into the main screen.

**Tech Stack:** Kotlin, Jetpack Compose, Android ViewModel, JUnit4, Gradle

---

### Task 1: Add failing tests for scalar refinement and state erase

**Files:**
- Modify: `app/src/test/java/com/example/daysurpopt/ui/screens/OptimizationModeFlowTest.kt`
- Create: `app/src/test/java/com/example/daysurpopt/logic/ScalarOptimizationRefinementTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
// OptimizationModeFlowTest.kt
@Test
fun clearAnalysisState_resets_results_front_and_markers() {
    val state = AnalysisUiState(
        optimizationResult = OptimizationResult(
            mode = OptimizationMode.TRUE_SCALAR,
            gaFitness = 1.0,
            bonusWeight = 0.5,
            finalFitness = 1.1,
            p1 = 0.2,
            p2 = 60,
            p3 = 0.3,
            p4 = 65
        ),
        paretoFrontResult = ParetoFrontResult(points = emptyList()),
        selectedParetoPoint = sampleParetoPoint(),
        appliedParetoSnapshot = sampleSnapshot(),
        objectiveFunctionValue = 1.2,
        simulationCount = 4,
        sensitivityCount = 3
    )

    val cleared = clearAnalysisStateForTest(state)

    assertEquals(null, cleared.optimizationResult)
    assertEquals(null, cleared.paretoFrontResult)
    assertEquals(null, cleared.selectedParetoPoint)
    assertEquals(null, cleared.appliedParetoSnapshot)
    assertEquals(null, cleared.objectiveFunctionValue)
    assertEquals(0, cleared.simulationCount)
    assertEquals(0, cleared.sensitivityCount)
}

// ScalarOptimizationRefinementTest.kt
@Test
fun refineTrueScalarResult_never_worsens_ga_fitness() {
    val start = ParamsCandidate(0.40, 60, 0.30, 65)
    val improved = refineScalarCandidateForTest(
        evaluator = { candidate ->
            if (candidate == start) 10.0 else 11.0
        },
        start = start,
        config = sampleConfig(),
        maximize = true
    )

    assertTrue(improved.bestFitness >= 10.0)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "*OptimizationModeFlowTest" --tests "*ScalarOptimizationRefinementTest"`
Expected: FAIL with unresolved symbols for `AnalysisUiState`, `clearAnalysisStateForTest`, and `refineScalarCandidateForTest`

- [ ] **Step 3: Write minimal implementation**

```kotlin
internal data class AnalysisUiState(...)

internal fun clearAnalysisStateForTest(state: AnalysisUiState): AnalysisUiState = ...

internal fun refineScalarCandidateForTest(...): OptimizationLogic.OptimizationResult = ...
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "*OptimizationModeFlowTest" --tests "*ScalarOptimizationRefinementTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/example/daysurpopt/ui/screens/OptimizationModeFlowTest.kt app/src/test/java/com/example/daysurpopt/logic/ScalarOptimizationRefinementTest.kt app/src/main/java/com/example/daysurpopt/ui/screens/FinancialViewModel.kt app/src/main/java/com/example/daysurpopt/logic/OptimizationLogic.kt
git commit -m "test: cover scalar refinement and analysis reset"
```

### Task 2: Refine true scalar after GA

**Files:**
- Modify: `app/src/main/java/com/example/daysurpopt/logic/OptimizationLogic.kt`
- Modify: `app/src/main/java/com/example/daysurpopt/ui/screens/FinancialViewModel.kt`
- Test: `app/src/test/java/com/example/daysurpopt/logic/ScalarOptimizationRefinementTest.kt`

- [ ] **Step 1: Write the failing refinement test around the helper**

```kotlin
@Test
fun refineTrueScalarResult_returns_local_search_result_when_better() {
    val start = ParamsCandidate(0.40, 60, 0.30, 65)
    val refined = refineScalarCandidateForTest(
        evaluator = { candidate -> if (candidate == start.copy(p1 = 0.45)) 12.0 else 10.0 },
        start = start,
        config = sampleConfig(),
        maximize = true
    )

    assertEquals(0.45, refined.bestParams.p1, 1e-9)
    assertEquals(12.0, refined.bestFitness, 1e-9)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "*ScalarOptimizationRefinementTest.refineTrueScalarResult_returns_local_search_result_when_better"`
Expected: FAIL

- [ ] **Step 3: Implement minimal refinement hook**

```kotlin
fun refineScalarCandidate(
    baseInputs: FinancialInput,
    start: ParamsCandidate,
    config: GAConfig,
    specificExpenses: List<SpecificExpense>,
    surplusData: SurplusInput
): OptimizationResult {
    return coordinateSearch(
        baseInputs = baseInputs,
        start = start,
        config = config,
        specificExpenses = specificExpenses,
        surplusData = surplusData
    )
}
```

```kotlin
val gaResult = OptimizationLogic.optimizeParameters(...)
val refinedResult = withContext(Dispatchers.Default) {
    OptimizationLogic.refineScalarCandidate(
        baseInputs = inputs,
        start = gaResult.bestParams,
        config = scalarConfig,
        specificExpenses = specificExpenses,
        surplusData = surplusData
    )
}
val finalScalarResult = if (refinedResult.bestFitness >= gaResult.bestFitness) refinedResult else gaResult
```

- [ ] **Step 4: Run the focused test and then the suite**

Run: `./gradlew testDebugUnitTest --tests "*ScalarOptimizationRefinementTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/daysurpopt/logic/OptimizationLogic.kt app/src/main/java/com/example/daysurpopt/ui/screens/FinancialViewModel.kt app/src/test/java/com/example/daysurpopt/logic/ScalarOptimizationRefinementTest.kt
git commit -m "feat: refine true scalar optimum after ga"
```

### Task 3: Make Pareto optimization explicitly w-free and preserve scalar chart semantics

**Files:**
- Modify: `app/src/main/java/com/example/daysurpopt/logic/ParetoOptimizationLogic.kt`
- Modify: `app/src/main/java/com/example/daysurpopt/ui/screens/FinancialViewModel.kt`
- Modify: `app/src/main/java/com/example/daysurpopt/ui/screens/ChartsViewModel.kt`
- Test: `app/src/test/java/com/example/daysurpopt/ui/screens/OptimizationModeFlowTest.kt`

- [ ] **Step 1: Write the failing semantic test**

```kotlin
@Test
fun chartScalarWeightDisplay_is_independent_from_optimization_mode_choice() {
    val initial = FinancialInput(bonusStdWeight = 0.77)
    val updated = applyChartWeightUpdateForTest(initial, 0.77)

    assertEquals(0.77, updated.first.bonusStdWeight, 1e-9)
    assertEquals("0.77", updated.second.bonusStdWeight)
}
```

- [ ] **Step 2: Run test to verify behavior is protected**

Run: `./gradlew testDebugUnitTest --tests "*OptimizationModeFlowTest"`
Expected: PASS after keeping current chart-weight helpers intact

- [ ] **Step 3: Implement explicit Pareto metric evaluation**

```kotlin
fun evaluateParetoPoint(
    baseInputs: FinancialInput,
    candidate: ParamsCandidate,
    specificExpenses: List<SpecificExpense>,
    surplusData: SurplusInput
): ParetoPoint { ... }
```

```kotlin
val years = calculateSimulation(in2, specificExpenses, surplusData)
val metrics = calculateObjectivesFromYears(
    years = years,
    bonusStdWeight = 0.0,
    legacyTarget = in2.soldiDaConservare
)
```

- [ ] **Step 4: Keep charts scalar with current input `w`**

Run: no code-path change to mode-based chart objective; confirm [ChartsViewModel] continues using `inputs.bonusStdWeight` only for chart `fObjW`.

- [ ] **Step 5: Run tests**

Run: `./gradlew testDebugUnitTest --tests "*OptimizationModeFlowTest" --tests "*ParetoKneeSelectionLogicTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/daysurpopt/logic/ParetoOptimizationLogic.kt app/src/main/java/com/example/daysurpopt/ui/screens/FinancialViewModel.kt app/src/main/java/com/example/daysurpopt/ui/screens/ChartsViewModel.kt app/src/test/java/com/example/daysurpopt/ui/screens/OptimizationModeFlowTest.kt
git commit -m "refactor: make pareto optimization weight free"
```

### Task 4: Expose erase/reset action in the main UI

**Files:**
- Modify: `app/src/main/java/com/example/daysurpopt/ui/screens/FinancialCalculatorScreen.kt`
- Modify: `app/src/main/java/com/example/daysurpopt/ui/screens/FinancialViewModel.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-it/strings.xml`
- Modify: `app/src/main/res/values-es/strings.xml`
- Test: `app/src/test/java/com/example/daysurpopt/ui/screens/OptimizationModeFlowTest.kt`

- [ ] **Step 1: Write the failing reset test**

```kotlin
@Test
fun clearAnalysisState_preserves_inputs_but_removes_derived_analysis() {
    val state = AnalysisUiState(...)
    val cleared = clearAnalysisStateForTest(state)

    assertEquals(state.inputsWeight, cleared.inputsWeight, 1e-9)
    assertEquals(0, cleared.simulationCount)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "*OptimizationModeFlowTest.clearAnalysisState_preserves_inputs_but_removes_derived_analysis"`
Expected: FAIL

- [ ] **Step 3: Implement the erase action**

```kotlin
fun clearAnalysisState() {
    objectiveFunctionValue = null
    objectiveResults = null
    simulationResults = emptyList()
    sensitivityResults = null
    sensitivityMessageResId = null
    clearOptimizationArtifacts()
    clearOptimizationSnapshots()
}
```

```kotlin
OutlinedButton(
    onClick = onClearAnalysisState,
    modifier = Modifier.fillMaxWidth(),
) {
    Text(stringResource(R.string.clear_analysis_state))
}
```

- [ ] **Step 4: Add localized strings**

```xml
<string name="clear_analysis_state">Erase Analysis Results</string>
<string name="clear_analysis_state_desc">Clears saved simulation results, sensitivity output, Pareto points, and optimization markers without resetting your inputs.</string>
```

- [ ] **Step 5: Run tests**

Run: `./gradlew testDebugUnitTest --tests "*OptimizationModeFlowTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/daysurpopt/ui/screens/FinancialCalculatorScreen.kt app/src/main/java/com/example/daysurpopt/ui/screens/FinancialViewModel.kt app/src/main/res/values/strings.xml app/src/main/res/values-it/strings.xml app/src/main/res/values-es/strings.xml app/src/test/java/com/example/daysurpopt/ui/screens/OptimizationModeFlowTest.kt
git commit -m "feat: add explicit erase action for analysis state"
```

### Task 5: Verify, lint, and document

**Files:**
- Modify: `F:/MCP/TRADING/WORKFLOW.md`

- [ ] **Step 1: Run unit tests**

Run: `./gradlew testDebugUnitTest`
Expected: PASS

- [ ] **Step 2: Run assemble**

Run: `./gradlew assembleDebug`
Expected: PASS

- [ ] **Step 3: Check diagnostics in edited files**

Use VS Code diagnostics on:
- `app/src/main/java/com/example/daysurpopt/ui/screens/FinancialViewModel.kt`
- `app/src/main/java/com/example/daysurpopt/logic/OptimizationLogic.kt`
- `app/src/main/java/com/example/daysurpopt/logic/ParetoOptimizationLogic.kt`
- `app/src/main/java/com/example/daysurpopt/ui/screens/FinancialCalculatorScreen.kt`

- [ ] **Step 4: Update workflow log**

Document:
- true scalar now uses GA + local refinement
- Pareto optimization path is explicitly weight-free
- charts remain scalar with current `w`
- new erase action clears derived analysis only

- [ ] **Step 5: Final commit**

```bash
git add F:/MCP/TRADING/WORKFLOW.md
git commit -m "docs: update workflow for scalar pareto cleanup"
```
