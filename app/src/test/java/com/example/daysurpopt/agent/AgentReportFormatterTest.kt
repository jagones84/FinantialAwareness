package com.example.daysurpopt.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentReportFormatterTest {

    @Test
    fun computeStabilityIndex_matchesSimulationLogicScaling() {
        assertEquals(25.0, AgentReportFormatter.computeStabilityIndex(0.125, 0.50), 1e-9)
    }
}
