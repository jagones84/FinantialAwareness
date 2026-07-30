package com.example.daysurpopt.agent

import com.example.daysurpopt.data.SearchRepository
import com.example.daysurpopt.domain.CurvePoint
import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.GAConfig
import com.example.daysurpopt.domain.ParamsCandidate
import com.example.daysurpopt.domain.SpecificExpense
import com.example.daysurpopt.domain.SurplusInput
import com.example.daysurpopt.logic.OptimizationLogic
import com.example.daysurpopt.logic.calculateSimulationWithWeight
import com.example.daysurpopt.utils.AppDebugLog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Executes tools requested by the AI agent (Simulations, Optimization, Web Search, etc.).
 */
object AgentToolExecutor {

    suspend fun checkForToolUse(
        response: String, 
        baseInputs: FinancialInput, 
        specificExpenses: List<SpecificExpense>, 
        surplusData: SurplusInput,
        llmRequest: suspend (String) -> String // Callback for Multi-Agent workflow
    ): String? {
        // 1. Identify the command
        val commandRegex = Regex("(WEB_SEARCH|RUN_SIMULATION|RUN_OPTIMIZATION|RUN_MULTI_AGENT_ANALYSIS|GET_TIME|FETCH_PAGE|GET_FINANCIAL_CONTEXT)")
        val match = commandRegex.find(response) ?: return null
        val command = match.value
        val startIndex = match.range.last + 1

        // 2. Extract JSON parameters with brace counting to handle nested objects (e.g., curves)
        var jsonParams = ""
        val jsonStartIndex = response.indexOf('{', startIndex)
        
        if (jsonStartIndex != -1) {
            var braceCount = 0
            var jsonEndIndex = -1
            
            for (i in jsonStartIndex until response.length) {
                if (response[i] == '{') braceCount++
                else if (response[i] == '}') braceCount--
                
                if (braceCount == 0) {
                    jsonEndIndex = i + 1
                    break
                }
            }
            
            if (jsonEndIndex != -1) {
                jsonParams = response.substring(jsonStartIndex, jsonEndIndex)
            }
        }
        
        // Log the extracted JSON for debugging
        if (jsonParams.isNotEmpty()) {
            AppDebugLog.add("Agent", "Extracted Params for $command: $jsonParams")
        }

        return try {
             when (command) {
                "WEB_SEARCH" -> {
                    val query = extractStringParam(jsonParams, "query") ?: return null
                    "**Search Output:**\n" + SearchRepository.performWebSearch(query)
                }
                "FETCH_PAGE" -> {
                    val url = extractStringParam(jsonParams, "url") ?: return null
                    "**Page Content:**\n" + SearchRepository.fetchPageContent(url)
                }
                "GET_TIME" -> {
                    "**System Time:**\n" + SearchRepository.getCurrentTime()
                }
                "GET_FINANCIAL_CONTEXT" -> {
                    val gson = Gson()
                    val contextData = mapOf(
                        "financialInput" to baseInputs,
                        "surplusInput" to surplusData,
                        "specificExpenses" to specificExpenses
                    )
                    "**Current Context:**\n```json\n" + gson.toJson(contextData) + "\n```"
                }
                "RUN_SIMULATION" -> {
                    executeSimulation(jsonParams, baseInputs, specificExpenses, surplusData)
                }
                "RUN_OPTIMIZATION" -> {
                    executeOptimization(jsonParams, baseInputs, specificExpenses, surplusData)
                }
                "RUN_MULTI_AGENT_ANALYSIS" -> {
                    executeMultiAgentWorkflow(baseInputs, specificExpenses, surplusData, llmRequest)
                }
                else -> null
            }
        } catch (e: Exception) {
            "Tool execution failed: ${e.message}"
        }
    }
    
