package cn.xiangyu.museum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext

@Composable
internal fun StoryChapterContent(endpoint: String, username: String, onExplore: () -> Unit,
                                 completed: Set<String> = emptySet(), modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val progress by produceState<StoryChapterProgress?>(null, endpoint, username, completed, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            value = withContext(Dispatchers.IO) {
                runCatching { StoryChapterStore(context).use {
                    val scope = storyChapterScope(endpoint, username)
                    it.restoreCompleted(scope, completed)
                    it.load(scope)
                } }.getOrNull()
            }
            awaitCancellation()
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.small)) {
        Text("马王堆的三件发现")
        progress?.let {
            Text("衣 · 画 · 图  /  已收好 ${it.pages} / 3")
            if (it.stamped) Text("三件发现纪念章已收好")
            Text("新版探索记录保存在这台设备。")
        }
        OutlinedButton(onClick = onExplore) { Text("探索三件文物") }
    }
}
