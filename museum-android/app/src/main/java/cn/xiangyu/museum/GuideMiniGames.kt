package cn.xiangyu.museum

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

internal enum class GuideGame(val id: String, val title: String, val art: Int, val instruction: String) {
    GAUZE("gauze-observe", "观察素纱单衣", R.drawable.collection_gauze, "看实物照片，在衣服上找出三个部位"),
    BANNER("banner-observe", "帛画寻宝", R.drawable.collection_banner, "在帛画上点亮三条线索"),
    LACQUER("lacquer-restore", "拼合漆耳杯", R.drawable.collection_lacquer, "交换图块，把实物照片拼完整"),
    DAOYIN("daoyin-pairs", "导引图配对", R.drawable.collection_daoyin, "翻开图卡，找到相同姿势的人物")
}

internal val guideGames = listOf(GuideGame.BANNER, GuideGame.LACQUER, GuideGame.DAOYIN)

@Composable
internal fun MuseumGame(game: GuideGame, onRelated: (() -> Unit)? = null, onComplete: () -> Unit) {
    var round by rememberSaveable(game.id) { mutableIntStateOf(0) }
    var complete by rememberSaveable(game.id) { mutableStateOf(false) }
    fun finish() {
        if (!complete) { complete = true; onComplete() }
    }
    Text(game.instruction, style = MaterialTheme.typography.bodyLarge)
    key(game.id, round) {
        when (game) {
            GuideGame.GAUZE -> HotspotGame(game.art, 1f, gauzeClues,
                listOf(.51f to .20f, .86f to .23f, .48f to .85f), ::finish)
            GuideGame.BANNER -> HotspotGame(game.art, .46f, bannerClues,
                listOf(.82f to .39f, .19f to .38f, .50f to .72f), ::finish)
            GuideGame.LACQUER -> LacquerGame(::finish)
            GuideGame.DAOYIN -> DaoyinGame(::finish)
        }
    }
    if (complete) {
        MuseumNotice(when (game) {
            GuideGame.GAUZE -> "观察完成。直裾素纱单衣的衣身下缘较直，留意细细的丝纱和颜色较深的衣缘。"
            GuideGame.BANNER -> "寻宝完成。帛画上缘同时出现日、月，细看同一区域还能找到动物和云气纹。"
            GuideGame.LACQUER -> "拼合完成。观察两侧伸出的耳状部位，可以看懂它为什么叫耳杯。"
            GuideGame.DAOYIN -> "配对完成。导引图用人物姿势记录古人对身体活动的认识。"
        })
        if (onRelated != null) GuideAction("看对应文物", onRelated, Modifier.fillMaxWidth(), end = "›")
        GuideAction("再玩一次", { complete = false; round++ }, secondary = true)
    }
}

private val bannerClues = listOf(
    "先找画里的太阳" to "看看画面上部，哪边有圆形日轮？",
    "再找弯弯的月亮" to "找帛画上部像弯钩的图案。",
    "最后找画中的人物" to "往日月图案下方看，有一组面向彼此的人。"
)

private val gauzeClues = listOf(
    "找到交叠的衣领" to "两条深色衣缘在胸前交会。",
    "再找一边的袖口" to "沿着伸开的衣袖找到末端。",
    "最后找整件衣服的下摆" to "看衣身最下面的一条边。"
)

@Composable
private fun HotspotGame(art: Int, fraction: Float, clues: List<Pair<String, String>>,
                        marks: List<Pair<Float, Float>>, onComplete: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var feedback by rememberSaveable { mutableStateOf("") }
    val bitmap = ImageBitmap.imageResource(art)
    val upperHeight = (bitmap.height * fraction).toInt()
    Text("已找到 ${step}/3", style = MaterialTheme.typography.titleMedium)
    clues.getOrNull(step)?.let { (title, hint) ->
        Text(title, style = MaterialTheme.typography.titleLarge)
        GuideAction("提示", { feedback = hint }, secondary = true)
    }
    OutlinedCard(Modifier.fillMaxWidth().widthIn(max = 420.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / upperHeight)) {
            Image(remember(bitmap, upperHeight) { BitmapPainter(bitmap, srcSize = IntSize(bitmap.width, upperHeight)) },
                "文物观察图", Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
            marks.forEachIndexed { index, (x, y) ->
                FilledTonalButton(onClick = {
                    if (index == step) {
                        step++
                        feedback = "找到了！"
                        if (step == clues.size) onComplete()
                    } else feedback = "还没找到。再看看图案的位置。"
                }, enabled = index >= step, contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.offset(x = maxWidth * x - 24.dp, y = maxHeight * y - 24.dp)
                        .size(48.dp).semantics { contentDescription = "标记 ${index + 1}" }) {
                    Text(if (index < step) "✓" else "${index + 1}")
                }
            }
        }
    }
    if (feedback.isNotBlank() && step < clues.size) GuideFeedback(feedback)
}

