package com.example.andbook.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.andbook.R

import androidx.compose.ui.text.font.FontStyle

// Declare 16 offline FontFamilies + Nyght Serif
val EBGaramondFontFamily = FontFamily(Font(R.font.eb_garamond, FontWeight.Normal))
val CormorantGaramondFontFamily = FontFamily(Font(R.font.cormorant_garamond, FontWeight.Normal))
val CardoFontFamily = FontFamily(Font(R.font.cardo, FontWeight.Normal))
val CinzelFontFamily = FontFamily(Font(R.font.cinzel, FontWeight.Normal))
val LoraFontFamily = FontFamily(Font(R.font.lora, FontWeight.Normal))
val MerriweatherFontFamily = FontFamily(Font(R.font.merriweather, FontWeight.Normal))
val LibreBaskervilleFontFamily = FontFamily(Font(R.font.libre_baskerville, FontWeight.Normal))
val SpectralFontFamily = FontFamily(Font(R.font.spectral, FontWeight.Normal))
val PtSerifFontFamily = FontFamily(Font(R.font.pt_serif, FontWeight.Normal))
val PlayfairDisplayFontFamily = FontFamily(Font(R.font.playfair_display, FontWeight.Normal))
val OutfitFontFamily = FontFamily(Font(R.font.outfit, FontWeight.Normal))
val InterFontFamily = FontFamily(Font(R.font.inter, FontWeight.Normal))
val MontserratFontFamily = FontFamily(Font(R.font.montserrat, FontWeight.Normal))
val RobotoFontFamily = FontFamily(Font(R.font.roboto, FontWeight.Normal))
val FiraSansFontFamily = FontFamily(Font(R.font.fira_sans, FontWeight.Normal))
val JetBrainsMonoFontFamily = FontFamily(Font(R.font.jetbrains_mono, FontWeight.Normal))

val NyghtSerifFontFamily = FontFamily(
    Font(R.font.nyght_serif_light, FontWeight.Light),
    Font(R.font.nyght_serif_light_italic, FontWeight.Light, style = FontStyle.Italic),
    Font(R.font.nyght_serif_regular, FontWeight.Normal),
    Font(R.font.nyght_serif_regular_italic, FontWeight.Normal, style = FontStyle.Italic),
    Font(R.font.nyght_serif_medium, FontWeight.Medium),
    Font(R.font.nyght_serif_medium_italic, FontWeight.Medium, style = FontStyle.Italic),
    Font(R.font.nyght_serif_bold, FontWeight.Bold),
    Font(R.font.nyght_serif_bold_italic, FontWeight.Bold, style = FontStyle.Italic),
    Font(R.font.nyght_serif_dark, FontWeight.Black),
    Font(R.font.nyght_serif_dark_italic, FontWeight.Black, style = FontStyle.Italic)
)

val FontMap = mapOf(
    "nyght_serif" to NyghtSerifFontFamily,
    "eb_garamond" to EBGaramondFontFamily,
    "cormorant_garamond" to CormorantGaramondFontFamily,
    "cardo" to CardoFontFamily,
    "cinzel" to CinzelFontFamily,
    "lora" to LoraFontFamily,
    "merriweather" to MerriweatherFontFamily,
    "libre_baskerville" to LibreBaskervilleFontFamily,
    "spectral" to SpectralFontFamily,
    "pt_serif" to PtSerifFontFamily,
    "playfair_display" to PlayfairDisplayFontFamily,
    "outfit" to OutfitFontFamily,
    "inter" to InterFontFamily,
    "montserrat" to MontserratFontFamily,
    "roboto" to RobotoFontFamily,
    "fira_sans" to FiraSansFontFamily,
    "jetbrains_mono" to JetBrainsMonoFontFamily
)

val FontDisplayNameMap = mapOf(
    "nyght_serif" to "Nyght Serif",
    "eb_garamond" to "EB Garamond",
    "cormorant_garamond" to "Cormorant Garamond",
    "cardo" to "Cardo",
    "cinzel" to "Cinzel",
    "lora" to "Lora",
    "merriweather" to "Merriweather",
    "libre_baskerville" to "Libre Baskerville",
    "spectral" to "Spectral",
    "pt_serif" to "PT Serif",
    "playfair_display" to "Playfair Display",
    "outfit" to "Outfit",
    "inter" to "Inter",
    "montserrat" to "Montserrat",
    "roboto" to "Roboto",
    "fira_sans" to "Fira Sans",
    "jetbrains_mono" to "JetBrains Mono"
)

// Large, bold, spacious typography scale
val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = NyghtSerifFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 42.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = NyghtSerifFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = NyghtSerifFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = LoraFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp, // Large reading text by default
        lineHeight = 36.sp // 1.5x line spacing
    ),
    bodyMedium = TextStyle(
        fontFamily = NyghtSerifFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp, // Bold titles in row elements
        lineHeight = 26.sp
    ),
    labelLarge = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 16.sp
    )
)
