package cn.xiangyu.museum

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
internal fun BrandSplash(onFinished: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (visible) 1f else .82f, label = "brand")
    LaunchedEffect(Unit) { visible = true; delay(1000); onFinished() }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Image(painterResource(R.drawable.app_emblem), null,
            Modifier.size(112.dp).graphicsLayer { scaleX = scale; scaleY = scale })
        Spacer(Modifier.height(20.dp))
        Text("湘遇", style = MaterialTheme.typography.headlineLarge)
        Text("文物里的故事", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun StoryInvitation(previewOnly: Boolean = false, onReady: () -> Unit) {
    val context = LocalContext.current
    val videos = remember(context) { storyVideoResources(context) }
    if (!previewOnly && videos.first() != 0) StoryVideoInvitation(videos.take(1), onReady)
    else StoryboardInvitation(onReady)
}

@Composable
private fun StoryboardInvitation(onReady: () -> Unit) {
    var frame by rememberSaveable { mutableIntStateOf(0) }
    val images = listOf(R.drawable.story_invitation, R.drawable.workshop_scene, R.drawable.chapter_journal)
    val lines = listOf("阿帛收到了一本少了一页的旅行手帐。", "一根丝线，会变成怎样的衣服？一起去找找。", "发现轻薄的衣料，再找一片花叶，补上这页故事。")
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("衣服里的秘密", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        Image(painterResource(images[frame]), "原创故事分镜 ${frame + 1}",
            Modifier.fillMaxWidth().aspectRatio(1.5f))
        Spacer(Modifier.height(20.dp))
        Text(lines[frame], style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))
        Button(onClick = { if (frame < 2) frame++ else onReady() }) {
            Text(if (frame < 2) "翻开下一幕" else "开始旅程")
        }
        TextButton(onClick = onReady) { Text("跳过引入") }
        Text("分镜预览 · 视频待制作", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun AdultRelics(onAsk: (Relic) -> Unit) {
    Text("文物档案", style = MaterialTheme.typography.headlineLarge)
    FirstChapter.relics.forEach { relic ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(relic.title, style = MaterialTheme.typography.titleLarge)
                Text(relic.adultText, style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = { onAsk(relic) }) { Text("深入讲解") }
            }
        }
    }
}
