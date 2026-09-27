package cn.xiangyu.museum

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

@Composable
fun WorkshopContent(progress: TaskProgress, onTask: (ObservationTask) -> Unit, modifier: Modifier = Modifier) {
    var clip by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.medium)) {
        Text("阿帛的汉代衣坊", style = MaterialTheme.typography.headlineLarge)
        Text("阿帛的旅行卡少了两枚贴纸。点点衣坊里的东西，帮它找回来！")
        Surface(shape = MaterialTheme.shapes.extraLarge) {
            Box(Modifier.fillMaxWidth().aspectRatio(1.5f)) {
                Image(painterResource(R.drawable.workshop_scene), null, Modifier.fillMaxSize())
                Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(Space.small),
                    horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(onClick = { clip = !clip }, modifier = Modifier.heightIn(min = Space.touch)) { Text("丝线") }
                    Button(onClick = { onTask(FirstChapter.tasks[0]) }, modifier = Modifier.heightIn(min = Space.touch)) { Text("衣服") }
                    Button(onClick = { onTask(FirstChapter.tasks[1]) }, modifier = Modifier.heightIn(min = Space.touch)) { Text("花叶") }
                }
            }
        }
        Text("原创故事场景 · 不是汉代衣坊的真实复原", style = MaterialTheme.typography.bodySmall)
        if (clip) SilkAnimation()
        ChapterProgress(progress)
        FirstChapter.tasks.forEach { task ->
            MuseumButton(onClick = { onTask(task) }) {
                Text(if (task.id in progress.completed) "再玩一次 · ${task.title}" else "去发现 · ${task.title}")
            }
        }
    }
}

@Composable
private fun SilkAnimation() {
    var playing by rememberSaveable { mutableStateOf(false) }
    var tick by rememberSaveable { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(playing, lifecycle) {
        if (playing) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (tick < 120) { delay(100); tick++ }
            playing = false
        }
    }
    val ink = MaterialTheme.colorScheme.primary
    val stage = (tick / 40).coerceAtMost(2)
    val captions = listOf("细细的丝线，是织造的材料。", "丝线交错，织成衣料。", "衣料经过裁剪、缝制，可以做成衣服。")
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(Space.medium), verticalArrangement = Arrangement.spacedBy(Space.small)) {
            Text("丝线的旅行 · 12 秒小动画", style = MaterialTheme.typography.titleMedium)
            Canvas(Modifier.fillMaxWidth().height(100.dp).semantics { contentDescription = captions[stage] }) {
                val reveal = ((tick % 40) / 40f).coerceAtLeast(0.1f)
                if (stage < 2) {
                    repeat(12) { index ->
                        val x = size.width * (0.15f + index * 0.06f)
                        drawLine(ink, Offset(x, size.height * 0.1f), Offset(x, size.height * 0.9f), 2f)
                        if (stage == 1) drawLine(ink.copy(alpha = 0.6f),
                            Offset(size.width * 0.1f, size.height * (0.1f + index * 0.065f)),
                            Offset(size.width * (0.1f + 0.8f * reveal), size.height * (0.1f + index * 0.065f)), 2f)
                    }
                } else {
                    drawRect(ink.copy(alpha = 0.2f), Offset(size.width * 0.35f, 0f),
                        androidx.compose.ui.geometry.Size(size.width * 0.3f, size.height))
                    drawLine(ink, Offset(size.width * 0.2f, size.height * 0.2f), Offset(size.width * 0.8f, size.height * 0.2f), 20f)
                    drawLine(ink, Offset(size.width * 0.4f, 0f), Offset(size.width * 0.6f, size.height * 0.6f), 3f)
                }
            }
            Text(captions[stage])
            Text("${tick / 10} / 12 秒", style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(progress = { tick / 120f }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                OutlinedButton(onClick = { if (tick >= 120) tick = 0; playing = !playing }) {
                    Text(if (playing) "暂停动画" else if (tick == 0) "播放动画" else "继续播放")
                }
                TextButton(onClick = { tick = 0; playing = true }) { Text("从头播放") }
            }
            Text("织造概念示意 · 无配音，不是文物制作过程的精确复原", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun WorkshopTaskContent(task: ObservationTask, state: ProgressState, onComplete: (String) -> Unit,
                        modifier: Modifier = Modifier) {
    val done = task.id in state.progress.completed
    var revealed by rememberSaveable(task.id) { mutableStateOf(false) }
    var placed by rememberSaveable(task.id) { mutableStateOf(false) }
    val ready = if (task == FirstChapter.tasks[0]) revealed else placed
    Column(modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(Space.medium)) {
        Text(task.title, style = MaterialTheme.typography.headlineLarge)
        if (task == FirstChapter.tasks[0]) {
            Text("轻轻掀开故事里的衣料，看看后面藏着什么。")
            val alpha by animateFloatAsState(if (revealed) 0.25f else 1f, label = "gauzeReveal")
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceVariant) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.abo), null, Modifier.size(130.dp))
                    Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }
                        .background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                        Text("衣料观察窗", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            MuseumButton(onClick = { revealed = !revealed }) { Text(if (revealed) "盖回衣料" else "掀开衣料") }
            if (revealed) Text("找到阿帛啦！素纱单衣的衣料轻薄，仔细看能发现丝纱的线索。")
            Text("这是故事观察窗，不是文物透光实验。馆内请勿触摸展品。", style = MaterialTheme.typography.bodySmall)
        } else {
            Text("把花叶贴纸拖进旅行卡，完成你的纹样。也可以点按钮放入。")
            FlowerPuzzle(placed, onPlaced = { placed = true })
            if (placed) Text("拼好了！花叶让我们联想到植物。文物衣袍的纹样也藏着观察线索。")
            Text("自由创作小游戏 · 不是对文物纹样的精确复原", style = MaterialTheme.typography.bodySmall)
        }
        MuseumButton(onClick = { onComplete(task.id) }, enabled = ready && !done && !state.loading) {
            Text(if (done) "已收进手帐" else "把发现收进手帐")
        }
        state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun FlowerPuzzle(placed: Boolean, onPlaced: () -> Unit) {
    var target by remember { mutableStateOf(Rect.Zero) }
    var source by remember { mutableStateOf(Rect.Zero) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    val currentPlaced by rememberUpdatedState(onPlaced)
    Column(verticalArrangement = Arrangement.spacedBy(Space.small)) {
        Row(Modifier.fillMaxWidth().height(180.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(80.dp).testTag("flower-piece").onGloballyPositioned { source = it.boundsInRoot() }
                .graphicsLayer { translationX = drag.x; translationY = drag.y }
                .pointerInput(placed) {
                    if (!placed) detectDragGestures(onDragEnd = {
                        if (target.contains(source.center + drag)) currentPlaced()
                        drag = Offset.Zero
                    }, onDragCancel = { drag = Offset.Zero }) { change, amount -> change.consume(); drag += amount }
                }, contentAlignment = Alignment.Center) {
                if (!placed) TaskSticker(1, true, Modifier.fillMaxSize().semantics { contentDescription = "花叶贴纸" })
            }
            Surface(Modifier.width(180.dp).fillMaxHeight().testTag("flower-target")
                .onGloballyPositioned { target = it.boundsInRoot() }, shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant) {
                Box(contentAlignment = Alignment.Center) {
                    if (placed) TaskSticker(1, true, Modifier.size(100.dp)) else Text("放进旅行卡", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        OutlinedButton(onClick = onPlaced, enabled = !placed, modifier = Modifier.heightIn(min = Space.touch)) {
            Text(if (placed) "花叶已放入" else "点一下放入花叶")
        }
    }
}
