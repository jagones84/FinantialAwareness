package com.example.daysurpopt.agent

import com.example.daysurpopt.domain.FinancialInput
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentPromptsTest {

    @Test
    fun riskPrompt_usesCurrentBoundedStabilityScoreDefinition() {
        val prompt = AgentPrompts.getRiskPrompt(
            baseInputs = FinancialInput(),
            commonFinancialContext = "context"
        )

        assertTrue(
            "Risk prompt must define StabilityScore as Avg / (Avg + StdDev)",
            prompt.contains("Avg / (Avg + StdDev)")
        )
    }

    @Test
    fun riskPrompt_usesCurrentPenalizedHappinessObjective() {
        val prompt = AgentPrompts.getRiskPrompt(
            baseInputs = FinancialInput(),
            commonFinancialContext = "context"
        )

        assertTrue(
            "Risk prompt must describe the penalized happiness objective fScalar = Avg * ((1 - w) + w * StabilityScore)",
            prompt.contains("Avg * ((1 - w) + w * StabilityScore)")
        )
    }

    @Test
    fun riskPrompt_neverMentionsLegacyStaleFormulas() {
        val prompt = AgentPrompts.getRiskPrompt(
            baseInputs = FinancialInput(),
            commonFinancialContext = "context"
        )

        assertFalse(
            "Risk prompt must not teach the removed 'StdDev / (Weight/100)' definition",
            prompt.contains("StdDev / (Weight/100)")
        )
        assertFalse(
            "Risk prompt must not teach the removed unbounded 'AvgUtility / StdDev' reward term",
            prompt.contains("AvgUtility / StdDev")
        )
    }
}
