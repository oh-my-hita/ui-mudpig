package com.kajimi.hita.ui.mudpig

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.stupidtree.hita.api.ComposeUiPlugin
import com.stupidtree.hita.api.PluginUiHost
import com.stupidtree.hita.api.RuntimeUiService

/**
 * OH-MY-HITA runtime UI plugin that embeds the original browser game
 * 泥んこ豚洗い (Muddy Pig Wash). Everything is plain framework Android Views;
 * no Compose and no XML resources are used.
 */
class MudPigUiPlugin : ComposeUiPlugin {
    override val id = "com.kajimi.hita.ui.mudpig"
    override val displayName = "泥猪洗"
    override val requiresApi = 11

    private var gameView: MudPigGameView? = null

    override fun createView(context: Context, host: PluginUiHost): View {
        // The main-tab path always injects tabId (RuntimeUiTabFragment.kt:39-41);
        // a dedicated PluginUiActivity only carries whatever the caller passed.
        return if (host.arguments.containsKey("tabId")) {
            buildLauncher(context, host)
        } else {
            obtainGameView(context)
        }
    }

    override fun onDestroy() {
        gameView?.destroy()
        gameView = null
    }

    private fun obtainGameView(context: Context): MudPigGameView {
        gameView?.let { return it }
        return MudPigGameView(context).also { gameView = it }
    }

    private fun buildLauncher(context: Context, host: PluginUiHost): View {
        val backgroundColor = themeColor(context, android.R.attr.colorBackground, Color.WHITE)
        val primary = themeColor(context, android.R.attr.textColorPrimary, 0xFF212121.toInt())
        val secondary = themeColor(context, android.R.attr.textColorSecondary, 0xFF757575.toInt())
        val accent = themeColor(context, android.R.attr.colorAccent, 0xFFD81B60.toInt())

        val root = FrameLayout(context).apply { setBackgroundColor(backgroundColor) }

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(context.dp(24), context.dp(28), context.dp(24), context.dp(28))
        }

        fun add(view: View, topMarginDp: Int = 0, width: Int = LinearLayout.LayoutParams.MATCH_PARENT) {
            column.addView(
                view,
                LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = context.dp(topMarginDp)
                },
            )
        }

        add(TextView(context).apply {
            text = "泥猪洗"
            textSize = 32f
            setTextColor(primary)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })
        add(TextView(context).apply {
            text = "泥んこ豚洗い"
            textSize = 17f
            setTextColor(secondary)
            gravity = Gravity.CENTER
        }, topMarginDp = 4)
        add(TextView(context).apply {
            text = "冲掉小猪身上的泥巴。网页小游戏，运行时需要联网。"
            textSize = 15f
            setTextColor(primary)
            gravity = Gravity.CENTER
        }, topMarginDp = 18)

        val hints = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(16), context.dp(12), context.dp(16), context.dp(12))
            background = roundedBackground(0x14808080, context.dp(12).toFloat())
        }
        listOf("单指拖动=冲水", "双指拖动=转视角", "双指捏合=缩放").forEach { hint ->
            hints.addView(TextView(context).apply {
                text = hint
                textSize = 15f
                setTextColor(primary)
                setPadding(0, context.dp(3), 0, context.dp(3))
            })
        }
        add(hints, topMarginDp = 22)

        val start = TextView(context).apply {
            text = "开始游戏"
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            minimumHeight = context.dp(52)
            setPadding(0, context.dp(16), 0, context.dp(16))
            background = roundedBackground(accent, context.dp(14).toFloat())
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val opened = runCatching {
                    host.service(RuntimeUiService.KEY)?.open(
                        context,
                        CAP_PLAY,
                        Bundle().apply { putBoolean("game", true) },
                    ) == true
                }.getOrDefault(false)
                if (!opened) {
                    // Host routing is unavailable (old host / plugin disabled):
                    // play inline so the tab is never a dead end.
                    root.removeAllViews()
                    val inline = obtainGameView(context)
                    (inline.parent as? ViewGroup)?.removeView(inline)
                    root.addView(
                        inline,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        ),
                    )
                }
            }
        }
        add(start, topMarginDp = 24)

        add(TextView(context).apply {
            text = "在浏览器中打开原站"
            textSize = 14f
            setTextColor(accent)
            gravity = Gravity.CENTER
            setPadding(0, context.dp(12), 0, context.dp(12))
            isClickable = true
            isFocusable = true
            setOnClickListener { openExternal(context, Uri.parse(GAME_URL)) }
        }, topMarginDp = 6)

        add(TextView(context).apply {
            text = "游戏 © ふるかわけい / keifurukawafunnych.com\n" +
                "本插件仅嵌入原站，不复制游戏资源"
            textSize = 11f
            setTextColor(secondary)
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.25f)
        }, topMarginDp = 28)

        val scroll = ScrollView(context).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(
                column,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        root.addView(
            scroll,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        return root
    }

    private fun themeColor(context: Context, attr: Int, fallback: Int): Int {
        val value = TypedValue()
        if (!context.theme.resolveAttribute(attr, value, true)) return fallback
        return when {
            value.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT ->
                value.data
            value.resourceId != 0 ->
                runCatching { context.getColor(value.resourceId) }.getOrDefault(fallback)
            else -> fallback
        }
    }
}
