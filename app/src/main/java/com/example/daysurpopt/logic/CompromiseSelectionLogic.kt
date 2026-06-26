package com.example.daysurpopt.logic

import com.example.daysurpopt.domain.ParetoPoint
import kotlin.math.abs
import kotlin.math.max

object CompromiseSelectionLogic {

    fun selectBestCompromise(
        points: List<ParetoPoint>,
        alpha: Double = 1.0,
        beta: Double = 1.0,
        rho: Double = 1e-6
    ): ParetoPoint {
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