    private fun extractStringParam(json: String, key: String): String? {
        if (json.isBlank()) return null
        return try {
             val type = object : TypeToken<Map<String, Any>>() {}.type
             val params: Map<String, Any> = Gson().fromJson(json, type)
             params[key] as? String
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun executeSimulation(json: String, baseInputs: FinancialInput, specificExpenses: List<SpecificExpense>, surplusData: SurplusInput): String {
        AppDebugLog.add("Agent", "executeSimulation: $json")
        if (json.isBlank()) return "Missing simulation parameters."
        return try {
             val type = object : TypeToken<Map<String, Any>>() {}.type
             val params: Map<String, Any> = Gson().fromJson(json, type)
             
             val modifiedInputs = applyFinancialOverrides(baseInputs, params)
             val modifiedSurplus = applySurplusOverrides(surplusData, params)
             val modifiedSpecificExpenses = applySpecificExpenseOverrides(specificExpenses, params)
             
             AppDebugLog.add("Agent", "Simulating with: Rate=${modifiedInputs.tassoGuadagnoInteresse}, P1=${modifiedInputs.p1SavingRatioSurplus}, Wage=${modifiedSurplus.stipendioMensile}")
             AppDebugLog.add("Agent", "Base Rate=${baseInputs.tassoGuadagnoInteresse}, Modified Rate=${modifiedInputs.tassoGuadagnoInteresse}")

             val (obj, years) = withContext(Dispatchers.Default) {
                  calculateSimulationWithWeight(modifiedInputs, modifiedSpecificExpenses, modifiedSurplus)
             }
             
             "\n\n**Simulation Result:**\n" +
                     "- Objective Function: %.4f\n".format(obj) +
                     "- Final Capital: %.2f\n".format(years.lastOrNull()?.capitaleFineAnno ?: 0.0) +
                     "- Avg Utility: %.4f".format(years.map { it.funzioneUtilita }.average())
        } catch (e: Exception) {
            AppDebugLog.add("Agent", "Simulation error: ${e.message}")
            "Simulation failed: ${e.message}"
        }
    }

    private suspend fun executeOptimization(json: String, baseInputs: FinancialInput, specificExpenses: List<SpecificExpense>, surplusData: SurplusInput): String {
        AppDebugLog.add("Agent", "executeOptimization: $json")
        return try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val params: Map<String, Any> = if (json.isNotBlank()) Gson().fromJson(json, type) else emptyMap()
            
            val modifiedInputs = applyFinancialOverrides(baseInputs, params)
            val modifiedSurplus = applySurplusOverrides(surplusData, params)
            val modifiedSpecificExpenses = applySpecificExpenseOverrides(specificExpenses, params)

            // Allow overriding GA config via JSON if needed, otherwise use default/safe values
            val gaConfig = GAConfig(
                popSize = 100,
                generations = 50, // Increased to ensure convergence (was 20)
                pc = 0.7,
                pm = 0.08,
                min = ParamsCandidate(0.0, modifiedInputs.etaAttuale, 0.0, modifiedInputs.etaAttuale),
                max = ParamsCandidate(1.0, modifiedInputs.etaPensione, 1.0, modifiedInputs.etaMorte),
                maximize = true
            )
            
            // 1. Calculate Current Metrics
            val (currentObj, currentYears) = withContext(Dispatchers.Default) {
                calculateSimulationWithWeight(modifiedInputs, modifiedSpecificExpenses, modifiedSurplus)
            }
            val currentUtilities = currentYears.flatMap { year ->
                if (year.monthlyUtilitySamples.isNotEmpty()) year.monthlyUtilitySamples else listOf(year.funzioneUtilita)
            }
            val currentAvg = currentUtilities.average()
            val currentStdDev = com.example.daysurpopt.logic.calculateStandardDeviation(currentUtilities)
            val currentStability = AgentReportFormatter.computeStabilityIndex(currentAvg, currentStdDev)

            // Create initial guess from current parameters to seed the optimization
            // This ensures the optimization never performs worse than the current strategy
            val initialGuess = ParamsCandidate(
                modifiedInputs.p1SavingRatioSurplus,
                modifiedInputs.p2EtaFineRisparmioNoCapitale,
                modifiedInputs.p3PercentualeCapitaleDaSpendereAnnualmente,
                modifiedInputs.p4EtaAnticipataInizioSpesaCapitale
            )

            val result = withContext(Dispatchers.Default) {
                // 2. GA
                val gaRes = OptimizationLogic.optimizeParameters(modifiedInputs, gaConfig, modifiedSpecificExpenses, modifiedSurplus, initialGuess)
                // 3. Coordinate Search
                OptimizationLogic.coordinateSearch(modifiedInputs, gaRes.bestParams, gaConfig, specificExpenses = modifiedSpecificExpenses, surplusData = modifiedSurplus)
            }
            
            // 4. Calculate Optimized Metrics (for Stability Index)
            val optInputs = modifiedInputs.copy(
                p1SavingRatioSurplus = result.bestParams.p1,
                p2EtaFineRisparmioNoCapitale = result.bestParams.p2,
                p3PercentualeCapitaleDaSpendereAnnualmente = result.bestParams.p3,
                p4EtaAnticipataInizioSpesaCapitale = result.bestParams.p4
            )
            val (optObj, optYears) = withContext(Dispatchers.Default) {
                calculateSimulationWithWeight(optInputs, modifiedSpecificExpenses, modifiedSurplus)
            }
            val optUtilities = optYears.flatMap { year ->
                if (year.monthlyUtilitySamples.isNotEmpty()) year.monthlyUtilitySamples else listOf(year.funzioneUtilita)
            }
            val optAvg = optUtilities.average()
            val optStdDev = com.example.daysurpopt.logic.calculateStandardDeviation(optUtilities)
            val optStability = AgentReportFormatter.computeStabilityIndex(optAvg, optStdDev)
            
            val gain = optObj - currentObj

            """
            **Optimization Analysis (Current vs Optimized):**
            
            **1. Key Metrics:**
            - **Objective Function**: ${"%.4f".format(currentObj)} -> **${"%.4f".format(optObj)}** (Gain: ${"%+.4f".format(gain)})
            - **Stability Score**: ${"%.4f".format(currentStability)} -> **${"%.4f".format(optStability)}** (Higher is better)
            - **Standard Deviation**: ${"%.4f".format(currentStdDev)} -> **${"%.4f".format(optStdDev)}**
            
            **2. Optimized Parameters:**
            - **P1 (Savings Rate)**: ${"%.2f%%".format(modifiedInputs.p1SavingRatioSurplus * 100)} -> **${"%.2f%%".format(result.bestParams.p1 * 100)}**
            - **P2 (Savings End Age)**: ${modifiedInputs.p2EtaFineRisparmioNoCapitale} -> **${result.bestParams.p2}**
            - **P3 (Spending Rate)**: ${"%.2f%%".format(modifiedInputs.p3PercentualeCapitaleDaSpendereAnnualmente * 100)} -> **${"%.2f%%".format(result.bestParams.p3 * 100)}**
            - **P4 (Spending Start Age)**: ${modifiedInputs.p4EtaAnticipataInizioSpesaCapitale} -> **${result.bestParams.p4}**
            
            **Analyst Verdict**: The optimized plan improves the objective score by ${"%.2f%%".format((gain/currentObj)*100)}.
            """.trimIndent()
        } catch (e: Exception) {
            "Optimization failed: ${e.message}"
        }
    }
    
    // --- Helper Functions for Parameter Mapping ---

    private fun applyFinancialOverrides(base: FinancialInput, params: Map<String, Any>): FinancialInput {
        var inputs = base.copy()
        
        fun getDouble(key: String): Double? {
            val value = params[key] ?: params[key.lowercase()]
            return when (value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull()
                else -> null
            }
        }

        fun getInt(key: String): Int? {
            val value = params[key] ?: params[key.lowercase()]
            return when (value) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull()
                else -> null
            }
        }

        getDouble("eredita")?.let { inputs = inputs.copy(eredita = it) }
        getDouble("soldiDaConservare")?.let { inputs = inputs.copy(soldiDaConservare = it) }
        getDouble("tfrNetto")?.let { inputs = inputs.copy(tfrNetto = it) }
        getDouble("tassoGuadagnoInteresse")?.let { inputs = inputs.copy(tassoGuadagnoInteresse = it) }
        getDouble("tassoInteresseDebito")?.let { inputs = inputs.copy(tassoInteresseDebito = it) }
        getDouble("sogliaMinimaFunzioneUtilita")?.let { inputs = inputs.copy(sogliaMinimaFunzioneUtilita = it) }
        getDouble("capitaleIniziale")?.let { inputs = inputs.copy(capitaleIniziale = it) }
        getDouble("valoreSpesaGiornalieraMaxUtilita")?.let { inputs = inputs.copy(valoreSpesaGiornalieraMaxUtilita = it) }
        
        getInt("etaAttuale")?.let { inputs = inputs.copy(etaAttuale = it) }
        getInt("etaPensione")?.let { inputs = inputs.copy(etaPensione = it) }
        getInt("etaRicevimentoEredita")?.let { inputs = inputs.copy(etaRicevimentoEredita = it) }
        getInt("etaMorte")?.let { inputs = inputs.copy(etaMorte = it) }
        
        getDouble("p1SavingRatioSurplus")?.let { inputs = inputs.copy(p1SavingRatioSurplus = it) }
        getInt("p2EtaFineRisparmioNoCapitale")?.let { inputs = inputs.copy(p2EtaFineRisparmioNoCapitale = it) }
        getDouble("p3PercentualeCapitaleDaSpendereAnnualmente")?.let { inputs = inputs.copy(p3PercentualeCapitaleDaSpendereAnnualmente = it) }
        getInt("p4EtaAnticipataInizioSpesaCapitale")?.let { inputs = inputs.copy(p4EtaAnticipataInizioSpesaCapitale = it) }
        getDouble("bonusStdWeight")?.let { inputs = inputs.copy(bonusStdWeight = it) }
        
        // Curves
        val utilityPoints = getCurvePoints(params["utilityCurvePoints"])
        if (utilityPoints != null) inputs = inputs.copy(utilityCurvePoints = utilityPoints)
        
        val degradationPoints = getCurvePoints(params["degradationCurvePoints"])
        if (degradationPoints != null) inputs = inputs.copy(degradationCurvePoints = degradationPoints)
        
        return inputs
    }

