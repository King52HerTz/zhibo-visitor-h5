package cn.xiangyu.museum

import android.os.Bundle
import org.godotengine.godot.Godot
import org.godotengine.godot.GodotActivity
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.UsedByGodot
import org.godotengine.godot.plugin.SignalInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.security.MessageDigest
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.net.Uri
import android.graphics.Color
import android.widget.FrameLayout
import android.widget.VideoView
import android.widget.TextView
import android.view.Gravity

/** Isolated engine process: its default teardown must not terminate the account UI. */
class PuzzleActivity : GodotActivity() {
    private var video: VideoView? = null
    private var videoLayer: FrameLayout? = null
    private var videoPosition = 0
    private var clipPaused = false
    private var afterClip: ((Boolean) -> Unit)? = null
    private var storyAccessibility: StoryAccessibilityView? = null
    private val storyStore by lazy { StoryChapterStore(this) }
    private val storyClips = listOf("story_scroll_opening", "story_scroll_cloth", "story_scroll_cloth_response",
        "story_scroll_family", "story_scroll_painting", "story_scroll_painting_response", "story_scroll_daoyin", "story_scroll_ending")
    private val progressKey by lazy {
        ProgressKey(intent.getStringExtra("endpoint").orEmpty(), intent.getStringExtra("username").orEmpty(), Audience.CHILD)
    }
    private val checkpointKey by lazy {
        MessageDigest.getInstance("SHA-256").digest(
            "${progressKey.endpoint}\u0000${progressKey.username}\u0000child".toByteArray()
        ).joinToString("") { "%02x".format(it) }
    }
    private val checkpoints by lazy { getSharedPreferences("puzzle_checkpoints", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (intent.getStringExtra("endpoint").isNullOrBlank() || intent.getStringExtra("username").isNullOrBlank()) {
            super.onCreate(savedInstanceState)
            finish()
            return
        }
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun getCommandLine() = mutableListOf("--main-pack", "res://game.pck", "--rendering-method", "gl_compatibility")

    private fun loadCheckpoint(taskId: String, suffix: String): Int {
        val local = runBlocking(Dispatchers.IO) {
            SqliteProgressStore(this@PuzzleActivity).use { it.load(progressKey) }
        }
        if (taskId in local.completed) checkpoints.edit().putInt(checkpointKey + suffix, 7).commit()
        return checkpoints.getInt(checkpointKey + suffix, 0).coerceIn(0, 7)
    }

    private fun saveCheckpoint(mask: Int, suffix: String): Boolean {
        if (mask !in 0..7) return false
        val combined = checkpoints.getInt(checkpointKey + suffix, 0) or mask
        return checkpoints.edit().putInt(checkpointKey + suffix, combined).commit()
    }

    private fun completeTask(taskId: String, suffix: String): Boolean = try {
        if (checkpoints.getInt(checkpointKey + suffix, 0) != 7) false else {
            runBlocking(Dispatchers.IO) {
                SqliteProgressStore(this@PuzzleActivity).use { it.complete(progressKey, taskId) }
            }
            true
        }
    } catch (_: Exception) { false }

    private fun storyProgress(): StoryChapterProgress = runBlocking(Dispatchers.IO) {
        SqliteProgressStore(this@PuzzleActivity).use { tasks ->
            val synced = tasks.load(progressKey).completed
            storyStore.restoreCompleted(checkpointKey, synced)
            val current = storyStore.load(checkpointKey)
            // Recover task reporting if the engine exited between the two durable writes.
            storyTaskIds.take(current.pages).filterNot { it in synced }.forEach { tasks.complete(progressKey, it) }
            current
        }
    }

    override fun getHostPlugins(engine: Godot): Set<GodotPlugin> = setOf(object : GodotPlugin(engine) {
        override fun getPluginName() = "MuseumPuzzle"
        override fun getPluginSignals() = setOf(SignalInfo("story_clip_finished", String::class.java),
            SignalInfo("story_action", String::class.java))

        @UsedByGodot fun is_story_slice() = intent.getBooleanExtra("story_slice", false)
        @UsedByGodot fun update_story_accessibility(title: String, actions: String) {
            this@PuzzleActivity.runOnUiThread {
                if (isFinishing || !is_story_slice()) return@runOnUiThread
                if (storyAccessibility == null) {
                    storyAccessibility = StoryAccessibilityView(this@PuzzleActivity) { emitSignal("story_action", it) }
                    addContentView(storyAccessibility, android.view.ViewGroup.LayoutParams(-1, -1))
                }
                storyAccessibility?.update(title, actions)
            }
        }
        @UsedByGodot fun load_story_slice_checkpoint() = storyProgress().mask
        @UsedByGodot fun save_story_slice_checkpoint(mask: Int): Boolean = try {
            if (!storyStore.save(checkpointKey, mask)) false else { storyProgress(); true }
        } catch (_: Exception) { false }
        @UsedByGodot fun is_story_stamped() = storyStore.load(checkpointKey).stamped
        @UsedByGodot fun stamp_story(): Boolean = try {
            storyStore.stamp(checkpointKey)
        } catch (_: Exception) { false }
        @UsedByGodot fun play_story_clip(index: Int): Boolean {
            if (!is_story_slice() || index !in storyClips.indices || isFinishing) return false
            val name = storyClips[index]
            if (resources.getIdentifier(name, "raw", packageName) == 0) return false
            this@PuzzleActivity.runOnUiThread {
                if (isFinishing || !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                    emitSignal("story_clip_finished", "interrupted")
                } else playClip(name) { complete ->
                    if (complete) runCatching { storyStore.watched(checkpointKey, index) }
                    emitSignal("story_clip_finished", if (complete) "complete" else "skipped")
                }
            }
            return true
        }
        @UsedByGodot fun load_checkpoint() = loadCheckpoint("clothes-light-v1", "")
        @UsedByGodot fun save_checkpoint(mask: Int) = saveCheckpoint(mask, "")
        @UsedByGodot fun complete_light() = completeTask("clothes-light-v1", "")
        @UsedByGodot fun load_pattern_checkpoint() = loadCheckpoint("clothes-pattern-v1", ":pattern")
        @UsedByGodot fun save_pattern_checkpoint(mask: Int) = saveCheckpoint(mask, ":pattern")
        @UsedByGodot fun complete_pattern() = completeTask("clothes-pattern-v1", ":pattern")
        @UsedByGodot fun is_stamped() = checkpoints.getBoolean(checkpointKey + ":stamp", false)
        @UsedByGodot fun stamp(): Boolean {
            val completed = runBlocking(Dispatchers.IO) {
                SqliteProgressStore(this@PuzzleActivity).use { it.load(progressKey).completed }
            }
            if (!completed.containsAll(setOf("clothes-light-v1", "clothes-pattern-v1"))) return false
            return checkpoints.edit().putBoolean(checkpointKey + ":stamp", true).commit()
        }
        @UsedByGodot fun play_workshop() {
            if (!checkpoints.getBoolean(checkpointKey + ":intro", false)) {
                checkpoints.edit().putBoolean(checkpointKey + ":intro", true).commit()
                this@PuzzleActivity.runOnUiThread { playClip("story_workshop") }
            }
        }
        @UsedByGodot fun play_ending() { this@PuzzleActivity.runOnUiThread { playClip("story_journal") } }
        @UsedByGodot fun leave_game() { this@PuzzleActivity.runOnUiThread { finish() } }
        @UsedByGodot fun show_licenses(text: String) {
            this@PuzzleActivity.runOnUiThread {
                android.app.AlertDialog.Builder(this@PuzzleActivity).setTitle("开源许可 · Godot")
                    .setMessage(text).setPositiveButton("关闭", null).show()
            }
        }
    })

    private fun closeClip(complete: Boolean = false) {
        val callback = afterClip
        afterClip = null
        video?.stopPlayback()
        video = null
        videoPosition = 0
        videoLayer?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) }
        videoLayer = null
        callback?.invoke(complete)
    }

