package cn.xiangyu.museum

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

internal enum class GuideTab(val label: String, val art: Int) {
    EXHIBITS("展品", R.drawable.zhibo_nav_exhibit),
    STORIES("故事", R.drawable.zhibo_nav_story),
    GRAPH("图解", R.drawable.zhibo_nav_chart),
    CHAT("对话", R.drawable.zhibo_nav_chat),
    GAMES("游戏", R.drawable.zhibo_nav_game),
    TIMELINE("年表", R.drawable.zhibo_nav_timeline),
    MINE("我的", R.drawable.zhibo_nav_me)
}

internal fun guideTabs(audience: Audience) = if (audience == Audience.CHILD)
    listOf(GuideTab.EXHIBITS, GuideTab.STORIES, GuideTab.CHAT, GuideTab.GAMES, GuideTab.MINE)
else listOf(GuideTab.EXHIBITS, GuideTab.GRAPH, GuideTab.CHAT, GuideTab.TIMELINE, GuideTab.MINE)

@Composable
internal fun GuideNav(audience: Audience, current: GuideTab, onSelect: (GuideTab) -> Unit) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
            guideTabs(audience).forEach { tab ->
                val selected = tab == current
                NavigationBarItem(selected = selected, onClick = { onSelect(tab) },
                    icon = {
                        Image(painterResource(tab.art), null, Modifier.size(if (tab == GuideTab.CHAT) 34.dp else 30.dp))
                    },
                    label = {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(tab.label, Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                            if (selected) HorizontalDivider(Modifier.width(24.dp).padding(top = 2.dp),
                                color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.surface,
                        selectedTextColor = MaterialTheme.colorScheme.primary))
            }
        }
    }
}

