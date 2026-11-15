package cn.xiangyu.museum

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Space {
    val small = 8.dp
    val medium = 16.dp
    val large = 24.dp
    val touch = 56.dp
}

@Composable
fun MuseumTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFFAE391E), onPrimary = Color(0xFFFFF7E7),
            secondary = Color(0xFFEED5AB), onSecondary = Color(0xFF392413),
            secondaryContainer = Color(0xFFF0CDB0), onSecondaryContainer = Color(0xFF852D18),
            tertiary = Color(0xFF5D6A4B),
            background = Color(0xFFFFEDCF), onBackground = Color(0xFF392413),
            surface = Color(0xFFFFEDCF), onSurface = Color(0xFF392413),
            surfaceContainer = Color(0xFFF9E4BF), surfaceContainerHigh = Color(0xFFFFE9C5),
            surfaceVariant = Color(0xFFF4DCB6), onSurfaceVariant = Color(0xFF654B34),
            outline = Color(0xFF8D6744), outlineVariant = Color(0xFFC9A87D)
        ),
        typography = Typography(
            headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
            titleMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
            titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 23.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
            bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 30.sp),
            bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 26.sp),
            labelLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
            bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 22.sp)
        ), content = content
    )
}
