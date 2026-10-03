package cn.xiangyu.museum

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

@Composable
fun ChatContent(state: MuseumState, onDraft: (String) -> Unit, onSend: (String?) -> Unit,
                onStop: () -> Unit, onReload: () -> Unit, onNew: () -> Unit,
                onSession: (ChatSessionInfo) -> Unit, onExhibit: (String?) -> Unit,
                modifier: Modifier = Modifier, onMode: (() -> Unit)? = null) {
    var showSessions by remember { mutableStateOf(false) }
    val child = state.audience == Audience.CHILD
    val subject = FirstChapter.relics.firstOrNull { it.id == state.exhibitId }?.title ?: "博物馆主题"
    val list = rememberLazyListState()
    val dragged by list.interactionSource.collectIsDraggedAsState()
    var followLatest by remember(state.session?.id, state.audience) { mutableStateOf(true) }
    LaunchedEffect(dragged) { if (dragged) followLatest = false else if (!list.canScrollForward) followLatest = true }
    LaunchedEffect(state.session?.id, state.audience, state.messages.size) {
        followLatest = true
        if (state.messages.isNotEmpty()) list.scrollToItem(state.messages.lastIndex, Int.MAX_VALUE)
    }
    LaunchedEffect(state.messages.lastOrNull(), state.status, followLatest) {
        if (followLatest && !dragged && state.messages.isNotEmpty()) {
            list.scrollToItem(state.messages.lastIndex, Int.MAX_VALUE)
        }
    }
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(horizontal = Space.medium, vertical = Space.small)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Image(painterResource(R.drawable.abo), null,
                            Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
                        Column(Modifier.weight(1f)) {
                            Text(if (child) "问阿帛" else "文物讲解", style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.semantics { heading() })
                            Text(if (child) "你的文物旅行伙伴" else "从史料与文物中，读懂历史",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { showSessions = true }, enabled = !state.chatBusy) { Text("历史会话") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(subject, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        if (onMode != null) TextButton(onClick = onMode, enabled = !state.chatBusy) {
                            Text("${state.audience?.label} · 切换模式", style = MaterialTheme.typography.labelMedium)
                        }
                        TextButton(onClick = onNew, enabled = !state.chatBusy) { Text("新对话") }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            Box(Modifier.weight(1f)) {
                LazyColumn(state = list, modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Space.medium), verticalArrangement = Arrangement.spacedBy(Space.large)) {
                    if (state.messages.isEmpty()) {
                        item { ChatWelcome(child, state.chatBusy || state.needsRefresh, onDraft) }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(Space.small)) {
                                Text("想聊哪件文物？", style = MaterialTheme.typography.titleSmall)
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                                    FilterChip(selected = state.exhibitId == null, onClick = { onExhibit(null) },
                                        enabled = !state.chatBusy, label = { Text("主题问答") },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            selectedLabelColor = MaterialTheme.colorScheme.primary))
                                    FirstChapter.relics.forEach { relic ->
                                        FilterChip(selected = state.exhibitId == relic.id, onClick = { onExhibit(relic.id) },
                                            enabled = !state.chatBusy, label = { Text(relic.title) },
                                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                selectedLabelColor = MaterialTheme.colorScheme.primary))
                                    }
                                }
                            }
                        }
                    } else itemsIndexed(state.messages) { index, line ->
                        ChatBubble(line, { onSend(it) }, enabled = index == state.messages.lastIndex &&
                            !state.chatBusy && !state.needsRefresh, author = if (child) "阿帛" else "湘遇讲解")
                    }
                }
                if (!followLatest && state.messages.isNotEmpty() && list.canScrollForward) {
                    FilledTonalButton(onClick = { followLatest = true }, modifier = Modifier.align(Alignment.BottomCenter).padding(Space.small)) {
                        Text("回到最新回复")
                    }
                }
            }
            ChatComposer(state, child, onDraft, { onSend(null) }, onStop, onReload)
        }
    }
    if (showSessions) {
        AlertDialog(onDismissRequest = { showSessions = false }, title = { Text("历史会话") },
            text = {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(Space.small)) {
                    if (state.sessions.isEmpty()) item { Text("当前模式暂无历史会话") }
                    itemsIndexed(state.sessions) { _, session ->
                        OutlinedButton(onClick = { onSession(session); showSessions = false }, enabled = !state.chatBusy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch)) {
                            Text(session.title, modifier = Modifier.weight(1f))
                            if (session.id == state.session?.id) Text("当前", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }, confirmButton = { TextButton(onClick = { showSessions = false; onReload() }, enabled = !state.chatBusy) { Text("刷新会话") } },
            dismissButton = { TextButton(onClick = { showSessions = false }) { Text("关闭") } })
    }
}

@Composable
private fun ChatWelcome(child: Boolean, disabled: Boolean, onDraft: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.medium)) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
            Row(Modifier.padding(Space.large), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.small)) {
                    Text(if (child) "你好，我是阿帛" else "让文物讲述它的故事", style = MaterialTheme.typography.titleLarge)
                    Text(if (child) "带着好奇来问我吧。\n我们一起找找文物里的线索。" else "聊聊材料、工艺和历史背景。\n也可以从两件文物的比较开始。",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Image(painterResource(R.drawable.abo), null, Modifier.width(88.dp).height(128.dp))
            }
        }
        Text("从一个问题开始", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val prompts = listOf("比较两件衣服" to "这两件衣服的材料和工艺有什么不同？",
            "纱衣为什么这么轻？" to "素纱单衣为什么这么轻？", "纹样里藏着什么？" to "这件绵袍的纹样有哪些特点？")
        prompts.forEachIndexed { index, (label, question) ->
            Surface(onClick = { onDraft(question) }, enabled = !disabled, shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = Space.medium, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("0${index + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun ChatComposer(state: MuseumState, child: Boolean, onDraft: (String) -> Unit,
                         onSend: () -> Unit, onStop: () -> Unit, onReload: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
        Column(Modifier.fillMaxWidth().padding(Space.medium), verticalArrangement = Arrangement.spacedBy(Space.small)) {
            if (state.status.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                    if (state.chatBusy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(state.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { stateDescription = state.status })
                }
            }
            state.error?.let { MuseumNotice(it, error = true) }
            if (state.needsRefresh) {
                Text("问题已保留，请先刷新核对记录。", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onReload, enabled = !state.chatBusy) { Text("刷新会话") }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                OutlinedTextField(state.draft, onDraft, modifier = Modifier.weight(1f), enabled = !state.chatBusy,
                    label = { Text(if (child) "想问阿帛什么？" else "想了解什么？") }, maxLines = 4,
                    shape = MaterialTheme.shapes.large, textStyle = MaterialTheme.typography.bodyMedium,
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)))
                Button(onClick = if (state.chatBusy) onStop else onSend,
                    enabled = state.chatBusy || (state.draft.isNotBlank() && !state.needsRefresh),
                    modifier = Modifier.widthIn(max = 112.dp).heightIn(min = Space.touch), shape = MaterialTheme.shapes.large,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(if (state.chatBusy) "停止回复" else "发送问题")
                }
            }
        }
    }
}