    private fun applySurplusOverrides(base: SurplusInput, params: Map<String, Any>): SurplusInput {
        var surplus = base.copy()
        
        fun getDouble(key: String): Double? {
            val value = params[key] ?: params[key.lowercase()]
            return when (value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull()
                else -> null
            }
        }
        
        fun getInt(key: String): Int? {
            val value = params[key] ?: params[key.lowercase()]
            return when (value) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull()
                else -> null
            }
        }

        // Income
        getDouble("stipendioMensile")?.let { surplus = surplus.copy(stipendioMensile = it) }
        getDouble("premioRisultatoNettoAnnuale")?.let { surplus = surplus.copy(premioRisultatoNettoAnnuale = it) }
        getDouble("tredicesimaQuattordicesimaNetto")?.let { surplus = surplus.copy(tredicesimaQuattordicesimaNetto = it) }
        getDouble("bonusEventualiPersonaliMensile")?.let { surplus = surplus.copy(bonusEventualiPersonaliMensile = it) }
        getInt("bonusEventualiPersonaliMensileFinoEta")?.let { surplus = surplus.copy(bonusEventualiPersonaliMensileFinoEta = it) }
        
        // Pension
        getDouble("pensioneMensileNetta")?.let { surplus = surplus.copy(pensioneMensileNetta = it) }
        getDouble("tredicesimaQuattordicesimaNettoPensione")?.let { surplus = surplus.copy(tredicesimaQuattordicesimaNettoPensione = it) }
        getDouble("bonusEventualiPersonaliPensioneMensile")?.let { surplus = surplus.copy(bonusEventualiPersonaliPensioneMensile = it) }
        getInt("bonusEventualiPersonaliPensioneMensileFinoEta")?.let { surplus = surplus.copy(bonusEventualiPersonaliPensioneMensileFinoEta = it) }
        getDouble("altreEntrateMensiliPensione")?.let { surplus = surplus.copy(altreEntrateMensiliPensione = it) }
        
