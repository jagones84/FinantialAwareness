package com.example.daysurpopt.logic

import com.example.daysurpopt.domain.*
import com.example.daysurpopt.utils.AppDebugLog
import kotlin.math.*

private fun sortedFiniteCurve(points: List<CurvePoint>): List<CurvePoint> {
    return points
        .filter { it.x.isFinite() && it.y.isFinite() }
        .sortedBy { it.x }
}

private fun interpolateCurveY(points: List<CurvePoint>, x: Double): Double {
    if (points.isEmpty()) return 0.0
    if (points.size == 1) return points[0].y
    if (x <= points[0].x) return points[0].y
    if (x >= points[points.lastIndex].x) return points[points.lastIndex].y

    for (i in 0 until points.lastIndex) {
        val p0 = points[i]
        val p1 = points[i + 1]
        if (x >= p0.x && x <= p1.x) {
            val dx = p1.x - p0.x
            if (dx == 0.0) return p0.y
            val t = (x - p0.x) / dx
            return p0.y + t * (p1.y - p0.y)
        }
    }

    return points[points.lastIndex].y
}

private fun invertCurveX(points: List<CurvePoint>, yTarget: Double): Double {
    if (points.isEmpty()) return 0.0
    if (points.size == 1) return points[0].x

    val ys = points.map { it.y }
    val minY = ys.minOrNull() ?: 0.0
    val maxY = ys.maxOrNull() ?: 1.0

    if (yTarget <= minY) return points[0].x
    if (yTarget >= maxY) return points[points.lastIndex].x

    var bestX: Double? = null

    for (i in 0 until points.lastIndex) {
        val p0 = points[i]
        val p1 = points[i + 1]
        val y0 = p0.y
        val y1 = p1.y
        val lo = min(y0, y1)
        val hi = max(y0, y1)
        if (yTarget < lo || yTarget > hi) continue

        val dy = y1 - y0
        val x = if (dy == 0.0) {
            p0.x
        } else {
            val t = (yTarget - y0) / dy
            p0.x + t * (p1.x - p0.x)
        }

        bestX = if (bestX == null) x else min(bestX!!, x)
    }

    return bestX ?: points[points.lastIndex].x
}

fun calculateStandardDeviation(numbers: List<Double>): Double {
    val finite = numbers.filter { it.isFinite() }
    if (finite.size < 2) return 0.0
    val mean = finite.average()
    val variance = finite.sumOf { (it - mean).pow(2) } / finite.size
    return sqrt(variance)
}

fun funzioneDegradoPerEta(eta: Int): Double {
    return 0.3 + (1 - 0.3) / (1 + exp(0.15 * (eta - 55)))
}

fun funzioneDegradoPerEta(eta: Int, inputs: FinancialInput): Double {
    val curve = inputs.degradationCurvePoints?.let(::sortedFiniteCurve)?.takeIf { it.size >= 2 }
    return if (curve != null) {
        interpolateCurveY(curve, eta.toDouble()).coerceIn(0.0, 1.0)
    } else {
        funzioneDegradoPerEta(eta)
    }
}

fun utilitaDaSpesa(eta: Int, spesaMensile: Double, inputs: FinancialInput): Double {
    val daysPerMonth = 365.0 / 12.0
    val curve = inputs.utilityCurvePoints?.let(::sortedFiniteCurve)?.takeIf { it.size >= 2 }

    val uRaw = if (curve != null) {
        val daily = (spesaMensile / daysPerMonth).coerceAtLeast(0.0)
        interpolateCurveY(curve, daily).coerceIn(0.0, 1.0)
    } else {
        val baselineMax = Defaults.BASELINE_MAX_SPESA
        val baselineCenter = Defaults.BASELINE_CENTER
        val baselineK = Defaults.BASELINE_K
        val target = inputs.valoreSpesaGiornalieraMaxUtilita * daysPerMonth
        val scale = if (target > 0) target / baselineMax else 1.0
        val x0 = baselineCenter * scale
        val k = baselineK / scale

        val u = 1.0 / (1.0 + exp(-k * (spesaMensile - x0)))
        u.coerceIn(0.0, 1.0)
    }

    val fdeg = funzioneDegradoPerEta(eta, inputs)
    return (fdeg * uRaw).coerceIn(0.0, 1.0)
}

