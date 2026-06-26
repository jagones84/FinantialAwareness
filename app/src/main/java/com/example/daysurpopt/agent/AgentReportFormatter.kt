package com.example.daysurpopt.agent

object AgentReportFormatter {

    fun computeStabilityIndex(stdDev: Double, bonusStdWeight: Double): Double {
        val weight = bonusStdWeight / 100.0
        return if (weight > 1e-9) stdDev / weight else 0.0
    }
}
