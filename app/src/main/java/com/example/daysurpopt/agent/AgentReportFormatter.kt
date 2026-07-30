package com.example.daysurpopt.agent

import com.example.daysurpopt.logic.computeStabilityScore

object AgentReportFormatter {

    fun computeStabilityIndex(avgUtilita: Double, stdDev: Double): Double {
        return computeStabilityScore(avgUtilita, stdDev)
    }
}
