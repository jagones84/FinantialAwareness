# FinancialAwareness — HANDOFF (2026-08-05)

Complete audit + handoff for the next agent. The audit pass added tests only
(`AgentToolParityTest`, `PromptDocumentationTest`, `RetirementCapitalSolverTest`); the follow-up
TDD pass applied the fixes — see section 0. Full unit suite (92 tests) and `assembleDebug` are green.

---

## 0. Fix status after the TDD application pass (2026-08-05)

All fixes were applied **red-first** (failing test → implementation → green → full suite + build).

- **F4 FIXED** — `AgentPrompts.getRiskPrompt` now teaches `StabilityScore = Avg / (Avg + StdDev)` and
  `fScalar = Avg * ((1 - w) + w * StabilityScore)`; stale texts are forbidden by `AgentPromptsTest`.
- **F5 FIXED** — system prompt documents `FETCH_PAGE` and the `sogliaMinimaFunzioneUtilita` override
  (`PromptDocumentationTest`).
- **F1a FIXED** — Surplus removed from the bottom bar, re-added as the first button of Section 1
  (Data Input & Setup) on the home screen.
- **F1b FIXED** — `QuickStartDialog` wired: auto-shown after privacy consent on first launch
  (`QuickStartRepository` flag), reopenable via a Star button in the home top bar.
- **Goal Solver (flagship) DONE** — `logic/GoalSolverLogic.kt` (bisection, ceiling validation F7,
  monotonicty tests, reference 275,390.63 € @ threshold 0.3), GUI "Goal Solver (Capital Needed)"
  button + dialog in Section 3 with Apply (writes capital through the standard `updateInputs`
  path: persist + re-simulate + clear stale analysis), localized en/it/es. `RetirementCapitalSolverTest`
  superseded and deleted by `GoalSolverLogicTest`.
- **Agent tool `RUN_RETIREMENT_SOLVER` DONE** — same `GoalSolverLogic`, same JSON override semantics
  as `RUN_SIMULATION`, ceiling-aware infeasibility reason, documented in the prompt.
- **F6 FIXED (agent↔GUI optimization parity)** — `RUN_OPTIMIZATION` now uses the user's `GAConfigUI`
  (via `buildAgentGaConfig` → `OptimizationLogic.parseGaConfig`) with optional JSON overrides
  `popSize/generations/pc/pm`; new `mode` parameter supports `TRUE_SCALAR` / `PARETO_KNEE` /
  `PARETO_FRONT` (same Pareto logic as the GUI); comparison context (`COMPARISON MODE ACTIVE`) is
  built by `FinancialViewModel.buildComparisonContextForAgent` in compare mode and injected into the
  multi-agent sustainability/risk prompts (`isComparing`).
- **Agent sensitivity access DONE** — new tool `RUN_SENSITIVITY` wraps the GUI's
  `OptimizationLogic.runSensitivityAnalysis` with the same override semantics, readable ranked
  output (`AgentSensitivityToolTest`), documented in the prompt.
