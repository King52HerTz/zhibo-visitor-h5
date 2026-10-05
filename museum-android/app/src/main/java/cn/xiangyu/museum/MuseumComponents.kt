package cn.xiangyu.museum

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import java.net.URI
import java.net.URISyntaxException

@Composable
fun MuseumButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
                 content: @Composable RowScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val sheet = ImageBitmap.imageResource(R.drawable.button_sprites_v2)
    val row = if (!enabled) 2 else if (pressed) 1 else 0
    val sourceTop = intArrayOf(110, 402, 697)[row]
    val painter = remember(sheet, row) {
        BitmapPainter(sheet, srcOffset = IntOffset(48, sourceTop),
            srcSize = IntSize(sheet.width - 96, 245))
    }
    Box(modifier.fillMaxWidth().heightIn(min = Space.touch), contentAlignment = Alignment.Center) {
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
               enabled: Boolean = true, author: String = "阿帛") {
    var expanded by remember(line.evidence) { mutableStateOf(false) }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    Column(modifier.fillMaxWidth().padding(start = if (line.human) 40.dp else 0.dp,
        end = if (line.human) 0.dp else 16.dp), horizontalAlignment = if (line.human) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(Space.small)) {
        Text(if (line.human) "你" else author, style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp,
            bottomStart = if (line.human) 20.dp else 4.dp, bottomEnd = if (line.human) 4.dp else 20.dp),
            color = if (line.human) MaterialTheme.colorScheme.primary else Color.White,
            contentColor = if (line.human) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            border = if (line.human) null else BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))) {
            Column(Modifier.padding(Space.medium), verticalArrangement = Arrangement.spacedBy(Space.small)) {
                SelectionContainer { Text(line.text.ifEmpty { "正在核验资料…" }, style = MaterialTheme.typography.bodyLarge) }
                if (line.incomplete) Text("回复未完成", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                line.options.forEach { option ->
                    OutlinedButton(onClick = { onOption(option) }, enabled = enabled,
                        modifier = Modifier.heightIn(min = Space.touch)) { Text(option) }
                }
                if (line.evidence.isNotEmpty()) {
                    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.semantics {
                        stateDescription = if (expanded) "已展开" else "已收起"
                    }) {
                        Text(if (expanded) "收起引用" else "展开引用 · ${line.evidence.size} 条")
                    }
                    if (expanded) Text("资料来源", modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (expanded) line.evidence.forEachIndexed { index, evidence ->
                        var sourceUnavailable by remember(evidence) { mutableStateOf(false) }
                        val sourceUrl = evidence.url?.takeIf { candidate ->
                            try {
                                val uri = URI(candidate)
                                uri.scheme == "https" && !uri.host.isNullOrBlank()
                            } catch (_: URISyntaxException) { false }
                        }
                        Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))) {
                            Column(Modifier.padding(Space.medium), verticalArrangement = Arrangement.spacedBy(Space.small)) {
                                Text("[${index + 1}] ${evidence.filename}", style = MaterialTheme.typography.titleSmall)
                                SelectionContainer { Text(evidence.text, style = MaterialTheme.typography.bodyMedium) }
                                if (sourceUrl != null) TextButton(onClick = {
                                    sourceUnavailable = false
                                    try { uriHandler.openUri(sourceUrl) }
                                    catch (_: IllegalArgumentException) { sourceUnavailable = true }
                                }, modifier = Modifier.heightIn(min = Space.touch)) {
                                    Text(if (sourceUnavailable) "重试打开来源网页" else "打开来源网页")
                                }
                                if (sourceUnavailable || (evidence.url != null && sourceUrl == null)) Text(
                                    if (sourceUnavailable) "网页暂时无法打开，引用摘录仍可阅读。"
                                    else "网页链接不可用，引用摘录仍可阅读。",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
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
