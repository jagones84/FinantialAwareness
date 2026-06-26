# Agent Weight Safety Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the in-app AI agent handle `bonusStdWeight` (`w`) explicitly and report stability metrics with the same scaling used by the simulation logic.

**Architecture:** Keep the patch narrow. Update prompt text so the model knows `bonusStdWeight` is a first-class override, extract a tiny reporting helper for agent stability formatting, and validate the behavior with focused JVM tests. Avoid changing simulation math or the optimization search space.

**Tech Stack:** Kotlin, JUnit4, Android/JVM unit tests, Jetpack Compose app architecture

---

### Task 1: Lock Prompt Contract With Tests

**Files:**
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\test\java\com\example\daysurpopt\agent\PromptConstructorTest.kt`
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\agent\PromptConstructor.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.daysurpopt.agent

import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.SurplusInput
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptConstructorTest {

    @Test
    fun systemPrompt_explicitlyListsBonusStdWeightAsAgentOverride() {
        val prompt = PromptConstructor.constructSystemPrompt(
            FinancialInput(),
            emptyList(),
            SurplusInput()
        )

        assertTrue(prompt.contains("bonusStdWeight"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.agent.PromptConstructorTest"`
Expected: FAIL because the current prompt text does not clearly expose `bonusStdWeight`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
- `RUN_SIMULATION {param: value}`: Run single simulation.
  Allowed params (FinancialInput):
  - `bonusStdWeight` (Stability Weight, w)

- `RUN_OPTIMIZATION {param: value}`: Run GA + Coordinate Search to find best parameters. Supports same overrides as simulation, including `bonusStdWeight` as a fixed scenario input.
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.agent.PromptConstructorTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/example/daysurpopt/agent/PromptConstructorTest.kt app/src/main/java/com/example/daysurpopt/agent/PromptConstructor.kt
git commit -m "test: expose agent bonusStdWeight override"
```

### Task 2: Lock Stability Scaling With Tests

**Files:**
- Create: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\test\java\com\example\daysurpopt\agent\AgentReportFormatterTest.kt`
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\agent\AgentReportFormatter.kt`
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\agent\AgentToolExecutor.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.daysurpopt.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentReportFormatterTest {

    @Test
    fun computeStabilityIndex_matchesSimulationLogicScaling() {
        assertEquals(25.0, AgentReportFormatter.computeStabilityIndex(0.125, 0.50), 1e-9)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.agent.AgentReportFormatterTest"`
Expected: FAIL because `AgentReportFormatter` does not exist yet.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.example.daysurpopt.agent

object AgentReportFormatter {
    fun computeStabilityIndex(stdDev: Double, bonusStdWeight: Double): Double {
        val weight = bonusStdWeight / 100.0
        return if (weight > 1e-9) stdDev / weight else 0.0
    }
}
```

- [ ] **Step 4: Wire the helper into agent reporting**

```kotlin
val currentStability = AgentReportFormatter.computeStabilityIndex(currentStdDev, modifiedInputs.bonusStdWeight)
val optStability = AgentReportFormatter.computeStabilityIndex(optStdDev, optInputs.bonusStdWeight)
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.agent.AgentReportFormatterTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/test/java/com/example/daysurpopt/agent/AgentReportFormatterTest.kt app/src/main/java/com/example/daysurpopt/agent/AgentReportFormatter.kt app/src/main/java/com/example/daysurpopt/agent/AgentToolExecutor.kt
git commit -m "fix: align agent stability reporting"
```

### Task 3: Clean Prompt Wording Without Broad Behavior Changes

**Files:**
- Modify: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\agent\AgentPrompts.kt`
- Test: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\test\java\com\example\daysurpopt\agent\PromptConstructorTest.kt`

- [ ] **Step 1: Update stability wording to match app logic**

```kotlin
- Weight (w): ${baseInputs.bonusStdWeight}
- Stability Index definition: StdDev / (w/100).
- Objective stability reward term: AvgUtility / StdDev.
```

- [ ] **Step 2: Remove wording that can encourage proxy reasoning**

```kotlin
2. **Risk Profile**: Is the plan resilient to a market shock or high inflation?
3. **Stability Index**: Evaluate the plan's smoothness using the actual app formulas.
```

- [ ] **Step 3: Run focused tests**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.agent.*"`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/daysurpopt/agent/AgentPrompts.kt app/src/test/java/com/example/daysurpopt/agent/PromptConstructorTest.kt
git commit -m "fix: clarify agent weight and stability wording"
```

### Task 4: Final Verification

**Files:**
- Check: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\agent\AgentToolExecutor.kt`
- Check: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\agent\AgentPrompts.kt`
- Check: `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\agent\PromptConstructor.kt`

- [ ] **Step 1: Run targeted agent tests**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.agent.*"`
Expected: PASS

- [ ] **Step 2: Run broader safety check**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.daysurpopt.domain.AgentSettingsTest" --tests "com.example.daysurpopt.domain.ProfileStateMapperTest"`
Expected: PASS

- [ ] **Step 3: Check diagnostics**

Use IDE diagnostics on edited Kotlin files and confirm no new errors.

- [ ] **Step 4: Commit final patch**

```bash
git add app/src/main/java/com/example/daysurpopt/agent app/src/test/java/com/example/daysurpopt/agent docs/superpowers/specs/2026-06-26-agent-weight-safety-design.md docs/superpowers/plans/2026-06-26-agent-weight-safety.md
git commit -m "fix: make agent weight handling consistent"
```
