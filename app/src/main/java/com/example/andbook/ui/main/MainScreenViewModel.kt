package com.example.andbook.ui.main

import androidx.lifecycle.ViewModel
import com.example.andbook.data.DataRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

sealed interface MainScreenUiState {
    object Loading : MainScreenUiState
    data class Success(val items: List<String>) : MainScreenUiState
}

class MainScreenViewModel(private val repository: DataRepository) : ViewModel() {
    // Boilerplate ViewModel to satisfy the template compiler and tests.
    // Reactive data flows are managed directly by DataRepository in MainScreen.
    val uiState: Flow<MainScreenUiState> = MutableStateFlow(MainScreenUiState.Loading)
}