@Composable
private fun LacquerGame(onComplete: () -> Unit) {
    val bitmap = ImageBitmap.imageResource(R.drawable.collection_lacquer)
    val solution = "012345"
    var order by rememberSaveable { mutableStateOf("213504") }
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    var moves by rememberSaveable { mutableIntStateOf(0) }
    var showReference by rememberSaveable { mutableStateOf(false) }
    val solved = order == solution
    if (solved) {
        GuideArt(R.drawable.collection_lacquer, "完整的漆耳杯展陈照片", ratio = 1.8f, scale = ContentScale.Fit)
        return
    }
    Text("已交换 ${moves} 次", style = MaterialTheme.typography.titleMedium)
    GuideAction(if (showReference) "收起参考图" else "看参考图", { showReference = !showReference }, secondary = true)
    if (showReference) GuideArt(R.drawable.collection_lacquer, "完整的漆耳杯展陈照片")
    order.chunked(3).forEachIndexed { row, pieces ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            pieces.forEachIndexed { col, tile ->
                val position = row * 3 + col
                OutlinedCard(onClick = {
                    if (selected == -1 || selected == position) selected = if (selected == position) -1 else position
                    else {
                        order = order.toCharArray().also { values ->
                            val first = values[selected]; values[selected] = values[position]; values[position] = first
                        }.concatToString()
                        moves++
                        selected = -1
                        if (order == solution) onComplete()
                    }
                }, border = BorderStroke(if (selected == position) 3.dp else 1.dp,
                    if (selected == position) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.weight(1f).aspectRatio(1f).semantics {
                        contentDescription = "第 ${position + 1} 格，图块 ${tile.digitToInt() + 1}" +
                            if (selected == position) "，已选中" else ""
                    }) {
                    val index = tile.digitToInt()
                    val width = bitmap.width / 3
                    val height = bitmap.height / 2
                    Image(remember(bitmap, index) {
                        BitmapPainter(bitmap, IntOffset(index % 3 * width, index / 3 * height), IntSize(width, height))
                    }, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                }
            }
        }
    }
}

@Composable
private fun DaoyinGame(onComplete: () -> Unit) {
    val bitmap = ImageBitmap.imageResource(R.drawable.collection_daoyin)
    val deck = "021120"
    var first by rememberSaveable { mutableIntStateOf(-1) }
    var second by rememberSaveable { mutableIntStateOf(-1) }
    var matched by rememberSaveable { mutableIntStateOf(0) }
    val labels = listOf("右臂向上", "双臂高举", "披红衣")
    val regions = listOf(floatArrayOf(.005f,.005f,.089f,.239f),
        floatArrayOf(.103f,.001f,.154f,.244f), floatArrayOf(.185f,.004f,.255f,.216f))
    LaunchedEffect(first, second) {
        if (first >= 0 && second >= 0) {
            if (deck[first] == deck[second]) {
                matched = matched or (1 shl first) or (1 shl second)
                if (matched == 63) onComplete()
            } else delay(850)
            first = -1; second = -1
        }
    }
    Text("已配对 ${Integer.bitCount(matched) / 2}/3", style = MaterialTheme.typography.titleMedium)
    deck.chunked(3).forEachIndexed { row, tiles ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tiles.forEachIndexed { col, tile ->
                val position = row * 3 + col
                val done = matched and (1 shl position) != 0
                val visible = done || position == first || position == second
                val figure = tile.digitToInt()
                OutlinedCard(onClick = { if (first == -1) first = position else second = position },
                    enabled = !visible && second == -1, modifier = Modifier.weight(1f).aspectRatio(.9f).semantics {
                        contentDescription = "第 ${position + 1} 张图卡" + if (visible) "，${labels[figure]}" else "，未翻开"
                    }) {
                    if (visible) {
                        val crop = regions[figure]
                        Image(remember(bitmap, figure) {
                            BitmapPainter(bitmap, IntOffset((crop[0]*bitmap.width).toInt(), (crop[1]*bitmap.height).toInt()),
                                IntSize(((crop[2]-crop[0])*bitmap.width).toInt(), ((crop[3]-crop[1])*bitmap.height).toInt()))
                        }, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    } else Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center) {
                        Text("翻开", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideFeedback(message: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
        Text(message, Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