    private fun playClip(name: String, onClosed: ((Boolean) -> Unit)? = null) {
        val resource = resources.getIdentifier(name, "raw", packageName)
        if (resource == 0 || isFinishing) return
        closeClip()
        afterClip = onClosed
        val layer = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val player = VideoView(this)
        layer.addView(player, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        val skip = TextView(this).apply {
            text = "跳过"
            textSize = 18f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(150, 20, 40, 40))
            setPadding(28, 18, 28, 18)
            minimumHeight = (48 * resources.displayMetrics.density).toInt()
            minimumWidth = (80 * resources.displayMetrics.density).toInt()
            setOnClickListener { closeClip() }
            contentDescription = "跳过短片"
        }
        layer.addView(skip, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END))
        video = player
        videoLayer = layer
        addContentView(layer, android.view.ViewGroup.LayoutParams(-1, -1))
        player.setOnCompletionListener { closeClip(complete = true) }
        player.setOnErrorListener { _, _, _ -> closeClip(); true }
        player.setVideoURI(Uri.parse("android.resource://$packageName/$resource"))
        clipPaused = !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
        player.setOnPreparedListener { if (!clipPaused) player.start() }
    }

    override fun onPause() {
        // New chapter films return to the same actionable slot when interrupted.
        if (afterClip != null) closeClip()
        videoPosition = video?.currentPosition ?: 0
        clipPaused = true
        video?.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        video?.let { player ->
            player.seekTo(videoPosition)
            // Backgrounded clips resume only through a deliberate tap.
            player.setOnClickListener { clipPaused = false; player.start() }
        }
    }

    override fun onDestroy() {
        closeClip()
        if (intent.getBooleanExtra("story_slice", false)) storyStore.close()
        super.onDestroy()
        // The native engine cannot be initialized twice in one process. Only the
        // dedicated puzzle process exits; the signed-in account shell stays alive.
        if (isFinishing) android.os.Process.killProcess(android.os.Process.myPid())
    }
}
