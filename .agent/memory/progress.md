# Progress Log

## 2026-01-22: Compare Mode & PDF Polish

### Status: Completed

#### Completed

1. **Result Table Update (Gained Capital)**:
    - Changed label from "Eroded Capital" to "Gained Capital".
    - Updated logic in `Tables.kt` to calculate `EndYear - StartYear`.
    - Applied semantic coloring: Green for positive gain, Red for negative.
    - Updated Delta logic: `P2 Gain - P1 Gain` (Higher is better).
    - Updated `strings.xml` (EN, IT, ES).

2. **PDF Export Comparison**:
    - Refactored `PdfExporter.kt` to support `CompareState`.
    - All tables (User Data, Utility, Optimization, Results, Sensitivity, Details) now append delta values when in comparison mode.
    - Modified `drawTable` calls to compute and format comparisons on the fly.

## 2026-01-22: Compare Mode Enhancements

### Status: Completed

#### Completed

1. **Semantic Delta Coloring** - Updated `CommonUI.kt`:
   - Added `positiveIsGood` parameter to `DeltaInputField` and `DeltaInputFieldInt`
   - Green for good deltas, Red for bad deltas
   - Changed delta format from "Δ +X" to "(P2: +X)" for clarity

2. **User Inputs Screen** - Updated `UserInputsScreen.kt`:
   - Set `positiveIsGood=false` for debt interest rate field
   - All other fields use default `positiveIsGood=true`

3. **Profile Overwrite Selection** - Updated `ProfilesDialog.kt`:
   - Made existing profile names clickable in Save Mode
   - Clicking a name populates the save field for easy overwriting

4. **Real-time Delta Updates** - Updated `FinancialViewModel.kt`:
   - Modified `runSimulation()` to recompute deltas when Profile 1 changes
   - Modified `runSensitivityAnalysis()` to recompute sensitivity deltas
   - Deltas now update immediately when user edits inputs in compare mode

5. **AI Comparison Support** - Updated `fetchFullAiReport()`:
   - Added comparison context to AI prompts when in compare mode
   - Master agent now produces dedicated comparison analysis
   - Includes delta information in sustainability analysis

6. **PDF Comparison Support** - Updated `PdfExporter.kt`:
   - Added optional `compareState`, `profile2Results`, and `deltaResults` parameters
   - PDF title shows "COMPARISON: P1 vs P2" when in compare mode
   - Foundation laid for full comparison tables (future enhancement)

## 2026-01-20: Compare Profiles Feature

### Status: Core Implementation Complete

#### Completed

1. **Domain Layer** - Created `ComparisonModels.kt` with:
   - `CompareState` data class for tracking comparison mode
   - `DeltaObjectiveResults`, `DeltaSimulationYear`, `DeltaSensitivityResult` for delta values
   - `DeltaCalculator` object with functions for computing deltas

2. **ViewModel Updates** - Added to `FinancialViewModel.kt`:
   - Compare mode state variables (compareState, profile2Results, deltaResults)
   - `enterCompareMode(p1, p2)` and `exitCompareMode()` functions
   - `computeComparisonResults()` for calculating both profiles and deltas

3. **Profile Selection UI** - Updated `ProfilesDialog.kt`:
   - Added compare mode toggle switch
   - Dual checkbox selection for 2 profiles
   - P1/P2 badges showing selection order
   - Compare button when 2 profiles selected

4. **Compare Mode Banner** - Added to `FinancialCalculatorScreen.kt`:
   - Prominent banner when in compare mode (secondaryContainer color)
   - Shows profile names being compared
   - Exit Compare Mode button

5. **Delta Results Display** - Updated results section:
   - Title changes to "Delta Results" in compare mode
   - Each metric shows Profile 1 value with red delta in parentheses
   - surfaceVariant container color for visual distinction

6. **Delta Input Components** - Added to `CommonUI.kt`:
   - `DeltaInputField` for Double values with red delta indicator
   - `DeltaInputFieldInt` for Int values with red delta indicator

7. **Delta Tables** - Updated `Tables.kt`:
   - `SimulationResultTable` now shows red deltas under each cell value
   - `SensitivityAnalysisTable` now shows Δ column in red

8. **Input Screens with Deltas**:
   - `UserInputsScreen.kt` - All fields show delta indicators
   - `OptimizationParametersScreen.kt` - P1-P4 show delta indicators

9. **String Resources** - Added EN, IT, ES translations for all compare mode strings
10. **Charts Screen - Delta Z**:
    - Toggle for Delta View (P2-P1) in 3D/2D
    - Dual grid computation logic
    - Localized strings
