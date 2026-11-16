package cn.xiangyu.museum

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val cameraLaunchers = mutableMapOf<String, ActivityResultLauncher<Uri>>()
    private fun registerCamera(id: String): ActivityResultLauncher<Uri> = cameraLaunchers.getOrPut(id) {
        activityResultRegistry.register("journal-camera-$id", ActivityResultContracts.TakePicture()) { success ->
            ViewModelProvider(this)[MuseumViewModel::class.java].observations.cameraResult(id, success)
            cameraLaunchers.remove(id)?.unregister()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val model = ViewModelProvider(this)[MuseumViewModel::class.java]
        model.observations.pendingCaptureId()?.let { registerCamera(it) }
        setContent { MuseumTheme { MuseumApp(model.controller, progressController = model.progress,
            observationController = model.observations, onCamera = { id, uri -> registerCamera(id).launch(uri) }) } }
    }
    override fun onDestroy() {
        cameraLaunchers.values.forEach { it.unregister() }
        cameraLaunchers.clear()
        super.onDestroy()
    }
    override fun onResume() {
        super.onResume()
        ViewModelProvider(this)[MuseumViewModel::class.java].progress.sync()
    }
}

private enum class DraftIdentityExit { MODE, LOGOUT, CHILD, ADULT }

@Composable
fun MuseumApp(controller: MuseumController, modifier: Modifier = Modifier, progressController: ProgressController? = null,
              observationController: LocalObservationController? = null, onCamera: (String, Uri) -> Unit = { _, _ -> }) {
    val state by controller.state.collectAsStateWithLifecycle()
    val progress = progressController?.state?.collectAsStateWithLifecycle()?.value ?: ProgressState()
    var splash by rememberSaveable { mutableStateOf(true) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Image(painterResource(R.drawable.zhibo_paper), null, Modifier.matchParentSize(),
            contentScale = ContentScale.Crop, alpha = .44f)
        if (splash) BrandSplash { splash = false }
        else key(state.endpoint, state.username) {
            MuseumPages(state, controller, modifier, progress, progressController, observationController, onCamera)
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun MuseumPages(state: MuseumState, controller: MuseumController?, modifier: Modifier = Modifier,
                        progress: ProgressState = ProgressState(), progressController: ProgressController? = null,
                        observationController: LocalObservationController? = null, onCamera: (String, Uri) -> Unit = { _, _ -> }) {
    val mode = state.audience?.name.orEmpty()
    var tab by rememberSaveable(mode) { mutableStateOf(GuideTab.EXHIBITS) }
    var exhibitId by rememberSaveable(mode) { mutableStateOf<String?>(null) }
    var storyId by rememberSaveable(mode) { mutableStateOf<String?>(null) }
    var gameId by rememberSaveable(mode) { mutableStateOf<String?>(null) }
    var showHistory by rememberSaveable(mode) { mutableStateOf(false) }
    var chapter by rememberSaveable { mutableStateOf(false) }
    var sources by rememberSaveable { mutableStateOf(false) }
    var taskId by rememberSaveable(mode) { mutableStateOf<String?>(null) }
    val observation = observationController?.state?.collectAsStateWithLifecycle()?.value
    val journalTab = GuideTab.MINE
    val chatTab = GuideTab.CHAT
    var pendingExit by rememberSaveable(mode) { mutableStateOf<DraftIdentityExit?>(null) }
    var resumePendingDraft by rememberSaveable { mutableStateOf(false) }
    fun waitingDraftAudience(): Audience? = if (state.audience == null && state.username != null)
        observationController?.pendingDraftAudience(state.endpoint, state.username) else null
    fun hasObservationDraft(): Boolean = observationController?.hasUnsavedDraft() == true || waitingDraftAudience() != null
    fun finishIdentityExit(action: DraftIdentityExit) {
        if (observationController?.state?.value?.busy == true) return
        if (observationController?.state?.value?.editing == true || waitingDraftAudience() != null)
            observationController?.cancelDraft()
        resumePendingDraft = false
        pendingExit = null
        chapter = false; sources = false; taskId = null; tab = GuideTab.EXHIBITS
        exhibitId = null; storyId = null; gameId = null; showHistory = false
        when (action) {
            DraftIdentityExit.MODE -> controller?.chooseMode()
            DraftIdentityExit.LOGOUT -> controller?.logout()
            DraftIdentityExit.CHILD -> controller?.selectAudience(Audience.CHILD)
            DraftIdentityExit.ADULT -> controller?.selectAudience(Audience.ADULT)
        }
    }
    fun requestIdentityExit(action: DraftIdentityExit) {
        if (observationController?.state?.value?.busy == true) return
        if (state.draft.isNotBlank() || hasObservationDraft()) pendingExit = action
        else finishIdentityExit(action)
    }
    fun selectAudience(audience: Audience) {
        if (observationController?.state?.value?.busy == true) return
        val waiting = waitingDraftAudience()
        if (waiting != null && waiting != audience) {
            pendingExit = if (audience == Audience.CHILD) DraftIdentityExit.CHILD else DraftIdentityExit.ADULT
        } else {
            resumePendingDraft = waiting == audience
            controller?.selectAudience(audience)
        }
    }
    fun keepIdentityDraft() {
        pendingExit = null
        observationController?.keepDraft()
        if (state.audience == null) return
        controller?.stop()
        tab = if (hasObservationDraft()) journalTab else chatTab
        chapter = false; sources = false; taskId = null
    }
    fun openObservation(relic: Relic) {
        observationController?.let {
            if (it.state.value.loading || it.state.value.busy) return
            it.newRecord(relic.id)
            tab = journalTab; chapter = false; sources = false; taskId = null
        }
    }
    pendingExit?.let { action ->
        val actionLabel = when (action) {
            DraftIdentityExit.MODE -> "切换模式"
            DraftIdentityExit.LOGOUT -> "退出登录"
            DraftIdentityExit.CHILD -> "进入儿童模式"
            DraftIdentityExit.ADULT -> "进入成人模式"
        }
        val chatOnly = state.draft.isNotBlank() && !hasObservationDraft()
        val discardedContent = if (chatOnly) "问题" else "草稿"
        val dialogTitle = if (chatOnly) "问题还没发送" else "放下草稿？"
        AlertDialog(onDismissRequest = ::keepIdentityDraft, modifier = Modifier.semantics { paneTitle = dialogTitle },
            title = { Text(dialogTitle, modifier = Modifier.semantics { heading() }) },
            text = { Text(when {
                chatOnly -> "${actionLabel}会清空当前未发送的问题。"
                state.draft.isNotBlank() -> "${actionLabel}会放下未保存的文字、照片和未发送的问题，已保存的记录会保留。"
                else -> "${actionLabel}会放下未保存的文字和照片，已保存的记录会保留。"
            }) },
            confirmButton = { TextButton(onClick = { finishIdentityExit(action) }, enabled = observation?.busy != true,
                modifier = Modifier.heightIn(min = Space.touch)) {
                Text(if (action == DraftIdentityExit.LOGOUT) "放下${discardedContent}并退出" else "放下${discardedContent}并切换")
            } },
            dismissButton = { TextButton(onClick = ::keepIdentityDraft,
                modifier = Modifier.heightIn(min = Space.touch)) { Text(when {
                    state.audience == null -> "返回选择模式"
                    chatOnly -> "继续提问"
                    else -> "继续记录"
                }) } })
    }
    LaunchedEffect(resumePendingDraft, mode, observation?.editing) {
        if (resumePendingDraft && state.audience != null && observation?.editing == true) {
            tab = journalTab
            resumePendingDraft = false
        }
    }
    val pageScroll = rememberScrollState()
    LaunchedEffect(tab, chapter, taskId, exhibitId, storyId, gameId, mode, observation?.editing) { pageScroll.scrollTo(0) }
    BackHandler(mode.isNotEmpty()) {
        when {
            tab == journalTab && observation?.editing == true -> observationController?.requestCancelDraft()
            taskId != null -> taskId = null
            sources -> sources = false
            chapter -> chapter = false
            tab == GuideTab.EXHIBITS && exhibitId != null -> exhibitId = null
            tab == GuideTab.STORIES && storyId != null -> storyId = null
            tab == GuideTab.GAMES && gameId != null -> gameId = null
            tab != GuideTab.EXHIBITS -> { controller?.stop(); tab = GuideTab.EXHIBITS }
            else -> requestIdentityExit(DraftIdentityExit.MODE)
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
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val username = state.username
    val library = remember(context, state.endpoint, username, audience) {
        if (username != null && audience != null) GuideLibrary(context, state.endpoint, username, audience) else null
    }
    fun openExhibit(exhibit: GuideExhibit) {
        library?.open(exhibit)
        exhibitId = exhibit.id
        tab = GuideTab.EXHIBITS
    }
    fun openStory(story: GuideStory) {
        library?.recordStory(story.id)
        storyId = story.id
        tab = GuideTab.STORIES
    }
    fun askExhibit(exhibit: GuideExhibit) {
        library?.open(exhibit)
        controller?.selectExhibit(exhibit.apiId,
            contextName = exhibit.name.takeIf { exhibit.apiId == null })
        tab = chatTab
    }
    Scaffold(modifier = modifier.imePadding(), containerColor = Color.Transparent, bottomBar = {
        if (audience != null && !(tab == chatTab && keyboardVisible)) GuideNav(audience, tab) { next ->
            if (tab == chatTab && next != chatTab) controller?.stop()
            if (tab != next) {
                chapter = false; sources = false; taskId = null
                exhibitId = null; storyId = null; gameId = null; showHistory = false
            }
            tab = next
        }
    }) { padding ->
        if (state.username != null && audience != null && tab == chatTab) {
            ChatContent(state, { controller?.draft(it) }, { controller?.send(it) },
                { controller?.stop() }, { controller?.loadConversation() },
                { controller?.loadConversation(new = true) }, { controller?.loadConversation(it) },
                { controller?.selectExhibit(it) }, modifier = Modifier.padding(padding).fillMaxSize(),
                onMode = { requestIdentityExit(DraftIdentityExit.MODE) },
                openHistory = showHistory, onHistoryShown = { showHistory = false })
            return@Scaffold
        }
        if (username == null) {
            LoginPage(state, Modifier.padding(padding), keyboardVisible) { endpoint, account, password, register ->
                controller?.authenticate(endpoint, account, password, register)
            }
            return@Scaffold
        }
        if (audience != null && library != null && tab == GuideTab.EXHIBITS) {
            ExhibitPage(audience, guideExhibits.firstOrNull { it.id == exhibitId }, exhibitId in library.saved,
                observation?.busy != true, ::openExhibit, { exhibitId = null }, ::askExhibit, library::toggle,
                ::openObservation, { requestIdentityExit(DraftIdentityExit.MODE) }, Modifier.padding(padding))
            return@Scaffold
        }
        Column(modifier = Modifier.padding(padding).fillMaxSize()
            .verticalScroll(pageScroll).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (audience == null) {
                ModeSelection(observation?.busy != true, ::selectAudience) {
                    requestIdentityExit(DraftIdentityExit.LOGOUT)
                }
            } else if (library != null) {
                GuideHeader(audience, observation?.busy != true) { requestIdentityExit(DraftIdentityExit.MODE) }
                when (tab) {
                    GuideTab.EXHIBITS -> Unit
                    GuideTab.STORIES -> if (!chapter) {
                        StoryPage(guideStories.firstOrNull { it.id == storyId }, ::openStory,
                            { storyId = null }, { chapter = true }, ::openExhibit)
                    } else if (taskId != null) {
                        TextButton(onClick = { taskId = null }) { Text("返回故事") }
                        FirstChapter.tasks.firstOrNull { it.id == taskId }?.let { task ->
                            WorkshopTaskContent(task, progress, { progressController?.complete(it) })
                        }
                    } else {
                        TextButton(onClick = { chapter = false }) { Text("返回故事") }
                        var showIntro by rememberSaveable { mutableStateOf(false) }
                        if (showIntro) StoryInvitation { showIntro = false }
                        else OutlinedButton(onClick = { showIntro = true }) { Text("重看故事引入") }
                        OutlinedButton(onClick = ::openPuzzle) { Text("进入衣坊") }
                        StoryChapterContent(state.endpoint, username, ::openStorySlice, progress.progress.completed)
                        WorkshopContent(progress.progress, onTask = { taskId = it.id })
                        OutlinedButton(onClick = { sources = !sources }) { Text(if (sources) "收起档案" else "文物与来源") }
                        if (sources) {
                            ChapterContent(audience, { taskId = it.id }, {
                                controller?.selectExhibit(it.id); tab = chatTab; chapter = false
                            }, ::openObservation, observation?.loading != true && observation?.busy != true)
                            SourceContent()
                        }
                    }
                    GuideTab.GAMES -> GamePage(guideGames.firstOrNull { it.id == gameId }, { gameId = it.id },
                        { gameId = null }, { library.finishGame(it.id) }, ::openStorySlice, ::openPuzzle, ::openExhibit)
                    GuideTab.GRAPH -> GraphPage(::openExhibit, ::askExhibit)
                    GuideTab.TIMELINE -> TimelinePage(::openExhibit)
                    GuideTab.MINE -> MinePage(audience, library, ::openExhibit, ::openStory, {
                        gameId = it.id; tab = GuideTab.GAMES
                    }, {
                        showHistory = true; tab = chatTab
                    }, {
                        requestIdentityExit(DraftIdentityExit.MODE)
                    }, {
                        requestIdentityExit(DraftIdentityExit.LOGOUT)
                    }, editing = observation?.editing == true, identityEnabled = observation?.busy != true) {
                        observationController?.let { LocalObservationPanel(audience, it, onCamera) }
                        if (audience == Audience.CHILD && observation?.editing != true) JournalContent(audience, progress,
                            onExplore = { tab = GuideTab.STORIES; chapter = true }, onSync = { progressController?.sync() })
                    }
                    GuideTab.CHAT -> Unit
                }
            }
        }
    }
}

@Composable
private fun ChapterContent(audience: Audience, onTask: (ObservationTask) -> Unit, onAsk: (Relic) -> Unit,
                           onObserve: (Relic) -> Unit, observationEnabled: Boolean) {
    Text("衣服里的秘密", style = MaterialTheme.typography.headlineLarge)
    FirstChapter.relics.forEach { relic ->
        Text(relic.title, style = MaterialTheme.typography.titleLarge)
        Text(FirstChapter.text(relic, audience), style = MaterialTheme.typography.bodyLarge)
        if (audience == Audience.CHILD) FirstChapter.tasks.first { it.relicId == relic.id }.let { task ->
            MuseumButton(onClick = { onTask(task) }) { Text("观察任务 · ${task.title}") }
        }
        OutlinedButton(onClick = { onAsk(relic) }) { Text("关于${relic.title}，问阿帛") }
        OutlinedButton(onClick = { onObserve(relic) }, enabled = observationEnabled, modifier = Modifier.heightIn(min = Space.touch)
            .semantics { contentDescription = "记下${relic.title}的观察" }) { Text("记下观察") }
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

@Preview(showBackground = true)
@Composable
private fun MuseumPreview() { MuseumTheme { MuseumPages(MuseumState(restoring = false,
    username = "预览", audience = Audience.CHILD), null) } }
