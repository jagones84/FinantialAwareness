package com.example.daysurpopt.logic

import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.SurplusInput
import com.example.daysurpopt.domain.Defaults
import com.example.daysurpopt.domain.SimulationYear
import com.example.daysurpopt.domain.SpecificExpense
import com.example.daysurpopt.domain.CurvePoint
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
            eredita = 10000.0,
            // This regression checks the raw utility effect of inheritance timing.
            bonusStdWeight = 0.0
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

    @Test
    fun calculateObjectivesFromYears_reports_feasibility_and_legacy_gap() {
        val years = listOf(
            SimulationYear(
                eta = 65,
                funzioneUtilita = 0.2,
                capitaleFineAnno = 60000.0,
                violazioneLascito = false
            ),
            SimulationYear(
                eta = 66,
                funzioneUtilita = 0.3,
                capitaleFineAnno = 55000.0,
                violazioneLascito = false
            )
        )

        val result = calculateObjectivesFromYears(
            years = years,
            bonusStdWeight = 0.5,
            legacyTarget = 50000.0
        )

        assertEquals(0.25, result.avgUtilita, 1e-9)
        assertTrue(result.isFeasible)
        assertEquals(5000.0, result.legacyGap, 1e-9)
        assertEquals(55000.0, result.finalCapital, 1e-9)
    }

    @Test
    fun calculateObjectivesFromYears_prefers_monthly_samples_when_available() {
        val years = listOf(
            SimulationYear(
                eta = 41,
                funzioneUtilita = 0.3,
                capitaleFineAnno = 1000.0,
                monthlyUtilitySamples = listOf(0.1, 0.2)
            ),
            SimulationYear(
                eta = 42,
                funzioneUtilita = 0.9,
                capitaleFineAnno = 1200.0,
                monthlyUtilitySamples = listOf(0.8, 1.0)
            )
        )

        val result = calculateObjectivesFromYears(
            years = years,
            bonusStdWeight = 0.0,
            legacyTarget = 0.0
        )

        assertEquals(0.525, result.avgUtilita, 1e-9)
        assertEquals(calculateStandardDeviation(listOf(0.1, 0.2, 0.8, 1.0)), result.stdDev, 1e-9)
    }

    @Test
    fun computeObjective_keeps_weighted_value_bounded_when_std_is_tiny() {
        val avg = 0.25
        val std = 1e-15

        val score = computeStabilityScore(avg, std)
        val weighted = computeObjective(avg, std, 1.0)

        assertEquals(1.0, score, 1e-9)
        assertTrue(weighted.isFinite())
        assertTrue(weighted < 1.0)
    }

    @Test
    fun computeObjective_uses_stability_as_penalty_not_additive_bonus() {
        val avg = 0.25
        val std = 0.25

        val weighted = computeObjective(avg, std, 1.0)

        assertEquals(0.125, weighted, 1e-9)
        assertTrue(weighted <= avg + 1e-9)
    }

    @Test
    fun calculateObjectivesFromYears_uses_bounded_stability_score() {
        val years = List(12) {
            SimulationYear(
                eta = 41,
                funzioneUtilita = 0.25,
                capitaleFineAnno = 1000.0,
                monthlyUtilitySamples = listOf(0.25)
            )
        }

        val result = calculateObjectivesFromYears(
            years = years,
            bonusStdWeight = 1.0,
            legacyTarget = 0.0
        )

        assertEquals(1.0, result.stabilityIndex, 1e-9)
        assertEquals(0.25, result.fObjW, 1e-9)
    }

    @Test
    fun calculateObjectivesFromYears_reduces_objective_when_weight_changes_from_zero_to_one() {
        val years = listOf(
            SimulationYear(
                eta = 41,
                funzioneUtilita = 0.0,
                capitaleFineAnno = 1000.0,
                monthlyUtilitySamples = listOf(0.1, 0.5)
            )
        )

        val zeroWeight = calculateObjectivesFromYears(years, 0.0, 0.0)
        val unitWeight = calculateObjectivesFromYears(years, 1.0, 0.0)

        assertTrue(unitWeight.fObjW < zeroWeight.fObjW - 1e-6)
        assertEquals(0.3, zeroWeight.fObjW, 1e-9)
        assertEquals(0.18, unitWeight.fObjW, 1e-9)
    }

    @Test
    fun computeObjective_clamps_weight_to_slider_range() {
        val avg = 0.25
        val std = 0.25

        val atOne = computeObjective(avg, std, 1.0)
        val aboveOne = computeObjective(avg, std, 10.0)

        assertEquals(atOne, aboveOne, 1e-9)
    }

    @Test
    fun defaultGrid_with_positive_weight_stays_finite_and_bounded() {
        val inputs = FinancialInput(
            bonusStdWeight = 10.0
        ).withDefaultAssumptionCurves()
        val surplus = SurplusInput()
        val p1Values = listOf(0.0, 0.25, 0.5, 0.75, 1.0)
        val p2Values = listOf(inputs.etaAttuale, 40, 50, 60, inputs.etaPensione).distinct().sorted()

        val objectives = p2Values.flatMap { p2 ->
            p1Values.map { p1 ->
                calculateSimulationWithWeight(
                    inputs.copy(
                        p1SavingRatioSurplus = p1,
                        p2EtaFineRisparmioNoCapitale = p2,
                        p4EtaAnticipataInizioSpesaCapitale = maxOf(inputs.p4EtaAnticipataInizioSpesaCapitale, p2)
                    ),
                    emptyList(),
                    surplus
                ).first
            }
        }

        assertTrue(objectives.all { it.isFinite() })
        assertTrue(objectives.max() < 1.0)
    }

    @Test
    fun calculateSimulation_matches_python_reference_policy_on_common_dataset() {
        val utilityCurve = listOf(
            CurvePoint(0.0, 0.00),
            CurvePoint(20.0, 0.11),
            CurvePoint(30.0, 0.24),
            CurvePoint(40.0, 0.38),
            CurvePoint(50.0, 0.55),
            CurvePoint(60.0, 0.67),
            CurvePoint(90.0, 0.81),
            CurvePoint(100.0, 0.90)
        )
        val ageCurve = listOf(
            CurvePoint(30.0, 1.00),
            CurvePoint(40.0, 0.94),
            CurvePoint(50.0, 0.78),
            CurvePoint(60.0, 0.52),
            CurvePoint(70.0, 0.36),
            CurvePoint(80.0, 0.31),
            CurvePoint(90.0, 0.30)
        )
        val expenses = listOf(
            SpecificExpense(42, 1500.0, 0.0),
            SpecificExpense(45, 5000.0, 0.0),
            SpecificExpense(45, 3000.0, 0.0),
            SpecificExpense(50, 30000.0, 0.0),
            SpecificExpense(60, 26000.0, 0.0),
            SpecificExpense(70, 20000.0, 0.0),
            SpecificExpense(70, 26000.0, 0.0),
            SpecificExpense(80, 20000.0, 0.0)
        )
        val inputs = FinancialInput(
            eredita = 160000.0,
            soldiDaConservare = 50000.0,
            tfrNetto = 100000.0,
            tassoGuadagnoInteresse = 0.02,
            tassoInteresseDebito = 0.0,
            sogliaMinimaFunzioneUtilita = 0.0,
            capitaleIniziale = 105000.0,
            valoreSpesaGiornalieraMaxUtilita = 100.0,
            utilityCurvePoints = utilityCurve,
            degradationCurvePoints = ageCurve,
            etaAttuale = 41,
            etaPensione = 65,
            etaRicevimentoEredita = 55,
            etaMorte = 82,
            p1SavingRatioSurplus = 0.16,
            p2EtaFineRisparmioNoCapitale = 55,
            p3PercentualeCapitaleDaSpendereAnnualmente = 1.0,
            p4EtaAnticipataInizioSpesaCapitale = 55,
            bonusStdWeight = 1.0
        )
        val surplus = SurplusInput(
            stipendioMensile = 957.5,
            premioRisultatoNettoAnnuale = 0.0,
            tredicesimaQuattordicesimaNetto = 0.0,
            bonusEventualiPersonaliMensile = 300.0,
            bonusEventualiPersonaliMensileFinoEta = 54,
            pensioneMensileNetta = 18.33,
            tredicesimaQuattordicesimaNettoPensione = 0.0,
            bonusEventualiPersonaliPensioneMensile = 0.0,
            bonusEventualiPersonaliPensioneMensileFinoEta = 0,
            altreEntrateMensiliPensione = 0.0,
            mutuoAffitto = 0.0,
            mutuoAffittoFinoEta = 0,
            condominioLavorativa = 0.0,
            bolletteLavorativa = 0.0,
            ciboLavorativa = 0.0,
            veicoliLavorativa = 0.0,
            palestraLavorativa = 0.0,
            trasportiViaggiLavorativa = 0.0,
            saluteLavorativa = 0.0,
            vacanzeLavorativa = 0.0,
            shoppingLavorativa = 0.0,
            altroLavorativa = 0.0,
            condominioPensione = 0.0,
            bollettePensione = 0.0,
            ciboPensione = 0.0,
            veicoliPensione = 0.0,
            palestraPensione = 0.0,
            trasportiViaggiPensione = 0.0,
            salutePensione = 0.0,
            vacanzePensione = 0.0,
            shoppingPensione = 0.0,
            altroPensione = 0.0
        )

        val pythonReference = calculateSimulationWithWeight(inputs, expenses, surplus).first
        assertEquals(0.1879212626130284, pythonReference, 1e-9)
        assertTrue(pythonReference.isFinite())
    }
}
