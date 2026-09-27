package cn.xiangyu.museum

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

@Composable
fun ChapterProgress(progress: TaskProgress, modifier: Modifier = Modifier) {
    val count = FirstChapter.tasks.count { it.id in progress.completed }
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(Space.medium), verticalArrangement = Arrangement.spacedBy(Space.small)) {
            Text("衣服里的秘密 · $count / 2 个发现", style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(progress = { count / 2f }, modifier = Modifier.fillMaxWidth())
            Text(if (count == 2) "首章纪念页已收进手帐" else "做两个小观察，收下一页汉代旅行纪念",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TaskSticker(index: Int, completed: Boolean, modifier: Modifier = Modifier) {
    val sheet = ImageBitmap.imageResource(R.drawable.observation_sprites)
    val width = sheet.width / 2
    val height = sheet.height / 2
    val painter = remember(sheet, index, completed) {
        BitmapPainter(sheet, IntOffset(index * width, if (completed) 0 else height), IntSize(width, height))
    }
    Image(painter, contentDescription = null, modifier = modifier)
}

@Composable
fun ObservationContent(task: ObservationTask, audience: Audience, state: ProgressState,
                       onComplete: (String) -> Unit, modifier: Modifier = Modifier) {
    var choice by rememberSaveable(task.id) { mutableStateOf<Int?>(null) }
    val complete = task.id in state.progress.completed
    val index = FirstChapter.tasks.indexOf(task)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.medium)) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(Modifier.fillMaxWidth().padding(Space.medium), verticalAlignment = Alignment.CenterVertically) {
                TaskSticker(index, complete, Modifier.size(Space.touch * 2))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.small)) {
                    Text("观察 ${index + 1} / 衣服里的秘密",
                        style = MaterialTheme.typography.labelMedium)
                    Text(task.title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                    Text(if (complete) "这个发现已经收好了" else "慢慢看，不用记年份", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        val relic = FirstChapter.relics.first { it.id == task.relicId }
        Text(relic.title, style = MaterialTheme.typography.titleLarge)
        Text(FirstChapter.text(relic, audience), style = MaterialTheme.typography.bodyLarge)
        HorizontalDivider()
        Text(if (audience == Audience.CHILD) task.childPrompt else task.adultPrompt,
            style = MaterialTheme.typography.titleLarge)
        task.options.forEachIndexed { option, label ->
            OutlinedButton(onClick = { choice = option }, enabled = !complete && !state.loading,
                modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch).semantics { selected = choice == option },
                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (choice == option)
                    MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)) {
                Text(label)
            }
        }
        if (choice != null && choice != task.answer && !complete) {
            Text("再看看上面的故事，线索就在衣料和纹样里。可以重新选择。",
                style = MaterialTheme.typography.bodyMedium)
        }
        MuseumButton(onClick = { onComplete(task.id) }, enabled = choice == task.answer && !complete && !state.loading) {
            Text(if (complete) "已收进手帐" else "把发现收进手帐")
        }
        state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Text("馆内观察请隔着展柜；未到馆也可阅读故事完成。", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun JournalContent(audience: Audience, state: ProgressState, onExplore: () -> Unit,
                   onSync: () -> Unit, modifier: Modifier = Modifier) {
    val unlocked = FirstChapter.tasks.all { it.id in state.progress.completed }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.medium)) {
        Text("我的旅行手帐", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
        Text(if (audience == Audience.CHILD) "每一个小发现，都值得留下来。" else "把观察连成故事，把故事留在手帐。",
            style = MaterialTheme.typography.bodyMedium)
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.padding(Space.medium), verticalArrangement = Arrangement.spacedBy(Space.small)) {
                Text("第一章 · 衣服里的秘密", style = MaterialTheme.typography.titleLarge)
                Image(painterResource(R.drawable.chapter_journal), contentDescription = "以素纱单衣、花叶纹样和阿帛组成的原创旅行手帐插画",
                    modifier = Modifier.fillMaxWidth().aspectRatio(1.5f), alpha = if (unlocked) 1f else 0.55f)
                Text(if (unlocked) "首章纪念页 · 已获得" else "再完成两个观察，点亮这一页",
                    style = MaterialTheme.typography.titleMedium)
                Text("主题插画 · 记录参与，不是到馆证明", style = MaterialTheme.typography.bodySmall)
            }
        }
        ChapterProgress(state.progress)
        FirstChapter.tasks.forEachIndexed { index, task ->
            val done = task.id in state.progress.completed
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                TaskSticker(index, done, Modifier.size(Space.touch + Space.medium))
                Column(Modifier.weight(1f)) {
                    Text(task.title, style = MaterialTheme.typography.titleMedium)
                    Text(if (done) "已完成" else "等你发现", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text(when {
            state.loading -> "正在翻开手帐…"
            state.syncing -> "正在同步手帐…"
            state.progress.pending.isNotEmpty() -> "${state.progress.pending.size} 个发现已保存在本机，等待同步"
            state.message != null -> state.message
            else -> "手帐已同步 · ${audience.label}独立保存"
        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onSync, enabled = !state.loading && !state.syncing,
            modifier = Modifier.heightIn(min = Space.touch)) { Text("同步手帐") }
        MuseumButton(onClick = onExplore) { Text(if (unlocked) "回看衣服里的秘密" else "继续探索衣服里的秘密") }
    }
}
