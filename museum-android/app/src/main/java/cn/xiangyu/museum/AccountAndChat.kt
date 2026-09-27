package cn.xiangyu.museum

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun AccountContent(state: MuseumState, onSubmit: (String, String, String, Boolean) -> Unit,
                   modifier: Modifier = Modifier) {
    var register by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var endpoint by remember(state.endpoint) { mutableStateOf(state.endpoint) }
    var connectionSettings by remember(state.endpoint) { mutableStateOf(state.endpoint.isBlank()) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.medium)) {
        AuthHero()
        if (state.restoring) {
            Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = Space.small / 2) {
                Row(Modifier.padding(Space.large), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.medium)) {
                    CircularProgressIndicator(Modifier.size(Space.large))
                    Text("正在找回你的旅行手帐", style = MaterialTheme.typography.bodyLarge)
                }
            }
        } else {
            Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface, tonalElevation = Space.small / 2,
                shadowElevation = Space.small / 2) {
                Column(Modifier.padding(horizontal = Space.large, vertical = Space.medium),
                    verticalArrangement = Arrangement.spacedBy(Space.small + Space.small / 2)) {
                    AuthModeSwitcher(register = register, enabled = !state.authBusy, onChange = {
                        register = it
                        password = ""
                    })
                    Column(verticalArrangement = Arrangement.spacedBy(Space.small / 2)) {
                        Text(if (register) "创建你的旅行手帐" else "欢迎回来",
                            modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                        Text(if (register) "只需账号和密码，稍后再选择儿童或成人模式"
                            else "登录后继续上次的故事和对话",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    MuseumField(username, { username = it }, "账号", enabled = !state.authBusy)
                    MuseumField(password, { password = it }, "密码", password = true, enabled = !state.authBusy)
                    state.error?.let { MuseumNotice(it, error = true) }
                    MuseumButton(onClick = { onSubmit(endpoint, username, password, register); password = "" },
                        enabled = !state.authBusy && username.isNotBlank() && password.isNotBlank()) {
                        if (state.authBusy) {
                            CircularProgressIndicator(Modifier.size(Space.large), strokeWidth = Space.small / 4)
                            Spacer(Modifier.width(Space.small))
                        }
                        Text(if (state.authBusy) "正在连接…" else if (register) "注册并开始旅行" else "登录并开始旅行")
                    }
                }
            }
            TextButton(onClick = { connectionSettings = !connectionSettings }, enabled = !state.authBusy,
                modifier = Modifier.align(Alignment.End).heightIn(min = Space.touch)
                    .semantics { stateDescription = if (connectionSettings) "已展开" else "已收起" }) {
                Text(if (connectionSettings) "收起连接设置" else "连接设置  ›")
            }
            if (connectionSettings) {
                Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.padding(Space.medium), verticalArrangement = Arrangement.spacedBy(Space.small)) {
                        MuseumField(endpoint, { endpoint = it }, "服务地址", enabled = !state.authBusy)
                        Text(if (BuildConfig.DEBUG) "模拟器使用 10.0.2.2；真机填写电脑的局域网地址。"
                            else "请使用 HTTPS 服务地址。", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text("小朋友请由成人协助注册 · 无需填写真实姓名、年龄或学校",
                modifier = Modifier.align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AuthHero(modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth().heightIn(min = Space.touch * 3),
        shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(start = Space.large, top = Space.medium, bottom = Space.medium),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.small)) {
                Text("湘遇 · 文物旅行记", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                Text("从一件文物，\n走进一段历史", modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLarge)
                Text("阿帛在这里等你出发", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Image(painterResource(R.drawable.abo), contentDescription = "阿帛挥手邀请你开始文物旅行",
                modifier = Modifier.width(Space.touch * 2 + Space.medium).height(Space.touch * 3),
                contentScale = ContentScale.Fit)
        }
    }
}

@Composable
private fun AuthModeSwitcher(register: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit,
                             modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(Space.small / 2), horizontalArrangement = Arrangement.spacedBy(Space.small / 2)) {
            listOf(false to "登录", true to "注册").forEach { (value, label) ->
                TextButton(onClick = { onChange(value) }, enabled = enabled,
                    modifier = Modifier.weight(1f).heightIn(min = Space.touch)
                        .semantics { selected = register == value },
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = if (register == value) MaterialTheme.colorScheme.surface
                            else androidx.compose.ui.graphics.Color.Transparent,
                        contentColor = if (register == value) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant)) {
                    Text(label)
                }
            }
        }
    }
}

@Composable
fun ChatContent(state: MuseumState, onDraft: (String) -> Unit, onSend: (String?) -> Unit,
                onStop: () -> Unit, onReload: () -> Unit, onNew: () -> Unit,
                onSession: (ChatSessionInfo) -> Unit, onExhibit: (String?) -> Unit,
                modifier: Modifier = Modifier) {
    var showSessions by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.medium)) {
        Text("问阿帛", style = MaterialTheme.typography.headlineLarge)
        Text("${state.audience?.label} · 独立会话", style = MaterialTheme.typography.titleMedium)
        Text("AI 需要联网核验资料；阅读首章不需要联网。", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.small)) {
            TextButton(onClick = { showSessions = !showSessions }, enabled = !state.chatBusy) { Text("历史会话") }
            TextButton(onClick = onNew, enabled = !state.chatBusy) { Text("新对话") }
            TextButton(onClick = onReload, enabled = !state.chatBusy) { Text("刷新会话") }
        }
        if (showSessions) {
            if (state.sessions.isEmpty()) Text("当前模式暂无历史会话")
            state.sessions.forEach { session ->
                OutlinedButton(onClick = { onSession(session); showSessions = false }, enabled = !state.chatBusy) {
                    Text(session.title)
                }
            }
        }
        Text("当前文物：${FirstChapter.relics.firstOrNull { it.id == state.exhibitId }?.title ?: "博物馆主题"}",
            style = MaterialTheme.typography.bodyMedium)
        if (state.messages.isEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                TextButton(onClick = { onExhibit(null) }, enabled = !state.chatBusy) { Text("主题问答") }
                FirstChapter.relics.forEach { relic ->
                    TextButton(onClick = { onExhibit(relic.id) }, enabled = !state.chatBusy,
                        modifier = Modifier.weight(1f)) { Text(relic.title) }
                }
            }
            MuseumNotice("你可以问用途、比较或故事。资料不足时，阿帛会说明缺口。")
        }
        state.messages.forEachIndexed { index, line -> ChatBubble(line, { onSend(it) },
            enabled = index == state.messages.lastIndex && !state.chatBusy && !state.needsRefresh) }
        if (state.status.isNotBlank()) MuseumNotice(state.status)
        state.error?.let { MuseumNotice(it, error = true) }
        MuseumField(state.draft, onDraft, "想问阿帛什么？", enabled = !state.chatBusy, singleLine = false)
        if (state.chatBusy) MuseumButton(onStop) { Text("停止回复") }
        else MuseumButton(onClick = { onSend(null) }, enabled = state.draft.isNotBlank() && !state.needsRefresh) { Text("发送问题") }
        if (state.needsRefresh) Text("问题已保留。请先刷新核对服务器记录，不会自动重复发送。", style = MaterialTheme.typography.bodySmall)
    }
}

@Preview(showBackground = true)
@Composable
private fun AccountPreview() { MuseumTheme { AccountContent(MuseumState(restoring = false), { _, _, _, _ -> }) } }
