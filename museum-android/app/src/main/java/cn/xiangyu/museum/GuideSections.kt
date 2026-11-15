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

internal data class GuideStory(val id: String, val title: String, val art: Int, val text: String)
internal val guideStories = listOf(
    GuideStory("han-day", "汉代的一天", R.drawable.zhibo_market,
        "想象自己来到汉代的长沙。早晨，做饭的人拿出漆器，织工把丝线穿进织机。我们今天看到的衣服和餐具，就是认识他们日常生活的线索。先看一看：哪件文物让你想起自己每天都会用到的东西？"),
    GuideStory("stone", "石头上的故事", R.drawable.zhibo_rubbing,
        "古人把车马、动物和人的活动刻在石头上。后来，人们用纸和墨把刻出来的图案印下来，这叫拓印。想一想：如果要在石头上留下一幅画，你会画哪一件事？"),
    GuideStory("bamboo", "写在竹片上的信", R.drawable.zhibo_bamboo,
        "古人可以把字写在削好的竹片上，再用绳子把竹片连起来。读的时候，要顺着竹片一片一片看。今天我们翻纸页，就不必把竹片绑起来了。")
)

internal data class GuideGame(val id: String, val title: String, val art: Int, val hint: String,
                              val options: List<String>, val answer: Int, val fact: String)
internal val guideGames = listOf(
    GuideGame("banner", "帛画寻宝", R.drawable.zhibo_game_banner, "太阳在这张帛画示意图的哪里？",
        listOf("画面右上", "画面左下", "画面正中"), 0, "这里用圆形画出太阳。也可以去展品档案，找找帛画上的月亮。"),
    GuideGame("bells", "编钟排排队", R.drawable.zhibo_bells, "观察图里的四件钟，按从小到大的顺序点选。",
        listOf("最小的钟", "最大的钟", "中间的钟"), 1, "你按大小排好了编钟。还可以留意钟口形状和表面花纹。"),
    GuideGame("pottery", "陶罐拼一拼", R.drawable.zhibo_pottery, "从上往下，依次找出陶罐的三个部位。",
        listOf("最下面", "中间", "最上面"), 2, "器口在最上，下面依次是器身和底部。考古工作里还会对照断口和纹饰来判断碎片能否相接。")
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
                       onOpenOriginal: () -> Unit) {
    if (selected != null) {
        TextButton(onClick = onBack) { Text("返回故事") }
        GuideTitle(selected.title)
        GuideArt(selected.art, null)
        GuideNarration(selected.text, "听故事")
        Text(if (selected.id == "han-day") "场景想象" else "文物故事",
            color = MaterialTheme.colorScheme.primary)
        Text(selected.text, style = MaterialTheme.typography.bodyLarge)
        return
    }
    GuideTitle("听故事")
    GuideFeature(guideStories.first().title, "两千年前，人们怎么过日子？", guideStories.first().art,
        "开始听") { onOpen(guideStories.first()) }
    Text("更多故事", style = MaterialTheme.typography.titleLarge)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        guideStories.drop(1).forEach { GuideTile(it.title, it.art, Modifier.weight(1f)) { onOpen(it) } }
    }
    OutlinedButton(onClick = onOpenOriginal) { Text("衣服里的秘密") }
}

