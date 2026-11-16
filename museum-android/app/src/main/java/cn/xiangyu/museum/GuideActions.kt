package cn.xiangyu.museum

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

private val actionShape = RoundedCornerShape(11.dp)

@Composable
internal fun GuideAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
                         secondary: Boolean = false, enabled: Boolean = true, end: String? = null) {
    val height = modifier.heightIn(min = 48.dp)
    val content: @Composable RowScope.() -> Unit = {
        Text(label, style = MaterialTheme.typography.labelLarge)
        end?.let { Spacer(Modifier.width(8.dp)); Text(it, style = MaterialTheme.typography.titleMedium) }
    }
    if (secondary) OutlinedButton(onClick, height, enabled, shape = actionShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), content = content)
    else Button(onClick, height, enabled, shape = actionShape, content = content)
}

@Composable
internal fun GuideRowAction(label: String, description: String? = null, onClick: () -> Unit,
                            modifier: Modifier = Modifier, artwork: Int? = null, enabled: Boolean = true) {
    OutlinedCard(onClick, modifier.fillMaxWidth(), enabled = enabled, shape = actionShape,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (artwork != null) Image(painterResource(artwork), null, Modifier.size(42.dp).clip(RoundedCornerShape(7.dp)),
                contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                description?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}
