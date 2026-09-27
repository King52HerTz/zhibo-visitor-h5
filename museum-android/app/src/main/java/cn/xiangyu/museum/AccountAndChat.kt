package cn.xiangyu.museum

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("湘遇", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
            Text("文物旅行记 / 第一页", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.small)) {
                Text("翻开你的\n文物旅行记", style = MaterialTheme.typography.headlineLarge)
                Text("和阿帛一起，\n发现两千年前的生活。", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Image(painterResource(R.drawable.abo), contentDescription = "阿帛挥手邀请你开始旅行",
                modifier = Modifier.weight(0.8f).height(Space.touch * 3))
        }
        if (state.restoring) {
            CircularProgressIndicator()
            Text("正在恢复登录")
        } else {
            Surface(shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(Space.small / 8, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))) {
                Column(Modifier.padding(Space.large), verticalArrangement = Arrangement.spacedBy(Space.medium)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                        listOf(false to "登录", true to "注册").forEach { (value, label) ->
                            TextButton(onClick = { register = value; password = "" }, enabled = !state.authBusy,
                                modifier = Modifier.weight(1f).heightIn(min = Space.touch)
                                    .semantics { selected = register == value },
                                colors = ButtonDefaults.textButtonColors(
                                    containerColor = if (register == value) MaterialTheme.colorScheme.surfaceVariant
                                        else androidx.compose.ui.graphics.Color.Transparent)) { Text(label) }
                        }
                    }
                    Text(if (register) "领取一本属于你的手帐" else "欢迎回到你的旅行手帐",
                        style = MaterialTheme.typography.titleMedium)
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
                    Text("阅读故事 · 问阿帛 · 留下你的发现", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("小朋友请由成人协助注册。无需填写真实姓名、年龄或学校。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { connectionSettings = !connectionSettings }, enabled = !state.authBusy,
                modifier = Modifier.semantics { stateDescription = if (connectionSettings) "已展开" else "已收起" }) {
                Text(if (connectionSettings) "收起连接设置" else "连接设置")
            }
            if (connectionSettings) {
                MuseumField(endpoint, { endpoint = it }, "服务地址", enabled = !state.authBusy)
                Text(if (BuildConfig.DEBUG) "模拟器使用 10.0.2.2 访问电脑；真机填写可达的服务地址。HTTP 仅用于可信开发网络。"
                    else "请使用 HTTPS 服务地址。", style = MaterialTheme.typography.bodySmall)
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
