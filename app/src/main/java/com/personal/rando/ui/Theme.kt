package com.personal.rando.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val RandoGreen = Color(0xFF2E7D4F)
val RandoOrange = Color(0xFFE0672B)
val RandoAmber = Color(0xFFC98A1B)
val RandoRed = Color(0xFFC0392B)

@Composable
fun RandoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = RandoGreen,
            secondary = RandoOrange,
            secondaryContainer = Color(0xFFDCEFE1),
            onSecondaryContainer = Color(0xFF14361F),
        ),
        content = content,
    )
}
