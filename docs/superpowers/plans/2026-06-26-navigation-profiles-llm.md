# Navigation, Profiles, And LLM Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish the repository cleanly to GitHub, remove redundant top-level navigation, fix full surplus profile persistence, and align report generation with the user-selected AI model.

**Architecture:** Keep the current Compose screen structure and SharedPreferences repositories. Fix the profile persistence bug in `FinancialViewModel`, change only the duplicated entry points in `FinancialCalculatorScreen`, and keep AI model persistence centralized in `AgentSettingsRepository`.

**Tech Stack:** Kotlin, Jetpack Compose, Android ViewModel, SharedPreferences, Gson, JUnit, Gradle

---

### Task 1: Publish Repository Metadata

**Files:**
- Create: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\README.md`
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\.gitignore`

- [ ] **Step 1: Write the documentation files**

```md
# FinancialAwareness
...
```

- [ ] **Step 2: Review repository-visible files**

Run: `git status --short`
Expected: shows `README.md` and `.gitignore` changes only for metadata publication

- [ ] **Step 3: Commit publication files**

```bash
git add README.md .gitignore
git commit -m "docs: add repository metadata"
```

- [ ] **Step 4: Configure remote and push**

```bash
git remote add origin https://github.com/jagones84/FinantialAwareness
git push -u origin main
```

### Task 2: Fix AI Model Default With TDD

**Files:**
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\domain\AgentModels.kt`
- Test: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\test\java\com\example\daysurpopt\AgentSettingsTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
import com.example.daysurpopt.domain.AgentSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentSettingsTest {
    @Test
    fun defaultModel_isQwen37Plus() {
        assertEquals("qwen/qwen3.7-plus", AgentSettings().model)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.AgentSettingsTest"`
Expected: FAIL because default model is still `x-ai/grok-4.1-fast`

- [ ] **Step 3: Write minimal implementation**

```kotlin
data class AgentSettings(
    val apiKey: String = "",
    val model: String = "qwen/qwen3.7-plus",
    val showThinking: Boolean = true
)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.AgentSettingsTest"`
Expected: PASS

### Task 3: Fix Surplus Profile Persistence With TDD

**Files:**
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\FinancialViewModel.kt`
- Test: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\test\java\com\example\daysurpopt\ProfilePersistenceTest.kt`

- [ ] **Step 1: Write a failing test for save-profile source data**

```kotlin
// Test intent:
// saving a profile after editing surplus must save the in-memory surplusData,
// not a stale value reloaded from storage.
```

- [ ] **Step 2: Run the test to verify failure**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.ProfilePersistenceTest"`
Expected: FAIL showing stale surplus profile data

- [ ] **Step 3: Write minimal implementation**

```kotlin
fun saveProfile(name: String) {
    val fullProfile = FullProfile(
        financialInput = inputs,
        surplusInput = surplusData,
        specificExpenses = specificExpenses,
        gaConfig = gaUI
    )
    ProfileRepository.saveProfile(context, name, fullProfile)
    fetchProfileNames()
}
```

- [ ] **Step 4: Add a failing test for load-profile state restoration**

```kotlin
// Test intent:
// loadProfile(profile) must assign surplusData = profile.surplusInput
// before derived summary refresh happens.
```

- [ ] **Step 5: Implement the restoration fix**

```kotlin
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
    surplusData = profile.surplusInput
    refreshSurplusData()
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.ProfilePersistenceTest"`
Expected: PASS

### Task 4: Remove Redundant Top-Level Navigation

**Files:**
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\FinancialCalculatorScreen.kt`

- [ ] **Step 1: Remove duplicated top-level menu buttons**

```kotlin
// Remove buttons that navigate to:
// "surplusCalculator"
// "charts"
// "assumptions"
```

- [ ] **Step 2: Keep only secondary entry points**

```kotlin
// Keep buttons for:
// "userData"
// "specificExpenses"
// "gaConfig"
// "optimizationParams"
```

- [ ] **Step 3: Run build verification**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

### Task 5: Verify Report Uses Persisted Model

**Files:**
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\FinancialViewModel.kt`
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\AgentScreen.kt`

- [ ] **Step 1: Review model read path**

```kotlin
val agentSettings = AgentSettingsRepository.loadSettings(context)
```

- [ ] **Step 2: Ensure report/export keeps using persisted settings after dialog save**

```kotlin
viewModel.updateSettings(newSettings)
// persisted via AgentSettingsRepository.saveSettings(...)
```

- [ ] **Step 3: Run targeted tests and full unit suite**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: PASS

- [ ] **Step 4: Commit implementation**

```bash
git add app/src/main/java app/src/test/java README.md .gitignore docs
git commit -m "fix: align navigation profiles and llm settings"
```
