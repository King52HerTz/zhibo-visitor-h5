package cn.xiangyu.museum

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun MuseumButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
                 content: @Composable RowScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val sheet = ImageBitmap.imageResource(R.drawable.button_sprites)
    val row = if (!enabled) 2 else if (pressed) 1 else 0
    val painter = remember(sheet, row) {
        BitmapPainter(sheet, srcOffset = IntOffset(24, row * (sheet.height / 3)),
            srcSize = IntSize(sheet.width - 48, sheet.height / 3))
    }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Image(painter, contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch), enabled = enabled,
            interactionSource = interaction,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSecondary,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant), content = content)
    }
}

@Composable
fun MuseumField(value: String, onValueChange: (String) -> Unit, label: String,
                modifier: Modifier = Modifier, enabled: Boolean = true, password: Boolean = false,
                singleLine: Boolean = true) {
    var passwordVisible by remember { mutableStateOf(false) }
    OutlinedTextField(value, onValueChange, modifier = modifier.fillMaxWidth(), enabled = enabled,
        label = { Text(label) }, singleLine = singleLine, maxLines = if (singleLine) 1 else 5,
        shape = MaterialTheme.shapes.large,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
        trailingIcon = if (password) { { TextButton(onClick = { passwordVisible = !passwordVisible }, enabled = enabled) {
            Text(if (passwordVisible) "隐藏" else "显示", style = MaterialTheme.typography.labelMedium)
        } } } else null,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Text),
        visualTransformation = if (password && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None)
}

@Composable
fun MuseumNotice(text: String, modifier: Modifier = Modifier, error: Boolean = false) {
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant) {
        Text(text, modifier = Modifier.padding(Space.medium), style = MaterialTheme.typography.bodyMedium,
            color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ChatBubble(line: ChatLine, onOption: (String) -> Unit, modifier: Modifier = Modifier,
               enabled: Boolean = true) {
    var expanded by remember(line) { mutableStateOf(false) }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = if (line.human) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(Space.medium), verticalArrangement = Arrangement.spacedBy(Space.small)) {
            Text(if (line.human) "你" else "阿帛", style = MaterialTheme.typography.labelLarge)
            Text(line.text.ifEmpty { "正在核验资料…" }, style = MaterialTheme.typography.bodyLarge)
            if (line.incomplete) Text("回复未完成", style = MaterialTheme.typography.bodySmall)
            line.options.forEach { option ->
                OutlinedButton(onClick = { onOption(option) }, enabled = enabled,
                    modifier = Modifier.heightIn(min = Space.touch)) { Text(option) }
            }
            if (line.evidence.isNotEmpty()) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "收起引用" else "展开引用 · ${line.evidence.size} 条")
                }
                if (expanded) line.evidence.forEachIndexed { index, evidence ->
                    Text("[${index + 1}] ${evidence.filename}", style = MaterialTheme.typography.titleSmall)
                    Text(evidence.text, style = MaterialTheme.typography.bodyMedium)
                    if (evidence.url != null) TextButton(onClick = { uriHandler.openUri(evidence.url) }) { Text("打开来源网页") }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ComponentsPreview() { MuseumTheme {
    Column(Modifier.padding(Space.medium), verticalArrangement = Arrangement.spacedBy(Space.medium)) {
        MuseumField("", {}, "账号")
        MuseumButton({}) { Text("开始旅行") }
        MuseumNotice("联网后，阿帛会核验知识库资料。")
        ChatBubble(ChatLine(false, "从衣服的纹样开始观察吧。"), {})
    }
} }
