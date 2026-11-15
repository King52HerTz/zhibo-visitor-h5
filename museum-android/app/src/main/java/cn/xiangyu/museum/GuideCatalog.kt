package cn.xiangyu.museum

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val art: Int, val child: String, val adult: String, val apiId: String? = null
)

internal val guideExhibits = listOf(
    GuideExhibit("silk-plain-straight", "直裾素纱单衣", "织物", "西汉", "马王堆一号汉墓",
        R.drawable.zhibo_gauze, FirstChapter.relics[0].childText, FirstChapter.relics[0].adultText, "silk-plain-straight"),
    GuideExhibit("t-banner", "T形帛画", "书画", "西汉", "马王堆一号汉墓", R.drawable.zhibo_banner,
        "这幅画画在丝织物上。先看最宽的地方，你能找到太阳和月亮吗？",
        "帛画以丝织物为载体，画面呈T形。可从画面布局、图像细节和出土情境了解这件文物。"),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExhibitPage(audience: Audience, selected: GuideExhibit?, saved: Boolean,
                         onOpen: (GuideExhibit) -> Unit, onBack: () -> Unit,
                         onAsk: (GuideExhibit) -> Unit, onSave: (GuideExhibit) -> Unit,
                         onNote: (Relic) -> Unit) {
    val child = audience == Audience.CHILD
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("全部") }
    if (selected != null) {
        TextButton(onClick = onBack) { Text("返回展品") }
        GuideTitle(selected.name)
        GuideArt(selected.art, selected.name, scale = ContentScale.Fit)
        if (!child) {
            Text(listOfNotNull(selected.period.takeIf { it.isNotBlank() }, selected.category, selected.origin)
                .joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(if (child) selected.child else selected.adult, style = MaterialTheme.typography.bodyLarge)
        Text("文物示意图", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MuseumButton({ onAsk(selected) }) { Text("问问这件文物") }
        OutlinedButton(onClick = { onSave(selected) }) { Text(if (saved) "取消收藏" else "收藏展品") }
        FirstChapter.relics.firstOrNull { it.id == selected.id }?.let { relic ->
            OutlinedButton(onClick = { onNote(relic) },
                modifier = Modifier.semantics { contentDescription = "记下${relic.title}的观察" }) { Text("记下观察") }
        }
        return
    }
    GuideTitle(if (child) "看文物" else "馆藏展品")
    if (!child) {
        MuseumField(query, { query = it }, "搜索文物、年代或类别")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("全部", "书画", "漆器", "青铜器", "织物").forEach {
                FilterChip(category == it, { category = it }, label = { Text(it) })
            }
        }
    }
    val featured = if (child) guideExhibits[0] else guideExhibits[1]
    val showFeature = child || (query.isBlank() && category == "全部")
    if (showFeature) {
        OutlinedCard(onClick = { onOpen(featured) }, modifier = Modifier.fillMaxWidth()) {
            GuideArt(featured.art, featured.name, ratio = if (child) 1.43f else 1.66f,
                scale = ContentScale.Fit)
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (child) "素纱单衣" else featured.name, style = MaterialTheme.typography.titleLarge)
                Text(if (child) "这件衣服为什么这么轻？" else
                    "年代　西汉　　类别　帛画\n出土　马王堆一号汉墓", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { onOpen(featured) }, shape = RoundedCornerShape(8.dp)) {
                    Text(if (child) "看看细节" else "查看档案")
                }
            }
        }
    }
    val results = if (child) guideExhibits.filter { it != featured }.sortedBy { if (it.id == "bronze-mirror") 0 else 1 }
    else guideExhibits.filter { exhibit ->
        (!showFeature || exhibit != featured) && (category == "全部" || exhibit.category == category) &&
            listOf(exhibit.name, exhibit.period, exhibit.category, exhibit.origin.orEmpty()).any { query.trim() in it }
    }.let { items -> if (showFeature) items.sortedBy { if (it.id in listOf("lacquer-box", "bronze-ding")) 0 else 1 } else items }
    Text(if (showFeature) "更多文物" else "搜索结果", style = MaterialTheme.typography.titleLarge)
    if (results.isEmpty()) Text("没有找到相关展品。可以试试其他关键字。")
    results.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { exhibit ->
                GuideTile(exhibit.name, exhibit.art, Modifier.weight(1f)) { onOpen(exhibit) }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
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
