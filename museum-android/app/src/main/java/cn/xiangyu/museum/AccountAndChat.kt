package cn.xiangyu.museum

import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun LoginPage(state: MuseumState, modifier: Modifier, keyboardVisible: Boolean,
                       onSubmit: (String, String, String, Boolean) -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val footer = !keyboardVisible && maxHeight >= 620.dp
        if (footer) Image(painterResource(R.drawable.zhibo_motif), null,
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(108.dp),
            alignment = Alignment.BottomCenter, contentScale = ContentScale.Crop)
        AccountContent(state, onSubmit, Modifier.fillMaxSize()
            .padding(bottom = if (footer) 100.dp else 0.dp).verticalScroll(rememberScrollState())
            .padding(horizontal = Space.large, vertical = Space.medium))
    }
}

@Composable
fun AccountContent(state: MuseumState, onSubmit: (String, String, String, Boolean) -> Unit,
                   modifier: Modifier = Modifier) {
    var register by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var endpoint by remember(state.endpoint) { mutableStateOf(state.endpoint) }
    var connectionSettings by remember(state.endpoint) { mutableStateOf(state.endpoint.isBlank()) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.medium)) {
        BrandLockup(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 16.dp))
        if (state.restoring) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.medium), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp))
                Text("正在恢复登录")
            }
        } else {
            Text(if (register) "注册账号" else "登录", Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineLarge)
            MuseumField(username, { username = it }, "账号", enabled = !state.authBusy)
            MuseumField(password, { password = it }, "密码", password = true, enabled = !state.authBusy)
            state.error?.let { MuseumNotice(it, error = true) }
            MuseumButton(onClick = { onSubmit(endpoint, username, password, register); password = "" },
                enabled = !state.authBusy && username.isNotBlank() && password.isNotEmpty() && endpoint.isNotBlank()) {
                Text(if (state.authBusy) "正在连接…" else if (register) "注册" else "登录")
            }
            TextButton(onClick = { register = !register; password = "" }, enabled = !state.authBusy,
                modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp)) {
                Text(if (register) "返回登录" else "注册账号")
            }
            if (register) Text("儿童请由成人协助注册。", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { connectionSettings = !connectionSettings }, enabled = !state.authBusy) {
                Text(if (connectionSettings) "收起连接设置" else "连接设置")
            }
            if (connectionSettings) {
                MuseumField(endpoint, { endpoint = it }, "知识库服务地址", enabled = !state.authBusy)
                Text("填写网页版知识库所在的服务地址，使用同一账号登录。",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun BrandLockup(modifier: Modifier = Modifier, sealSize: Int = 56) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.small)) {
        Image(painterResource(R.drawable.zhibo_seal), null, Modifier.size(sealSize.dp))
        Text("智博导览", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
internal fun ModeSelection(enabled: Boolean, onSelect: (Audience) -> Unit, onLogout: () -> Unit) {
    BrandLockup(Modifier.fillMaxWidth().padding(vertical = Space.medium))
    Text("选择导览模式", Modifier.fillMaxWidth().semantics { heading() },
        style = MaterialTheme.typography.headlineLarge,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    Audience.entries.forEach { audience ->
        val child = audience == Audience.CHILD
        OutlinedCard(onClick = { onSelect(audience) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(if (child) R.drawable.zhibo_child_mode else R.drawable.zhibo_adult_mode),
                    null, Modifier.size(84.dp), contentScale = ContentScale.Fit)
                Column(Modifier.weight(1f)) {
                    Text(audience.label, style = MaterialTheme.typography.titleLarge)
                    Text(if (child) "听文物故事，轻松看展" else "读懂文物与历史背景",
                        style = MaterialTheme.typography.bodyMedium)
                }
                Text("›")
            }
        }
    }
    TextButton(onClick = onLogout, enabled = enabled) { Text("退出登录") }
}
