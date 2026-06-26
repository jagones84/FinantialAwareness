# Weight Sync And Chart Rerun Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `w` shared and synchronized between `User Data` and `Charts`, and rerun the active optimization mode plus simulation when the chart slider is released.

**Architecture:** Use `inputs.bonusStdWeight` as the single source of truth, always rebuild `uiInputs` from it after chart-side changes, and remove the special cached-front release path so chart release behavior is consistent across all optimization modes. Keep verification TDD-first with focused pure tests in `FinancialViewModel` helpers.

**Tech Stack:** Kotlin, Jetpack Compose, Android ViewModel, JUnit4

---

## File Map

**Create:**
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\docs\superpowers\specs\2026-06-26-weight-sync-and-chart-rerun-design.md`

**Modify:**
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\test\java\com\example\daysurpopt\ui\screens\OptimizationModeFlowTest.kt`
- `c:\Users\giova\AndroidStudioProjects\FinancialAwareness\app\src\main\java\com\example\daysurpopt\ui\screens\FinancialViewModel.kt`
- `F:\MCP\TRADING\WORKFLOW.md`
