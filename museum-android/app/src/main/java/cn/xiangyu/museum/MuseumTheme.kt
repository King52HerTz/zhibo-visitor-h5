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
            secondaryContainer = Color(0xFFF2DDD1), onSecondaryContainer = Color(0xFF852D18),
            tertiary = Color(0xFF5D6A4B),
            background = Color(0xFFF8F4EB), onBackground = Color(0xFF30241F),
            surface = Color(0xFFF8F4EB), onSurface = Color(0xFF30241F),
            surfaceContainer = Color(0xFFF0EADF), surfaceContainerHigh = Color(0xFFEDE5D7),
            surfaceVariant = Color(0xFFF1E7D9), onSurfaceVariant = Color(0xFF66554A),
            outline = Color(0xFF887568), outlineVariant = Color(0xFFD6C8BA)
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