@Composable
internal fun GamePage(selected: GuideGame?, onOpen: (GuideGame) -> Unit, onBack: () -> Unit,
                      onFinish: (GuideGame) -> Unit, onStorySlice: () -> Unit, onPuzzle: () -> Unit) {
    if (selected != null) {
        var choice by remember(selected.id) { mutableIntStateOf(-1) }
        TextButton(onClick = onBack) { Text("返回游戏") }
        GuideTitle(selected.title)
        GuideArt(selected.art, null)
        Text(selected.hint, style = MaterialTheme.typography.titleLarge)
        if (selected.id == "banner") {
            selected.options.forEachIndexed { i, option ->
                OutlinedButton(onClick = { choice = i; if (i == selected.answer) onFinish(selected) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch)) { Text(option) }
            }
            if (choice >= 0) MuseumNotice(if (choice == selected.answer) selected.fact else "再看看图里的细节，试一次。")
        } else {
            val (pieces, solution) = if (selected.id == "bells")
                listOf("最左边", "左数第二", "左数第三", "最右边") to listOf(3, 2, 1, 0)
            else listOf("底部", "口沿", "器身") to listOf(1, 2, 0)
            ArrangePieces(selected.id, pieces, solution, selected.fact) { onFinish(selected) }
        }
        return
    }
    GuideTitle("玩历史")
    val feature = guideGames.first()
    GuideFeature(feature.title, "在帛画里找找太阳", feature.art, "开始找") { onOpen(feature) }
    Text("更多游戏", style = MaterialTheme.typography.titleLarge)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        guideGames.drop(1).forEach { GuideTile(it.title, it.art, Modifier.weight(1f)) { onOpen(it) } }
    }
    OutlinedButton(onClick = onStorySlice) { Text("三件文物的旅程") }
    OutlinedButton(onClick = onPuzzle) { Text("进入横屏衣坊") }
}

@Composable
private fun ArrangePieces(id: String, labels: List<String>, solution: List<Int>,
                          fact: String, onFinish: () -> Unit) {
    var order by rememberSaveable(id) { mutableStateOf(arrayListOf<Int>()) }
    Text(if (order.isEmpty()) "依次点选下方的部件" else order.joinToString(" → ") { labels[it] },
        style = MaterialTheme.typography.bodyMedium)
    labels.forEachIndexed { index, label ->
        OutlinedButton(onClick = {
            val next = ArrayList(order + index)
            order = next
            if (next == solution) onFinish()
        }, enabled = index !in order,
            modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch)) { Text(label) }
    }
    if (order.size == solution.size)
        MuseumNotice(if (order == solution) fact else "顺序还差一点，清空之后可以再试一次。")
    if (order.isNotEmpty()) TextButton(onClick = { order = arrayListOf() }) { Text("重新排列") }
}

@Composable
private fun GuideFeature(title: String, subtitle: String, art: Int, action: String, onClick: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        GuideArt(art, null)
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onClick, shape = RoundedCornerShape(8.dp)) { Text(action) }
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
        GuideArt(R.drawable.zhibo_bronze, "铜鼎示意图")
        listOf("耳" to "位于口沿上方，观察它与器口的连接。", "腹" to "器身的主要部分，表面常见纹饰。",
            "足" to "支撑器身，可从形状和数量观察器物特征。").forEach { (name, detail) ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(name, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                Text(detail, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            }
        }
        MuseumButton({ onOpen(guideExhibits.last()) }) { Text("查看铜鼎") }
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
                            GuideArt(item.art, null, ratio = 1.7f)
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
    TextButton(onClick = { onOpen(guideExhibits[1]) }) { Text("查看出土文物 ›") }
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
                      onMode: () -> Unit, onLogout: () -> Unit, onNotes: @Composable () -> Unit) {
    val child = audience == Audience.CHILD
    var section by remember { mutableStateOf("") }
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
        TextButton(onClick = {
            if (label == "对话记录") onHistory() else section = if (section == label) "" else label
        }, modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch)) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text("›")
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (section == label) when (label) {
            labels.first() -> {
                val saved = guideExhibits.filter { it.id in library.saved }
                if (saved.isEmpty()) Text("还没有收藏，可以在展品详情里收藏。")
                saved.forEach { TextButton(onClick = { onOpen(it) }) { Text(it.name) } }
            }
            "游戏进度" -> guideGames.forEach { game ->
                TextButton(onClick = { onGame(game) }) {
                    Text(game.title + if (game.id in library.games) " · 已完成" else " · 未完成")
                }
            }
            "观展笔记", "观展手帐" -> onNotes()
        }
    }
    OutlinedButton(onClick = onMode, modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch)) {
        Text("切换导览模式  ⇄")
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        FilledTonalButton(onClick = onLogout, modifier = Modifier.fillMaxWidth(.55f).heightIn(min = 48.dp)) {
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
