package com.example.daysurpopt.ui.screens

import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.OptimizationMode
import com.example.daysurpopt.domain.OptimizationMarkerSnapshot
import com.example.daysurpopt.domain.ParamsCandidate
import com.example.daysurpopt.domain.ParetoFrontResult
import com.example.daysurpopt.domain.ParetoPoint
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
    fun chartWeightReleaseAction_is_knee_for_pareto_knee_mode() {
        assertEquals(
            "rerun_knee",
            chartWeightReleaseActionForMode(OptimizationMode.PARETO_KNEE)
        )
    }

    @Test
    fun chartWeightReleaseAction_is_rerun_front_for_pareto_front_mode() {
        assertEquals(
            "rerun_front",
            chartWeightReleaseActionForMode(OptimizationMode.PARETO_FRONT)
        )
    }

    @Test
    fun defaultOptimizationMode_is_true_scalar() {
        assertEquals(
            OptimizationMode.TRUE_SCALAR,
            defaultOptimizationModeForTest()
        )
    }

    @Test
    fun optimizationExecutionPath_is_distinct_for_each_mode() {
        assertEquals("scalar_optimizer", optimizationExecutionPathForMode(OptimizationMode.TRUE_SCALAR))
        assertEquals("pareto_knee", optimizationExecutionPathForMode(OptimizationMode.PARETO_KNEE))
        assertEquals("pareto_front", optimizationExecutionPathForMode(OptimizationMode.PARETO_FRONT))
    }

    @Test
    fun scalarMode_chartWeightRelease_never_maps_to_reselect_front() {
        val action = chartWeightReleaseActionForMode(OptimizationMode.TRUE_SCALAR)
        assertEquals("rerun_scalar", action)
    }

    @Test
    fun chartWeightUpdate_syncs_domain_weight_and_ui_weight_string() {
        val initial = FinancialInput(bonusStdWeight = 0.50)

        val synced = applyChartWeightUpdateForTest(initial, 0.85)

        assertEquals(0.85, synced.first.bonusStdWeight, 1e-9)
        assertEquals("0.85", synced.second.bonusStdWeight)
    }

    @Test
    fun chartSliderValue_preserves_exact_weight_without_step_snapping() {
        assertEquals(0.53f, chartSliderValueForWeight(0.53), 1e-6f)
    }

    @Test
    fun chartWeightFromSliderValue_roundTrips_exact_weight_without_grid_quantization() {
        assertEquals(0.53, chartWeightFromSliderValue(0.53f), 1e-6)
    }

    @Test
    fun clearAnalysisState_resets_results_front_and_markers() {
        val point = ParetoPoint(
            params = ParamsCandidate(0.2, 60, 0.3, 65),
            avgUtility = 0.8,
            stdDevUtility = 0.2,
            isFeasible = true,
            finalCapital = 50000.0,
            legacyGap = 1000.0
        )
        val snapshot = OptimizationMarkerSnapshot(
            mode = OptimizationMode.PARETO_FRONT,
            params = point.params,
            objectiveValue = 1.1,
            avgUtility = point.avgUtility,
            stdDevUtility = point.stdDevUtility,
            stabilityIndex = 0.3,
            weightUsed = 0.5
        )
        val state = AnalysisUiState(
            objectiveFunctionValue = 1.2,
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
            paretoFrontResult = ParetoFrontResult(points = listOf(point), referencePoint = point),
            selectedParetoPoint = point,
            appliedParetoSnapshot = snapshot,
            lastTrueScalarSnapshot = snapshot,
            lastParetoCompromiseSnapshot = snapshot,
            lastParetoReferenceSnapshot = snapshot,
            simulationResultsCount = 4,
            sensitivityResultsCount = 3
        )

        val cleared = clearAnalysisStateForTest(state)

        assertEquals(null, cleared.objectiveFunctionValue)
        assertEquals(null, cleared.optimizationResult)
        assertEquals(null, cleared.paretoFrontResult)
        assertEquals(null, cleared.selectedParetoPoint)
        assertEquals(null, cleared.appliedParetoSnapshot)
        assertEquals(null, cleared.lastTrueScalarSnapshot)
        assertEquals(null, cleared.lastParetoCompromiseSnapshot)
        assertEquals(null, cleared.lastParetoReferenceSnapshot)
        assertEquals(0, cleared.simulationResultsCount)
        assertEquals(0, cleared.sensitivityResultsCount)
    }

    @Test
    fun clearAnalysisState_preserves_current_weight_inputs() {
        val state = AnalysisUiState(
            objectiveFunctionValue = 1.2,
            optimizationResult = null,
            paretoFrontResult = null,
            selectedParetoPoint = null,
            appliedParetoSnapshot = null,
            lastTrueScalarSnapshot = null,
            lastParetoCompromiseSnapshot = null,
            lastParetoReferenceSnapshot = null,
            simulationResultsCount = 1,
            sensitivityResultsCount = 1,
            currentWeight = 0.77
        )

        val cleared = clearAnalysisStateForTest(state)

        assertEquals(0.77, cleared.currentWeight, 1e-9)
        assertEquals(0, cleared.simulationResultsCount)
        assertEquals(0, cleared.sensitivityResultsCount)
    }
}
