# Technical Context

## Technology Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose (Material3)
- **Architecture**: MVVM with AndroidViewModel
- **Charts**: Plotly.js via WebView
- **Persistence**: SharedPreferences with Gson serialization
- **AI Integration**: OpenRouter API (Retrofit)
- **PDF**: Android PDF APIs

## Key Data Models

- `FullProfile`: Contains financialInput, surplusInput, specificExpenses, gaConfig
- `FinancialInput`: Core financial parameters (ages, capital, rates, P1-P4)
- `SurplusInput`: Income/expense details for working/retirement
- `SimulationYear`: Yearly simulation output
- `ObjectiveResults`: fObjW, fObj0, stabilityIndex, stdDev, avgUtilita
- `SensitivityResult`: Parameter sensitivity impacts

## Key Repositories

- `ProfileRepository`: Manage saved profiles
- `FinancialDataRepository`: Save/load FinancialInput
- `SurplusDataRepository`: Save/load SurplusInput
- `GaConfigRepository`: Save/load GA configuration

## State Management

- FinancialViewModel holds all UI state as `mutableStateOf`
- Profiles stored in SharedPreferences keyed by name
- Real-time updates via Compose recomposition
