package cn.xiangyu.museum

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest

internal fun storyChapterScope(endpoint: String, username: String) = MessageDigest.getInstance("SHA-256")
    .digest("$endpoint\u0000$username\u0000child".toByteArray()).joinToString("") { "%02x".format(it) }

internal val storyTaskIds = listOf("story-gauze-v1", "story-tpainting-v1", "story-daoyin-v1")
internal val mobileTaskIds = FirstChapter.tasks.map { it.id }.toSet() + storyTaskIds

internal fun storyMaskFromTasks(completed: Set<String>): Int = when {
    completed.containsAll(storyTaskIds) -> 511
    completed.containsAll(storyTaskIds.take(2)) -> 63
    storyTaskIds[0] in completed -> 7
    else -> 0
}

data class StoryChapterProgress(val mask: Int = 0, val stamped: Boolean = false, val watched: Int = 0) {
    val pages get() = listOf(7, 63, 511).count { mask and it == it }
    val complete get() = mask == 511
}

/** Prefix checkpoints preserve the first slice's three bits and enforce chapter order. */
internal fun validStoryMask(mask: Int) = mask in 0..511 && mask and (mask + 1) == 0

/** SQLite handles the account shell and the isolated Godot process without preference caches. */
class StoryChapterStore(context: Context) : AutoCloseable {
    private val legacy = context.getSharedPreferences("puzzle_checkpoints", Context.MODE_PRIVATE)
    private val helper = object : SQLiteOpenHelper(context, "museum_story.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE chapter(scope TEXT PRIMARY KEY, mask INTEGER NOT NULL, stamped INTEGER NOT NULL, watched INTEGER NOT NULL)")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private fun ensure(db: SQLiteDatabase, scope: String) {
        val previous = legacy.getInt(scope + ":story-slice-v1", 0)
        val imported = if (validStoryMask(previous) && previous <= 7) previous else 0
        db.execSQL("INSERT OR IGNORE INTO chapter VALUES(?,?,0,0)", arrayOf<Any>(scope, imported))
    }

    private fun read(db: SQLiteDatabase, scope: String): StoryChapterProgress =
        db.rawQuery("SELECT mask,stamped,watched FROM chapter WHERE scope=?", arrayOf(scope)).use {
            check(it.moveToFirst())
            StoryChapterProgress(it.getInt(0), it.getInt(1) == 1, it.getInt(2))
        }

    fun load(scope: String): StoryChapterProgress = synchronized(helper) {
        val db = helper.writableDatabase
        ensure(db, scope)
        read(db, scope)
    }

    private fun change(scope: String, action: (SQLiteDatabase, StoryChapterProgress) -> Boolean): Boolean = synchronized(helper) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            ensure(db, scope)
            val success = action(db, read(db, scope))
            if (success) db.setTransactionSuccessful()
            success
        } finally { db.endTransaction() }
    }

    fun save(scope: String, mask: Int): Boolean {
        if (!validStoryMask(mask)) return false
        return change(scope) { db, current ->
            val combined = current.mask or mask
            if (combined != current.mask && combined != current.mask * 2 + 1) false else {
                db.execSQL("UPDATE chapter SET mask=? WHERE scope=?", arrayOf<Any>(combined, scope))
                true
            }
        }
    }

    fun restoreCompleted(scope: String, completed: Set<String>) = change(scope) { db, current ->
        db.execSQL("UPDATE chapter SET mask=? WHERE scope=?", arrayOf<Any>(current.mask or storyMaskFromTasks(completed), scope))
        true
    }

    fun stamp(scope: String) = change(scope) { db, current ->
        if (!current.complete) false else {
            db.execSQL("UPDATE chapter SET stamped=1 WHERE scope=?", arrayOf(scope))
            true
        }
    }

    fun watched(scope: String, index: Int): Boolean {
        if (index !in 0..7) return false
        return change(scope) { db, current ->
            db.execSQL("UPDATE chapter SET watched=? WHERE scope=?", arrayOf<Any>(current.watched or (1 shl index), scope))
            true
        }
    }

    override fun close() = helper.close()
}
