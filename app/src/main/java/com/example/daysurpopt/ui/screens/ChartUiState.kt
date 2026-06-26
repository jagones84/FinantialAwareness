package com.example.daysurpopt.ui.screens

data class ChartUiState(
    val grid: SurfaceGrid? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)