fun calculateSimulation(
    inputs: FinancialInput,
    specificExpenses: List<SpecificExpense>,
    surplusData: SurplusInput,
    surplusOffset: Double = 0.0
): List<SimulationYear> {
    try {
        val simulationYears = mutableListOf<SimulationYear>()
        var capitaleAnnoPrecedente = inputs.capitaleIniziale
        var currentDebt = 0.0
        val debtInterestRate = inputs.tassoInteresseDebito
        val daysPerMonth = 365.0 / 12.0
        val valoreSpesaMensileMaxUtilita = inputs.valoreSpesaGiornalieraMaxUtilita * daysPerMonth

        val mutuoFinoEta = surplusData.mutuoAffittoFinoEta
        val surplusLavorativaConMutuo = surplusData.calculateSurplusGiornalieroLavorativa(true)
        val surplusLavorativaSenzaMutuo = surplusData.calculateSurplusGiornalieroLavorativa(false)
        val surplusPensioneConMutuo = surplusData.calculateSurplusGiornalieroPensione(true)
        val surplusPensioneSenzaMutuo = surplusData.calculateSurplusGiornalieroPensione(false)
        val bonusLavoroGiornaliero = surplusData.bonusEventualiPersonaliMensile * 12 / 365.0
        val bonusPensioneGiornaliero = surplusData.bonusEventualiPersonaliPensioneMensile * 12 / 365.0
        var cumulativeUtilityOffset = 0.0

        // Optimization: Pre-compute arrays for O(1) access
        val maxAge = inputs.etaMorte
        val expenseAmountByAge = DoubleArray(maxAge + 1)
        val expenseUtilityOffsetByAge = DoubleArray(maxAge + 1)
        for (expense in specificExpenses) {
            if (expense.age <= maxAge) {
                expenseAmountByAge[expense.age] += expense.amount
                expenseUtilityOffsetByAge[expense.age] += expense.utilityOffset
            }
        }

        val surplusGiornalieroByAge = DoubleArray(maxAge + 1)
        val ereditaTfrByAge = DoubleArray(maxAge + 1)

        for (eta in inputs.etaAttuale..maxAge) {
             var s = (when {
                eta < inputs.etaPensione -> if (eta < mutuoFinoEta) surplusLavorativaConMutuo else surplusLavorativaSenzaMutuo
                else -> if (eta < mutuoFinoEta) surplusPensioneConMutuo else surplusPensioneSenzaMutuo
            }) + surplusOffset

            if (eta < inputs.etaPensione) {
                if (eta > surplusData.bonusEventualiPersonaliMensileFinoEta) {
                    s -= bonusLavoroGiornaliero
                }
            } else {
                if (eta > surplusData.bonusEventualiPersonaliPensioneMensileFinoEta) {
                    s -= bonusPensioneGiornaliero
                }
            }
            surplusGiornalieroByAge[eta] = s

            val ereditaTfr = (if (eta == inputs.etaPensione) inputs.tfrNetto else 0.0) + 
                             (if (eta == inputs.etaRicevimentoEredita) inputs.eredita else 0.0) - 
                             expenseAmountByAge[eta]
            ereditaTfrByAge[eta] = ereditaTfr
        }

        fun spesaMinimaPerEta(eta: Int, utilityOffset: Double): Double {
            val fdeg = funzioneDegradoPerEta(eta, inputs).coerceAtLeast(1e-9)
            val requiredUtility = (inputs.sogliaMinimaFunzioneUtilita - utilityOffset).coerceAtLeast(0.0)
            if (requiredUtility <= 0.0) return 0.0

            val requiredRaw = (requiredUtility / fdeg).coerceIn(0.0, 1.0)

            val curve = inputs.utilityCurvePoints?.let(::sortedFiniteCurve)?.takeIf { it.size >= 2 }
            return if (curve != null) {
                val dailyNeeded = invertCurveX(curve, requiredRaw).coerceAtLeast(0.0)
                val res = dailyNeeded * daysPerMonth
                if (res.isFinite()) res else 0.0
            } else {
                val q = requiredRaw.coerceIn(1e-6, 1.0 - 1e-6)
                val baselineMax = Defaults.BASELINE_MAX_SPESA
                val baselineCenter = Defaults.BASELINE_CENTER
                val baselineK = Defaults.BASELINE_K
                val scale = if (valoreSpesaMensileMaxUtilita > 0) valoreSpesaMensileMaxUtilita / baselineMax else 1.0
                val x0 = baselineCenter * scale
                val k = baselineK / scale
                val res = x0 + (1.0 / k) * ln(q / (1.0 - q))
                if (res.isFinite()) res else 0.0
            }
        }

        fun forecastFinalWithMin(startCapital: Double, startAge: Int, startDebt: Double, startUtilityOffset: Double): Double {
            var cap = startCapital
            var debt = startDebt
            var utilityOffset = startUtilityOffset
            for (eta in startAge..inputs.etaMorte) {
                utilityOffset += expenseUtilityOffsetByAge[eta]
                val surplusM = surplusGiornalieroByAge[eta] * daysPerMonth
                val smin = spesaMinimaPerEta(eta, utilityOffset)
                var cashPool = cap + ereditaTfrByAge[eta] + (surplusM - smin) * 12

                if (debt > 0) {
                    val debtInterest = debt * debtInterestRate
                    debt += debtInterest
                    val repayable = cashPool.coerceAtLeast(0.0)
                    val paid = min(repayable, debt)
                    cashPool -= paid
                    debt -= paid
                }

                if (cashPool < 0) {
                    debt += -cashPool
                    cashPool = 0.0
                }

                cap = cashPool * (1 + inputs.tassoGuadagnoInteresse)
                if (!cap.isFinite()) return -1e15 // Divergence
            }
            return cap - debt
        }


        for (eta in inputs.etaAttuale..inputs.etaMorte) {
            val capitaleInizioAnno = capitaleAnnoPrecedente
            var debtRepayment = 0.0

            cumulativeUtilityOffset += expenseUtilityOffsetByAge[eta]

            val surplusGiornaliero = surplusGiornalieroByAge[eta]
            val surplusMensile = surplusGiornaliero * daysPerMonth
            val ereditaTfrAnno = ereditaTfrByAge[eta]

            var spesaMensileCorrettaFinale: Double

            // --- Determine desired spending level (using original logic) ---
            // Force P4 >= P2 constraint in the local calculation
            val p2 = inputs.p2EtaFineRisparmioNoCapitale
            val p4 = max(inputs.p4EtaAnticipataInizioSpesaCapitale, p2)

            val quotaCapitaleSpesaAnnuale = if (eta == inputs.etaMorte) {
                max(0.0, capitaleInizioAnno - inputs.soldiDaConservare)
            } else if (eta >= inputs.etaPensione) {
                (capitaleInizioAnno - inputs.soldiDaConservare) / (inputs.etaMorte - eta)
            } else if (eta >= p4 && eta >= p2) {
                inputs.p3PercentualeCapitaleDaSpendereAnnualmente * (capitaleInizioAnno - inputs.soldiDaConservare) / (inputs.etaMorte - eta)
            } else {
                0.0
            }

            val spesaMensileSurplusNoCapitale =
                if (eta < p2) surplusMensile * (1 - inputs.p1SavingRatioSurplus) else surplusMensile
            val spesaMensileSurplusCorretta = spesaMensileSurplusNoCapitale + quotaCapitaleSpesaAnnuale / 12

            val spesaMensileMinima = spesaMinimaPerEta(eta, cumulativeUtilityOffset)

            spesaMensileCorrettaFinale = min(
                max(spesaMensileSurplusCorretta, spesaMensileMinima),
                valoreSpesaMensileMaxUtilita
            )

            fun nextCapitalAndDebtFrom(spesaMensile: Double, capStart: Double, debtStart: Double): Pair<Double, Double> {
                var cashPool = capStart + ereditaTfrAnno + (surplusMensile - spesaMensile) * 12
                var debt = debtStart
                if (debt > 0) {
                    val debtInterest = debt * debtInterestRate
                    debt += debtInterest
                    val repayable = cashPool.coerceAtLeast(0.0)
                    val paid = min(repayable, debt)
                    cashPool -= paid
                    debt -= paid
                }
                if (cashPool < 0) {
                    debt += -cashPool
                    cashPool = 0.0
                }
                val capNext = cashPool * (1 + inputs.tassoGuadagnoInteresse)
                return capNext to debt
            }

            if (eta >= inputs.etaPensione && eta < inputs.etaMorte) {
                val remainingIncludingThis = (inputs.etaMorte - eta + 1).toDouble()
                if (remainingIncludingThis > 1.0) {
                    val i = inputs.tassoGuadagnoInteresse
                    val ceff = (capitaleInizioAnno + ereditaTfrAnno - currentDebt).coerceAtLeast(0.0)
                    val wReq = if (i > 0.0) {
                        ((ceff * (1.0 + i).pow(remainingIncludingThis) - inputs.soldiDaConservare) * i) /
                                ((1.0 + i).pow(remainingIncludingThis) - 1.0)
                    } else {
                        (ceff - inputs.soldiDaConservare) / remainingIncludingThis
                    }
                    val debtInterestAnnual = currentDebt * debtInterestRate
                    val spendReq = surplusMensile + (wReq - debtInterestAnnual) / 12.0
                    spesaMensileCorrettaFinale = min(
                        max(spendReq, spesaMensileMinima),
                        valoreSpesaMensileMaxUtilita
                    )
                    val (capNext, debtNext) = nextCapitalAndDebtFrom(spesaMensileCorrettaFinale, capitaleInizioAnno, currentDebt)
                    val finalIfMin = forecastFinalWithMin(capNext, eta + 1, debtNext, cumulativeUtilityOffset)
                    
                    // Safety margin: require slightly more than the goal to account for floating point errors
                    // and ensure we don't accidentally fall just below the threshold.
                    if (finalIfMin < inputs.soldiDaConservare + 1.0) {
                        spesaMensileCorrettaFinale = min(
                            spesaMensileMinima,
                            valoreSpesaMensileMaxUtilita
                        )
                    }
                }
            }

            if (eta == inputs.etaMorte) {
                val i = inputs.tassoGuadagnoInteresse
                val ceff = (capitaleInizioAnno + ereditaTfrAnno - currentDebt).coerceAtLeast(0.0)
                val wReq = (ceff - inputs.soldiDaConservare / (1.0 + i))
                val debtInterestAnnual = currentDebt * debtInterestRate
                val spendReq = surplusMensile + (wReq - debtInterestAnnual) / 12.0
                spesaMensileCorrettaFinale = min(
                    max(spendReq, spesaMensileMinima),
                    valoreSpesaMensileMaxUtilita
                )
            }

            // --- New consolidated cash flow and debt logic ---
            val spesaAnnuale = spesaMensileCorrettaFinale * 12
            val netCashFlow = (surplusMensile * 12) - spesaAnnuale

            var cashPool = capitaleInizioAnno + ereditaTfrAnno + netCashFlow

            if (currentDebt > 0) {
                val debtInterest = currentDebt * debtInterestRate
                currentDebt += debtInterest

                val repayable = cashPool.coerceAtLeast(0.0)
                val totalPaid = min(repayable, currentDebt)
                cashPool -= totalPaid

                val interestPaid = min(totalPaid, debtInterest)
                val principalPaid = totalPaid - interestPaid

                debtRepayment = totalPaid
                currentDebt -= totalPaid
            }

            if (cashPool < 0) {
                currentDebt += -cashPool
                cashPool = 0.0
            }

            val capitaleFineAnno = cashPool * (1 + inputs.tassoGuadagnoInteresse)
            val patrimonioNettoFineAnno = capitaleFineAnno - currentDebt

            val funzioneUtilitaRaw =
                if (eta == inputs.etaMorte && patrimonioNettoFineAnno < 0.9 * inputs.soldiDaConservare) {
                    -100.0
                } else {
                    utilitaDaSpesa(eta, spesaMensileCorrettaFinale, inputs) + cumulativeUtilityOffset
                }
            
            val funzioneUtilita = if (funzioneUtilitaRaw.isFinite()) funzioneUtilitaRaw else -1.0

            val savingRatioEffettivo = if (surplusMensile > 0.0) ((surplusMensile - spesaMensileSurplusNoCapitale) / surplusMensile) else 0.0
            
            // Fix: Lascito violation should ONLY apply at death (failed goal).
            // Debt during life is allowed as long as it's repaid by the end.
            // We use a small tolerance (1.0) to avoid floating point issues.
            val isLegacyFailure = (eta == inputs.etaMorte && patrimonioNettoFineAnno < (inputs.soldiDaConservare - 1.0))
            val violazioneLascito = isLegacyFailure
            
            val utilityAtThreshold = abs(spesaMensileCorrettaFinale - spesaMensileMinima) <= 1e-6

            simulationYears.add(
                SimulationYear(
                    eta = eta,
                    capitaleInizioAnno = capitaleInizioAnno,
                    spesaMensileCorrettaFinale = spesaMensileCorrettaFinale,
                    funzioneUtilita = funzioneUtilita,
                    savingRatioEffettivo = savingRatioEffettivo,
                    violazioneLascito = violazioneLascito,
                    capitaleFineAnno = capitaleFineAnno,
                    utilityAtThreshold = utilityAtThreshold,
                    debtAmount = currentDebt,
                    debtRepayment = debtRepayment,
                    capitaleEroso = capitaleInizioAnno - capitaleFineAnno
                )
            )

            capitaleAnnoPrecedente = capitaleFineAnno
            if (!capitaleAnnoPrecedente.isFinite()) throw ArithmeticException("Capitale non finito")
        }

        return simulationYears
    } catch (e: Exception) {
        // Return a special dummy year that will force fobj to 0 in upstream functions
        return listOf(SimulationYear(eta = inputs.etaAttuale, funzioneUtilita = -1e9))
    }
}


