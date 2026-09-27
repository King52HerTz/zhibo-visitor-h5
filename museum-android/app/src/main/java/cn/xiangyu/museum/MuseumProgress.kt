package cn.xiangyu.museum

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ProgressKey(val endpoint: String, val username: String, val audience: Audience)
data class TaskProgress(val completed: Set<String> = emptySet(), val pending: Set<String> = emptySet())
data class ProgressState(val progress: TaskProgress = TaskProgress(), val loading: Boolean = false,
                         val syncing: Boolean = false, val message: String? = null)

interface ProgressStore {
    suspend fun load(key: ProgressKey): TaskProgress
    suspend fun complete(key: ProgressKey, taskId: String): TaskProgress
    suspend fun merge(key: ProgressKey, remote: Set<String>): TaskProgress
}

interface MuseumProgressApi {
    suspend fun syncProgress(login: LoginSession, audience: Audience, completed: Set<String>): Set<String>
}

class SqliteProgressStore(context: Context, name: String = "museum_progress.db") : ProgressStore, AutoCloseable {
    private val helper = object : SQLiteOpenHelper(context, name, null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE tasks(endpoint TEXT NOT NULL, username TEXT NOT NULL, audience TEXT NOT NULL, " +
                "task_id TEXT NOT NULL, pending INTEGER NOT NULL, PRIMARY KEY(endpoint,username,audience,task_id))")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
    private fun args(key: ProgressKey) = arrayOf(key.endpoint, key.username, key.audience.apiValue)
    private fun read(db: SQLiteDatabase, key: ProgressKey): TaskProgress {
        val completed = mutableSetOf<String>()
        val pending = mutableSetOf<String>()
        db.rawQuery("SELECT task_id,pending FROM tasks WHERE endpoint=? AND username=? AND audience=?", args(key)).use {
            while (it.moveToNext()) {
                completed += it.getString(0)
                if (it.getInt(1) == 1) pending += it.getString(0)
            }
        }
        return TaskProgress(completed, pending)
    }
    override suspend fun load(key: ProgressKey) = withContext(Dispatchers.IO) {
        synchronized(helper) { read(helper.readableDatabase, key) }
    }
    private suspend fun change(key: ProgressKey, action: (SQLiteDatabase) -> Unit) = withContext(Dispatchers.IO) {
        synchronized(helper) {
            val db = helper.writableDatabase
            db.beginTransaction()
            try {
                action(db)
                val result = read(db, key)
                db.setTransactionSuccessful()
                result
            } finally { db.endTransaction() }
        }
    }
    override suspend fun complete(key: ProgressKey, taskId: String) = change(key) { db ->
        require(FirstChapter.tasks.any { it.id == taskId })
        db.execSQL("INSERT OR IGNORE INTO tasks VALUES(?,?,?,?,1)", args(key) + taskId)
    }
    override suspend fun merge(key: ProgressKey, remote: Set<String>) = change(key) { db ->
        remote.filter { id -> FirstChapter.tasks.any { it.id == id } }.forEach { id ->
            db.execSQL("INSERT OR IGNORE INTO tasks VALUES(?,?,?,?,0)", args(key) + id)
            db.execSQL("UPDATE tasks SET pending=0 WHERE endpoint=? AND username=? AND audience=? AND task_id=?", args(key) + id)
        }
    }
    override fun close() = helper.close()
}

class ProgressController(private val api: MuseumProgressApi, private val store: ProgressStore,
                         private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(ProgressState())
    val state = mutable.asStateFlow()
    private var login: LoginSession? = null
    private var key: ProgressKey? = null
    private var epoch = 0
    private var network: Job? = null

    fun bind(identity: LoginSession?, audience: Audience?) {
        epoch++
        network?.cancel()
        login = identity
        key = if (identity != null && audience != null) ProgressKey(identity.endpoint, identity.username, audience) else null
        mutable.value = ProgressState(loading = key != null)
        sync()
    }

    fun complete(taskId: String) {
        val target = key ?: return
        val ticket = epoch
        if (mutable.value.loading || taskId in mutable.value.progress.completed || FirstChapter.tasks.none { it.id == taskId }) return
        scope.launch {
            try {
                val saved = store.complete(target, taskId)
                if (ticket == epoch) {
                    mutable.value = mutable.value.copy(progress = saved, message = null)
                    if (network?.isActive != true) sync()
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) {
                if (ticket == epoch) mutable.value = mutable.value.copy(message = "手帐暂时无法保存，请重试")
            }
        }
    }

    fun sync() {
        val identity = login ?: return
        val target = key ?: return
        if (network?.isActive == true) return
        val ticket = epoch
        network = scope.launch {
            try {
                var local = store.load(target)
                if (ticket != epoch) return@launch
                mutable.value = ProgressState(local, syncing = true)
                do {
                    val remote = api.syncProgress(identity, target.audience, local.completed)
                    if (ticket != epoch) return@launch
                    local = store.merge(target, remote)
                    if (ticket != epoch) return@launch
                    mutable.value = ProgressState(local, syncing = local.pending.isNotEmpty())
                } while (local.pending.isNotEmpty())
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                if (ticket == epoch) mutable.value = mutable.value.copy(loading = false, syncing = false,
                    message = if (error is ApiFailure && error.status == 401) "登录已失效，请退出后重新登录" else
                        "暂未连接，发现已保存在这台设备。联网后可同步。")
            }
        }
    }
}