- **Multi-agent grounding DONE (2026-08-05, part 4)** — the Sustainability/Risk agents previously
  received only raw inputs and hallucinated monetary figures (contradictory legacy math, "eliminate
  debt" on a debt-free plan, P1 benchmarked against income-based stats). `AgentToolExecutor.buildMultiAgentFinancialContext`
  now injects REAL engine results (objective, avg utility, std dev, stability, final capital, monthly
  surplus/saving breakdown, actual debt years) plus P1 surplus semantics into the shared context;
  Master prompt forbids invented numbers. Double execution of `RUN_MULTI_AGENT_ANALYSIS` in one turn
  blocked by `alreadyExecutedCommands` guard in `checkForToolUse` + `extractCommandName` tracking in
  `AgentViewModel` + "at most once per user request" prompt instruction (`AgentMultiAgentContextTest`).
- **Still open (by design, low priority):** F2 (in-context age validation in the Surplus form — the
  Goal Solver dialog validates its own ages), F3 partial (threshold/ceiling now surfaced via the
  Goal Solver dialog, not in the main results card), optional "apply optimization results" agent
  write-back tool, PDF/charts/profile-management agent tools.
- Verification: `testDebugUnitTest` 109 tests / 0 failures / 0 errors (1 opt-in skip); `assembleDebug` green.

## 0b. TO VALIDATE — assumption curves access & web research (user request, 2026-08-05)

Status of the agent's access to the curves edited in the **Setup (Assumptions) tab** — the utility
curve (utility vs extra daily spending, x = EUR/day) and the degradation curve (decay with age,
x = age) — and of its web-research capability:

- **READ — implemented & validated.** `GET_FINANCIAL_CONTEXT` returns `effectiveCurves` with the
  engine-effective points (defaults materialized when the user has no custom curve). Unit-tested
  (`AgentCurveAccessTest`) and validated end-to-end with a real LLM: the agent quoted y = 0.3037 at
  age 90, engine-exact.
- **EDIT — what-if only; persistence NOT available (to validate with the user).** The agent can test
  curve modifications via `RUN_SIMULATION` overrides (`utilityCurvePoints`, `degradationCurvePoints`)
  — parity locked by tests and exercised end-to-end (Final Capital 75,711 € with a flatter
  degradation curve). There is **no write-back tool**: curve changes are NOT persisted to the Setup
  tab; the agent is instructed to tell the user to apply them manually. If persistence is desired,
  a new tool (e.g. `SET_ASSUMPTIONS`) writing through the ViewModel/Repository path is needed.
- **WEB RESEARCH — implemented, e2e still to validate.** `WEB_SEARCH` (DuckDuckGo HTML via jsoup,
  no API key) and `FETCH_PAGE` are available and documented for curve-shaping research. Not yet
  exercised in a real-harness run: DuckDuckGo HTML can be rate-limited/blocked from mobile networks
  — validate on-device and consider a fallback engine if unreliable.
- **OpenRouter config (user question: "è settato in env?").** NO key exists in env vars or
  `local.properties` (only `sdk.dir`). `OPENROUTER_BASE_URL`/`HTTP_REFERER`/`TITLE` come from
  `local.properties` with defaults; the **API key and model live only in the app's SharedPreferences**
  (`AgentPrefs`, entered via the in-app settings dialog; default model `qwen/qwen3.7-plus`). The
  "Invalid API key." message in the chat is the 401 path when the dialog key is wrong/empty.
- **Can the agent be interrogated from outside with its own tool harness? YES.**
  `AgentOpenRouterHarnessTest` runs the agent exactly like the chat (safety prompt + system prompt +
  `AgentToolExecutor` loop) against any OpenAI-compatible provider: `OPENROUTER_API_KEY` preferred,
  else `OPENAI_API_KEY` / `DEEPSEEK_API_KEY` / `GEMINI_API_KEY` (failover on 401/402/429). It is
  **opt-in** (`AGENT_HARNESS_E2E=1` + key env) because real LLMs are non-deterministic. Validated
  with DeepSeek. Run: `$env:AGENT_HARNESS_E2E="1"; .\gradlew.bat testDebugUnitTest --tests "...AgentOpenRouterHarnessTest"`.
- **Real defects found by that e2e (fixed):** (1) multiple tool commands in one LLM response
  executed only the first → `checkForToolUse` now executes ALL of them; (2) models sometimes
  announce a tool without emitting the command token → strict "Tool emission" rule added to the
  system prompt.

---

## 1. What the app is

Android app (Kotlin 2.2.10, Compose BOM 2024.09.00, Material3, minSdk 24 / target 35) for
life-financial simulation and behavioral parameter optimization:

- Monthly-step simulation engine ([SimulationLogic.kt](app/src/main/java/com/example/daysurpopt/logic/SimulationLogic.kt)):
  `rm = (1+r)^(1/12)-1`, reserve-first capital rule, debt bucket, one-time expenses with
  cumulative utility offset from event age onward, forced minimum spend to guarantee
  `sogliaMinimaFunzioneUtilita` (happiness threshold) by drawing capital.
- Objective: `StabilityScore = Avg/(Avg+StdDev)`; **True Scalar** `fScalar = Avg*((1-w)+w*Stability)`
  with direct `w ∈ [0,1]`. Pareto modes are weight-free (knee selection via normalized chord distance).
- Three optimization modes: TRUE_SCALAR (GA + `refineScalarCandidate` coordinate search), PARETO_KNEE,
  PARETO_FRONT. Optimization results are applied back to live inputs (P1..P4) and re-simulated.
- Persistence: SharedPreferences (profiles via `ProfileStateMapper`, chats via `ChatRepository`,
  agent settings via `AgentSettingsRepository`).
- Charts: Plotly 2.30.0 in WebView, black paper/plot background, white fonts.
- Cross-model parity: `tools/cross_model_regression.py` vs Python reference (`C:\Users\giova\OneDrive\Documents\DOCUMENTS\Scripts\Finantial Awareness_v2`), tolerance 1e-9.

Theme (changed 2026-08-05): dark is **forced app-wide** ([Theme.kt](app/src/main/java/com/example/daysurpopt/ui/theme/Theme.kt)
uses `dynamicDarkColorScheme` on API 31+, static dark scheme otherwise; [themes.xml](app/src/main/res/values/themes.xml)
parent is `android:Theme.Material.NoActionBar`). There is no light variant anymore.

## 2. Navigation & GUI workflow audit (the "buttons vs tabs" problem)

### Current map

- Bottom bar (MainActivity): `Simulation` (financialCalculator) · `Surplus` · `Charts` · `Setup` (assumptions) · `AI Agent` · `Debug Log`.
- Simulation screen is sectioned by **buttons** that navigate to full screens:
  - Section "1. Data Input & Setup" → buttons: `userData` (UserInputsScreen: ages, capital, rates, threshold, P1..P4), `specificExpenses` (one-time expenses).
  - Section "2. Optimization Configuration" → button: `gaConfig` (GA params) + inline optimization-mode radios (True Scalar / Pareto Knee / Pareto Front).
  - Section "3. Analysis & Actions" → `Optimize` button, button to `optimizationParams`, `Run Simulation`, `Sensitivity`, `Erase Analysis Results`, `Reset Inputs`; results cards below.
  - "Manage Profiles" card at top; "About" card at bottom.
- Surplus screen: standalone bottom-bar destination with its own long input form (income green / expenses red,
  work & pension columns) and a back-arrow "save and return" that pushes computed daily-surplus values
  into `previousBackStackEntry.savedStateHandle`.
- Weight `w`: lives in the Charts screen slider (shared state in FinancialViewModel; slider release reruns active mode).
- Assumptions screen: utility & degradation curves editing + reset to defaults.

### Findings

- **F1 — Mixed input paradigm (UX blocker).** The workflow is non-linear and self-describing only partially:
  the user must *know* that income/expenses live in the bottom-bar `Surplus` tab while ages/capital/rates/P1..P4
  live behind the `User Data` button on the home screen, and that `w` lives in `Charts`. Nothing guides the
  sequence Surplus → User Data → (opt.) GA Config → Optimize → Charts. The intended sequence exists only as text:
  `quick_start_step1..6` strings (en/it/es) describe a 6-step guided path — **but `QuickStartDialog` is dead code,
  never instantiated anywhere**.
  *Recommendation:* reintroduce the flow explicitly. Options (in order of effort):
  1. Minimum: wire `QuickStartDialog` on first launch (privacy-consent accepted && no profile yet) + a "?" action.
  2. Better: replace the bottom-bar `Surplus` tab with a home-screen button inside "1. Data Input & Setup"
     (matching the old "buttons in the right workflow position" the user preferred), keeping bottom bar for
     Simulation / Charts / Agent / Setup only.
  3. Best: a 3-step onboarding wizard (Surplus → User Data → Optimize) with a visible stepper and a
     completion state that unlocks "Run Optimization".
- **F2 — Surplus data entry has no in-context validation of ages.** `mutuoAffittoFinoEta`, bonus "until age"
  etc. are free ints; the simulation silently clamps (e.g. `p4Age = max(p4, p2)`), the user never sees it.
- **F3 — `sogliaMinimaFunzioneUtilita` (happiness threshold) is buried** in User Data and never surfaced in
  results. Given finding F7 (ceiling), the app should show "your settings can reach at most X happiness at age Y".

## 3. AI Agent audit (tools vs GUI parity)

### Architecture

- [AgentViewModel.kt](app/src/main/java/com/example/daysurpopt/ui/AgentViewModel.kt): OpenRouter chat,
  system prompt = `Defaults.OPENROUTER_SAFETY_SYSTEM_PROMPT` + `PromptConstructor.constructSystemPrompt(...)`,
  last-10 visible messages as context, tool loop with **max depth 5** per user message; tool outputs appended
  as `system` messages; fresh GUI state (`inputs`, `specificExpenses`, `surplusData`) is passed on every user message.
- [AgentToolExecutor.kt](app/src/main/java/com/example/daysurpopt/agent/AgentToolExecutor.kt) commands:
  `WEB_SEARCH`, `FETCH_PAGE`, `GET_TIME`, `GET_FINANCIAL_CONTEXT`, `RUN_SIMULATION`, `RUN_OPTIMIZATION`,
  `RUN_MULTI_AGENT_ANALYSIS` (regex-detected + brace-counted JSON args; overrides mapped in
  `applyFinancialOverrides` / `applySurplusOverrides` / `applySpecificExpenseOverrides`).

### Parity verdict (the user's question: "same functions as GUI, in batch, exactly identical?")

- **Simulation: YES, exact.** `RUN_SIMULATION` calls `calculateSimulationWithWeight(modified, expenses, surplus)`
  — the same engine entry point the GUI uses. Locked by `AgentToolParityTest` (objective / final capital / avg
  utility strings must equal direct engine computation, with and without overrides, including
  `sogliaMinimaFunzioneUtilita` override).
- **Optimization: PARTIAL.**
  - Same core (`OptimizationLogic.optimizeParameters` + coordinate search = `refineScalarCandidate`), and the
    agent seeds the GA with the current parameters, so result ≥ current (locked by test). BUT:
  - **GA config mismatch:** agent hardcodes `popSize=100, generations=50, pc=0.7, pm=0.08, P1[0,1],
    P2[etaAttuale,etaPensione], P3[0,1], P4[etaAttuale,etaMorte]`; the GUI uses the user's `GAConfigUI` via
    `OptimizationLogic.parseGaConfig` (defaults pop 350 / gen 100, user-editable min/max ranges, and
    `maxP4 = max(maxP4raw, maxP2)`). Different budgets → agent optima can differ from GUI optima.
  - **Modes missing:** GUI has Pareto Knee / Pareto Front (`ParetoOptimizationLogic` +
    `ParetoKneeSelectionLogic`); the agent can only run True-Scalar-style GA. No mode parameter exists.
  - **No state write-back:** GUI optimization applies P1..P4 to live inputs and re-publishes results; the agent
    only prints a report. There is no "apply these parameters" tool.
  - **GUI features with NO agent access:** sensitivity analysis (`OptimizationLogic.runSensitivityAnalysis`),
    profile comparison / compare-state, PDF export, charts/marker snapshots, GA-config editing, profile
    create/load/delete, erase-analysis / reset-inputs, weight slider rerun semantics.
- **Other findings:**
  - **F4 — Stale formulas in `AgentPrompts.getRiskPrompt`** (lines 52–53): it still tells the LLM
    `Stability Index definition: StdDev / (Weight/100)` and `Objective stability reward term: AvgUtility / StdDev`.
    Both are pre-2026-07-30 semantics; the real ones are `Avg/(Avg+StdDev)` and
    `fScalar = Avg*((1-w)+w*Stability)`. The LLM is being fed wrong math for every risk/multi-agent report.
  - **F5 — `FETCH_PAGE` is implemented in the executor but NOT documented** in the system prompt's tool list
    (the LLM can hardly discover it). Also `sogliaMinimaFunzioneUtilita` is overridable in the executor but
    missing from the documented RUN_SIMULATION parameter list.
  - **F6 — Multi-agent report hardcodes `isComparing = false`** in `executeMultiAgentWorkflow`; in compare mode
    the agent still analyzes profile 1 only.
  - Note: `AgentReportFormatter.computeStabilityIndex` correctly delegates to the current `computeStabilityScore`
    — the *computation* is right; only the prompt *text* (F4) is stale.
  - PromptConstructorTest exists and passes; it does not cover F4/F5 (see `PromptDocumentationTest` note).

## 4. The inverse problem: "how much must I accumulate to stop working with happiness ≥ 0.3?"

**Answer: NOT possible today via GUI or agent tools — but provably implementable with existing primitives.**

- There is **no root-finding/solver** anywhere in the app. The only "numeric tools" are the GA (maximizes
  fScalar over P1..P4) and one-sided finite-difference sensitivity. Neither answers "minimum capital such that
  plan X is feasible". An LLM would have to manually iterate `RUN_SIMULATION` guesses — unreliable, and the
  tool loop is capped at 5 turns.
- Semantics map cleanly onto existing engine fields (this is the key insight for implementation):
  - "Stop working" → `etaPensione = stop-work age` (work-income bucket never applies) + zeroed pension income
    in `SurplusInput` (agent can already override every income/expense field).
  - "Resulting happiness ≥ 0.3" → `sogliaMinimaFunzioneUtilita = 0.3`; the engine *already forces* the minimum
    monthly spend achieving it, drawing capital (going into debt when capital runs out).
  - "Feasible" → no `debtAmount` in any year, no `violazioneLascito`, all utility samples ≥ threshold.
  - Then `min capitaleIniziale` is found by simple bisection (monotone: more capital only relaxes constraints).
- **Proven by `RetirementCapitalSolverTest`** (test-only solver, app untouched):
  - Stop work at 40, zero income, happiness ≥ 0.3 (custom degradation floor 0.5): **≈ 275,391 €** needed.
  - Threshold 0.25 → ≈ 222,656 €; 0.30 → ≈ 275,391 € (monotonic ✓). Solver verified: −2,000 € ⇒ infeasible.
- **F7 — Important discovered constraint (locked by test):** with the **default** curves, the utility curve
  ceiling is ≈ 0.9347 (the baseline logistic evaluated at `BASELINE_MAX_SPESA` never reaches 1.0), so max
  achievable happiness at age 82 is ≈ 0.9347 × fdeg(82) ≈ **0.295**. A 0.3 threshold is unreachable at 80+
  *regardless of capital* with default curves; a custom degradation curve (floor 0.5) restores feasibility.
  Any future "Goal Solver" must validate `soglia ≤ 0.9347 × min(fdeg)` and warn the user.

### Recommended implementation (next session, TDD-ready)

1. Add `logic/GoalSolverLogic.kt`: `solveMinimumInitialCapital(inputs, expenses, surplus, threshold, tolerance)` —
   bisection exactly as in `RetirementCapitalSolverTest` (feasibility = no debt + no lascito violation +
   utilities ≥ threshold). ~80 lines, pure, unit-testable.
2. Expose GUI card "Goal Solver" (Simulation screen, section 3): inputs = stop-work age + happiness threshold;
   output = required capital + achieved plan; "Apply" writes `capitaleIniziale` (and optionally the zero-income
   surplus variant as a what-if).
3. Expose agent tool `RUN_RETIREMENT_SOLVER {"stopWorkAge": 45, "happinessThreshold": 0.3}` reusing the same
   logic; document it in `PromptConstructor` (avoid a new F5).
4. Add ceiling validation + warning (F7) before solving.
5. Update `cross_model_regression.py` if Python parity for the solver is desired (mirrored bisection).

## 5. Tests added in this pass (all green, no app changes)

| File | What it locks |
|---|---|
| [AgentToolParityTest.kt](app/src/test/java/com/example/daysurpopt/agent/AgentToolParityTest.kt) | Agent `RUN_SIMULATION` output == direct `calculateSimulationWithWeight` (no overrides, with financial/surplus overrides, with threshold override); `GET_FINANCIAL_CONTEXT` returns full GUI state JSON; agent `RUN_OPTIMIZATION` never reports less than the current objective and lists P1..P4. |
| [PromptDocumentationTest.kt](app/src/test/java/com/example/daysurpopt/agent/PromptDocumentationTest.kt) | System prompt documents the 6 core tools, direct-weight semantics ("do not use P3 as proxy"), all documented override fields; risk prompt mentions Stability Index / Weight (w). Comment marks F4/F5 as open. |
| [RetirementCapitalSolverTest.kt](app/src/test/java/com/example/daysurpopt/logic/RetirementCapitalSolverTest.kt) | Reference bisection solver for the inverse problem + the default-curves utility ceiling (0.299 unreachable, 0.29 reachable at 3M). Reuse this algorithm for the future tool. |

## 6. Priorities for the next agent

1. ~~**P0** Fix F4~~ **DONE** (see section 0).
2. ~~**P0** Fix F5~~ **DONE** (see section 0).
3. ~~**P1** Goal Solver~~ **DONE** — GUI dialog + `GoalSolverLogic` + `RUN_RETIREMENT_SOLVER`.
4. ~~**P1** GUI flow rework per F1~~ **DONE** (QuickStartDialog wired + Surplus moved to home button).
5. ~~**P2** Agent optimization parity~~ **DONE** — user GA config, Pareto modes, comparison context,
   `RUN_SENSITIVITY`. Optional remaining: "apply optimization results" write-back tool.
6. **P2/P3 remaining:** F2 (age validation in Surplus form), F3 full (threshold/ceiling visibility in
   the main results card), agent access to PDF export / charts / profile management.
7. **P3** `NEXT_AGENT_HANDOFF.md` from 2026-07-30 no longer exists at repo root — this file supersedes it.

## 7. Conventions & commands

- Build: `.\gradlew.bat assembleDebug` · Tests: `.\gradlew.bat testDebugUnitTest` (JUnit4, `app/src/test`).
- TDD mandatory; simulation math must stay finite; Python/Android parity tolerance ~1e-15 on grid points
  (`tools/cross_model_regression.py --python-root <path>`).
- LLM default model `qwen/qwen3.7-plus`; OpenRouter keys via `local.properties` (`OPENROUTER_BASE_URL`,
  `OPENROUTER_HTTP_REFERER`, `OPENROUTER_TITLE`); no secrets in repo.
- Dark theme is forced (2026-08-05): do not reintroduce `isSystemInDarkTheme` / light schemes.
- Realtime debug log: `AppDebugLog` (viewable in-app in `debugLog` screen).
- Workflow doc: `F:\MCP\TRADING\WORKFLOW.md` must be updated after each completed subtask.
