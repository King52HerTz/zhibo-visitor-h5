package cn.xiangyu.museum

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import android.net.Uri

@Composable
internal fun LocalObservationPanel(audience: Audience, controller: LocalObservationController,
    onCamera: (String, Uri) -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    if (state.editing) {
        LocalObservationEditor(audience, state.relicId, state.note, state.photo != null,
            state.busy || state.loading, state.message, controller::relic, controller::note,
            { controller.takePhoto(onCamera) }, controller::withoutPhoto, controller::save,
            controller::requestCancelDraft, { LocalJournalPhoto(state.photo) })
    } else {
        LocalObservationList(audience, state.records, state.loading || state.busy, state.message,
            { controller.newRecord() }, controller::delete, { LocalJournalPhoto(controller.photo(it)) })
    }
    if (state.confirmDiscard) AlertDialog(onDismissRequest = controller::keepDraft,
        title = { Text("放下这次观察？") },
        text = { Text("这次还没收进手帐的文字和照片会被放下。已经保存的记录不受影响。") },
        confirmButton = { TextButton(onClick = controller::cancelDraft,
            modifier = Modifier.heightIn(min = Space.touch)) { Text("放下草稿") } },
        dismissButton = { TextButton(onClick = controller::keepDraft,
            modifier = Modifier.heightIn(min = Space.touch)) { Text("继续记录") } })
    if (state.pendingRelicId != null) AlertDialog(onDismissRequest = controller::keepRelic,
        title = { Text("更换观察的文物？") },
        text = { Text("更换后，这次尚未保存的照片会移除，文字会保留。确定观察“${FirstChapter.relics.first { it.id == state.pendingRelicId }.title}”吗？") },
        confirmButton = { TextButton(onClick = controller::confirmRelicChange,
            modifier = Modifier.heightIn(min = Space.touch)) { Text("更换并移除照片") } },
        dismissButton = { TextButton(onClick = controller::keepRelic,
            modifier = Modifier.heightIn(min = Space.touch)) { Text("保留原文物") } })
}

@Composable
private fun LocalJournalPhoto(file: File?) {
    val bitmap by produceState<ImageBitmap?>(null, file?.path) {
        value = null
        value = withContext(Dispatchers.IO) { file?.let { runCatching { readJournalPhoto(it)?.asImageBitmap() }.getOrNull() } }
    }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
        val photo = bitmap
        if (photo != null) Image(photo, contentDescription = "这条观察的本机照片",
            modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f), contentScale = ContentScale.Fit)
        else Text("照片暂时无法显示，观察文字仍然可以阅读。", Modifier.padding(Space.medium),
            style = MaterialTheme.typography.bodyMedium)
    }
}
