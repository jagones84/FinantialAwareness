# Financial Awareness App - Project Brief

## Overview

Android financial planning application built with Jetpack Compose. Allows users to model long-term financial planning scenarios including:

- Personal financial data (ages, capital, rates)
- Surplus calculations (income/expenses during working/retirement)
- Optimization of 4 key parameters (P1-P4) using genetic algorithm
- Simulation of yearly financial trajectories
- Sensitivity analysis
- Interactive 3D/2D charts for objective function visualization
- AI-powered financial reports via OpenRouter API

## Core Features

1. **Profile Management**: Save/Load/Delete financial configurations
2. **Surplus Calculator**: Calculate daily surplus for working years and retirement
3. **User Data Input**: Personal ages, capital, rates, utility parameters
4. **Optimization**: Genetic algorithm + coordinate search for optimal P1-P4
5. **Simulation**: Year-by-year financial projection with utility function
6. **Sensitivity Analysis**: Impact of parameter changes on objective function
7. **Charts**: Interactive Plotly-based 3D surfaces and 2D heatmaps
8. **Model Assumptions**: Editable utility and degradation curves
9. **PDF Export**: Export results with optional AI commentary

## Architecture

- **Clean Architecture**: Data/Domain/UI layers
- **MVVM**: FinancialViewModel as central state holder
- **Jetpack Compose**: Modern declarative UI
- **SharedPreferences**: Profile and settings persistence via Gson

## Key Files

- `FinancialViewModel.kt`: Central ViewModel with all state and business logic
- `FinancialCalculatorScreen.kt`: Main dashboard screen
- `ChartsScreen.kt`: Interactive 3D/2D objective function visualization
- `ProfilesDialog.kt`: Profile save/load/delete dialog
- `SimulationLogic.kt`, `OptimizationLogic.kt`: Core computation
