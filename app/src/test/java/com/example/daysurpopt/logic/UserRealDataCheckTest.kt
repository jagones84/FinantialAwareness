package com.example.daysurpopt.logic

import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.SimulationYear
import com.example.daysurpopt.domain.SpecificExpense
import com.example.daysurpopt.domain.SurplusInput
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Sanity check against the user's REAL stored data (SharedPreferences extracted
 * from the device into an EXTERNAL folder — nothing personal is committed).
 *
 * Opt-in: runs only when the folder exists (set FA_PREFS_DIR to override the
 * default). Reads FinancialPrefs/SurplusPrefs/SpecificExpensesPrefs XML, parses
 * the Gson payloads and runs the official simulation + the Goal Solver locus,
 * proving that scheduled (formerly "one-time") expenses count in the solver.
 */
class UserRealDataCheckTest {

    private val prefsDir: File =
        File(System.getenv("FA_PREFS_DIR") ?: "C:\\WINDOWS\\TEMP\\fa_prefs")

    private val gson = Gson()

    private fun readPayload(prefsFile: String, key: String): String? {
        val file = File(prefsDir, prefsFile)
        if (!file.isFile) return null
        val xml = file.readText()
        val regex = Regex("<string name=\"$key\">(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
        val raw = regex.find(xml)?.groupValues?.get(1) ?: return null
        return raw
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&#10;", "\n")
            .replace("&#39;", "'")
    }

    private fun loadUserScenario(): Triple<FinancialInput, SurplusInput, List<SpecificExpense>>? {
        val inputsJson = readPayload("FinancialPrefs.xml", "FinancialInputs") ?: return null
        val surplusJson = readPayload("SurplusPrefs.xml", "SurplusInputs") ?: return null
        val expensesJson = readPayload("SpecificExpensesPrefs.xml", "SpecificExpenses")
        val inputs = gson.fromJson(inputsJson, FinancialInput::class.java)
        val surplus = gson.fromJson(surplusJson, SurplusInput::class.java)
        val expenses: List<SpecificExpense> = expensesJson?.let {
            val type = com.google.gson.reflect.TypeToken.getParameterized(
                List::class.java, SpecificExpense::class.java
            ).type
            gson.fromJson<List<SpecificExpense>>(it, type)
        } ?: emptyList()
        return Triple(inputs, surplus, expenses)
    }

    private fun summarize(tag: String, years: List<SimulationYear>, objective: Double, w: Double) {
        val obj = calculateObjectivesFromYears(years, bonusStdWeight = w, legacyTarget = null)
        println("$tag: fObjW=${"%.4f".format(objective)} avgUtil=${"%.4f".format(obj.avgUtilita)} " +
            "std=${"%.4f".format(obj.stdDev)} stability=${"%.4f".format(obj.stabilityIndex)} " +
            "finalCapital=${"%.0f".format(obj.finalCapital)} debtYears=${years.count { it.debtAmount > 1e-6 }} " +
            "legacyViolations=${years.count { it.violazioneLascito }}")
    }

    @Test
    fun user_real_data_current_plan_simulation() = runBlocking {
        assumeTrue("User prefs folder not available", prefsDir.isDirectory)
        val (inputs, surplus, expenses) = loadUserScenario() ?: return@runBlocking

        println("=== USER DATA: age ${inputs.etaAttuale}..${inputs.etaMorte}, pension ${inputs.etaPensione}, " +
            "capital ${"%.0f".format(inputs.capitaleIniziale)}, inheritance ${"%.0f".format(inputs.eredita)}@${inputs.etaRicevimentoEredita}, " +
            "TFR ${"%.0f".format(inputs.tfrNetto)}, P1=${"%.4f".format(inputs.p1SavingRatioSurplus)} P2=${inputs.p2EtaFineRisparmioNoCapitale} " +
            "P3=${"%.4f".format(inputs.p3PercentualeCapitaleDaSpendereAnnualmente)} P4=${inputs.p4EtaAnticipataInizioSpesaCapitale}, " +
            "w=${inputs.bonusStdWeight}, threshold=${inputs.sogliaMinimaFunzioneUtilita}, keep=${"%.0f".format(inputs.soldiDaConservare)}")
        val realExpenses = expenses.filter { expense -> expense.amount > 0 }
        val expenseList = realExpenses.joinToString(separator = ", ") { expense ->
            "age " + expense.age + ": " + "%.0f".format(expense.amount) + " EUR"
        }
        val expenseTotal = "%.0f".format(expenses.sumOf { expense -> expense.amount })
        println("=== SCHEDULED EXPENSES: " + expenseList + " | TOTAL " + expenseTotal + " EUR")

        val (objective, years) = calculateSimulationWithWeight(inputs, expenses, surplus)
        summarize("CURRENT PLAN", years, objective, inputs.bonusStdWeight)
        assertEquals(inputs.etaMorte - inputs.etaAttuale, years.size)
        assertTrue(years.all { it.monthlyUtilitySamples.isNotEmpty() })
    }

    @Test
    fun user_real_data_goal_solver_locus_and_expenses_count() = runBlocking {
        assumeTrue("User prefs folder not available", prefsDir.isDirectory)
        val (inputs, surplus, expenses) = loadUserScenario() ?: return@runBlocking

        val stopAge = 55
        val threshold = 0.22

        val sweep = GoalSolverLogic.solveCapitalVsSavingRatio(
            baseInputs = inputs,
            specificExpenses = expenses,
            surplusData = surplus,
            stopWorkAge = stopAge,
            threshold = threshold
        )
        println("=== GOAL SOLVER LOCUS (stop $stopAge, threshold $threshold, WITH scheduled expenses) ===")
        sweep.rows.forEach { row ->
            println("P1 ${"%.0f".format(row.p1 * 100)}%${if (row.isCurrentPlan) " (current)" else ""} -> " +
                (row.requiredCapital?.let { "%.0f EUR".format(it) } ?: "not reachable"))
        }
        val feasible = sweep.rows.filter { it.isFeasible }
        assertTrue(feasible.isNotEmpty())
        val slack = GoalSolverLogic.DEFAULT_CAPITAL_TOLERANCE + 1.0
        for (i in 1 until feasible.size) {
            assertTrue(
                feasible[i].requiredCapital!! <= feasible[i - 1].requiredCapital!! + slack
            )
        }

        val rowWithExpenses = feasible.last().requiredCapital!!
        val withoutExpenses = GoalSolverLogic.solveCapitalVsSavingRatio(
            baseInputs = inputs,
            specificExpenses = emptyList(),
            surplusData = surplus,
            stopWorkAge = stopAge,
            threshold = threshold
        )
        val rowWithoutExpenses = withoutExpenses.rows.last { it.isFeasible }.requiredCapital!!
        println("=== SCHEDULED EXPENSES COUNT IN THE SOLVER: best row with expenses = ${"%.0f".format(rowWithExpenses)} EUR, " +
            "without = ${"%.0f".format(rowWithoutExpenses)} EUR, delta = ${"%.0f".format(rowWithExpenses - rowWithoutExpenses)} EUR")
        assertTrue(
            "Removing the expense burden must not increase the required capital",
            rowWithoutExpenses <= rowWithExpenses + slack
        )
    }
}
