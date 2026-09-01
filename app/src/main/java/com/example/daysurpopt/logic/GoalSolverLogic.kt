package com.example.daysurpopt.logic

import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.SimulationYear
import com.example.daysurpopt.domain.SpecificExpense
import com.example.daysurpopt.domain.SurplusInput
import java.util.Locale

/**
 * Goal Solver: answers the inverse question
 * "how much capital do I need to accumulate so that I can stop working at age X
 * while keeping my happiness (utility) at or above threshold T?".
 *
 * Semantics (mapped onto existing engine primitives, no new persistent inputs):
 *  - "Stop working at X" -> etaPensione = X: the work-income bucket stops at X and
 *    the pension income of the given [SurplusInput] applies afterwards. Pass a
 *    zero-income SurplusInput for a pure capital-based plan.
 *  - "Happiness >= T" -> sogliaMinimaFunzioneUtilita = T. The engine already forces
 *    the minimum monthly spend that achieves T, drawing capital (and creating debt
 *    once capital is exhausted).
 *  - The what-if plan forces p3 = 0 (spend exactly the utility minimum, no extra
 *    percentage draw) and p2 = p4 = stopWorkAge.
 *  - "Feasible" -> no debt in any year, no legacy violation, every utility sample >= T.
 *
 * Feasibility is monotone in initial capital (more capital can only relax the
 * constraints under the same spending rule), so a bisection on capitaleIniziale
 * converges to the exact minimum.
 */
object GoalSolverLogic {

    const val DEFAULT_CAPITAL_TOLERANCE = 1000.0
    const val DEFAULT_CAPITAL_UPPER_BOUND = 3_000_000.0

    private const val UTILITY_EPSILON = 1e-6
    private const val DEBT_EPSILON = 1e-6

    /**
     * Builds the goal what-if inputs used by the solver: stops work at [stopWorkAge],
     * applies the happiness [threshold] and places [initialCapital] as starting capital.
     * All other assumptions (rates, curves, expenses, surplus, legacy) come from the base.
     */
    fun buildGoalWhatIfInputs(
        baseInputs: FinancialInput,
        threshold: Double,
        stopWorkAge: Int,
        initialCapital: Double
    ): FinancialInput {
        return baseInputs
            .withDefaultAssumptionCurves()
            .copy(
                etaPensione = stopWorkAge,
                p2EtaFineRisparmioNoCapitale = stopWorkAge,
                p4EtaAnticipataInizioSpesaCapitale = stopWorkAge,
                p3PercentualeCapitaleDaSpendereAnnualmente = 0.0,
                sogliaMinimaFunzioneUtilita = threshold,
                capitaleIniziale = initialCapital
            )
    }

    /**
     * Builds the inputs to install when the user APPLIES the solver result:
     * the full goal plan (stop work at the solved age, save until it, spend
     * exactly the utility minimum) with the required initial capital — not
     * just the capital. Applying the capital alone would run the user's own
     * plan shape and contradict the solver's promise.
     */
    fun buildGoalApplyInputs(
        baseInputs: FinancialInput,
        result: GoalSolverResult
    ): FinancialInput {
        val capital = result.requiredCapital
            ?: throw IllegalArgumentException("Cannot apply an infeasible goal result: ${result.reason}")
        return buildGoalWhatIfInputs(baseInputs, result.threshold, result.stopWorkAge, capital)
    }

    /**
     * Validates that [threshold] is achievable at all: the maximum achievable utility is
     * the utility-curve ceiling times the minimum degradation over the simulated ages.
     * With default curves the ceiling is ~0.9347 (the baseline logistic at
     * BASELINE_MAX_SPESA never reaches 1.0), so thresholds above ~0.295 at age 82 are
     * unreachable regardless of capital.
     */
    fun validateThreshold(baseInputs: FinancialInput, threshold: Double): GoalValidation {
        val normalized = baseInputs.withDefaultAssumptionCurves()
        val ceiling = normalized.utilityCurvePoints
            ?.filter { it.x.isFinite() && it.y.isFinite() }
            ?.maxOfOrNull { it.y }
            ?.coerceIn(0.0, 1.0)
            ?: 1.0
        val minDegradation = (normalized.etaAttuale..normalized.etaMorte)
            .minOfOrNull { funzioneDegradoPerEta(it, normalized) }
            ?.coerceIn(0.0, 1.0)
            ?: 1.0
        val maxAchievable = (ceiling * minDegradation).coerceIn(0.0, 1.0)
        return GoalValidation(
            isAchievable = threshold <= maxAchievable,
            maxAchievableUtility = maxAchievable,
            utilityCurveCeiling = ceiling,
            minDegradation = minDegradation
        )
    }

