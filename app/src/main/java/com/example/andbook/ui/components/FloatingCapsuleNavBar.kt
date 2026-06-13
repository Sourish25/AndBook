package com.example.andbook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class MainTab {
    HISTORY, LIBRARY, BROWSE, QUOTES
}

@Composable
fun FloatingCapsuleNavBar(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    onShowSettings: () -> Unit,
    onResumeLastBook: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Elegant dark coffee bean capsule background color
    val capsuleBgColor = Color(0xFF1F1511)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(0.92f) // Stretches long horizontally
                .border(1.dp, Color(0xFFEFE6DD).copy(alpha = 0.15f), RoundedCornerShape(50.dp))
                .clip(RoundedCornerShape(50.dp))
                .background(capsuleBgColor)
                .padding(horizontal = 8.dp, vertical = 6.dp), // Extremely thin vertically!
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. History (GridView)
            NavBarIconItem(
                icon = Icons.Default.GridView,
                isSelected = currentTab == MainTab.HISTORY,
                onClick = {
                    if (currentTab == MainTab.HISTORY) {
                        onResumeLastBook()
                    } else {
                        onTabSelected(MainTab.HISTORY)
                    }
                }
            )

            // 2. Browse (Search)
            NavBarIconItem(
                icon = Icons.Default.Search,
                isSelected = currentTab == MainTab.BROWSE,
                onClick = { onTabSelected(MainTab.BROWSE) }
            )

            // 3. Library (LibraryBooks)
            NavBarIconItem(
                icon = Icons.Default.LibraryBooks,
                isSelected = currentTab == MainTab.LIBRARY,
                onClick = { onTabSelected(MainTab.LIBRARY) }
            )

            // 4. Quotes (FormatQuote)
            NavBarIconItem(
                icon = Icons.Default.FormatQuote,
                isSelected = currentTab == MainTab.QUOTES,
                onClick = { onTabSelected(MainTab.QUOTES) }
            )

            // 5. Settings (Gear)
            NavBarIconItem(
                icon = Icons.Default.Settings,
                isSelected = false,
                onClick = onShowSettings
            )
        }
    }
}

@Composable
private fun NavBarIconItem(
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // Warm caramel gold highlight for active, cream white for inactive
    val activeColor = Color(0xFFD9A066)
    val contentColor = if (isSelected) {
        activeColor
    } else {
        Color(0xFFEFE6DD).copy(alpha = 0.5f)
    }

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(50.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
    }
}
