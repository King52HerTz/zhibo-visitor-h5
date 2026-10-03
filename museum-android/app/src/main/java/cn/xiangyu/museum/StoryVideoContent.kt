package cn.xiangyu.museum

import android.content.Context
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Suppress("DiscouragedApi")
internal fun storyVideoResources(context: Context) = listOf("story_invitation", "story_workshop", "story_journal")
    .map { context.resources.getIdentifier(it, "raw", context.packageName) }

@Composable
internal fun StoryVideoInvitation(videos: List<Int>, onReady: () -> Unit, modifier: Modifier = Modifier) {
    var frame by rememberSaveable { mutableIntStateOf(0) }
    var replay by remember { mutableIntStateOf(0) }
    var position by rememberSaveable(frame) { mutableIntStateOf(0) }
    var paused by rememberSaveable(frame) { mutableStateOf(false) }
    var complete by rememberSaveable(frame) { mutableStateOf(false) }
    var failed by remember(frame, replay) { mutableStateOf(false) }
    var prepared by remember(frame, replay) { mutableStateOf(false) }
    var player by remember { mutableStateOf<VideoView?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestReady by rememberUpdatedState(onReady)
    val titles = listOf("阿帛的邀请", "走进衣坊", "收下探索邀请")
    val guidance = listOf("跟随纸蝶，去看看衣坊。", "记住两条线索：轻薄的衣料、花叶的纹样。", "现在轮到你来发现了！")

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                player?.let { position = it.currentPosition; it.pause() }
                paused = true
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); player?.stopPlayback() }
    }

    Column(modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(Space.large),
        verticalArrangement = Arrangement.spacedBy(Space.medium)) {
        Text("衣服里的秘密", style = MaterialTheme.typography.headlineMedium)
        Text("${frame + 1} / ${videos.size} · ${titles[frame]}", style = MaterialTheme.typography.titleMedium)
        key(frame, replay) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
                AndroidView(factory = { context ->
                    VideoView(context).apply {
                        contentDescription = "故事视频"
                        player = this
                        setOnPreparedListener { media ->
                            prepared = true
                            media.isLooping = false
                            seekTo(position)
                            if (!paused && !complete && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) start()
                        }
                        setOnCompletionListener { complete = true; paused = true }
                        setOnErrorListener { _, _, _ -> failed = true; paused = true; true }
                        setVideoURI(Uri.parse("android.resource://${context.packageName}/${videos[frame]}"))
                    }
                }, update = { view ->
                    if (prepared && !complete && !failed) {
                        if (paused) view.pause()
                        else if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.start()
                    }
                }, onRelease = { view ->
                    view.stopPlayback()
                    if (player === view) player = null
                }, modifier = Modifier.fillMaxSize().testTag("story-video"))
            }
        }
        if (failed) Text("视频暂时无法播放，可以重试或跳过。", color = MaterialTheme.colorScheme.error)
        else if (!prepared) Text("正在打开故事…", style = MaterialTheme.typography.bodySmall)
        if (complete) Text(guidance[frame], style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.small)) {
            if (!complete && !failed) OutlinedButton(onClick = { paused = !paused }, enabled = prepared) {
                Text(if (paused) "继续播放" else "暂停播放")
            }
            OutlinedButton(onClick = {
                player?.stopPlayback()
                position = 0; paused = false; complete = false; replay++
            }) { Text(if (failed) "重试视频" else "重播本幕") }
        }
        if (complete) MuseumButton(onClick = {
            if (frame < videos.lastIndex) { player?.stopPlayback(); frame++ } else latestReady()
        }) { Text(if (videos.size == 1) "跟随纸蝶" else listOf("跟随纸蝶", "打开手帐", "开始探索")[frame]) }
        TextButton(onClick = { latestReady() }) { Text("跳过引入") }
        Text("原创故事 · AI 生成视频", style = MaterialTheme.typography.labelSmall)
    }
}
