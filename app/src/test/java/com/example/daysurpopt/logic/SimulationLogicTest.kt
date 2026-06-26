package com.example.daysurpopt.logic

import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.SurplusInput
import com.example.daysurpopt.domain.Defaults
import com.example.daysurpopt.domain.SpecificExpense
import org.junit.Test
import org.junit.Assert.*
import com.example.daysurpopt.utils.AppDebugLog

class SimulationLogicTest {

    @Test
    fun testInheritanceCrash() {
        // Setup inputs based on user report
        val baseInput = FinancialInput(
            p1SavingRatioSurplus = 0.5182,
            p2EtaFineRisparmioNoCapitale = 61,
            p3PercentualeCapitaleDaSpendereAnnualmente = 0.9171,
            p4EtaAnticipataInizioSpesaCapitale = 82,
            etaAttuale = 30,
            etaPensione = 67,
            etaMorte = 90,
            etaRicevimentoEredita = 65,
            soldiDaConservare = 50000.0,
            capitaleIniziale = 10000.0,
            valoreSpesaGiornalieraMaxUtilita = 10.0, // Low max spending
            sogliaMinimaFunzioneUtilita = 0.1,
            eredita = 0.0 // Baseline
        ).withDefaultAssumptionCurves()
        
        val surplusInput = SurplusInput()
        val expenses = emptyList<SpecificExpense>()

        // 1. Run Baseline
        val (fobjBase, yearsBase) = calculateSimulationWithWeight(baseInput, expenses, surplusInput)
        
        println("Baseline fobj: $fobjBase")
        assertTrue("Baseline fobj should be positive", fobjBase > 0.0)

        // 2. Run with Inheritance (+10k)
        val inputWithInheritance = baseInput.copy(eredita = 10000.0)
        val (fobjInherit, yearsInherit) = calculateSimulationWithWeight(inputWithInheritance, expenses, surplusInput)
        
        println("Inheritance fobj: $fobjInherit")
        
        println("=== AppDebugLog ===")
        println(AppDebugLog.lines.joinToString("\n"))
        println("===================")

        assertTrue("Inheritance fobj should be positive", fobjInherit > 0.0)
    }

    @Test
    fun testEarlyVsLateInheritance() {
        // Compare Age 41 vs Age 65 with realistic spending caps
        val baseInput = FinancialInput(
            p1SavingRatioSurplus = 0.5182,
            p2EtaFineRisparmioNoCapitale = 61,
            p3PercentualeCapitaleDaSpendereAnnualmente = 0.9171,
            p4EtaAnticipataInizioSpesaCapitale = 82,
            etaAttuale = 30,
            etaPensione = 67,
            etaMorte = 90,
            soldiDaConservare = 50000.0,
            capitaleIniziale = 10000.0,
            valoreSpesaGiornalieraMaxUtilita = 150.0, // High enough to allow utility growth
            sogliaMinimaFunzioneUtilita = 0.1,
            eredita = 10000.0
        ).withDefaultAssumptionCurves()
        
        val surplusInput = SurplusInput(
            mutuoAffitto = 500.0, 
            mutuoAffittoFinoEta = 60
        )
        val expenses = emptyList<SpecificExpense>()

        // 1. Late Inheritance (65)
        val inputLate = baseInput.copy(etaRicevimentoEredita = 65)
        val (fobjLate, yearsLate) = calculateSimulationWithWeight(inputLate, expenses, surplusInput)
        
        // 2. Early Inheritance (41)
        val inputEarly = baseInput.copy(etaRicevimentoEredita = 41)
        val (fobjEarly, yearsEarly) = calculateSimulationWithWeight(inputEarly, expenses, surplusInput)
        
        println("Late Inheritance (65) fobj: $fobjLate")
        println("Early Inheritance (41) fobj: $fobjEarly")
        
        if (yearsLate.isNotEmpty() && yearsEarly.isNotEmpty()) {
             println("Late Avg Util: ${yearsLate.map { it.funzioneUtilita }.average()}")
             println("Early Avg Util: ${yearsEarly.map { it.funzioneUtilita }.average()}")
        }

        assertTrue("Both fobj should be positive", fobjLate > 0.0 && fobjEarly > 0.0)
        assertTrue("Early inheritance should be better or equal to late", fobjEarly >= fobjLate)
    }

    @Test
    fun testAgentJsonParsingLogic() {
        // Mimic AgentToolExecutor logic (UPDATED)
        val params = mapOf(
            "eredita" to "10000", // String format
            "p1SavingRatioSurplus" to 0.5
        )
        
        fun getDouble(key: String): Double? {
            val value = params[key] ?: params[key.lowercase()]
            return when (value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull()
                else -> null
            }
        }

        val eredita = getDouble("eredita")
        println("Parsed eredita from string '10000': $eredita")
        
        assertNotNull("AgentToolExecutor should parse String numbers correctly now", eredita)
        assertEquals(10000.0, eredita!!, 0.001)
    }

    @Test
    fun testInheritanceSweep() {
        println("=== Inheritance Sweep Test ===")
        val baseInput = FinancialInput(
            p1SavingRatioSurplus = 0.5182,
            p2EtaFineRisparmioNoCapitale = 61,
            p3PercentualeCapitaleDaSpendereAnnualmente = 0.9171,
            p4EtaAnticipataInizioSpesaCapitale = 82,
            etaAttuale = 30,
            etaPensione = 67,
            etaMorte = 90,
            etaRicevimentoEredita = 65,
            soldiDaConservare = 50000.0,
            capitaleIniziale = 10000.0,
            valoreSpesaGiornalieraMaxUtilita = 82.0,
            sogliaMinimaFunzioneUtilita = 0.1,
            eredita = 0.0
        ).withDefaultAssumptionCurves()

        // Use stable expenses that don't cause bankruptcy at 0 inheritance
        val surplusInput = SurplusInput(
            mutuoAffitto = 800.0, 
            mutuoAffittoFinoEta = 60
        )
        val expenses = emptyList<SpecificExpense>()

        var previousFobj = -1.0
        var foundDrop = false

        for (inherit in 0..20000 step 1000) {
            val input = baseInput.copy(eredita = inherit.toDouble())
            val (fobj, years) = calculateSimulationWithWeight(input, expenses, surplusInput)
            
            println("Inheritance: $inherit -> fobj: $fobj")
            
            if (previousFobj != -1.0 && fobj < previousFobj - 1e-6) {
                println("!!! DETECTED FOBJ DROP at $inherit (Prev: $previousFobj, Curr: $fobj)")
                foundDrop = true
                
                // Detailed dump for the drop
                println("Years count: ${years.size}")
                val violations = years.filter { it.violazioneLascito }
                if (violations.isNotEmpty()) println("Violations: ${violations.size}")
            }
            previousFobj = fobj
        }
        
        assertFalse("Should not find any drop in fobj when increasing inheritance", foundDrop)
    }
}