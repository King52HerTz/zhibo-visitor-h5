package cn.xiangyu.museum

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
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
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
            guideTabs(audience).forEach { tab ->
                val selected = tab == current
                NavigationBarItem(selected = selected, onClick = { onSelect(tab) },
                    icon = {
                        val ink = if (selected || tab == GuideTab.CHAT) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        val filter = remember(ink) {
                            // Convert the pale paper in the raster glyphs to alpha while keeping the ink edges.
                            ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
                                0f, 0f, 0f, 0f, ink.red * 255,
                                0f, 0f, 0f, 0f, ink.green * 255,
                                0f, 0f, 0f, 0f, ink.blue * 255,
                                0f, 0f, -2.5f, 2.5f, -190f
                            )))
                        }
                        Image(painterResource(tab.art), null, Modifier.size(if (tab == GuideTab.CHAT) 30.dp else 26.dp),
                            colorFilter = filter)
                    },
                    label = {
                        Text(tab.label, Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center)
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
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
            contentPadding = PaddingValues(horizontal = 12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
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
            ExhibitDetail(selected, audience, saved, onBack, { onAsk(selected) }, { onSave(selected) }, onNote, modifier)
        }
        return
    }
    val focus = LocalFocusManager.current
    val featured = museumCollection.first { it.id == if (child) "silk-plain-straight" else "min-fanglei" }
    val term = query.trim()
    val browsing = term.isEmpty() && category == "全部"
    val results = remember(term, category, featured) {
        museumCollection.filter { exhibit ->
            (!browsing || exhibit != featured) && (category == "全部" || exhibit.category == category) &&
                listOf(exhibit.name, exhibit.period, exhibit.category, exhibit.origin.orEmpty()).any { term in it }
        }
    }
    val collections = remember { listOf("全部") + museumCollection.map { it.category }.distinct() }
    val coverHeight = if (LocalConfiguration.current.screenHeightDp < 720) 115.dp else 144.dp

    LazyColumn(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), state = scroll,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item("masthead") { GuideHeader(audience, modeEnabled, onMode) }
        item("title") {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("湖南博物院", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 2.sp)
                GuideTitle(if (child) "看文物" else "馆藏展品")
            }
        }
        item("search") {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("搜索文物、年代或类别") }, shape = RoundedCornerShape(9.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedBorderColor = Color.Transparent),
                trailingIcon = if (query.isEmpty()) null else {
                    { TextButton({ query = "" }) { Text("清除") } }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }))
        }
        item("categories") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                collections.forEach { label ->
                    val selectedCategory = category == label
                    Surface(selected = selectedCategory, onClick = { category = label; focus.clearFocus() },
                        shape = RoundedCornerShape(20.dp),
                        color = if (selectedCategory) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = if (selectedCategory) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant) {
                        Box(Modifier.heightIn(min = 34.dp).padding(horizontal = 9.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center) {
                            Text(label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
        if (browsing) item("feature") {
            Surface(onClick = { focus.clearFocus(); onOpen(featured) }, shape = RoundedCornerShape(12.dp),
                color = if (child) Color(0xFF100E13) else Color(0xFF211815)) {
                Column {
                    Image(painterResource(featured.art), null, Modifier.fillMaxWidth().height(coverHeight).padding(top = 7.dp),
                        contentScale = ContentScale.Fit)
                    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(featured.period, color = Color(0xFFEAD8BE), style = MaterialTheme.typography.labelMedium)
                            Text(featured.name, color = Color(0xFFFFF8EC), style = MaterialTheme.typography.titleLarge)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(if (child) "看看细节" else "查看档案", color = Color(0xFFFFF8EC),
                                style = MaterialTheme.typography.labelMedium)
                            Text("›", color = Color(0xFFEAD8BE), modifier = Modifier.semantics { hideFromAccessibility() })
                        }
                    }
                }
            }
        }
        item("result-heading") {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (browsing) "馆藏目录" else "搜索结果", Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleLarge)
                Text("${results.size + if (browsing) 1 else 0} 件", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (results.isEmpty()) item("no-results") {
            Text("没有找到相关展品。试试文物名称、朝代或材质类别。", style = MaterialTheme.typography.bodyMedium)
            GuideAction("查看全部", { query = ""; category = "全部"; focus.clearFocus() }, secondary = true)
        }
        items(results.chunked(2), key = { it.first().id }) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { exhibit ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button) { focus.clearFocus(); onOpen(exhibit) }) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1.18f).clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                            Image(painterResource(exhibit.art), null, Modifier.matchParentSize().padding(7.dp),
                                contentScale = ContentScale.Fit)
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(exhibit.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.semantics { hideFromAccessibility() })
                        }
                        Text("${exhibit.period} · ${exhibit.category}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ExhibitDetail(exhibit: GuideExhibit, audience: Audience, saved: Boolean,
                          onBack: () -> Unit, onAsk: () -> Unit, onSave: () -> Unit, onNote: ((Relic) -> Unit)?,
                          modifier: Modifier) {
    var fullImage by rememberSaveable { mutableStateOf(false) }
    val collection = exhibit.collection
    val child = audience == Audience.CHILD
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.Center) {
            TextButton(onBack, Modifier.heightIn(min = 48.dp)) {
                Text("‹", Modifier.padding(end = 6.dp))
                Text("返回展品")
            }
            FilledTonalButton(onSave, Modifier.heightIn(min = 48.dp)) {
                Text(if (saved) "取消收藏" else "收藏展品")
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Box(Modifier.fillMaxWidth().height(250.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Image(painterResource(exhibit.art), exhibit.name, Modifier.matchParentSize().padding(12.dp),
                    contentScale = ContentScale.Fit)
                FilledTonalButton({ fullImage = true }, Modifier.align(Alignment.BottomEnd).padding(12.dp)) {
                    Text("看大图")
                }
            }
            Column(Modifier.padding(horizontal = 22.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(listOf(exhibit.period, exhibit.category).filter(String::isNotBlank).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                GuideTitle(exhibit.name)
                val caption = collection?.imageLabel ?: "文物示意图"
                if (caption != "展品图像") Text(caption, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(if (child) "仔细看看" else "文物解读", style = MaterialTheme.typography.titleLarge)
                Text(if (child) exhibit.child else exhibit.adult, style = MaterialTheme.typography.bodyLarge)
                collection?.let { MuseumNotice(it.observation) }
                if (!child && collection != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("馆藏档案", style = MaterialTheme.typography.titleLarge)
                    listOfNotNull("材质" to collection.material, exhibit.origin?.let { "出土" to it },
                        "收藏" to "湖南博物院").forEach { (label, value) ->
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value, Modifier.weight(1f))
                        }
                    }
                }
                if (onNote != null) FirstChapter.relics.firstOrNull { it.id == exhibit.id }?.let { relic ->
                    GuideAction("记下观察", { onNote(relic) }, Modifier.fillMaxWidth()
                        .semantics { contentDescription = "记下${relic.title}的观察" }, secondary = true)
                }
                collection?.let {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    CollectionCredit(it)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        GuideAction("问问这件文物", onAsk, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), end = "›")
    }
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
