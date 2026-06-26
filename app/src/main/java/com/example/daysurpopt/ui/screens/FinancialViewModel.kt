package com.example.daysurpopt.ui.screens

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.daysurpopt.R
import com.example.daysurpopt.agent.AgentPrompts
import com.example.daysurpopt.data.*
import com.example.daysurpopt.domain.*
import com.example.daysurpopt.logic.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

import android.widget.Toast
import com.example.daysurpopt.logic.PdfExporter
import androidx.core.content.FileProvider
import android.content.Intent

data class OptimizationResult(
    val gaFitness: Double,
    val bonusWeight: Double,
    val finalFitness: Double,
    val p1: Double,
    val p2: Int,
    val p3: Double,
    val p4: Int
)

class FinancialViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    
    // ... (existing code)

    fun exportPdf(activityContext: android.content.Context) {
        viewModelScope.launch {
            // 1. Ensure latest simulation data is ready
            try {
                withContext(Dispatchers.Main) {
                   Toast.makeText(activityContext, activityContext.getString(R.string.pdf_updating), Toast.LENGTH_SHORT).show()
                }
                
                // Trigger calculation and wait
                val surplusData = SurplusDataRepository.loadInputs(context)
                val (objective, results) = withContext(Dispatchers.Default) {
                    calculateSimulationWithWeight(inputs.copy(), specificExpenses, surplusData)
                }
                objectiveFunctionValue = objective
                simulationResults = results
                objectiveResults = calculateObjectivesFromYears(results, inputs.bonusStdWeight)
                
                // Update comparison if needed
                if (compareState.isComparing && compareState.profile2 != null) {
                    withContext(Dispatchers.Default) {
                        computeComparisonResults(compareState.profile1 ?: FullProfile(inputs, surplusData, specificExpenses, gaUI), compareState.profile2!!)
                    }
                }
            } catch (e: Exception) {
                 e.printStackTrace()
            }
            
            if (simulationResults.isEmpty()) {
                 Toast.makeText(activityContext, activityContext.getString(R.string.pdf_simulation_failed), Toast.LENGTH_SHORT).show()
                 return@launch
            }

            val agentSettings = AgentSettingsRepository.loadSettings(context)
            var aiComment: String? = null

            if (PrivacyConsentRepository.isGranted(context) && agentSettings.apiKey.isNotBlank()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(activityContext, activityContext.getString(R.string.pdf_generating_ai), Toast.LENGTH_LONG).show()
                }
                val surplusData = SurplusDataRepository.loadInputs(context)
                aiComment = fetchFullAiReport(
                    agentSettings, 
                    inputs, 
                    specificExpenses, 
                    surplusData,
                    isComparing = compareState.isComparing,
                    profile2Inputs = profile2Inputs,
                    deltaObjectives = deltaObjectiveResults
                )
            }

            val file = withContext(Dispatchers.IO) {
                PdfExporter.generateReport(
                    activityContext,
                    inputs,
                    simulationResults,
                    objectiveFunctionValue ?: 0.0,
                    sensitivityResults,
                    aiComment,
                    agentSettings.model,
                    compareState = if (compareState.isComparing) compareState else null,
                    profile2Results = if (compareState.isComparing) Triple(
                        profile2ObjectiveResults,
                        profile2SimulationResults,
                        profile2SensitivityResults
                    ) else null,
                    deltaResults = if (compareState.isComparing) Triple(
                        deltaObjectiveResults,
                        deltaSimulationResults,
                        deltaSensitivityResults
                    ) else null
                )
            }
            
            withContext(Dispatchers.Main) {
                if (file != null) {
                    Toast.makeText(activityContext, activityContext.getString(R.string.pdf_exported_success, file.name), Toast.LENGTH_LONG).show()
                    
                    // Auto-open the PDF
                    try {
                        val uri = FileProvider.getUriForFile(
                            activityContext,
                            "${activityContext.packageName}.provider",
                            file
                        )
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/pdf")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        activityContext.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(activityContext, activityContext.getString(R.string.pdf_no_viewer), Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(activityContext, activityContext.getString(R.string.pdf_exported_error), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    // Stub for benchmark fetching (simulated web search)
    private suspend fun fetchBenchmarks(api: OpenRouterApi, settings: AgentSettings): String {
        // ideally this calls a Search Tool/API. For now, we ask the LLM to provide its internal knowledge 
        // or act as a search agent if it has browsing capabilities.
        val request = OpenRouterRequest(
             model = settings.model,
             messages = listOf(
                 OpenRouterMessage("system", Defaults.OPENROUTER_SAFETY_SYSTEM_PROMPT.trim() + "\n\n" + "You are a Financial Market Researcher. Your goal is to provide CURRENT economic benchmarks."),
                 OpenRouterMessage("user", "Provide a brief summary of current inflation rates (Eurozone), typical S&P500 annual returns (last 20y), and 'safe' withdrawal rates. Format as a concise list.")
             )
        )
        // We use a separate try/catch to not block the flow
        return try {
             api.chatCompletionText(
                 authorization = "Bearer ${settings.apiKey}",
                 request = request
             )
        } catch (e: Exception) {
             context.getString(R.string.market_data_unavailable)
        }
    }

    private suspend fun fetchFullAiReport(
        settings: AgentSettings,
        baseInputs: FinancialInput,
        specificExpenses: List<SpecificExpense>,
        surplusData: SurplusInput,
        isComparing: Boolean = false,
        profile2Inputs: FinancialInput? = null,
        deltaObjectives: DeltaObjectiveResults? = null
    ): String? {
        return try {
            val api = OpenRouterClient.create()
            
            suspend fun callAgent(systemRole: String, prompt: String): String {
                val request = OpenRouterRequest(
                    model = settings.model,
                    messages = listOf(
                        OpenRouterMessage("system", Defaults.OPENROUTER_SAFETY_SYSTEM_PROMPT.trim() + "\n\n" + systemRole),
                        OpenRouterMessage("user", prompt)
                    )
                )
                return api.chatCompletionText(
                    authorization = "Bearer ${settings.apiKey}",
                    request = request
                )
            }
            
            // Step 0: Market Context
            val marketContext = fetchBenchmarks(api, settings)

            coroutineScope {
                 // Agent 1: Sustainability
                val comparisonContext = if (isComparing && profile2Inputs != null && deltaObjectives != null) """
                    
                    **COMPARISON MODE**:
                    You are comparing TWO profiles:
                    - Profile 1 (Baseline): Age ${baseInputs.etaAttuale}, Capital ${baseInputs.capitaleIniziale}, P1 ${baseInputs.p1SavingRatioSurplus}
                    - Profile 2: Age ${profile2Inputs.etaAttuale}, Capital ${profile2Inputs.capitaleIniziale}, P1 ${profile2Inputs.p1SavingRatioSurplus}
                    - Delta Objective: ${String.format(Locale.US, "%.4f", deltaObjectives.deltaFObjW)}
                """ else ""
                
                val commonFinancialContext = """
                    **Financial Context**:
                    - Capital Interest Rate: ${baseInputs.tassoGuadagnoInteresse} (Note: This is the REAL interest rate, net of inflation).
                    - Debt Interest Rate: ${baseInputs.tassoInteresseDebito}.
                    - External Benchmarks: $marketContext
                """.trimIndent()

                val sustainabilityPrompt = AgentPrompts.getSustainabilityPrompt(
                    baseInputs, 
                    commonFinancialContext, 
                    comparisonContext
                )

                // Agent 2: Risk
                val riskPrompt = AgentPrompts.getRiskPrompt(baseInputs, commonFinancialContext, comparisonContext)

                // Agent 3: Analyst (Optimization + Stress Test)
                val analystDeferred = async(Dispatchers.Default) {
                    val gaConfig = GAConfig(
                        popSize = 100, generations = 20, pc = 0.7, pm = 0.08,
                        min = ParamsCandidate(0.0, baseInputs.etaAttuale, 0.0, baseInputs.etaAttuale),
                        max = ParamsCandidate(1.0, baseInputs.etaPensione, 1.0, baseInputs.etaMorte),
                        maximize = true
                    )
                    
                    // Run Optimization (Base)
                    val gaRes = OptimizationLogic.optimizeParameters(baseInputs, gaConfig, specificExpenses, surplusData)
                    val localRes = OptimizationLogic.coordinateSearch(baseInputs, gaRes.bestParams, gaConfig, specificExpenses = specificExpenses, surplusData = surplusData)
                    val finalRes = if (localRes.bestFitness >= gaRes.bestFitness) localRes else gaRes
                    
                    // Run Stress Test (Base: -1% Interest)
                    val stressTestInputs = baseInputs.copy(tassoGuadagnoInteresse = baseInputs.tassoGuadagnoInteresse - 0.01)
                    val (stressObj, stressYears) = calculateSimulationWithWeight(stressTestInputs, specificExpenses, surplusData)
                    val stressTestResult = """
                        - Objective Function: %.4f
                        - Final Capital: %.2f
                        - Avg Utility: %.4f
                    """.format(Locale.US, stressObj, stressYears.lastOrNull()?.capitaleFineAnno ?: 0.0, stressYears.map { it.funzioneUtilita }.average())

                    // Profile 2 Analysis (if comparing)
                    var profile2Analysis = ""
                    if (isComparing && profile2Inputs != null) {
                         // Quick Stress Test for P2
                         val p2StressInputs = profile2Inputs.copy(tassoGuadagnoInteresse = profile2Inputs.tassoGuadagnoInteresse - 0.01)
                         val (p2StressObj, p2StressYears) = calculateSimulationWithWeight(p2StressInputs, specificExpenses, surplusData)
                         
                         profile2Analysis = """
                         
                         **C. Profile 2 Analysis**:
                         - Current P1: ${profile2Inputs.p1SavingRatioSurplus}
                         - Stress Test (-1% Int): Obj=%.4f, EndCap=%.2f
                         """.format(Locale.US, p2StressObj, p2StressYears.lastOrNull()?.capitaleFineAnno ?: 0.0)
                    }

                    """
                    **Analyst Report**:
                    
                    **A. Optimization Analysis (Base Profile)**:
                    - Current Objective: ${String.format(Locale.US, "%.4f", objectiveFunctionValue ?: 0.0)}
                    - Potential Objective: ${String.format(Locale.US, "%.4f", finalRes.bestFitness)}
                    - Optimized Params: 
                      P1=${"%.2f".format(finalRes.bestParams.p1)}
                      P2=${finalRes.bestParams.p2}
                      P3=${"%.2f".format(finalRes.bestParams.p3)}
                      P4=${finalRes.bestParams.p4}
                    
                    **B. Stress Test (Base: Interest Rate -1%)**:
                    $stressTestResult
                    $profile2Analysis
                    """
                }

                val sustainabilityDeferred = async { callAgent("You are the Sustainability Agent.", sustainabilityPrompt) }
                val riskDeferred = async { callAgent("You are the Risk Agent.", riskPrompt) }
                
                val results = awaitAll(sustainabilityDeferred, riskDeferred, analystDeferred)

                // Master Agent
                val masterPrompt = AgentPrompts.getMasterPrompt(
                    marketContext = marketContext,
                    sustainabilityReport = results[0],
                    riskReport = results[1],
                    analystReport = results[2],
                    isComparing = isComparing,
                    locale = Locale(LanguageRepository.loadLanguage(context))
                )

                callAgent("You are the Master Financial Advisor.", masterPrompt)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "AI Analysis Failed: ${e.message}"
        }
    }


    // State
    var inputs by mutableStateOf(FinancialDataRepository.loadInputs(context))
        private set
    var uiInputs by mutableStateOf(FinancialInputUI.from(inputs))
        private set
    var gaUI by mutableStateOf(GaConfigRepository.loadConfig(context))
        private set
    var specificExpenses by mutableStateOf(SpecificExpensesRepository.loadExpenses(context))
        private set
    var surplusData by mutableStateOf(SurplusDataRepository.loadInputs(context))
        private set

    var objectiveFunctionValue by mutableStateOf<Double?>(null)
    var objectiveResults by mutableStateOf<ObjectiveResults?>(null)
    var simulationResults by mutableStateOf<List<SimulationYear>>(emptyList())
    var sensitivityResults by mutableStateOf<List<SensitivityResult>?>(null)
    var sensitivityMessageResId by mutableStateOf<Int?>(null)
    
    var optimizing by mutableStateOf(false)
    var optimizationResult by mutableStateOf<OptimizationResult?>(null)

    // Compare Mode State
    var compareState by mutableStateOf(CompareState())
        private set
    
    // Profile 2 computed results (when comparing)
    var profile2ObjectiveResults by mutableStateOf<ObjectiveResults?>(null)
        private set
    var profile2SimulationResults by mutableStateOf<List<SimulationYear>>(emptyList())
        private set
    var profile2SensitivityResults by mutableStateOf<List<SensitivityResult>?>(null)
        private set
    
    // Delta computed values
    var deltaObjectiveResults by mutableStateOf<DeltaObjectiveResults?>(null)
        private set
    var deltaSimulationResults by mutableStateOf<List<DeltaSimulationYear>>(emptyList())
        private set
    var deltaSensitivityResults by mutableStateOf<List<DeltaSensitivityResult>?>(null)
        private set
    
    // Profile 2 raw data (for chart delta computation)
    var profile2Inputs by mutableStateOf<FinancialInput?>(null)
        private set
    var profile2Expenses by mutableStateOf<List<SpecificExpense>>(emptyList())
        private set
    var profile2SurplusData by mutableStateOf<SurplusInput?>(null)
        private set

    // Surplus Summary State
    var surplusLavorativaMedia by mutableStateOf<Double?>(null)
    var surplusPensioneMedia by mutableStateOf<Double?>(null)
    var mutuoFinoEta by mutableStateOf<Int?>(null)

    init {
        val normalizedInputs = inputs.withDefaultAssumptionCurves()
        if (normalizedInputs != inputs) {
            inputs = normalizedInputs
            uiInputs = FinancialInputUI.from(inputs)
            FinancialDataRepository.saveInputs(context, inputs)
        }

        // Load surplus summary
        val surplusInput = SurplusDataRepository.loadInputs(context)
        surplusLavorativaMedia = surplusInput.calculateSurplusGiornalieroMedioLavorativa()
        surplusPensioneMedia = surplusInput.calculateSurplusGiornalieroMedioPensione()
        mutuoFinoEta = surplusInput.mutuoAffittoFinoEta

        // Restore Compare State if exists
        viewModelScope.launch {
            val savedState = CompareStateRepository.loadState(context)
            if (savedState != null) {
                val pName1 = savedState.profile1Name
                val pName2 = savedState.profile2Name
                
                if (pName1 != null && pName2 != null) {
                    val p1 = ProfileRepository.loadProfile(context, pName1)
                    val p2 = ProfileRepository.loadProfile(context, pName2)
                    
                    if (p1 != null && p2 != null) {
                        loadProfile(p1) // Load P1 as active
                        compareState = CompareState(
                            isComparing = true,
                            profile1Name = pName1,
                            profile2Name = pName2,
                            profile1 = p1,
                            profile2 = p2
                        )
                        computeComparisonResults(p1, p2)
                    } else {
                        // One of the profiles was deleted, clear state
                        CompareStateRepository.clearState(context)
                    }
                }
            }
        }
    }

    fun updateInputs(newInputs: FinancialInput) {
        inputs = newInputs
        uiInputs = FinancialInputUI.from(newInputs)
        saveInputs()
    }

    fun updateUiInputs(newUiInputs: FinancialInputUI) {
        uiInputs = newUiInputs
    }
    
    fun updateParsedInput(updater: (FinancialInput) -> FinancialInput) {
        inputs = updater(inputs)
        saveInputs()
    }

    fun runOptimization() {
        viewModelScope.launch {
            optimizing = true
            optimizationResult = null
            
            val cfg = OptimizationLogic.parseGaConfig(gaUI, inputs)
            val surplusData = SurplusDataRepository.loadInputs(context)
            
            val gaRes = withContext(Dispatchers.Default) { 
                OptimizationLogic.optimizeParameters(inputs, cfg, specificExpenses, surplusData) 
            }
            
            val localRes = withContext(Dispatchers.Default) {
                OptimizationLogic.coordinateSearch(
                    inputs,
                    gaRes.bestParams,
                    cfg,
                    specificExpenses = specificExpenses,
                    surplusData = surplusData
                )
            }
            
            val finalRes = if (cfg.maximize) {
                if (localRes.bestFitness >= gaRes.bestFitness) localRes else gaRes
            } else {
                if (localRes.bestFitness <= gaRes.bestFitness) localRes else gaRes
            }

            val p2 = finalRes.bestParams.p2
            val p4 = maxOf(finalRes.bestParams.p4, p2)
            
            updateParsedInput { i -> i.copy(
                p1SavingRatioSurplus = finalRes.bestParams.p1,
                p2EtaFineRisparmioNoCapitale = p2,
                p3PercentualeCapitaleDaSpendereAnnualmente = finalRes.bestParams.p3,
                p4EtaAnticipataInizioSpesaCapitale = p4
            )}
            
            updateUiInputs(uiInputs.copy(
                p1SavingRatioSurplus = String.format(Locale.US, "%.4f", finalRes.bestParams.p1),
                p2EtaFineRisparmioNoCapitale = p2.toString(),
                p3PercentualeCapitaleDaSpendereAnnualmente = String.format(
                    Locale.US,
                    "%.4f",
                    finalRes.bestParams.p3
                ),
                p4EtaAnticipataInizioSpesaCapitale = p4.toString()
            ))
            
            runSimulation()
            
            optimizationResult = OptimizationResult(
                gaFitness = gaRes.bestFitness,
                bonusWeight = inputs.bonusStdWeight,
                finalFitness = finalRes.bestFitness,
                p1 = finalRes.bestParams.p1,
                p2 = p2,
                p3 = finalRes.bestParams.p3,
                p4 = p4
            )
            
            optimizing = false
        }
    }

    fun runSimulation() {
        viewModelScope.launch {
            val surplusData = SurplusDataRepository.loadInputs(context)
            val (objective, results) = withContext(Dispatchers.Default) {
                calculateSimulationWithWeight(
                    inputs.copy(),
                    specificExpenses,
                    surplusData
                )
            }
            objectiveFunctionValue = objective
            simulationResults = results
            objectiveResults = calculateObjectivesFromYears(results, inputs.bonusStdWeight)
            
            // If in compare mode, recompute deltas using updated Profile 1 results
            if (compareState.isComparing && profile2ObjectiveResults != null) {
                deltaObjectiveResults = objectiveResults?.let { p1Obj ->
                    DeltaCalculator.computeDeltaObjectives(p1Obj, profile2ObjectiveResults!!)
                }
                deltaSimulationResults = DeltaCalculator.computeDeltaSimulation(
                    simulationResults,
                    profile2SimulationResults
                )
            }
        }
    }

    fun runSensitivityAnalysis() {
        viewModelScope.launch {
            sensitivityMessageResId = R.string.analysis_in_progress
            sensitivityResults = null
            
            val surplusData = SurplusDataRepository.loadInputs(context)
            val results = withContext(Dispatchers.Default) {
                OptimizationLogic.runSensitivityAnalysis(
                    inputs,
                    specificExpenses,
                    surplusData
                )
            }
            sensitivityResults = results
            sensitivityMessageResId = null
            
            // If in compare mode, recompute sensitivity deltas
            if (compareState.isComparing && profile2SensitivityResults != null && results != null) {
                deltaSensitivityResults = DeltaCalculator.computeDeltaSensitivity(
                    results,
                    profile2SensitivityResults!!
                )
            }
        }
    }

    fun updateGaConfig(newConfig: GAConfigUI) {
        gaUI = newConfig
        GaConfigRepository.saveConfig(context, newConfig)
        triggerRecalculation()
    }

    fun triggerRecalculation() { // Changed to public
        runSimulation()
    }

    fun updateSpecificExpenses(newExpenses: List<SpecificExpense>) {
        specificExpenses = newExpenses
        SpecificExpensesRepository.saveExpenses(context, specificExpenses)
        triggerRecalculation()
    }

    fun updateSurplusData(newData: SurplusInput) {
        surplusData = newData
        SurplusDataRepository.saveInputs(context, newData)
        triggerRecalculation()
    }

    private fun saveInputs() {
        FinancialDataRepository.saveInputs(context, inputs)
    }
    
    fun refreshSurplusData() {
        val surplusInput = SurplusDataRepository.loadInputs(context)
        surplusLavorativaMedia = surplusInput.calculateSurplusGiornalieroMedioLavorativa()
        surplusPensioneMedia = surplusInput.calculateSurplusGiornalieroMedioPensione()
        mutuoFinoEta = surplusInput.mutuoAffittoFinoEta
    }

    fun resetInputs() {
        val defaultFinancialInputs = FinancialInput().withDefaultAssumptionCurves()
        inputs = defaultFinancialInputs
        uiInputs = FinancialInputUI.from(defaultFinancialInputs)
        gaUI = gaUI.copy(
            minRange = "0.0;${defaultFinancialInputs.etaAttuale};0.0;${defaultFinancialInputs.etaAttuale}"
        )
        val defaultSurplus = SurplusInput()
        SurplusDataRepository.saveInputs(context, defaultSurplus)
        refreshSurplusData()
        SpecificExpensesRepository.saveExpenses(context, List(10) { SpecificExpense() })
        specificExpenses = SpecificExpensesRepository.loadExpenses(context)
        saveInputs()
        GaConfigRepository.saveConfig(context, gaUI)
    }

    fun loadProfile(profile: FullProfile) {
        val normalized = profile.financialInput.withDefaultAssumptionCurves()
        FinancialDataRepository.saveInputs(context, normalized)
        SurplusDataRepository.saveInputs(context, profile.surplusInput)
        SpecificExpensesRepository.saveExpenses(context, profile.specificExpenses)
        GaConfigRepository.saveConfig(context, profile.gaConfig)

        inputs = normalized
        uiInputs = FinancialInputUI.from(normalized)
        gaUI = profile.gaConfig
        specificExpenses = profile.specificExpenses
        refreshSurplusData()
    }

    // Profiles Management
    private val _profileNames = mutableStateOf<List<String>>(emptyList())
    val profileNames: State<List<String>> = _profileNames

    fun fetchProfileNames() {
        _profileNames.value = ProfileRepository.getProfileNames(context)
    }

    fun saveProfile(name: String) {
        val surplus = SurplusDataRepository.loadInputs(context)
        val fullProfile = FullProfile(
            financialInput = inputs,
            surplusInput = surplus,
            specificExpenses = specificExpenses,
            gaConfig = gaUI
        )
        ProfileRepository.saveProfile(context, name, fullProfile)
        fetchProfileNames()
    }

    fun loadProfileByName(name: String) {
        val profile = ProfileRepository.loadProfile(context, name)
        if (profile != null) {
            loadProfile(profile)
        }
    }

    fun deleteProfile(name: String) {
        ProfileRepository.deleteProfile(context, name)
        fetchProfileNames()
    }

    // ========== Compare Mode Functions ==========

    /**
     * Enter compare mode with two profiles.
     * Profile 1 values become the current inputs (read-only).
     * Profile 2 is used for delta computation.
     */
    fun enterCompareMode(profile1Name: String, profile2Name: String) {
        val p1 = ProfileRepository.loadProfile(context, profile1Name)
        val p2 = ProfileRepository.loadProfile(context, profile2Name)
        
        if (p1 == null || p2 == null) {
            return // Invalid profiles
        }

        // Load Profile 1 as the display baseline
        loadProfile(p1)

        val newState = CompareState(
            isComparing = true,
            profile1Name = profile1Name,
            profile2Name = profile2Name,
            profile1 = p1,
            profile2 = p2
        )
        compareState = newState
        CompareStateRepository.saveState(context, newState)

        // Compute Profile 2 results and deltas
        viewModelScope.launch {
            computeComparisonResults(p1, p2)
        }
    }

    /**
     * Exit compare mode and clear all comparison data.
     */
    fun exitCompareMode() {
        compareState = CompareState()
        CompareStateRepository.clearState(context)
        profile2ObjectiveResults = null
        profile2SimulationResults = emptyList()
        profile2SensitivityResults = null
        profile2Inputs = null
        profile2Expenses = emptyList()
        profile2SurplusData = null
        deltaObjectiveResults = null
        deltaSimulationResults = emptyList()
        deltaSensitivityResults = null
    }

    /**
     * Compute simulation/sensitivity for both profiles and their deltas.
     */
    private suspend fun computeComparisonResults(p1: FullProfile, p2: FullProfile) {
        withContext(Dispatchers.Default) {
            // Profile 1 results (already computed when loadProfile was called, but we recompute for consistency)
            val p1Inputs = p1.financialInput.withDefaultAssumptionCurves()
            val p1Surplus = p1.surplusInput
            val p1Simulation = calculateSimulation(p1Inputs, p1.specificExpenses, p1Surplus)
            val p1Objectives = calculateObjectivesFromYears(p1Simulation, p1Inputs.bonusStdWeight)
            val p1Sensitivity = OptimizationLogic.runSensitivityAnalysis(p1Inputs, p1.specificExpenses, p1Surplus)

            // Profile 2 results
            val p2Inputs = p2.financialInput.withDefaultAssumptionCurves()
            val p2Surplus = p2.surplusInput
            val p2Simulation = calculateSimulation(p2Inputs, p2.specificExpenses, p2Surplus)
            val p2Objectives = calculateObjectivesFromYears(p2Simulation, p2Inputs.bonusStdWeight)
            val p2Sensitivity = OptimizationLogic.runSensitivityAnalysis(p2Inputs, p2.specificExpenses, p2Surplus)

            // Compute deltas (Profile 2 - Profile 1)
            val deltaObj = DeltaCalculator.computeDeltaObjectives(p1Objectives, p2Objectives)
            val deltaSim = DeltaCalculator.computeDeltaSimulation(p1Simulation, p2Simulation)
            val deltaSens = DeltaCalculator.computeDeltaSensitivity(p1Sensitivity, p2Sensitivity)

            withContext(Dispatchers.Main) {
                // Update Profile 1 results (display)
                objectiveResults = p1Objectives
                simulationResults = p1Simulation
                sensitivityResults = p1Sensitivity
                objectiveFunctionValue = p1Objectives.fObjW

                // Store Profile 2 results
                profile2ObjectiveResults = p2Objectives
                profile2SimulationResults = p2Simulation
                profile2SensitivityResults = p2Sensitivity
                profile2Inputs = p2Inputs
                profile2Expenses = p2.specificExpenses
                profile2SurplusData = p2Surplus

                // Store deltas
                deltaObjectiveResults = deltaObj
                deltaSimulationResults = deltaSim
                deltaSensitivityResults = deltaSens
            }
        }
    }
}