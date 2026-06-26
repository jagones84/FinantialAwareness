package com.example.daysurpopt.ui.screens

import com.example.daysurpopt.domain.FinancialInput
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
}