fun computeObjective(avgUtilita: Double, stdDevUtilita: Double, bonusStdWeight: Double): Double {
    val weight = bonusStdWeight / 100.0 // Divide by 100 as requested
    val stabilityTerm = if (stdDevUtilita > 1e-9) {
        avgUtilita / stdDevUtilita
    } else {
        100.0 // Very high stability reward
    }
    // Weighted average of AvgUtility and StabilityTerm: (Avg + w/100 * Avg/Std) / (1 + w/100)
    return (avgUtilita + weight * stabilityTerm) / (1.0 + weight)
}

fun calculateObjectivesFromYears(
    years: List<SimulationYear>,
    bonusStdWeight: Double,
    legacyTarget: Double? = null
): ObjectiveResults {
    if (years.isEmpty()) return ObjectiveResults(0.0, 0.0, 0.0, 0.0, 0.0)

    val finalCapital = years.lastOrNull()?.capitaleFineAnno ?: 0.0
    val legacyGap = finalCapital - (legacyTarget ?: 0.0)
    val isFeasible = !years.any { it.violazioneLascito } &&
        years.none { !it.funzioneUtilita.isFinite() || it.funzioneUtilita < 0.0 }

    // NEW LOGIC: Any constraint violation or math error forces objective to 0.0.
    if (years.any { it.violazioneLascito }) {
        AppDebugLog.add("SimLogic", "Zero objective: violazioneLascito detected in ${years.count { it.violazioneLascito }} years")
        return ObjectiveResults(0.0, 0.0, 0.0, 0.0, 0.0, false, finalCapital, legacyGap)
    }
    if (years.any { !it.funzioneUtilita.isFinite() }) {
        AppDebugLog.add("SimLogic", "Zero objective: Non-finite utility detected")
        return ObjectiveResults(0.0, 0.0, 0.0, 0.0, 0.0, false, finalCapital, legacyGap)
    }
    if (years.any { it.funzioneUtilita < 0 }) {
        AppDebugLog.add("SimLogic", "Zero objective: Negative utility detected")
        return ObjectiveResults(0.0, 0.0, 0.0, 0.0, 0.0, false, finalCapital, legacyGap)
    }

    val utilities = years.map { it.funzioneUtilita }
    val avgUtilita = utilities.average()
    
    if (!avgUtilita.isFinite() || avgUtilita <= 0.0) {
        AppDebugLog.add("SimLogic", "Zero objective: avgUtilita <= 0 or infinite: $avgUtilita")
        return ObjectiveResults(0.0, 0.0, 0.0, 0.0, 0.0, false, finalCapital, legacyGap)
    }

    val stdDevUtilita = calculateStandardDeviation(utilities)
    val fObjW = computeObjective(avgUtilita, stdDevUtilita, bonusStdWeight)
    
    // Log meaningful results
    if (fObjW < 0.0001) {
        AppDebugLog.add("SimLogic", "Low fObjW: $fObjW (avg: $avgUtilita, std: $stdDevUtilita)")
    }

    val fObj0 = computeObjective(avgUtilita, stdDevUtilita, 0.0)
    
    // Stability Index = std / (w/100)
    val weight = bonusStdWeight / 100.0
    val stabilityIndex = if (weight > 1e-9) {
        stdDevUtilita / weight
    } else {
        0.0
    }

    return ObjectiveResults(
        fObjW = fObjW,
        fObj0 = fObj0,
        stabilityIndex = stabilityIndex,
        stdDev = stdDevUtilita,
        avgUtilita = avgUtilita,
        isFeasible = isFeasible,
        finalCapital = finalCapital,
        legacyGap = legacyGap
    )
}

fun calculateSimulationWithWeight(
    inputs: FinancialInput,
    specificExpenses: List<SpecificExpense>,
    surplusData: SurplusInput,
    surplusOffset: Double = 0.0
): Pair<Double, List<SimulationYear>> {
    val years = calculateSimulation(inputs, specificExpenses, surplusData, surplusOffset)
    val results = calculateObjectivesFromYears(
        years = years,
        bonusStdWeight = inputs.bonusStdWeight,
        legacyTarget = inputs.soldiDaConservare
    )
    return results.fObjW to years
}
