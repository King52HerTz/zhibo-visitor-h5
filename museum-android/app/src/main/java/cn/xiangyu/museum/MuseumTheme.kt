package cn.xiangyu.museum

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
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
            primary = Color(0xFF146C72), onPrimary = Color.White,
            secondary = Color(0xFFF4A35D), onSecondary = Color(0xFF193E40),
            tertiary = Color(0xFFA33E37),
            background = Color(0xFFFFF9EF), onBackground = Color(0xFF193E40),
            surface = Color(0xFFFFF9EF), onSurface = Color(0xFF193E40),
            surfaceVariant = Color(0xFFE4EED7), onSurfaceVariant = Color(0xFF365D4B)
        ),
        typography = Typography(
            headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
            titleLarge = TextStyle(fontSize = 23.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
            bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 30.sp),
            bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 26.sp),
            labelLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
            bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 22.sp)
        ), content = content
    )
}
