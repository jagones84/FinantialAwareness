package com.example.daysurpopt.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentSettingsTest {

    @Test
    fun defaultModel_isQwen37Plus() {
        assertEquals("qwen/qwen3.7-plus", AgentSettings().model)
    }
}
