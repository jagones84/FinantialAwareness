# Navigation, Profiles, And LLM Settings Design

## Scope

This change set covers four tightly related tasks:

1. publish the repository to GitHub with an explicit `README.md` and a safer `.gitignore`
2. remove redundant navigation paths to top-level pages
3. make surplus profile save/load fully reliable for every editable surplus field
4. align report model selection with the user-selected AI model, with default `qwen/qwen3.7-plus`

## Current State

- The repository has no configured Git remote.
- The project had no root `README.md`.
- `FinancialCalculatorScreen` exposes buttons that navigate to pages already reachable from the bottom navigation bar.
- `FinancialViewModel.loadProfile()` persists `profile.surplusInput` but does not clearly restore the in-memory `surplusData` state before the UI reuses it.
- `FinancialViewModel.saveProfile()` reloads surplus from the repository instead of always using the live `surplusData` state.
- AI settings are stored in `AgentSettingsRepository`, but the default model still points to `x-ai/grok-4.1-fast`.

## Design Decisions

### Repository Publication

- Add a concise root `README.md` with setup, build, test, and AI usage notes.
- Expand the root `.gitignore` to ignore Android Studio, Gradle, build, log, keystore, and local agent state files.
- Configure `origin` to `https://github.com/jagones84/FinantialAwareness`.

### Navigation Cleanup

- Treat bottom bar destinations as the only entry points for top-level pages.
- Remove in-screen buttons from `FinancialCalculatorScreen` that duplicate bottom-bar navigation.
- Keep secondary-detail screens reachable from `FinancialCalculatorScreen`, including:
  - `userData`
  - `specificExpenses`
  - `gaConfig`
  - `optimizationParams`
  - profiles management

### Surplus Profile Persistence

- Use `FinancialViewModel.surplusData` as the source of truth for profile save.
- Update `FinancialViewModel.loadProfile()` so it restores all four persisted domains into memory:
  - financial inputs
  - surplus input
  - specific expenses
  - GA config
- Refresh derived surplus summary values after the in-memory state is restored.
- Keep `SurplusCalculatorScreen` bound to `viewModel.surplusData`.

### LLM Model Synchronization

- Change `AgentSettings.model` default to `qwen/qwen3.7-plus`.
- Keep `AgentSettingsRepository` as the single persistence path.
- Ensure report generation in `FinancialViewModel` reads the latest persisted settings and therefore uses the same model selected in the AI settings dialog.

## Validation

- Add focused unit tests for:
  - default AI model
  - profile save/load preserving surplus data
  - load-profile restoring in-memory surplus state
- Run unit tests and a debug build before completing the task.
