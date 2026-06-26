# Technical Context

## 1. Stack & Versions
- **Language**: Kotlin
- **Framework**: Android Jetpack Compose (Material 3)
- **Build System**: Gradle (Kotlin DSL)
- **Target SDK**: 35 (Android 15)
- **Min SDK**: 24 (Android 7.0)

## 2. Key Libraries
- **UI**: `androidx.compose.material3`, `androidx.compose.ui`, `androidx.core:core-splashscreen`
- **Navigation**: `androidx.navigation.compose`
- **Security**: `com.google.android.play:integrity` (Play Integrity API)
- **Serialization**: `com.google.code.gson`
- **Charting**: Plotly.js (via WebView in `SurfaceWebView.kt`)
- **Architecture**: MVVM (ViewModel + Repository pattern)
- **PDF Generation**: Android `PdfDocument` with custom canvas drawing.

## 3. Directory Structure
- `app/src/main/java/com/example/daysurpopt/`
    - `data/`: Repositories (`SurplusDataRepository`, `AgentRepositories`).
    - `domain/`: Data classes (`FinancialInput`, `SimulationResult`).
    - `logic/`: Core algorithms (`OptimizationLogic`, `SimulationLogic`, `PdfExporter`, `PlayIntegrityHelper`).
    - `ui/`: Compose UI.
        - `screens/`: Individual screens (`ChartsScreen`, `AboutScreen`, `FinancialCalculatorScreen`).
        - `common/`: Shared components.
    - `assets/`: Web assets (Plotly.js).

## 4. Development Constraints
- **State Management**: Use `ViewModel` and `StateFlow`/`LiveData`.
- **Navigation**: Use `NavController` with strictly defined routes.
- **IO**: No blocking IO on Main Thread (use Coroutines for simulations and PDF generation).
- **Chart Logic**: Plotly.js specs are built in Kotlin (`PlotlySpecBuilder`) and passed to JS via `evaluateJavascript`.
- **UI Policy**: **Edge-to-Edge** enforcement required (no UI elements hidden by system bars).
- **Network Security**: **TLS 1.3** required; Cleartext traffic prohibited.
- **Terminology**: strictly follow "Standard Deviation Minimization Weight" across all languages.
