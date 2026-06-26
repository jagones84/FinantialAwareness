# Product Context

## User Flows
1.  **Input Data**: User enters financial data in the "Home" (FinancialCalculatorScreen) and "Surplus" (SurplusCalculatorScreen) tabs.
2.  **Configuration**: User sets optimization parameters (GA Config, Assumptions).
3.  **Simulation & Analysis**:
    - User goes to "Charts".
    - Selects Chart Type (2D/3D).
    - Adjusts "Standard Deviation Minimization Weight" (Stability vs Objective trade-off).
    - Views results (Surface, Contour, Heatmap) with interactive gestures.
4.  **Export**: User generates reports (PDF) of the results with units and explanations.

## Core Domain Concepts
- **Surplus**: The financial buffer available for extra spending.
- **Objective Function (fobj)**: A weighted measure of satisfaction (AvgUtility + w/100 * Stability) / (1 + w/100).
- **Stability Index**: Formula: `StdDev / (w / 100)`. Measures how robust the solution is relative to the minimization weight.
- **Standard Deviation Minimization Weight (w)**: The balance factor between maximizing Average Utility and minimizing Volatility (Standard Deviation). Renamed from "Utility Weight" for scientific accuracy.

## UI Structure
- **Bottom Navigation**: Home, Surplus, Charts.
- **Screens**:
    - `FinancialCalculatorScreen`: Main inputs and simulation results.
    - `SurplusCalculatorScreen`: Detailed surplus calculation.
    - `ChartsScreen`: 2D/3D visualizations.
    - `GaConfigScreen`: Genetic Algorithm settings.
    - `AssumptionsScreen`: Model assumptions and utility function preview.
    - `SpecificExpensesScreen`: Management of one-off or recurring specific expenses.
    - `PdfExporter`: Logic for generating scientific-grade PDF reports.