    /**
     * Bisection on the initial capital. Returns a [GoalSolverResult] whose
     * [GoalSolverResult.requiredCapital] is the minimum feasible capital (never worse
     * than [capitalTolerance] from the true minimum), or null with a human-readable
     * [GoalSolverResult.reason] when the goal cannot be reached.
     */
    fun solveMinimumInitialCapital(
        baseInputs: FinancialInput,
        specificExpenses: List<SpecificExpense>,
        surplusData: SurplusInput,
        stopWorkAge: Int,
        threshold: Double,
        capitalTolerance: Double = DEFAULT_CAPITAL_TOLERANCE,
        capitalUpperBound: Double = DEFAULT_CAPITAL_UPPER_BOUND
    ): GoalSolverResult {
        val validation = validateThreshold(baseInputs, threshold)

        fun infeasible(reason: String): GoalSolverResult =
            GoalSolverResult(
                requiredCapital = null,
                isFeasible = false,
                reason = reason,
                threshold = threshold,
                stopWorkAge = stopWorkAge,
                maxAchievableUtility = validation.maxAchievableUtility
            )

        if (stopWorkAge < baseInputs.etaAttuale || stopWorkAge >= baseInputs.etaMorte) {
            return infeasible(
                "Invalid stop-work age $stopWorkAge: it must be between the current age " +
                    "${baseInputs.etaAttuale} and the death age ${baseInputs.etaMorte}."
            )
        }

        if (threshold <= 0.0) {
            return GoalSolverResult(
                requiredCapital = 0.0,
                isFeasible = true,
                reason = null,
                threshold = threshold,
                stopWorkAge = stopWorkAge,
                maxAchievableUtility = validation.maxAchievableUtility
            )
        }

        if (!validation.isAchievable) {
            return infeasible(
                "Happiness threshold " + "%.4f".format(Locale.US, threshold) +
                    " is above the maximum achievable utility " +
                    "%.4f".format(Locale.US, validation.maxAchievableUtility) +
                    " (utility curve ceiling " + "%.4f".format(Locale.US, validation.utilityCurveCeiling) +
                    " x minimum degradation " + "%.4f".format(Locale.US, validation.minDegradation) +
                    "). Lower the threshold or edit the utility/degradation curves."
            )
        }

        fun simulate(capital: Double): List<SimulationYear> =
            calculateSimulation(
                buildGoalWhatIfInputs(baseInputs, threshold, stopWorkAge, capital),
                specificExpenses,
                surplusData
            )

        fun isFeasible(years: List<SimulationYear>): Boolean {
            if (years.isEmpty()) return false
            return years.all { year ->
                year.debtAmount <= DEBT_EPSILON &&
                    !year.violazioneLascito &&
                    year.funzioneUtilita >= threshold - UTILITY_EPSILON &&
                    year.monthlyUtilitySamples.all { it >= threshold - UTILITY_EPSILON }
            }
        }

        val low = 0.0
        val high = capitalUpperBound
        if (!isFeasible(simulate(high))) {
            return infeasible(
                "Even a capital of " + "%.0f".format(Locale.US, high) +
                    " EUR cannot sustain the goal with the current plan. " +
                    "Reduce the threshold, increase income, or extend the savings phase."
            )
        }
        if (isFeasible(simulate(low))) {
            return GoalSolverResult(0.0, true, null, threshold, stopWorkAge, validation.maxAchievableUtility)
        }

        var lo = low
        var hi = high
        while (hi - lo > capitalTolerance) {
            val mid = (lo + hi) / 2.0
            if (isFeasible(simulate(mid))) hi = mid else lo = mid
        }

        return GoalSolverResult(hi, true, null, threshold, stopWorkAge, validation.maxAchievableUtility)
    }
}

data class GoalValidation(
    val isAchievable: Boolean,
    val maxAchievableUtility: Double,
    val utilityCurveCeiling: Double,
    val minDegradation: Double
)

data class GoalSolverResult(
    val requiredCapital: Double?,
    val isFeasible: Boolean,
    val reason: String?,
    val threshold: Double,
    val stopWorkAge: Int,
    val maxAchievableUtility: Double
)
