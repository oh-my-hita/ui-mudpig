package com.kajimi.hita.ui.mudpig

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Message
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.io.ByteArrayInputStream

private const val GAME_BACKGROUND = 0xFF9AA3A9.toInt()

internal fun Context.dp(value: Int): Int = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP,
    value.toFloat(),
    resources.displayMetrics,
).toInt()

internal fun openExternal(context: Context, uri: Uri) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, uri)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }.onFailure { Log.w(TAG, "无法用外部浏览器打开：$uri", it) }
}

internal fun roundedBackground(color: Int, radiusPx: Float): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusPx
    }

/**
 * The inline game surface: a plain [FrameLayout] whose direct child is the game
 * [WebView]. The attribution chip and the error overlay are siblings on top.
 */
internal class MudPigGameView(context: Context) : FrameLayout(context) {

    private var webView: GameWebView? = null
    private var destroyed = false

    private val errorMessage = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 14f
        gravity = Gravity.CENTER
    }

    private val errorOverlay: View = buildErrorOverlay(context)

    init {
        setBackgroundColor(GAME_BACKGROUND)
        isClickable = true

        val wv = GameWebView(context)
        webView = wv
        wv.id = View.generateViewId()
        wv.contentDescription = "泥んこ豚洗い"
        wv.setBackgroundColor(GAME_BACKGROUND)
        addView(wv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        addView(
            buildAttributionChip(context),
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = context.dp(8)
            },
        )
        addView(errorOverlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        configure(wv)
        wv.loadUrl(GAME_URL)
    }

    /** Releases the WebView. Safe to call more than once. */
    fun destroy() {
        if (destroyed) return
        destroyed = true
        val wv = webView ?: return
        webView = null
        (wv.parent as? ViewGroup)?.removeView(wv)
        wv.stopLoading()
        wv.loadUrl("about:blank")
        wv.clearHistory()
        wv.destroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure(wv: GameWebView) {
        val settings = wv.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true

        wv.isVerticalScrollBarEnabled = false
        wv.isHorizontalScrollBarEnabled = false
        wv.overScrollMode = View.OVER_SCROLL_NEVER

        wv.webViewClient = GameClient()
        wv.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message,
            ): Boolean = false

            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                val level = consoleMessage.messageLevel()
                if (level == ConsoleMessage.MessageLevel.ERROR ||
                    level == ConsoleMessage.MessageLevel.WARNING
                ) {
                    Log.w(TAG, "console: ${consoleMessage.message()}")
                }
                return true
            }
        }
    }

    private fun buildAttributionChip(context: Context): TextView = TextView(context).apply {
        text = "泥んこ豚洗い · ふるかわけい"
        textSize = 10f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setPadding(context.dp(8), context.dp(3), context.dp(8), context.dp(3))
        background = roundedBackground(0x66000000, context.dp(12).toFloat())
        isClickable = true
        isFocusable = true
        setOnClickListener { openExternal(context, Uri.parse(GAME_URL)) }
    }

    private fun buildErrorOverlay(context: Context): View {
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(context.dp(24), context.dp(24), context.dp(24), context.dp(24))
        }
        panel.addView(
            errorMessage,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )
        panel.addView(
            actionButton(context, "重试") {
                hideError()
                webView?.reload()
            },
            LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = context.dp(20)
            },
        )
        panel.addView(
            actionButton(context, "在浏览器打开") { openExternal(context, Uri.parse(GAME_URL)) },
            LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = context.dp(10)
            },
        )
        return FrameLayout(context).apply {
            setBackgroundColor(0xEE151515.toInt())
            isClickable = true
            addView(
                panel,
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER
                },
            )
            visibility = GONE
        }
    }

    private fun actionButton(context: Context, label: String, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = roundedBackground(0x33FFFFFF, context.dp(12).toFloat())
            setPadding(context.dp(22), context.dp(10), context.dp(22), context.dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    private fun showError(message: String) {
        if (destroyed) return
        errorMessage.text = message
        errorOverlay.visibility = View.VISIBLE
    }

    private fun hideError() {
        errorOverlay.visibility = View.GONE
    }

    private inner class GameClient : WebViewClient() {

        private var networkFallbackLogged = false

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean = handleNavigation(view.context, request.url)

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
            handleNavigation(view.context, Uri.parse(url))

        private fun handleNavigation(context: Context, uri: Uri): Boolean {
            val scheme = uri.scheme?.lowercase()
            val host = uri.host?.lowercase()
            if ((scheme == "http" || scheme == "https") &&
                host == GAME_HOST &&
                uri.path?.startsWith(GAME_PATH) == true
            ) {
                return false
            }
            if (scheme == "http" || scheme == "https") {
                openExternal(context, uri)
            }
            return true
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? {
            if (request.url.toString() != THREE_JS_URL) return null
            val bytes = PluginAssets.threeJs(this@MudPigGameView.context)
            if (bytes == null) {
                if (!networkFallbackLogged) {
                    networkFallbackLogged = true
                    Log.i(TAG, "three.js asset missing, network fallback")
                }
                return null
            }
            Log.i(TAG, "serve local three.js ${bytes.size} bytes")
            val headers = mapOf(
                "Access-Control-Allow-Origin" to "*",
                "Cache-Control" to "public, max-age=86400",
            )
            return WebResourceResponse(
                "application/javascript",
                "utf-8",
                200,
                "OK",
                headers,
                ByteArrayInputStream(bytes),
            )
        }

        override fun onPageFinished(view: WebView, url: String) {
            Log.i(TAG, "onPageFinished $url")
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            if (!request.isForMainFrame) return
            Log.i(
                TAG,
                "onReceivedError main frame ${request.url} " +
                    "(${error.errorCode}) ${error.description}",
            )
            showError("加载失败：${error.description}")
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse,
        ) {
            if (!request.isForMainFrame) return
            val code = errorResponse.statusCode
            val reason = errorResponse.reasonPhrase.orEmpty()
            Log.i(TAG, "onReceivedHttpError main frame ${request.url} HTTP $code $reason")
            showError("服务器返回 HTTP $code $reason")
        }
    }

    private class GameWebView(context: Context) : WebView(context) {
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN ->
                    parent?.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    parent?.requestDisallowInterceptTouchEvent(false)
            }
            return super.onTouchEvent(event)
        }
    }
}
