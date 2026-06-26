# Pareto Knee Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Pareto modes weight-free by replacing the current ASF-weighted selector with a knee-point selector and relabel the app accordingly.

**Architecture:** Keep Pareto front generation unchanged, but swap the post-front decision layer to a normalized knee selector. Rename the middle optimization mode to `Pareto Knee`, keep `True Scalar` as the only `w`-driven mode, and update charts/export/text so the semantics stay consistent end-to-end.

**Tech Stack:** Kotlin, Jetpack Compose, Android ViewModel, JUnit4

---

## File Map

**Create:**
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\docs\superpowers\specs\2026-06-26-pareto-knee-redesign.md`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\logic\ParetoKneeSelectionLogic.kt`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\test\java\com\example\daysurpopt\logic\ParetoKneeSelectionLogicTest.kt`

**Modify:**
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\domain\ParetoModels.kt`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\FinancialViewModel.kt`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\FinancialCalculatorScreen.kt`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\ChartsScreen.kt`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\logic\PdfExporter.kt`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\res\values\strings.xml`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\res\values-it\strings.xml`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\res\values-es\strings.xml`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\test\java\com\example\daysurpopt\logic\OptimizationModeLabelTest.kt`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\test\java\com\example\daysurpopt\ui\screens\OptimizationModeFlowTest.kt`
- `F:\MCP\TRADING\WORKFLOW.md`
