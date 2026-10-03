package cn.xiangyu.museum

import android.os.Bundle
import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val model = ViewModelProvider(this)[MuseumViewModel::class.java]
        setContent { MuseumTheme { MuseumApp(model.controller, progressController = model.progress) } }
    }
    override fun onResume() {
        super.onResume()
        ViewModelProvider(this)[MuseumViewModel::class.java].progress.sync()
    }
}

@Composable
fun MuseumApp(controller: MuseumController, modifier: Modifier = Modifier, progressController: ProgressController? = null) {
    val state by controller.state.collectAsStateWithLifecycle()
    val progress = progressController?.state?.collectAsStateWithLifecycle()?.value ?: ProgressState()
    var splash by rememberSaveable { mutableStateOf(true) }
    if (splash) BrandSplash { splash = false }
    else key(state.endpoint, state.username) { MuseumPages(state, controller, modifier, progress, progressController) }
}

@Composable
private fun MuseumPages(state: MuseumState, controller: MuseumController?, modifier: Modifier = Modifier,
                        progress: ProgressState = ProgressState(), progressController: ProgressController? = null) {
    val mode = state.audience?.name.orEmpty()
    var tab by rememberSaveable(mode) { mutableIntStateOf(0) }
    var introDone by rememberSaveable(mode) { mutableStateOf(false) }
    var chapter by rememberSaveable { mutableStateOf(false) }
    var sources by rememberSaveable { mutableStateOf(false) }
    var taskId by rememberSaveable(mode) { mutableStateOf<String?>(null) }
    val pageScroll = rememberScrollState()
    LaunchedEffect(tab, chapter, taskId, mode) { pageScroll.scrollTo(0) }
    BackHandler(mode.isNotEmpty()) {
        when {
            taskId != null -> taskId = null
            sources -> sources = false
            chapter -> chapter = false
            tab != 0 -> { controller?.stop(); tab = 0 }
            else -> controller?.chooseMode()
        }
    }
    val audience = state.audience
    val context = LocalContext.current
    fun openPuzzle() {
        if (audience == Audience.CHILD && state.username != null) {
            context.startActivity(Intent(context, PuzzleActivity::class.java)
                .putExtra("endpoint", state.endpoint).putExtra("username", state.username))
        }
    }
    fun openStorySlice() {
        if (audience == Audience.CHILD && state.username != null) {
            context.startActivity(Intent(context, PuzzleActivity::class.java)
                .putExtra("endpoint", state.endpoint).putExtra("username", state.username)
                .putExtra("story_slice", true))
        }
    }
    val chatTab = if (audience == Audience.ADULT) 2 else 1
    if (audience == Audience.CHILD && !introDone) {
        BackHandler { controller?.chooseMode() }
        StoryInvitation { introDone = true }
        return
    }
    Scaffold(modifier = modifier.imePadding(), bottomBar = {
        if (audience != null) Surface(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp
        ) { NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
            val sheet = ImageBitmap.imageResource(if (audience == Audience.ADULT)
                R.drawable.adult_navigation_sprites else R.drawable.navigation_sprites)
            val columns = if (audience == Audience.ADULT) 4 else 3
            val cell = IntSize(sheet.width / columns, sheet.height / 2)
            val labels = if (audience == Audience.ADULT) listOf("故事", "文物", "讲解", "我的")
                else listOf("探索", "问阿帛", "我的手帐")
            labels.forEachIndexed { index, label ->
                val selected = tab == index
                val painter = remember(sheet, index, selected) {
                    // Trim cell padding so neighboring sticker shadows cannot bleed into the icon.
                    BitmapPainter(sheet, srcOffset = IntOffset(index * cell.width + 20,
                        (if (selected) cell.height else 0) + 20),
                        srcSize = IntSize(cell.width - 40, cell.height - 40))
                }
                NavigationBarItem(selected = tab == index, onClick = {
                    if (tab == chatTab && index != chatTab) controller?.stop()
                    tab = index; chapter = false; sources = false; taskId = null
                }, icon = {
                    Image(painter, contentDescription = null,
                        modifier = Modifier.size(if (audience == Audience.CHILD) 48.dp else 36.dp))
                },
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
            .verticalScroll(pageScroll).padding(Space.large),
            verticalArrangement = Arrangement.spacedBy(Space.medium)) {
            if (state.username != null) Text("湘遇", style = MaterialTheme.typography.titleLarge)
            if (state.username == null) {
                AccountContent(state, { endpoint, username, password, register ->
                    controller?.authenticate(endpoint, username, password, register)
                })
            } else if (audience == null) {
                Text("已登录 · ${state.username}", style = MaterialTheme.typography.bodySmall)
                Abo(Modifier.height(120.dp))
                Text("今天，怎样出发？", style = MaterialTheme.typography.headlineMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Audience.entries.forEach { option ->
                        OutlinedButton(onClick = { controller?.selectAudience(option) }) { Text(option.label) }
                    }
                }
                OutlinedButton(onClick = { controller?.logout() }) { Text("退出登录") }
            } else {
                Text("已登录 · ${state.username}", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { controller?.chooseMode(); chapter = false; sources = false; tab = 0 }) {
                    Text("${audience.label} · 切换模式")
                }
                when {
                    audience == Audience.ADULT && tab == 1 -> AdultRelics { relic ->
                        controller?.selectExhibit(relic.id); tab = chatTab
                    }
                    audience == Audience.ADULT && tab == 3 -> {
                        Text("我的旅程", style = MaterialTheme.typography.headlineLarge)
                        Text(state.username, style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(onClick = { controller?.loadConversation(); tab = chatTab }) { Text("我的讲解记录") }
                        OutlinedButton(onClick = { controller?.logout() }) { Text("退出登录") }
                    }
                    tab == 0 -> if (taskId != null) {
                        OutlinedButton(onClick = { taskId = null }) { Text("返回故事") }
                        FirstChapter.tasks.firstOrNull { it.id == taskId }?.let { task ->
                            if (audience == Audience.CHILD) WorkshopTaskContent(task, progress, { progressController?.complete(it) })
                            else ObservationContent(task, audience, progress, { progressController?.complete(it) })
                        }
                    } else if (chapter) {
                        OutlinedButton(onClick = { chapter = false; sources = false }) { Text("返回探索") }
                        if (audience == Audience.CHILD) MuseumButton(onClick = { openPuzzle() }, enabled = !progress.loading) { Text("进入横屏衣坊") }
                        if (audience == Audience.CHILD) StoryChapterContent(state.endpoint, state.username, { openStorySlice() }, progress.progress.completed)
                        if (audience == Audience.CHILD) WorkshopContent(progress.progress, onTask = { taskId = it.id })
                        OutlinedButton(onClick = { sources = !sources }) { Text(if (sources) "收起档案" else "文物与来源") }
                        if (audience == Audience.ADULT || sources) ChapterContent(audience,
                            onTask = { taskId = it.id }, onAsk = { relic ->
                                controller?.selectExhibit(relic.id); tab = chatTab; chapter = false
                            })
                        if (sources) SourceContent()
                        OutlinedButton(onClick = { controller?.selectExhibit(null); tab = chatTab; chapter = false }) {
                            Text(if (audience == Audience.CHILD) "问阿帛 · 本章" else "本章讲解")
                        }
                    } else {
                        HomeContent(audience, onOpen = {
                            if (audience == Audience.CHILD) openPuzzle() else chapter = true
                        })
                        if (audience == Audience.CHILD) TextButton(onClick = { chapter = true }) { Text("文物与观察任务") }
                        if (audience == Audience.CHILD) StoryChapterContent(state.endpoint, state.username, { openStorySlice() }, progress.progress.completed)
                        if (audience == Audience.CHILD) ChapterProgress(progress.progress)
                        if (audience == Audience.CHILD) TextButton(onClick = { introDone = false }) { Text("重看故事引入") }
                    }
                    tab == chatTab -> {
                        ChatContent(state, { controller?.draft(it) }, { controller?.send(it) },
                            { controller?.stop() }, { controller?.loadConversation() },
                            { controller?.loadConversation(new = true) }, { controller?.loadConversation(it) },
                            { controller?.selectExhibit(it) })
                    }
                    tab == 2 -> {
                        JournalContent(audience, progress, onExplore = { tab = 0; chapter = true },
                            onSync = { progressController?.sync() })
                        if (audience == Audience.CHILD) StoryChapterContent(state.endpoint, state.username, { openStorySlice() }, progress.progress.completed)
                        OutlinedButton(onClick = { controller?.logout() }) { Text("退出登录") }
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
private fun ChapterContent(audience: Audience, onTask: (ObservationTask) -> Unit, onAsk: (Relic) -> Unit) {
    Text("衣服里的秘密", style = MaterialTheme.typography.headlineLarge)
    FirstChapter.relics.forEach { relic ->
        Text(relic.title, style = MaterialTheme.typography.titleLarge)
        Text(FirstChapter.text(relic, audience), style = MaterialTheme.typography.bodyLarge)
        if (audience == Audience.CHILD) FirstChapter.tasks.first { it.relicId == relic.id }.let { task ->
            Action(onClick = { onTask(task) }) { Text("观察任务 · ${task.title}") }
        }
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
        modifier = modifier.fillMaxWidth().heightIn(max = Space.touch * 4))
}

@Composable
private fun Action(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    MuseumButton(onClick, modifier, content = content)
}

@Preview(showBackground = true)
@Composable
private fun MuseumPreview() { MuseumTheme { MuseumPages(MuseumState(restoring = false,
    username = "预览", audience = Audience.CHILD), null) } }