        // Shared Expenses
        getDouble("mutuoAffitto")?.let { surplus = surplus.copy(mutuoAffitto = it) }
        getInt("mutuoAffittoFinoEta")?.let { surplus = surplus.copy(mutuoAffittoFinoEta = it) }
        
        // Work Expenses
        getDouble("condominioLavorativa")?.let { surplus = surplus.copy(condominioLavorativa = it) }
        getDouble("bolletteLavorativa")?.let { surplus = surplus.copy(bolletteLavorativa = it) }
        getDouble("ciboLavorativa")?.let { surplus = surplus.copy(ciboLavorativa = it) }
        getDouble("veicoliLavorativa")?.let { surplus = surplus.copy(veicoliLavorativa = it) }
        getDouble("palestraLavorativa")?.let { surplus = surplus.copy(palestraLavorativa = it) }
        getDouble("trasportiViaggiLavorativa")?.let { surplus = surplus.copy(trasportiViaggiLavorativa = it) }
        getDouble("saluteLavorativa")?.let { surplus = surplus.copy(saluteLavorativa = it) }
        getDouble("vacanzeLavorativa")?.let { surplus = surplus.copy(vacanzeLavorativa = it) }
        getDouble("shoppingLavorativa")?.let { surplus = surplus.copy(shoppingLavorativa = it) }
        getDouble("altroLavorativa")?.let { surplus = surplus.copy(altroLavorativa = it) }
        
