package cn.xiangyu.museum

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import kotlin.math.min

/** Native, scalable controls dispatch the same coordinates as the illustrated game. */
internal class StoryAccessibilityView(context: Context, private val onAction: (String) -> Unit) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private var textMode = (context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager).isTouchExplorationEnabled
    private var currentTitle = ""
    private var currentActions = "[]"
    private val toggle = Button(context).apply {
        text = "文字"
        contentDescription = "切换到文字操作"
        minHeight = (48 * density).toInt()
        setOnClickListener { textMode = true; render() }
    }
    private val scroll = ScrollView(context)

    init {
        addView(scroll, LayoutParams(-1, -1))
        addView(toggle)
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> positionToggle() }
        render()
    }

    fun update(title: String, actions: String) {
        if (title == currentTitle && actions == currentActions) return
        currentTitle = title
        currentActions = actions
        render()
    }

    private fun positionToggle() {
        val scale = min(width / 1280f, height / 720f)
        val next = LayoutParams((112 * scale).toInt(), (88 * scale).toInt()).apply {
            leftMargin = ((width - 1280 * scale) / 2 + 632 * scale).toInt()
            topMargin = ((height - 720 * scale) / 2 + 16 * scale).toInt()
        }
        val previous = toggle.layoutParams as? LayoutParams
        if (previous?.width != next.width || previous.height != next.height ||
            previous.leftMargin != next.leftMargin || previous.topMargin != next.topMargin) toggle.layoutParams = next
    }

    private fun render() {
        toggle.visibility = if (textMode) View.GONE else View.VISIBLE
        scroll.visibility = if (textMode) View.VISIBLE else View.GONE
        if (!textMode) return
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * density).toInt(), (16 * density).toInt(), (24 * density).toInt(), (24 * density).toInt())
            setBackgroundColor(Color.rgb(255, 247, 232))
        }
        content.addView(Button(context).apply {
            text = "返回画面"
            setOnClickListener { textMode = false; render() }
        })
        content.addView(TextView(context).apply {
            text = currentTitle
            textSize = 22f
            setTextColor(Color.rgb(37, 61, 74))
            setPadding(0, (12 * density).toInt(), 0, (12 * density).toInt())
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        })
        val actions = JSONArray(currentActions)
        for (index in 0 until actions.length()) {
            val action = actions.getJSONObject(index)
            content.addView(Button(context).apply {
                text = action.getString("text")
                isEnabled = action.optBoolean("enabled", true)
                minHeight = (48 * density).toInt()
                gravity = Gravity.CENTER
                setOnClickListener { onAction("${action.getDouble("x")},${action.getDouble("y")}") }
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = (8 * density).toInt() })
        }
        scroll.removeAllViews()
        scroll.addView(content)
    }
}
