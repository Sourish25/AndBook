package com.example.andbook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.andbook.theme.AndBookTheme

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import com.example.andbook.data.DataRepository

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalView
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearOutSlowInEasing
import android.graphics.Bitmap

class MainActivity : ComponentActivity() {
  private val bookUriState = mutableStateOf<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    handleIntent(intent)

    enableEdgeToEdge()
    setContent {
      val repository = remember { DataRepository(applicationContext) }
      val settings by repository.settings.collectAsState()
      
      // Track the last touch coordinate globally
      var lastTouchPosition by remember { mutableStateOf(Offset.Zero) }
      
      // Track theme states
      var activeTheme by remember { mutableStateOf(settings.theme) }
      var snapshotBitmap by remember { mutableStateOf<Bitmap?>(null) }
      var revealProgress by remember { mutableStateOf(0f) }
      var animOffset by remember { mutableStateOf(Offset.Zero) }
      
      val view = LocalView.current
      
      // Listen to theme changes to trigger circular reveal transition
      LaunchedEffect(settings.theme) {
        if (settings.theme != activeTheme) {
          // Take static snapshot of the old theme before recomposition
          try {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            view.draw(canvas)
            
            snapshotBitmap = bitmap
            animOffset = lastTouchPosition
            revealProgress = 0f
            
            // Switch current rendering theme
            activeTheme = settings.theme
            
            // Expand circle from 0f to 1f progress
            animate(
              initialValue = 0f,
              targetValue = 1f,
              animationSpec = tween(durationMillis = 500, easing = LinearOutSlowInEasing)
            ) { value, _ ->
              revealProgress = value
            }
          } catch (e: Exception) {
            e.printStackTrace()
            activeTheme = settings.theme
          } finally {
            snapshotBitmap = null
          }
        }
      }

      AndBookTheme(theme = activeTheme) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
              awaitPointerEventScope {
                while (true) {
                  val event = awaitPointerEvent(PointerEventPass.Initial)
                  val change = event.changes.firstOrNull()
                  if (change != null && change.pressed) {
                    lastTouchPosition = change.position
                  }
                }
              }
            }
        ) {
          Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
          ) {
            val bookUri by bookUriState
            MainNavigation(
              bookUriToOpen = bookUri,
              onBookUriOpened = { bookUriState.value = null }
            )
          }

          // Render old snapshot overlay and clip the reveal path
          val currentSnapshot = snapshotBitmap
          if (currentSnapshot != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
              val maxRadius = kotlin.math.sqrt(size.width * size.width + size.height * size.height)
              val radius = maxRadius * revealProgress
              
              val clipPath = Path().apply {
                addOval(
                  Rect(
                    center = animOffset,
                    radius = radius
                  )
                )
              }
              
              // Draw old theme only OUTSIDE the expanding circle of the new theme
              clipPath(clipPath, clipOp = ClipOp.Difference) {
                drawImage(currentSnapshot.asImageBitmap())
              }
            }
          }
        }
      }
    }
  }

  override fun onNewIntent(intent: android.content.Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIntent(intent)
  }

  private fun handleIntent(intent: android.content.Intent?) {
    val bookUri = intent?.getStringExtra("bookUri")
    if (bookUri != null) {
      bookUriState.value = bookUri
      intent.removeExtra("bookUri")
    }
  }
}


