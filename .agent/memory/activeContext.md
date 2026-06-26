# Active Context - Compare Profiles & Polish

## Current Task

Polishing the "Compare Profiles" feature and refining UI elements based on user feedback.

## Recently Completed

1. **Gained Capital Column**: Replaced "Eroded Capital" with "Gained Capital" (End - Start) in the core simulation table.
   - Values are now colored Green (positive gain) or Red (negative gain/loss).
   - Delta logic updated to reflect "Higher Gain is Better".

2. **PDF Comparison Export**: Implemented detailed delta reporting in the PDF export when in Compare Mode.
   - PDF Title shows "COMPARISON: P1 vs P2".
   - Tables (User Data, Optim Params, Results, Sensitivity) now show appended deltas (e.g., `(Δ +500)`).

## Feature Requirements (Refined)

1. **Trigger**: Compare checkbox in Profile Management panel
2. **Selection**: Select two profiles for comparison
3. **Delta Calculation**: Profile 2 - Profile 1
4. **Display Logic**:
   - Results Tables: Show primary value + Delta
   - PDF: Match UI logic (append deltas)

## Current Status

Verification phase. Build successful. Unit tests (manual verification) required for PDF visual inspection.
