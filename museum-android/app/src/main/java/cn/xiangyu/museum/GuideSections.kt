package cn.xiangyu.museum

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale

internal data class GuideStory(val id: String, val title: String, val art: Int, val text: String,
                               val related: List<String>)
internal val guideStories = listOf(
    GuideStory("han-day", "汉代的一天", R.drawable.zhibo_market,
        "想象来到西汉的长沙。有位织工正在整理丝线，有人在摆放食具。我们没有那天的照片，却能从出土的衣服和餐具找到线索。\n\n" +
        "素纱单衣的衣身那么薄，深色的领缘却很容易看清。用手想象捏住那层纱，就会明白为什么织和缝都得格外小心。\n\n" +
        "饭桌也有线索。漆耳杯有两只小耳，拿起来更方便。衣服和杯子是两件不一样的东西，却都留下了汉代工匠动手制作的痕迹。",
        listOf("silk-plain-straight", "mwd-eared-cups")),
    GuideStory("stone", "在帛画里找日月", R.drawable.zhibo_game_banner,
        "先看帛画最宽的地方：圆圆的日轮在一边，弯弯的月亮在另一边。\n\n" +
        "顺着画面往下，还有人物和动物。试着比一比，它们是挤在同一处，还是有上、下不同的空间？\n\n" +
        "每个图案都能提出一个问题。下一次看完整的画时，先找自己最熟悉的图形，再留心那些看上去有点奇怪的细节。",
        listOf("t-banner")),
    GuideStory("bamboo", "写在丝帛上的字", R.drawable.zhibo_bamboo,
        "纸张还未普及时，古人可以把字写在竹木上，也可以写在丝帛上。丝帛展开时，字能沿着一列一列排下去。\n\n" +
        "马王堆三号墓出土了两种《老子》抄本。考古报告把它们称为甲本和乙本。今天看到的展品照片只是一小截，凑近能发现每一行留下的墨迹。",
        listOf("mwd-laozi-a"))
)

@Composable
internal fun GuideNarration(text: String, label: String) {
    val context = LocalContext.current
    var ready by remember { mutableStateOf(false) }
    var engine by remember { mutableStateOf<TextToSpeech?>(null) }
    var feedback by remember(text) { mutableStateOf<String?>(null) }
    DisposableEffect(context) {
        val voice = TextToSpeech(context.applicationContext) { status -> ready = status == TextToSpeech.SUCCESS }
        engine = voice
        onDispose { voice.stop(); voice.shutdown() }
    }
    OutlinedButton(onClick = {
        val voice = engine
        if (!ready || voice == null || voice.setLanguage(Locale.SIMPLIFIED_CHINESE) < 0) {
            feedback = "系统朗读暂不可用，可以阅读下方文字。"
        } else {
            feedback = if (voice.speak(text, TextToSpeech.QUEUE_FLUSH, null, "guide") == TextToSpeech.SUCCESS)
                null else "系统朗读暂不可用。"
        }
    }) { Text(label) }
    feedback?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}

@Composable
internal fun StoryPage(selected: GuideStory?, onOpen: (GuideStory) -> Unit, onBack: () -> Unit,
                       onOpenOriginal: () -> Unit, onExhibit: (GuideExhibit) -> Unit) {
    if (selected != null) {
        GuideAction("返回故事", onBack, secondary = true)
        GuideTitle(selected.title)
        GuideArt(selected.art, null)
        GuideNarration(selected.text, "听故事")
        Text(if (selected.id == "han-day") "场景想象" else "文物故事",
            color = MaterialTheme.colorScheme.primary)
        Text(selected.text, style = MaterialTheme.typography.bodyLarge)
        Text("故事里的文物", style = MaterialTheme.typography.titleLarge)
        selected.related.forEach { id ->
            val exhibit = museumCollection.first { it.id == id }
            GuideRowAction(exhibit.name, exhibit.period, { onExhibit(exhibit) }, artwork = exhibit.art)
        }
        return
    }
    GuideTitle("听故事")
    GuideFeature(guideStories.first().title, "两千年前，人们怎么过日子？", guideStories.first().art,
        "阅读故事") { onOpen(guideStories.first()) }
    Text("更多故事", style = MaterialTheme.typography.titleLarge)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        guideStories.drop(1).forEach { GuideTile(it.title, it.art, Modifier.weight(1f)) { onOpen(it) } }
    }
    GuideRowAction("衣服里的秘密", "故事、纹样和观察手帐", onOpenOriginal, artwork = R.drawable.zhibo_gauze)
}

