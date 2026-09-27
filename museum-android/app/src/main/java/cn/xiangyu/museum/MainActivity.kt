package cn.xiangyu.museum

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val model = ViewModelProvider(this)[MuseumViewModel::class.java]
        setContent { MuseumTheme { MuseumApp(model.controller) } }
    }
}

@Composable
fun MuseumApp(controller: MuseumController, modifier: Modifier = Modifier) {
    val state by controller.state.collectAsStateWithLifecycle()
    key(state.username) { MuseumPages(state, controller, modifier) }
}

@Composable
private fun MuseumPages(state: MuseumState, controller: MuseumController?, modifier: Modifier = Modifier) {
    val mode = state.audience?.name.orEmpty()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var chapter by rememberSaveable { mutableStateOf(false) }
    var sources by rememberSaveable { mutableStateOf(false) }
    BackHandler(mode.isNotEmpty()) {
        when {
            sources -> sources = false
            chapter -> chapter = false
            tab != 0 -> { controller?.stop(); tab = 0 }
            else -> controller?.chooseMode()
        }
    }
    val audience = state.audience
    Scaffold(modifier = modifier.imePadding(), bottomBar = {
        if (audience != null) Surface(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp
        ) { NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
            val sheet = ImageBitmap.imageResource(R.drawable.navigation_sprites)
            val cell = IntSize(sheet.width / 3, sheet.height / 2)
            listOf("探索", "问阿帛", "我的手帐").forEachIndexed { index, label ->
                val selected = tab == index
                val painter = remember(sheet, index, selected) {
                    // Trim cell padding so neighboring sticker shadows cannot bleed into the icon.
                    BitmapPainter(sheet, srcOffset = IntOffset(index * cell.width + 20,
                        (if (selected) cell.height else 0) + 20),
                        srcSize = IntSize(cell.width - 40, cell.height - 40))
                }
                NavigationBarItem(selected = tab == index, onClick = {
                    if (tab == 1 && index != 1) controller?.stop()
                    tab = index; chapter = false; sources = false
                }, icon = { Image(painter, contentDescription = null, modifier = Modifier.size(48.dp)) },
                    label = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = Color.Transparent,
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant))
            }
        } }
    }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()
            .verticalScroll(rememberScrollState()).padding(Space.large),
            verticalArrangement = Arrangement.spacedBy(Space.medium)) {
            if (state.username != null) Text("湘遇 · 文物旅行记", style = MaterialTheme.typography.titleLarge)
            if (state.username == null) {
                AccountContent(state, { endpoint, username, password, register ->
                    controller?.authenticate(endpoint, username, password, register)
                })
            } else if (audience == null) {
                Text("已登录 · ${state.username}", style = MaterialTheme.typography.bodySmall)
                Abo()
                Text("你想怎样开始这段旅行？", style = MaterialTheme.typography.headlineLarge)
                Text("同一段文化，两种发现方式。", style = MaterialTheme.typography.bodyLarge)
                Audience.entries.forEach { option ->
                    Action(onClick = { controller?.selectAudience(option) }) { Text(option.label) }
                    Text(if (option == Audience.CHILD) "6～9 岁 · 和阿帛一起观察与探索" else "跟随文物，读懂汉代生活", style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(onClick = { controller?.logout() }) { Text("退出登录") }
            } else {
                Text("已登录 · ${state.username}", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { controller?.chooseMode(); chapter = false; sources = false; tab = 0 }) {
                    Text("${audience.label} · 切换模式")
                }
                when (tab) {
                    0 -> if (chapter) {
                        OutlinedButton(onClick = { chapter = false; sources = false }) { Text("返回探索") }
                        ChapterContent(audience) { relic ->
                            controller?.selectExhibit(relic.id); tab = 1; chapter = false
                        }
                        Action(onClick = { sources = !sources }) { Text(if (sources) "收起来源" else "查看来源") }
                        if (sources) SourceContent()
                        Action(onClick = { controller?.selectExhibit(null); tab = 1; chapter = false }) { Text("问阿帛 · 本章") }
                    } else HomeContent(audience, onOpen = { chapter = true })
                    1 -> {
                        ChatContent(state, { controller?.draft(it) }, { controller?.send(it) },
                            { controller?.stop() }, { controller?.loadConversation() },
                            { controller?.loadConversation(new = true) }, { controller?.loadConversation(it) },
                            { controller?.selectExhibit(it) })
                    }
                    2 -> {
                        Text("我的旅行手帐", style = MaterialTheme.typography.headlineLarge)
                        Text("${audience.label} · 尚未解锁章节章", style = MaterialTheme.typography.titleLarge)
                        Text("任务进度与盖章将在后续阶段接入，本预览不保存账号进度。", style = MaterialTheme.typography.bodyLarge)
                        Action(onClick = { tab = 0; chapter = true }) { Text("探索衣服里的秘密") }
                        TextButton(onClick = { controller?.logout() }) { Text("退出登录") }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeContent(audience: Audience, onOpen: () -> Unit) {
    Text(if (audience == Audience.CHILD) "今天，寄一封信给汉代" else "从一件衣服，走进汉代生活",
        style = MaterialTheme.typography.headlineLarge)
    Text("第一章 / 衣服里的秘密", style = MaterialTheme.typography.titleLarge)
    Action(onClick = onOpen) { Text(if (audience == Audience.CHILD) "开始探索" else "开始阅读") }
    if (audience == Audience.CHILD) {
        Image(painterResource(R.drawable.travel_journal),
            contentDescription = "原创手帐插画，以衣服、餐具、身体、书写和天地串联五个主题，不是真实文物或馆内地图",
            modifier = Modifier.fillMaxWidth().aspectRatio(1.5f))
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.small)) {
            Abo(Modifier.size(Space.touch * 2))
            Text("阿帛的手帐少了一页。一起找找，衣服里藏着什么秘密？",
                modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        }
        Text("原创故事 · 主题路线，不是馆内导航", style = MaterialTheme.typography.bodySmall)
    } else Text("从服饰开始，沿着饮食、身体、知识与天地想象，读懂文物之间的联系。", style = MaterialTheme.typography.bodyLarge)
    HorizontalDivider()
    Text("素纱单衣与印花敷彩纱丝绵袍", style = MaterialTheme.typography.bodyLarge)
    Text("接下来：请你来吃饭 → 古人的身体课 → 把知识留下来 → 画中的天地", style = MaterialTheme.typography.bodyMedium)
    Text("其余章节尚未开放", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ChapterContent(audience: Audience, onAsk: (Relic) -> Unit) {
    Text("衣服里的秘密", style = MaterialTheme.typography.headlineLarge)
    FirstChapter.relics.forEach { relic ->
        Text(relic.title, style = MaterialTheme.typography.titleLarge)
        Text(FirstChapter.text(relic, audience), style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = { onAsk(relic) }) { Text("关于${relic.title}，问阿帛") }
        HorizontalDivider()
    }
    Text(if (audience == Audience.CHILD) "观察邀请：看看衣料，再找找像枝叶的纹样。不用记年份，也不用触摸文物。" else
        "故事下一站：服饰留下材料与工艺的线索，餐具则把我们带向饮食与待客。", style = MaterialTheme.typography.bodyLarge)
    Text("内容审稿版 · 精确馆藏条目及真实图片许可待补。当前不展示文物实拍。", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun SourceContent() {
    val uriHandler = LocalUriHandler.current
    Text("依据与来源", style = MaterialTheme.typography.titleLarge)
    Text("上海美术馆发布的马王堆研究院院长文章（2026-07-04）。来源页面需要联网；本页正文离线可读。", style = MaterialTheme.typography.bodyMedium)
    OutlinedButton(onClick = { uriHandler.openUri(FirstChapter.sourceUrl) }) { Text("打开原始来源") }
}

@Composable
private fun Abo(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.abo), contentDescription = "阿帛，带着青蓝飘带和橙色邮包的纸页精灵",
        modifier = modifier.fillMaxWidth().height(Space.touch * 4))
}

@Composable
private fun Action(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    MuseumButton(onClick, modifier, content = content)
}

@Preview(showBackground = true)
@Composable
private fun MuseumPreview() { MuseumTheme { MuseumPages(MuseumState(restoring = false,
    username = "预览", audience = Audience.CHILD), null) } }
