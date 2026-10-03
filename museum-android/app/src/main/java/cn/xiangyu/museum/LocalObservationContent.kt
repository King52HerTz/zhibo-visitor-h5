package cn.xiangyu.museum

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun LocalObservationList(audience: Audience, records: List<LocalObservation>, loading: Boolean,
                         message: String?, onNew: () -> Unit, onDelete: (LocalObservation) -> Unit,
                         photoContent: @Composable (LocalObservation) -> Unit, modifier: Modifier = Modifier) {
    var deletion by remember(audience, records) { mutableStateOf<LocalObservation?>(null) }
    val dateFormat = remember { DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.CHINA) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.medium)) {
        Text("我的观察记录", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
        Text(if (audience == Audience.CHILD) "把你看到的小线索，留在自己的手帐里。" else "记录材料、工艺与纹样，也记录自己的疑问。",
            style = MaterialTheme.typography.bodyMedium)
        MuseumButton(onClick = onNew, enabled = !loading) { Text("新建观察记录") }
        message?.let { MuseumNotice(it) }
        if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("正在翻开本机记录…", style = MaterialTheme.typography.bodyMedium)
        } else if (records.isEmpty()) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(Space.medium), verticalArrangement = Arrangement.spacedBy(Space.small)) {
                    Image(painterResource(R.drawable.chapter_journal), contentDescription = null,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1.5f))
                    Text("这一页，等你的发现", style = MaterialTheme.typography.titleLarge)
                    Text("允许拍照时可以留一张照片；不能拍照时，写下观察也一样可以收进手帐。",
                        style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        records.forEach { record ->
            val relic = FirstChapter.relics.first { it.id == record.relicId }
            val date = dateFormat.format(Instant.ofEpochMilli(record.createdAt).atZone(ZoneId.systemDefault()))
            key(record.id) {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.fillMaxWidth().padding(Space.medium), verticalArrangement = Arrangement.spacedBy(Space.small)) {
                        Text(relic.title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                        Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (record.photoName != null) photoContent(record)
                        SelectionContainer { Text(record.note, style = MaterialTheme.typography.bodyLarge) }
                        TextButton(onClick = { deletion = record }, enabled = !loading, modifier = Modifier.heightIn(min = Space.touch)
                            .semantics { contentDescription = "删除${relic.title}的${date}观察记录" }) { Text("删除记录") }
                    }
                }
            }
        }
        MuseumNotice("文字和照片仅保存在这台设备，按账号与模式分别保存，不会上传。卸载应用会移除这些记录，请先自行留存需要的内容。")
        Text("记录参与，不是到馆证明，也不会自动完成探索任务。", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    deletion?.let { record ->
        AlertDialog(onDismissRequest = { deletion = null }, title = { Text("删除这条观察？") },
            text = { Text(if (record.photoName == null) "这条本机文字记录将被删除，无法恢复。" else "这条本机文字记录和照片将被删除，无法恢复。") },
            confirmButton = {
                TextButton(onClick = { deletion = null; onDelete(record) }, enabled = !loading, modifier = Modifier.heightIn(min = Space.touch)) {
                    Text("确认删除", color = MaterialTheme.colorScheme.error)
                }
            }, dismissButton = {
                TextButton(onClick = { deletion = null }, modifier = Modifier.heightIn(min = Space.touch)) { Text("保留记录") }
            })
    }
}

@Composable
fun LocalObservationEditor(audience: Audience, relicId: String, note: String, hasPhoto: Boolean,
                           busy: Boolean, message: String?, onRelic: (String) -> Unit, onNote: (String) -> Unit,
                           onCamera: () -> Unit, onWithoutPhoto: () -> Unit, onSave: () -> Unit,
                           onCancel: () -> Unit, photoContent: @Composable () -> Unit, modifier: Modifier = Modifier) {
    val relic = FirstChapter.relics.first { it.id == relicId }
    val task = FirstChapter.tasks.first { it.relicId == relicId }
    val suggestions = if (relicId == "silk-plain-straight") listOf("我注意到衣料很轻薄。", "我想知道丝纱是怎样织成的。")
        else listOf("纹样让我想到植物的枝叶和花。", "我想知道哪些部分是印的，哪些是画的。")
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.medium)) {
        Text("记录一次观察", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
        Text(if (audience == Audience.CHILD) "阿帛陪你看一看，再用自己的话记下来。" else "从已有资料出发，留下自己的观察。",
            style = MaterialTheme.typography.bodyMedium)
        Text("今天观察哪件文物？", style = MaterialTheme.typography.titleMedium)
        FirstChapter.relics.forEach { option ->
            OutlinedButton(onClick = { onRelic(option.id) }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch).semantics { selected = relicId == option.id },
                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (relicId == option.id)
                    MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)) { Text(option.title) }
        }
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.padding(Space.medium), verticalArrangement = Arrangement.spacedBy(Space.small)) {
                Text("观察线索 · 离线可读", style = MaterialTheme.typography.labelLarge)
                Text(FirstChapter.text(relic, audience), style = MaterialTheme.typography.bodyLarge)
                Text(if (audience == Audience.CHILD) task.childPrompt else task.adultPrompt,
                    style = MaterialTheme.typography.titleMedium)
            }
        }
        Text("先看现场是否允许拍照", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        Text("遵守展厅提示，隔着展柜观察。禁止拍照、展品未展出或没有相机时，可以直接记录文字观察。",
            style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onCamera, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch)) {
            Text("允许拍照时拍一张")
        }
        if (hasPhoto) {
            photoContent()
            Text("照片仅保存在这台设备，确认记录后才收进手帐。", style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick = onWithoutPhoto, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch)) {
            Text(if (hasPhoto) "改用文字观察" else "只做观察")
        }
        Text("写一句自己的发现", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(note, { onNote(it.take(600)) }, enabled = !busy, label = { Text("我的发现") },
            modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 6, shape = MaterialTheme.shapes.large,
            supportingText = { Text("${note.length} / 600 字") },
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)))
        Text("也可以从一句提示开始，再补充你的观察：", style = MaterialTheme.typography.bodySmall)
        suggestions.forEach { suggestion ->
            OutlinedButton(onClick = { onNote(suggestion) }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch)) { Text(suggestion) }
        }
        message?.let { MuseumNotice(it) }
        MuseumButton(onClick = onSave, enabled = note.isNotBlank() && note.length <= 600 && !busy) {
            Text(if (busy) "正在收好观察…" else "确认收进手帐")
        }
        OutlinedButton(onClick = onCancel, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch)) {
            Text("返回观察记录")
        }
        Text("记录参与，不是到馆证明，也不会自动完成探索任务。", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