@Composable
internal fun GuideHeader(audience: Audience, modeEnabled: Boolean, onMode: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.zhibo_seal), null, Modifier.size(30.dp))
        Text("智博导览", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = onMode, enabled = modeEnabled,
            modifier = Modifier.semantics { contentDescription = "切换导览模式" },
            contentPadding = PaddingValues(horizontal = 12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)) {
            Text(audience.label, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
internal fun GuideTitle(title: String) {
    Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
}

@Composable
internal fun GuideArt(art: Int, description: String?, modifier: Modifier = Modifier, ratio: Float = 1.5f,
                      scale: ContentScale = ContentScale.Crop) {
    Image(painterResource(art), description,
        modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(10.dp)),
        contentScale = scale)
}

internal data class GuideExhibit(
    val id: String, val name: String, val category: String, val period: String, val origin: String?,
    val art: Int, val child: String, val adult: String, val apiId: String? = null,
    val collection: CollectionSource? = null
)

internal val guideExhibits = museumCollection + listOf(
    GuideExhibit("silk-painted-robe", "印花敷彩纱丝绵袍", "织物", "西汉", null,
        R.drawable.zhibo_textile, FirstChapter.relics[1].childText, FirstChapter.relics[1].adultText, "silk-painted-robe"),
    GuideExhibit("bronze-mirror", "铜镜", "青铜器", "", null, R.drawable.zhibo_mirror,
        "铜镜的正面用来看自己的样子，背面常常有好看的图案。看看这面镜子背后的图案是怎样排在一起的。",
        "以镜背纹饰为观察入口，可对照外缘、纹样和镜钮的位置。此处展示铜镜类型示意。"),
    GuideExhibit("lacquer-box", "漆盒", "漆器", "", null, R.drawable.zhibo_lacquer,
        "漆盒的盖子可以打开。看看盖子和盒身上的线条有什么不同。",
        "漆盒可以从器形、胎体和表面髹饰入手观察。此处展示漆器类型示意。"),
    GuideExhibit("bronze-ding", "铜鼎", "青铜器", "", null, R.drawable.zhibo_bronze,
        "看看鼎有什么特别的地方。你能找到它的两只耳和三条腿吗？",
        "可观察口沿、耳、足与腹部纹饰，比较不同鼎的结构。此处展示青铜器类型示意。")
)

internal class GuideLibrary(context: Context, endpoint: String, username: String, audience: Audience) {
    private val prefs: SharedPreferences = context.getSharedPreferences("zhibo_guide_library", Context.MODE_PRIVATE)
    private val scope = "${endpoint.length}:$endpoint${username.length}:$username:${audience.name}:"
    var recent by mutableStateOf(prefs.getString(scope + "recent", null))
        private set
    var saved by mutableStateOf(prefs.getStringSet(scope + "saved", emptySet()).orEmpty().toSet())
        private set
    var story by mutableStateOf(prefs.getString(scope + "story", null))
        private set
    var games by mutableStateOf(prefs.getStringSet(scope + "games", emptySet()).orEmpty().toSet())
        private set

    fun open(exhibit: GuideExhibit) {
        recent = exhibit.id
        prefs.edit().putString(scope + "recent", exhibit.id).apply()
    }

    fun toggle(exhibit: GuideExhibit) {
        saved = if (exhibit.id in saved) saved - exhibit.id else saved + exhibit.id
        prefs.edit().putStringSet(scope + "saved", saved).apply()
    }

    fun recordStory(id: String) {
        story = id
        prefs.edit().putString(scope + "story", id).apply()
    }

    fun finishGame(id: String) {
        games = games + id
        prefs.edit().putStringSet(scope + "games", games).apply()
    }
}

@Composable
internal fun ExhibitPage(audience: Audience, selected: GuideExhibit?, saved: Boolean, modeEnabled: Boolean,
                         onOpen: (GuideExhibit) -> Unit, onBack: () -> Unit,
                         onAsk: (GuideExhibit) -> Unit, onSave: (GuideExhibit) -> Unit,
                         onNote: (Relic) -> Unit, onMode: () -> Unit, modifier: Modifier = Modifier) {
    val child = audience == Audience.CHILD
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("全部") }
    val scroll = rememberLazyListState()
    if (selected != null) {
        key(selected.id) {
            Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExhibitDetail(selected, audience, saved, onBack, { onAsk(selected) }, { onSave(selected) }, onNote)
            }
        }
        return
    }
    val featured = if (child) museumCollection.first() else museumCollection[1]
    val showFeature = query.isBlank() && category == "全部"
    val results = museumCollection.filter { exhibit ->
        (!showFeature || exhibit != featured) && (category == "全部" || exhibit.category == category) &&
            listOf(exhibit.name, exhibit.period, exhibit.category, exhibit.origin.orEmpty()).any { query.trim() in it }
    }
    LazyColumn(modifier.fillMaxSize(), state = scroll, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { GuideHeader(audience, modeEnabled, onMode) }
        item {
            Text("湖南博物院", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            GuideTitle(if (child) "看文物" else "馆藏展品")
        }
        item { MuseumField(query, { query = it }, "搜索文物、年代或类别") }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (listOf("全部") + museumCollection.map { it.category }.distinct()).forEach {
                    FilterChip(category == it, { category = it }, label = { Text(it) })
                }
            }
        }
        if (showFeature) item {
            OutlinedCard(onClick = { onOpen(featured) }, modifier = Modifier.fillMaxWidth()) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val copy: @Composable (Modifier) -> Unit = { labelModifier ->
                        Column(labelModifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(if (child) "素纱单衣" else featured.name, style = MaterialTheme.typography.titleLarge)
                            Text(if (child) "这件衣服为什么这么轻？" else "西汉 · 马王堆一号汉墓",
                                style = MaterialTheme.typography.bodyMedium)
                            GuideAction(if (child) "看看细节" else "查看档案", { onOpen(featured) },
                                Modifier.fillMaxWidth(), end = "›")
                        }
                    }
                    val art: @Composable (Modifier) -> Unit = { artModifier ->
                        Surface(artModifier, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Image(painterResource(featured.art), featured.name,
                                Modifier.fillMaxWidth().height(180.dp), contentScale = ContentScale.Fit)
                        }
                    }
                    if (!child && maxWidth >= 280.dp && androidx.compose.ui.platform.LocalDensity.current.fontScale < 1.4f) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            art(Modifier.width(112.dp))
                            copy(Modifier.weight(1f))
                        }
                    } else {
                        Column {
                            art(Modifier.fillMaxWidth())
                            copy(Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (showFeature) "馆藏精选" else "搜索结果", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                Text("${results.size + if (showFeature) 1 else 0} 件", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (results.isEmpty()) item {
            MuseumNotice("没有找到相关展品。可以换个名称查找，或查看全部馆藏。")
            GuideAction("查看全部", { query = ""; category = "全部" }, secondary = true)
        }
        items(results.chunked(2), key = { it.first().id }) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { exhibit ->
                    OutlinedCard(onClick = { onOpen(exhibit) }, modifier = Modifier.weight(1f)) {
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Image(painterResource(exhibit.art), null, Modifier.fillMaxWidth().height(114.dp).padding(6.dp),
                                contentScale = ContentScale.Fit)
                        }
                        Column(Modifier.padding(10.dp)) {
                            Text(exhibit.name, style = MaterialTheme.typography.titleSmall)
                            Text("${exhibit.period} · ${exhibit.category}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ExhibitDetail(exhibit: GuideExhibit, audience: Audience, saved: Boolean,
                          onBack: () -> Unit, onAsk: () -> Unit, onSave: () -> Unit, onNote: ((Relic) -> Unit)?) {
    var fullImage by rememberSaveable { mutableStateOf(false) }
    val collection = exhibit.collection
    GuideAction("返回展品", onBack, secondary = true)
    GuideTitle(exhibit.name)
    Text(listOf(exhibit.period, exhibit.category).filter(String::isNotBlank).joinToString(" · "),
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(exhibit.art), exhibit.name, Modifier.fillMaxWidth().height(250.dp),
                contentScale = ContentScale.Fit)
            GuideAction("看大图", { fullImage = true }, secondary = true)
        }
    }
    Text(collection?.imageLabel ?: "文物示意图", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (audience == Audience.ADULT) {
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("馆藏档案", style = MaterialTheme.typography.titleMedium)
                listOfNotNull(collection?.let { "材质" to it.material }, exhibit.origin?.let { "出土" to it },
                    collection?.let { "收藏" to "湖南博物院" }).forEach { (label, value) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(label, color = MaterialTheme.colorScheme.primary)
                        Text(value, Modifier.weight(1f))
                    }
                }
            }
        }
    }
    Text(if (audience == Audience.CHILD) "仔细看看" else "文物解读", style = MaterialTheme.typography.titleLarge)
    Text(if (audience == Audience.CHILD) exhibit.child else exhibit.adult, style = MaterialTheme.typography.bodyLarge)
    collection?.let { MuseumNotice(it.observation) }
    GuideAction("问问这件文物", onAsk, Modifier.fillMaxWidth(), end = "›")
    GuideAction(if (saved) "取消收藏" else "收藏展品", onSave, Modifier.fillMaxWidth(), secondary = true)
    if (onNote != null) FirstChapter.relics.firstOrNull { it.id == exhibit.id }?.let { relic ->
        GuideAction("记下观察", { onNote(relic) }, Modifier.fillMaxWidth()
            .semantics { contentDescription = "记下${relic.title}的观察" }, secondary = true)
    }
    collection?.let { CollectionCredit(it) }
    if (fullImage) ExhibitImageDialog(exhibit) { fullImage = false }
}

@Composable
private fun CollectionCredit(source: CollectionSource) {
    val uri = LocalUriHandler.current
    var unavailable by remember { mutableStateOf(false) }
    fun open(url: String) {
        unavailable = false
        try { uri.openUri(url) } catch (_: IllegalArgumentException) { unavailable = true }
    }
    Text("资料与图像", style = MaterialTheme.typography.titleMedium)
    Text("${source.credit} · Wikimedia Commons · ${source.license.title} · 已缩放并转为 WebP",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    source.research?.let { GuideRowAction("阅读文物资料", onClick = { open(it) }) }
    GuideRowAction("图像原始出处", onClick = { open(source.imageUrl) })
    GuideAction("查看图像授权", { open(source.license.url) }, secondary = true)
    if (unavailable) Text("这台设备暂时无法打开网页。")
}

@Composable
private fun ExhibitImageDialog(exhibit: GuideExhibit, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoom, movement, _ ->
        scale = (scale * zoom).coerceIn(1f, 4f)
        pan = if (scale <= 1f) Offset.Zero else (pan + movement).let {
            Offset(it.x.coerceIn(-700f, 700f), it.y.coerceIn(-900f, 900f))
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GuideAction("关闭大图", onDismiss, secondary = true)
                Text(exhibit.name, style = MaterialTheme.typography.titleLarge)
                Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().transformable(state)) {
                    Image(painterResource(exhibit.art), exhibit.name, Modifier.fillMaxSize().graphicsLayer {
                        scaleX = scale; scaleY = scale; translationX = pan.x; translationY = pan.y
                    }, contentScale = ContentScale.Fit)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GuideAction("放大", { scale = (scale + .5f).coerceAtMost(4f) }, secondary = true, enabled = scale < 4f)
                    GuideAction("复位", { scale = 1f; pan = Offset.Zero }, secondary = true, enabled = scale > 1f)
                }
            }
        }
    }
}

@Composable
internal fun GuideTile(title: String, art: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = modifier) {
        GuideArt(art, null, ratio = 1.55f)
        Text(title, Modifier.fillMaxWidth().padding(8.dp), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleSmall)
    }
}
