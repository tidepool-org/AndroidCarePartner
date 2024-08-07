package org.tidepool.carepartner.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    background = Color.Black,
    primaryContainer = CardGrey,
    onPrimary = White,
    secondaryContainer = Color.Black,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    background = White,
    primaryContainer = Grey,
    onPrimary = Color.Black,
    secondaryContainer = White,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    
    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

private val LightLoopTheme = LoopTheme(
    primaryAccent = Loop_Light_Accent,
    warning = Loop_Light_Warning,
    critical = Loop_Light_Critical,
    insulin = Loop_Light_Insulin,
    carbohydrates = Loop_Light_Carbohydrates,
    bloodGlucose = Loop_Light_BloodGlucose,
    loopStatus = Loop_Light_Status,
    White
)

private val DarkLoopTheme = LoopTheme(
    primaryAccent = Loop_Dark_Accent,
    warning = Loop_Dark_Warning,
    critical = Loop_Dark_Critical,
    insulin = Loop_Dark_Insulin,
    carbohydrates = Loop_Dark_Carbohydrates,
    bloodGlucose = Loop_Dark_BloodGlucose,
    loopStatus = Loop_Dark_Status,
    onPrimaryAccent = Color.Black
)

@Composable
fun LoopFollowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        
        darkTheme                                                      -> DarkColorScheme
        else                                                           -> LightColorScheme
    }
    val loopTheme = if (darkTheme) {
        DarkLoopTheme
    } else {
        LightLoopTheme
    }
    CompositionLocalProvider(
        LocalLoopTheme provides loopTheme
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

data class LoopTheme(
    val primaryAccent: Color,
    val warning: Color,
    val critical: Color,
    val insulin: Color,
    val carbohydrates: Color,
    val bloodGlucose: Color,
    val loopStatus: Color,
    val onPrimaryAccent: Color
) {
    
    val buttonColors: ButtonColors by lazy {
        ButtonColors(
            primaryAccent,
            onPrimaryAccent,
            primaryAccent.copy(alpha = 0.5f),
            onPrimaryAccent.copy(alpha = 0.5f)
        )
    }
    
    companion object {
        
        val current: LoopTheme
            @Composable
            @ReadOnlyComposable
            get() = LocalLoopTheme.current
    }
}

internal val LocalLoopTheme = staticCompositionLocalOf { LightLoopTheme }