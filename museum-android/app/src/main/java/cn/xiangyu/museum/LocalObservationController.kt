package cn.xiangyu.museum

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

data class LocalObservationState(val records: List<LocalObservation> = emptyList(), val loading: Boolean = false,
    val busy: Boolean = false, val editing: Boolean = false, val draftId: String = "",
    val relicId: String = FirstChapter.relics.first().id, val note: String = "",
    val photo: File? = null, val message: String? = null, val confirmDiscard: Boolean = false,
    val pendingRelicId: String? = null)

class LocalObservationController internal constructor(private val context: Context,
    private val store: SqliteLocalObservationStore, private val scope: CoroutineScope,
    private val camera: LocalJournalCamera = LocalJournalCamera(context)) {
    private val mutable = MutableStateFlow(LocalObservationState())
    val state = mutable.asStateFlow()
    private var key: ProgressKey? = null
    private var bound = false
    private var epoch = 0
    private var reading: Job? = null

    fun bind(identity: ProgressKey?, restoring: Boolean = false, waitingForMode: Boolean = false) {
        if (restoring || (waitingForMode && !bound) || (bound && identity == key)) return
        bound = true
        key = identity
        val version = ++epoch
        reading?.cancel()
        mutable.value = LocalObservationState(loading = identity != null)
        val pending = camera.pending()
        if (pending != null && (identity == null || pending.scope != localObservationScope(identity))) {
            camera.discard(pending.id)
        }
        if (identity == null) return
        restoreCapture(camera.pending())
        reading = scope.launch {
            try {
                val records = store.load(identity)
                if (version == epoch) mutable.value = state.value.copy(records = records, loading = false)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                if (version == epoch) mutable.value = state.value.copy(loading = false, message = "观察记录暂时打不开，请返回后重试。")
            }
        }
    }

    private fun restoreCapture(capture: JournalCapture?) {
        val identity = key ?: return
        if (capture == null || capture.scope != localObservationScope(identity)) return
        if (capture.relicId !in FirstChapter.relics.map { it.id } || capture.note.length > 600) {
            camera.discard(capture.id)
            return
        }
        mutable.value = state.value.copy(editing = true, draftId = capture.draftId, relicId = capture.relicId,
            note = capture.note, photo = if (capture.result == "ready") camera.file(capture) else null,
            message = when (capture.result) {
                "ready" -> "照片只在本机。看一看，再确认收进手帐。"
                "waiting" -> "拍照尚未返回。也可以只写观察，稍后再拍。"
                else -> "没有保存照片，仍然可以写下观察。"
            })
    }

    fun newRecord(relicId: String = FirstChapter.relics.first().id) {
        if (key == null || state.value.busy || relicId !in FirstChapter.relics.map { it.id }) return
        if (state.value.editing) {
            if (relicId != state.value.relicId) mutable.value = state.value.copy(
                message = "你还有一条没收进手帐的观察。先收好或放下，再记录另一件文物。")
            return
        }
        camera.pending()?.let { camera.discard(it.id) }
        mutable.value = state.value.copy(editing = true, draftId = UUID.randomUUID().toString(),
            relicId = relicId, note = "", photo = null, message = null, confirmDiscard = false, pendingRelicId = null)
    }

    fun note(value: String) {
        if (state.value.editing && !state.value.busy && value.length <= 600) mutable.value = state.value.copy(note = value)
    }

    fun relic(id: String) {
        if (!state.value.editing || state.value.busy || id == state.value.relicId || id !in FirstChapter.relics.map { it.id }) return
        if (state.value.photo != null || hasPendingCapture(state.value)) mutable.value = state.value.copy(pendingRelicId = id)
        else {
            camera.pending()?.let { camera.discard(it.id) }
            mutable.value = state.value.copy(relicId = id, message = null, pendingRelicId = null)
        }
    }

    fun confirmRelicChange() {
        val draft = state.value
        val id = draft.pendingRelicId ?: return
        if (!draft.editing || draft.busy) return
        camera.pending()?.let { camera.discard(it.id) }
        mutable.value = state.value.copy(relicId = id, photo = null, message = null, pendingRelicId = null)
    }

    fun keepRelic() {
        if (!state.value.busy) mutable.value = state.value.copy(pendingRelicId = null)
    }

    fun hasUnsavedDraft(): Boolean {
        val draft = state.value
        return draft.editing && (draft.note.isNotBlank() || draft.photo != null || hasPendingCapture(draft))
    }

    fun pendingDraftAudience(endpoint: String, username: String): Audience? {
        val capture = camera.pending() ?: return null
        val audience = Audience.entries.firstOrNull {
            capture.scope == localObservationScope(ProgressKey(endpoint, username, it))
        } ?: return null
        return audience.takeIf { capture.note.isNotBlank() || capture.result == "waiting" ||
            (capture.result == "ready" && camera.file(capture)?.let { journalPhotoReadable(it) } == true) }
    }

    private fun hasPendingCapture(draft: LocalObservationState) = camera.pending()?.let {
        it.draftId == draft.draftId && it.result == "waiting"
    } == true

    fun withoutPhoto() {
        if (state.value.busy) return
        camera.pending()?.let { camera.discard(it.id) }
        mutable.value = state.value.copy(photo = null, message = "读一读文物介绍，把自己的发现写下来。", pendingRelicId = null)
    }

    fun cancelDraft() {
        if (state.value.busy) return
        camera.pending()?.let { camera.discard(it.id) }
        mutable.value = state.value.copy(editing = false, draftId = "", note = "", photo = null, message = null,
            confirmDiscard = false, pendingRelicId = null)
    }

    fun requestCancelDraft() {
        if (state.value.busy) return
        if (hasUnsavedDraft()) mutable.value = state.value.copy(confirmDiscard = true, pendingRelicId = null)
        else cancelDraft()
    }

    fun keepDraft() { mutable.value = state.value.copy(confirmDiscard = false) }

    fun takePhoto(launch: (String, Uri) -> Unit) {
        val identity = key ?: return
        val draft = state.value
        if (!draft.editing || draft.busy) return
        if (camera.pending()?.result == "waiting") {
            mutable.value = draft.copy(message = "上一张照片尚未返回。可以改用文字观察。")
            return
        }
        try {
            val capture = camera.begin(identity, draft.draftId, draft.relicId, draft.note)
            mutable.value = draft.copy(photo = null, message = "正在打开系统相机…")
            launch(capture.id, camera.uri(capture))
        } catch (_: ActivityNotFoundException) {
            camera.pending()?.let { camera.discard(it.id) }
            mutable.value = state.value.copy(photo = null, message = "这台设备暂时无法拍照，可以只写观察。")
        } catch (_: SecurityException) {
            camera.pending()?.let { camera.discard(it.id) }
            mutable.value = state.value.copy(photo = null, message = "相机暂时不可用，可以返回相机设置，或只写观察。")
        } catch (_: Exception) {
            camera.pending()?.let { camera.discard(it.id) }
            mutable.value = state.value.copy(photo = null, message = "照片暂时准备不了，观察文字可以继续填写。")
        }
    }

    fun cameraResult(id: String, success: Boolean) {
        scope.launch {
            val capture = try { withContext(Dispatchers.IO) { camera.finish(id, success) } }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                if (camera.pending()?.id == id) {
                    runCatching { camera.discard(id) }
                    mutable.value = state.value.copy(photo = null, message = "照片暂时无法读取，仍然可以保存文字观察。")
                }
                return@launch
            } ?: return@launch
            if (camera.pending()?.id != id) return@launch
            val identity = key
            if (!bound) return@launch // Authentication restoration will bind the persisted ticket.
            if (identity == null || capture.scope != localObservationScope(identity)) {
                camera.discard(id)
                return@launch
            }
            if (state.value.editing && state.value.draftId == capture.draftId) {
                mutable.value = state.value.copy(photo = if (capture.result == "ready") camera.file(capture) else null,
                    message = if (capture.result == "ready") "照片只在本机。看一看，再确认收进手帐。"
                        else "没有保存照片，仍然可以写下观察。")
            }
        }
    }

    fun save() {
        val identity = key ?: return
        val draft = state.value
        if (!draft.editing || draft.busy || draft.loading || draft.note.trim().isEmpty()) return
        val version = epoch
        val captureId = camera.pending()?.id
        mutable.value = draft.copy(busy = true, message = null)
        scope.launch {
            try {
                store.save(identity, draft.draftId, draft.relicId, draft.note, draft.photo)
                captureId?.let { camera.discard(it) }
                val records = store.load(identity)
                if (version == epoch) mutable.value = LocalObservationState(records = records, message = "这一点发现，已经留在本机手帐。")
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                if (version == epoch) mutable.value = state.value.copy(busy = false,
                    message = "记录暂时未保存。文字还在，可以重试，或改用文字观察。")
            }
        }
    }

    fun delete(record: LocalObservation) {
        val identity = key ?: return
        if (state.value.busy || state.value.loading) return
        val version = epoch
        mutable.value = state.value.copy(busy = true, message = null)
        scope.launch {
            try {
                store.delete(identity, record.id)
                val records = store.load(identity)
                if (version == epoch) mutable.value = state.value.copy(records = records, busy = false, message = "这条本机观察记录已删除。")
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                if (version == epoch) mutable.value = state.value.copy(busy = false, message = "这条记录暂时删不了，请稍后重试。")
            }
        }
    }

    fun photo(record: LocalObservation): File? = key?.let { store.photo(it, record) }
    internal fun pendingCaptureId(): String? = camera.pending()?.takeIf { it.result == "waiting" }?.id
}
