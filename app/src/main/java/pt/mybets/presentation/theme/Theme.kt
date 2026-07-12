package pt.mybets.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta baseada na Betano mas com Tema Verde Neon
val DarkGray = Color(0xFF121212)      // Fundo geral
val Graphite = Color(0xFF1E1E1E)      // Superfícies (cartões, barras)
val NeonGreen = Color(0xFF00E676)     // Cor primária / Acentos / Botões
val DarkGreen = Color(0xFF00C853)     // Cor de foco primário
val OffWhite = Color(0xFFECEFF1)      // Textos principais
val LightGray = Color(0xFFB0BEC5)     // Textos secundários
val ErrorRed = Color(0xFFD32F2F)      // Erros / Alertas / Perdas
val InfoBlue = Color(0xFF2196F3)      // Anuladas / Infos

private val DarkColorScheme = darkColorScheme(
    primary = NeonGreen,
    onPrimary = Color.Black,
    secondary = InfoBlue,
    background = DarkGray,
    onBackground = OffWhite,
    surface = Graphite,
    onSurface = OffWhite,
    error = ErrorRed,
    onError = Color.White,
    surfaceVariant = Graphite,
    onSurfaceVariant = LightGray,
    outline = LightGray,
    outlineVariant = Color(0xFF37474F)
)

@Composable
fun MyBetsTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
