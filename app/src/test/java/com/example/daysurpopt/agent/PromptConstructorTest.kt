package com.example.daysurpopt.agent

import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.SurplusInput
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptConstructorTest {

    @Test
    fun systemPrompt_explicitlyListsBonusStdWeightAsAgentOverride() {
        val prompt = PromptConstructor.constructSystemPrompt(
            inputs = FinancialInput(),
            specificExpenses = emptyList(),
            surplusData = SurplusInput()
        )

        assertTrue(prompt.contains("bonusStdWeight"))
    }

    @Test
    fun systemPrompt_tellsAgentNotToUseP3AsProxyForBonusStdWeight() {
        val prompt = PromptConstructor.constructSystemPrompt(
            inputs = FinancialInput(),
            specificExpenses = emptyList(),
            surplusData = SurplusInput()
        )

        assertTrue(prompt.contains("Do not use P3 as a proxy for bonusStdWeight"))
    }

    @Test
    fun systemPrompt_keepsWeightHandlingAndWorkflowSectionsDistinct() {
        val prompt = PromptConstructor.constructSystemPrompt(
            inputs = FinancialInput(),
            specificExpenses = emptyList(),
            surplusData = SurplusInput()
        )

        assertTrue(prompt.contains("8. **Weight Handling**"))
        assertTrue(prompt.contains("9. **Simulation vs Optimization Workflow**"))
        assertFalse(prompt.contains("8. **Simulation vs Optimization Workflow**"))
    }
}
