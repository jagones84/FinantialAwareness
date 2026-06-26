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
