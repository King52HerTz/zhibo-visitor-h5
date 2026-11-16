package cn.xiangyu.museum

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Uses the existing account-scoped journey and workshop records with the native game screens. */
class PuzzleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val endpoint = intent.getStringExtra("endpoint")
        val username = intent.getStringExtra("username")
        if (endpoint.isNullOrBlank() || username.isNullOrBlank()) { finish(); return }
        val key = ProgressKey(endpoint, username, Audience.CHILD)
        val chapter = intent.getBooleanExtra("story_slice", false)
        setContent {
            MuseumTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        GuideAction("返回导览", ::finish, secondary = true)
                        if (chapter) NativeJourney(key) else NativeWorkshop(key)
                    }
                }
            }
        }
    }
}

@Composable
private fun NativeJourney(identity: ProgressKey) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val checkpoint = storyChapterScope(identity.endpoint, identity.username)
    var progress by remember { mutableStateOf<StoryChapterProgress?>(null) }
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val games = listOf(GuideGame.GAUZE, GuideGame.BANNER, GuideGame.DAOYIN)
    suspend fun persist(page: Int? = null, stamp: Boolean = false): StoryChapterProgress = withContext(Dispatchers.IO) {
        StoryChapterStore(context).use { story ->
            SqliteProgressStore(context).use { tasks ->
                val remote = tasks.load(identity).completed
                story.restoreCompleted(checkpoint, remote)
                var current = story.load(checkpoint)
                if (page != null && page == current.pages) {
                    val target = listOf(7, 63, 511)[page]
                    while (current.mask < target) {
                        check(story.save(checkpoint, current.mask * 2 + 1))
                        current = story.load(checkpoint)
                    }
                }
                storyTaskIds.take(current.pages).filterNot { it in remote }.forEach { tasks.complete(identity, it) }
                if (stamp) check(story.stamp(checkpoint))
                story.load(checkpoint)
            }
        }
    }
    fun load(page: Int? = null, stamp: Boolean = false) {
        if (saving) return
        saving = true; error = null
        scope.launch {
            try { progress = persist(page, stamp) }
            catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (_: Exception) { error = "暂时无法保存手帐，请重试。" }
            finally { saving = false }
        }
    }
    LaunchedEffect(checkpoint) { load() }
    GuideTitle(if (selected < 0) "三件文物的旅程" else games[selected].title)
    val current = progress
    if (current == null) {
        if (saving) CircularProgressIndicator()
        if (error != null) GuideAction("重试", { load() })
    } else if (selected >= 0) {
        key(selected) { MuseumGame(games[selected]) { load(selected) } }
        if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error != null) GuideAction("重试保存", { load(selected) })
        GuideAction("返回旅程", { selected = -1 }, secondary = true)
    } else {
        Text("手帐 ${current.pages}/3", style = MaterialTheme.typography.titleMedium)
        games.forEachIndexed { index, game ->
            GuideRowAction(game.title, if (index < current.pages) "已完成，可重玩"
                else if (index == current.pages) game.instruction else "完成前一页后开启",
                onClick = { selected = index }, artwork = game.art, enabled = index <= current.pages)
        }
        if (current.complete) {
            if (current.stamped) MuseumNotice("三件文物的旅程 · 纪念章已收入手帐")
            else GuideAction("盖下纪念章", { load(stamp = true) }, enabled = !saving)
        }
    }
    error?.let { MuseumNotice(it, error = true) }
}

@Composable
private fun NativeWorkshop(identity: ProgressKey) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf(ProgressState()) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(identity) {
        SqliteProgressStore(context).use { progress = ProgressState(it.load(identity)) }
    }
    GuideTitle("衣坊")
    val task = FirstChapter.tasks.firstOrNull { it.id == selected }
    if (task == null) {
        WorkshopContent(progress.progress, onTask = { selected = it.id })
    } else {
        GuideAction("返回衣坊", { selected = null }, secondary = true)
        WorkshopTaskContent(task, progress, { taskId ->
            scope.launch {
                SqliteProgressStore(context).use { progress = ProgressState(it.complete(identity, taskId)) }
            }
        })
    }
}