        // Pension Expenses
        getDouble("condominioPensione")?.let { surplus = surplus.copy(condominioPensione = it) }
        getDouble("bollettePensione")?.let { surplus = surplus.copy(bollettePensione = it) }
        getDouble("ciboPensione")?.let { surplus = surplus.copy(ciboPensione = it) }
        getDouble("veicoliPensione")?.let { surplus = surplus.copy(veicoliPensione = it) }
        getDouble("palestraPensione")?.let { surplus = surplus.copy(palestraPensione = it) }
        getDouble("trasportiViaggiPensione")?.let { surplus = surplus.copy(trasportiViaggiPensione = it) }
        getDouble("salutePensione")?.let { surplus = surplus.copy(salutePensione = it) }
        getDouble("vacanzePensione")?.let { surplus = surplus.copy(vacanzePensione = it) }
        getDouble("shoppingPensione")?.let { surplus = surplus.copy(shoppingPensione = it) }
        getDouble("altroPensione")?.let { surplus = surplus.copy(altroPensione = it) }

        return surplus
    }

    private fun applySpecificExpenseOverrides(base: List<SpecificExpense>, params: Map<String, Any>): List<SpecificExpense> {
        val jsonList = params["specificExpenses"] as? List<*> ?: return base
        
        return try {
            jsonList.mapNotNull { item ->
                if (item is Map<*, *>) {
                    val ageVal = item["age"]
                    val age = when (ageVal) {
                        is Number -> ageVal.toInt()
                        is String -> ageVal.toIntOrNull() ?: 0
                        else -> 0
                    }
                    
                    val amountVal = item["amount"]
                    val amount = when (amountVal) {
                        is Number -> amountVal.toDouble()
                        is String -> amountVal.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }
                    
                    val utilVal = item["utilityOffset"]
                    val utilityOffset = when (utilVal) {
                        is Number -> utilVal.toDouble()
                        is String -> utilVal.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }
                    
                    SpecificExpense(age, amount, utilityOffset)
                } else null
            }
        } catch (e: Exception) {
            AppDebugLog.add("Agent", "Error parsing specificExpenses: ${e.message}")
            base
        }
    }
    
    private fun getCurvePoints(param: Any?): List<CurvePoint>? {
        if (param !is List<*>) return null
        return try {
            param.mapNotNull { item ->
                if (item is Map<*, *>) {
                    val xVal = item["x"]
                    val x = when (xVal) {
                        is Number -> xVal.toDouble()
                        is String -> xVal.toDoubleOrNull()
                        else -> null
                    } ?: return@mapNotNull null

                    val yVal = item["y"]
                    val y = when (yVal) {
                        is Number -> yVal.toDouble()
                        is String -> yVal.toDoubleOrNull()
                        else -> null
                    } ?: return@mapNotNull null
                    
                    CurvePoint(x, y)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun executeMultiAgentWorkflow(
        baseInputs: FinancialInput, 
        specificExpenses: List<SpecificExpense>, 
        surplusData: SurplusInput,
        llmRequest: suspend (String) -> String
    ): String {
        return try {
            coroutineScope {
                // 1. Fetch Market Context (Simulated or Knowledge-based)
                val marketContextPrompt = """
                    You are a Financial Market Researcher. 
                    Provide a brief summary of current inflation rates (Eurozone), typical S&P500 annual returns (last 20y), and 'safe' withdrawal rates. 
                    Format as a concise list.
                """.trimIndent()
                val marketContextDeferred = async { llmRequest(marketContextPrompt) }
                val marketContext = marketContextDeferred.await()

                val commonFinancialContext = """
                    **Financial Context**:
                    - Capital Interest Rate: ${baseInputs.tassoGuadagnoInteresse} (Note: This is the REAL interest rate, net of inflation).
                    - Debt Interest Rate: ${baseInputs.tassoInteresseDebito}.
                    - External Benchmarks: $marketContext
                """.trimIndent()

                // Agent 1: Sustainability & Growth
                val sustainabilityPrompt = AgentPrompts.getSustainabilityPrompt(baseInputs, commonFinancialContext)

                // Agent 2: Risk & Stability
                val riskPrompt = AgentPrompts.getRiskPrompt(baseInputs, commonFinancialContext)

                // Agent 3: Analyst & Optimizer (Runs hard calculations)
                val analystDeferred = async {
                    // Run Optimization to find the theoretical "Best Plan"
                    val optimizationResult = executeOptimization("", baseInputs, specificExpenses, surplusData)
                    
                    // Run a stress test simulation (Interest Rate -1%)
                    val stressTestResult = executeSimulation("{\"tassoGuadagnoInteresse\": ${baseInputs.tassoGuadagnoInteresse - 0.01}}", baseInputs, specificExpenses, surplusData)
                    
                    """
                    **Analyst Report**:
                    
                    **A. Optimization Analysis**:
                    $optimizationResult
                    
                    **B. Stress Test (Interest Rate -1%)**:
                    $stressTestResult
                    """
                }

                // 2. Run LLM Agents in Parallel
                val sustainabilityDeferred = async { llmRequest(sustainabilityPrompt) }
                val riskDeferred = async { llmRequest(riskPrompt) }
                
                val results = awaitAll(sustainabilityDeferred, riskDeferred, analystDeferred)

                // 3. Master Agent Integration
                val masterPrompt = AgentPrompts.getMasterPrompt(
                    marketContext = marketContext,
                    sustainabilityReport = results[0],
                    riskReport = results[1],
                    analystReport = results[2],
                    isComparing = false,
                    locale = java.util.Locale.getDefault()
                )

                val finalReport = llmRequest(masterPrompt)
                
                "**Multi-Agent Analysis Report:**\n\n$finalReport"
            }
        } catch (e: Exception) {
            "Multi-Agent Workflow failed: ${e.message}"
        }
    }
}