@Composable
internal fun GamePage(selected: GuideGame?, onOpen: (GuideGame) -> Unit, onBack: () -> Unit,
                      onFinish: (GuideGame) -> Unit, onStorySlice: () -> Unit, onPuzzle: () -> Unit,
                      onExhibit: (GuideExhibit) -> Unit) {
    if (selected != null) {
        GuideAction("返回游戏", onBack, secondary = true)
        GuideTitle(selected.title)
        val exhibitId = when (selected) {
            GuideGame.GAUZE -> "silk-plain-straight"
            GuideGame.BANNER -> "t-banner"
            GuideGame.LACQUER -> "mwd-eared-cups"
            GuideGame.DAOYIN -> "mwd-daoyin"
        }
        val exhibit = museumCollection.first { it.id == exhibitId }
        MuseumGame(selected, onRelated = { onExhibit(exhibit) }) { onFinish(selected) }
        return
    }
    GuideTitle("玩历史")
    val feature = guideGames.first()
    GuideFeature(feature.title, feature.instruction, R.drawable.zhibo_game_banner, "开始找") { onOpen(feature) }
    Text("更多游戏", style = MaterialTheme.typography.titleLarge)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        guideGames.drop(1).forEach { GuideTile(it.title, it.art, Modifier.weight(1f)) { onOpen(it) } }
    }
    GuideRowAction("三件文物的旅程", "串起素纱单衣、帛画和导引图", onStorySlice, artwork = R.drawable.zhibo_bamboo)
    GuideRowAction("进入衣坊", "完成花叶纹样和衣料观察", onPuzzle, artwork = R.drawable.zhibo_textile)
}

@Composable
private fun GuideFeature(title: String, subtitle: String, art: Int, action: String, onClick: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        GuideArt(art, null)
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyLarge)
            GuideAction(action, onClick, Modifier.fillMaxWidth(), end = "›")
        }
    }
}

@Composable
internal fun GraphPage(onOpen: (GuideExhibit) -> Unit, onAsk: (GuideExhibit) -> Unit) {
    var structure by remember { mutableStateOf(false) }
    var selected by remember { mutableIntStateOf(1) }
    val exhibit = guideExhibits[selected]
    GuideTitle("历史图解")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(!structure, { structure = false }, label = { Text("人物关系") })
        FilterChip(structure, { structure = true }, label = { Text("器物结构") })
    }
    if (structure) {
        Text("从结构读铜鼎", style = MaterialTheme.typography.titleLarge)
        GuideArt(R.drawable.collection_ding, "大禾人面纹方鼎", scale = androidx.compose.ui.layout.ContentScale.Fit)
        listOf("耳" to "位于口沿上方，观察它与器口的连接。", "腹" to "器身的主要部分，表面常见纹饰。",
            "足" to "这件方鼎以四足支撑器身。观察各足与四个器角的关系。").forEach { (name, detail) ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(name, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                Text(detail, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            }
        }
        MuseumButton({ onOpen(museumCollection.first { it.id == "human-face-ding" }) }) { Text("查看铜鼎") }
    } else {
        Text("马王堆一号汉墓", style = MaterialTheme.typography.titleLarge)
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                GuideArt(R.drawable.zhibo_xinzhui, "辛追形象示意", Modifier.width(76.dp), 1.45f)
                Text("辛追", style = MaterialTheme.typography.titleMedium)
                RelationshipLabel("墓主人")
                GuideArt(R.drawable.zhibo_tomb, "墓葬插画示意", Modifier.width(76.dp), 1.45f)
                Text("一号汉墓", style = MaterialTheme.typography.titleMedium)
                RelationshipLabel("出土文物")
                val connector = MaterialTheme.colorScheme.outline
                HorizontalDivider(Modifier.fillMaxWidth(.51f), color = connector)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    repeat(2) { Box(Modifier.width(1.dp).height(10.dp).background(connector)) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(1, 0).forEach { index ->
                        val item = guideExhibits[index]
                        OutlinedCard(onClick = { selected = index }, modifier = Modifier.weight(1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (selected == index)
                                MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                            GuideArt(item.art, null, ratio = 1.7f, scale = androidx.compose.ui.layout.ContentScale.Fit)
                            Text(item.name, Modifier.fillMaxWidth().padding(6.dp), textAlign = TextAlign.Center)
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text(exhibit.name, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onOpen(exhibit) }, modifier = Modifier.weight(1f)) { Text("查看档案") }
                    OutlinedButton(onClick = { onAsk(exhibit) }, modifier = Modifier.weight(1f)) { Text("问这件文物") }
                }
            }
        }
    }
}

