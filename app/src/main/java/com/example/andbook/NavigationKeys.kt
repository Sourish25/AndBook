package com.example.andbook

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey

@Serializable data class Reader(val bookUri: String) : NavKey

@Serializable data object Settings : NavKey

@Serializable data object Stats : NavKey

@Serializable data object AnimationSettings : NavKey

