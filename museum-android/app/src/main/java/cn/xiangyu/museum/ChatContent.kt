package cn.xiangyu.museum

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.unit.dp

@Composable
fun ChatContent(state: MuseumState, onDraft: (String) -> Unit, onSend: (String?) -> Unit,
                onStop: () -> Unit, onReload: () -> Unit, onNew: () -> Unit,
                onSession: (ChatSessionInfo) -> Unit, onExhibit: (String?) -> Unit,
                modifier: Modifier = Modifier, onMode: (() -> Unit)? = null) {
    var showSessions by remember { mutableStateOf(false) }
    val child = state.audience == Audience.CHILD
    val subject = FirstChapter.relics.firstOrNull { it.id == state.exhibitId }?.title ?: "博物馆主题"
    val hasFeedback = state.status.isNotBlank() || state.error != null || state.needsRefresh
    val lastItem = (if (state.messages.isEmpty()) 1 else state.messages.lastIndex) + if (hasFeedback) 1 else 0
    val list = rememberLazyListState()
    val dragged by list.interactionSource.collectIsDraggedAsState()
    var followLatest by remember(state.session?.id, state.audience) { mutableStateOf(true) }
    LaunchedEffect(dragged) { if (dragged) followLatest = false else if (!list.canScrollForward) followLatest = true }
    LaunchedEffect(state.session?.id, state.audience) {
        followLatest = true
        if (state.messages.isNotEmpty() || hasFeedback) list.scrollToItem(lastItem, Int.MAX_VALUE)
        else list.scrollToItem(0)
    }
    LaunchedEffect(state.messages.size) {
        if (state.messages.lastOrNull()?.human == true) followLatest = true
        if (followLatest && (state.messages.isNotEmpty() || hasFeedback)) list.scrollToItem(lastItem, Int.MAX_VALUE)
    }
    LaunchedEffect(state.messages.lastOrNull(), state.status, state.error, state.needsRefresh, followLatest) {
        if (followLatest && !dragged && (state.messages.isNotEmpty() || hasFeedback)) {
            list.scrollToItem(lastItem, Int.MAX_VALUE)
        }
    }
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 360.dp || maxWidth < 260.dp * LocalDensity.current.fontScale
        val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        Column(Modifier.widthIn(max = 760.dp).fillMaxSize().align(Alignment.TopCenter)) {
            if (compact) {
                FlowRow(Modifier.fillMaxWidth().padding(horizontal = Space.medium, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(Space.small), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (child) "问阿帛" else "文物讲解", style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.align(Alignment.CenterVertically).semantics { heading() })
                    Text(subject, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).align(Alignment.CenterVertically), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!keyboardOpen) {
                        if (onMode != null) TextButton(onClick = onMode, enabled = !state.chatBusy) { Text("切换模式") }
                        TextButton(onClick = { showSessions = true }, enabled = !state.chatBusy) { Text("历史会话") }
                        TextButton(onClick = onNew, enabled = !state.chatBusy) { Text("新对话") }
                    }
                }
            } else Surface(color = MaterialTheme.colorScheme.surface) {
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
            Box(Modifier.weight(1f).clipToBounds()) {
                LazyColumn(state = list, modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Space.medium), verticalArrangement = Arrangement.spacedBy(Space.large)) {
                    if (state.messages.isEmpty()) {
                        item { ChatWelcome(child, compact, state.exhibitId, state.chatBusy || state.needsRefresh, onDraft) }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(Space.small)) {
                                Text("想聊哪件文物？", style = MaterialTheme.typography.titleSmall)
                                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.small),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    FilterChip(selected = state.exhibitId == null, onClick = { onExhibit(null) },
                                        enabled = !state.chatBusy, label = { Text("主题问答") }, modifier = Modifier.heightIn(min = 48.dp),
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            selectedLabelColor = MaterialTheme.colorScheme.primary))
                                    FirstChapter.relics.forEach { relic ->
                                        FilterChip(selected = state.exhibitId == relic.id, onClick = { onExhibit(relic.id) },
                                            enabled = !state.chatBusy, label = { Text(relic.title) }, modifier = Modifier.heightIn(min = 48.dp),
                                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                selectedLabelColor = MaterialTheme.colorScheme.primary))
                                    }
                                }
                            }
                        }
                    } else itemsIndexed(state.messages) { index, line ->
                        ChatBubble(line, { followLatest = true; onSend(it) }, enabled = index == state.messages.lastIndex &&
                            !state.chatBusy && !state.needsRefresh, author = if (child) "阿帛" else "湘遇讲解")
                    }
                    if (hasFeedback) item {
                        val errorOnly = state.error != null && !state.needsRefresh
                        Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                            color = if (errorOnly) MaterialTheme.colorScheme.errorContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            contentColor = if (errorOnly) MaterialTheme.colorScheme.onErrorContainer
                                else MaterialTheme.colorScheme.onSurface,
                            border = BorderStroke(1.dp, (if (errorOnly) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary).copy(alpha = 0.14f))) {
                            Column(Modifier.padding(Space.medium), verticalArrangement = Arrangement.spacedBy(Space.small)) {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                                    if (state.chatBusy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Text(when {
                                        state.chatBusy -> "正在处理"
                                        state.needsRefresh -> "会话需要核对"
                                        state.error != null -> "暂时遇到问题"
                                        else -> "会话提示"
                                    }, modifier = Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleSmall)
                                }
                                if (state.status.isNotBlank()) Text(state.status, style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                                state.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                                if (state.needsRefresh) {
                                    Text("问题已保留，请先刷新核对记录。", style = MaterialTheme.typography.bodyMedium)
                                    FilledTonalButton(onClick = onReload, enabled = !state.chatBusy,
                                        modifier = Modifier.heightIn(min = Space.touch),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            contentColor = MaterialTheme.colorScheme.primary)) { Text("刷新会话") }
                                }
                            }
                        }
                    }
                }
                if (!followLatest && state.messages.isNotEmpty() && list.canScrollForward) {
                    FilledTonalButton(onClick = { followLatest = true }, modifier = Modifier.align(Alignment.BottomCenter).padding(Space.small),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            contentColor = MaterialTheme.colorScheme.primary)) {
                        Text("回到最新回复")
                    }
                }
            }
            ChatComposer(state, child, compact, onDraft, { followLatest = true; onSend(null) }, onStop)
        }
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
private fun ChatWelcome(child: Boolean, compact: Boolean, exhibitId: String?, disabled: Boolean, onDraft: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) Space.small else Space.medium)) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
            if (compact) Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(Space.small)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                    Text(if (child) "你好，我是阿帛" else "从文物开始聊", modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall)
                    Image(painterResource(R.drawable.abo), null, Modifier.width(40.dp).height(56.dp))
                }
            } else Row(Modifier.padding(Space.large), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.small)) {
                    Text(if (child) "你好，我是阿帛" else "让文物讲述它的故事", style = MaterialTheme.typography.titleLarge)
                    Text(if (child) "带着好奇来问我吧。\n我们一起找找文物里的线索。" else "聊聊材料、工艺和历史背景。\n也可以从两件文物的比较开始。",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Image(painterResource(R.drawable.abo), null, Modifier.width(88.dp).height(128.dp))
            }
        }
        if (!compact) Text("从一个问题开始", style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        val prompts = when (exhibitId) {
            "silk-plain-straight" -> listOf("纱衣为什么这么轻？" to "直裾素纱单衣为什么这么轻？",
                "衣领有什么特点？" to "直裾素纱单衣的衣领和衣襟有什么特点？",
                "丝纱怎样织成？" to "直裾素纱单衣的丝纱是怎样织成的？")
            "silk-painted-robe" -> listOf("纹样里藏着什么？" to "印花敷彩纱丝绵袍的纹样有哪些特点？",
                "印花和敷彩怎么做？" to "印花敷彩纱丝绵袍上的印花和敷彩是怎样制作的？",
                "先观察哪里？" to "观察印花敷彩纱丝绵袍时，可以先从哪些细节入手？")
            else -> listOf("比较两件衣服" to "这两件衣服的材料和工艺有什么不同？",
                "纱衣为什么这么轻？" to "素纱单衣为什么这么轻？", "纹样里藏着什么？" to "这件绵袍的纹样有哪些特点？")
        }
        prompts.forEachIndexed { index, (label, question) ->
            Surface(onClick = { onDraft(question) }, enabled = !disabled, shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.heightIn(min = Space.touch)
                    .padding(horizontal = Space.medium, vertical = if (compact) Space.small else 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!compact) Text("0${index + 1}", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    if (!compact) Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun ChatComposer(state: MuseumState, child: Boolean, compact: Boolean, onDraft: (String) -> Unit,
                         onSend: () -> Unit, onStop: () -> Unit) {
    val canSend = !state.chatBusy && !state.needsRefresh && state.draft.isNotBlank()
    val prompt = if (child) "想问阿帛什么？" else "想了解什么？"
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.medium, vertical = if (compact) 4.dp else Space.medium),
            verticalArrangement = Arrangement.spacedBy(Space.small)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                OutlinedTextField(state.draft, onDraft, modifier = Modifier.weight(1f).semantics {
                    if (compact) contentDescription = prompt
                }, enabled = !state.chatBusy,
                    label = if (compact) null else ({ Text(prompt) }),
                    placeholder = if (compact) ({ Text(prompt,
                        maxLines = 1, overflow = TextOverflow.Ellipsis) }) else null, maxLines = if (compact) 1 else 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                    shape = MaterialTheme.shapes.large, textStyle = MaterialTheme.typography.bodyMedium,
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)))
                Button(onClick = if (state.chatBusy) onStop else onSend,
                    enabled = state.chatBusy || canSend,
                    modifier = Modifier.widthIn(max = 112.dp).heightIn(min = Space.touch).semantics {
                        contentDescription = if (state.chatBusy) "停止回复" else "发送问题"
                    }, shape = MaterialTheme.shapes.large,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(if (state.chatBusy) "停止" else "发送")
                }
            }
        }
    }
}
