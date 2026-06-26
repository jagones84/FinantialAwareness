package com.example.daysurpopt.agent

import com.example.daysurpopt.domain.FinancialInput
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentPromptsTest {

    @Test
    fun riskPrompt_mentionsActualObjectiveStabilityRewardTerm() {
        val prompt = AgentPrompts.getRiskPrompt(
            baseInputs = FinancialInput(),
            commonFinancialContext = "context"
        )

        assertTrue(prompt.contains("AvgUtility / StdDev"))
    }
}
