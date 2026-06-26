package com.example.daysurpopt.logic

import com.example.daysurpopt.domain.ParamsCandidate
import com.example.daysurpopt.domain.ParetoPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class CompromiseSelectionLogicTest {

    @Test
    fun selectBestCompromise_picks_balanced_point_by_normalized_asf() {
        val points = listOf(
            ParetoPoint(
                ParamsCandidate(0.1, 60, 0.1, 65),
                avgUtility = 0.10,
                stdDevUtility = 0.05,
                isFeasible = true,
                finalCapital = 70000.0,
                legacyGap = 20000.0
            ),
            ParetoPoint(
                ParamsCandidate(0.2, 60, 0.2, 65),
                avgUtility = 0.20,
                stdDevUtility = 0.20,
                isFeasible = true,
                finalCapital = 65000.0,
                legacyGap = 15000.0
            ),
            ParetoPoint(
                ParamsCandidate(0.3, 60, 0.3, 65),
                avgUtility = 0.30,
                stdDevUtility = 0.40,
                isFeasible = true,
                finalCapital = 60000.0,
                legacyGap = 10000.0
            )
        )

        val selected = CompromiseSelectionLogic.selectBestCompromise(points)

        assertEquals(0.20, selected.avgUtility, 1e-9)
    }
}