@Composable
private fun RelationshipLabel(label: String) {
    val lineColor = MaterialTheme.colorScheme.outline
    Box(Modifier.height(8.dp).width(1.dp).background(lineColor))
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(label, Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
    }
    Box(Modifier.height(8.dp).width(1.dp).background(lineColor))
}

@Composable
internal fun TimelinePage(onOpen: (GuideExhibit) -> Unit) {
    GuideTitle("历史年表")
    Text("马王堆汉墓", style = MaterialTheme.typography.titleLarge)
    TimelinePeriod("西汉")
    TimelineRecord("公元前168年", "三号汉墓下葬", "纪年木牍为断代提供依据。", R.drawable.zhibo_slips)
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    TimelinePeriod("考古发掘")
    TimelineRecord("1972年", "发掘一号汉墓", null, null)
    GuideRowAction("查看出土文物", "一号汉墓T形帛画", { onOpen(guideExhibits[1]) }, artwork = R.drawable.collection_banner)
    TimelineRecord("1973—1974年", "发掘二号、三号汉墓", null, R.drawable.zhibo_excavation)
}

@Composable
private fun TimelinePeriod(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(11.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        Text(title, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun TimelineRecord(date: String, title: String, detail: String?, art: Int?) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.padding(start = 5.dp).fillMaxHeight().width(1.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = .65f)))
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(date, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            Text(title, style = MaterialTheme.typography.titleLarge)
            detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
        if (art != null) GuideArt(art, null, Modifier.width(86.dp), ratio = .92f)
    }
}

@Composable
internal fun MinePage(audience: Audience, library: GuideLibrary, onOpen: (GuideExhibit) -> Unit,
                      onStory: (GuideStory) -> Unit, onGame: (GuideGame) -> Unit, onHistory: () -> Unit,
                      onMode: () -> Unit, onLogout: () -> Unit, editing: Boolean, identityEnabled: Boolean,
                      onNotes: @Composable () -> Unit) {
    val child = audience == Audience.CHILD
    val notes = if (child) "观展手帐" else "观展笔记"
    var section by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(editing) { if (editing) section = notes }
    if (editing) onNotes() else {
        GuideTitle("我的")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(if (child) R.drawable.zhibo_child_mode else R.drawable.zhibo_nav_me), null,
                Modifier.size(50.dp))
            Text(if (child) "小小观展员" else "观展者", style = MaterialTheme.typography.titleLarge)
        }
        if (child) {
            guideStories.firstOrNull { it.id == library.story }?.let {
                ResumePanel("上次看到", it.title, it.art, "继续听") { onStory(it) }
            }
        } else guideExhibits.firstOrNull { it.id == library.recent }?.let {
            ResumePanel("最近浏览", it.name, it.art, "继续查看") { onOpen(it) }
        }
        Text("观展记录", style = MaterialTheme.typography.titleLarge)
        val labels = if (child) listOf("收藏文物", "游戏进度", "对话记录", "观展手帐")
            else listOf("收藏展品", "观展笔记", "对话记录")
        labels.forEach { label ->
            val image = when (label) {
                "对话记录" -> R.drawable.zhibo_nav_chat
                "游戏进度" -> R.drawable.zhibo_nav_game
                "观展笔记", "观展手帐" -> R.drawable.zhibo_nav_story
                else -> R.drawable.zhibo_nav_exhibit
            }
            GuideRowAction(label, onClick = {
                if (label == "对话记录") onHistory() else section = if (section == label) "" else label
            }, artwork = image)
            if (section == label) when (label) {
                labels.first() -> {
                    val saved = guideExhibits.filter { it.id in library.saved }
                    if (saved.isEmpty()) Text("还没有收藏，可以在展品详情里收藏。")
                    saved.forEach { item -> GuideRowAction(item.name, item.period, { onOpen(item) }, artwork = item.art) }
                }
                "游戏进度" -> guideGames.forEach { game ->
                    GuideRowAction(game.title, if (game.id in library.games) "已完成" else "尚未完成", { onGame(game) }, artwork = game.art)
                }
                "观展笔记", "观展手帐" -> onNotes()
            }
        }
    }
    OutlinedButton(onClick = onMode, enabled = identityEnabled, modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch)) {
        Text("切换导览模式  ⇄")
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        FilledTonalButton(onClick = onLogout, enabled = identityEnabled, modifier = Modifier.fillMaxWidth(.55f).heightIn(min = 48.dp)) {
            Text("退出登录")
        }
    }
}

@Composable
private fun ResumePanel(label: String, title: String, art: Int, action: String, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            GuideArt(art, null, Modifier.weight(1f), ratio = 1.3f)
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodySmall)
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(action + " ›", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
