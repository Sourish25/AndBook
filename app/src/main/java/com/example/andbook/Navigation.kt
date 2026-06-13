package com.example.andbook

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.andbook.ui.main.MainScreen
import com.example.andbook.ui.reader.ReaderScreen
import com.example.andbook.ui.settings.SettingsScreen
import com.example.andbook.ui.settings.StatsScreen
import androidx.compose.runtime.LaunchedEffect

@Composable
fun MainNavigation(bookUriToOpen: String? = null, onBookUriOpened: () -> Unit = {}) {
  val backStack = rememberNavBackStack(Main)

  LaunchedEffect(bookUriToOpen) {
    if (bookUriToOpen != null) {
      val currentTop = backStack.lastOrNull()
      if (currentTop !is Reader || currentTop.bookUri != bookUriToOpen) {
        if (currentTop is Reader) {
          backStack.removeLastOrNull()
        }
        backStack.add(Reader(bookUriToOpen))
      }
      onBookUriOpened()
    }
  }

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Main> {
          MainScreen(onItemClick = { navKey -> backStack.add(navKey) }, modifier = Modifier.fillMaxSize())
        }
        entry<Reader> { key ->
          ReaderScreen(bookUri = key.bookUri, onBack = { backStack.removeLastOrNull() }, modifier = Modifier.fillMaxSize())
        }
        entry<Settings> {
          val context = androidx.compose.ui.platform.LocalContext.current
          val repository = remember { com.example.andbook.data.DataRepository.getInstance(context.applicationContext) }
          SettingsScreen(
              repository = repository,
              onBack = { backStack.removeLastOrNull() },
              onNavigateToStats = { backStack.add(Stats) },
              modifier = Modifier.fillMaxSize()
          )
        }
        entry<Stats> {
          val context = androidx.compose.ui.platform.LocalContext.current
          val repository = remember { com.example.andbook.data.DataRepository.getInstance(context.applicationContext) }
          StatsScreen(
              repository = repository,
              onBack = { backStack.removeLastOrNull() },
              modifier = Modifier.fillMaxSize()
          )
        }
      },
  )
}

