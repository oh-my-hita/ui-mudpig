package com.kajimi.hita.ui.mudpig

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

/** Logs are tagged with a stable name so an on-device harness can filter them. */
internal const val TAG = "MudPigPlugin"

internal const val GAME_URL = "https://keifurukawafunnych.com/games/mud-pig-wash/"
internal const val GAME_HOST = "keifurukawafunnych.com"
internal const val GAME_PATH = "/games/mud-pig-wash/"
internal const val THREE_JS_URL = "https://cdn.jsdelivr.net/npm/three@0.160.0/build/three.module.js"
internal const val CAP_PLAY = "com.kajimi.hita.ui.mudpig.play"

/**
 * Finds this plugin's own APK and reads assets from it.
 *
 * The host installs UI plugins at `filesDir/ui-plugins/<id>/plugin.apk`, but the
 * running code only receives the host [Context], and the host AssetManager does
 * not expose plugin assets. So we open the APK as a plain zip file.
 */
internal object PluginAssets {
    private const val PLUGIN_ID = "com.kajimi.hita.ui.mudpig"
    private const val PLUGIN_APK_NAME = "plugin.apk"
    private const val PLUGIN_JSON_ENTRY = "assets/plugin.json"
    private const val THREE_JS_ENTRY = "assets/three.module.js"

    private val lock = Any()

    @Volatile
    private var loaded = false

    @Volatile
    private var threeJsBytes: ByteArray? = null

    /** Reads the bundled three.js module once; safe from any thread. */
    fun threeJs(context: Context): ByteArray? {
        if (loaded) return threeJsBytes
        synchronized(lock) {
            if (loaded) return threeJsBytes
            val apk = findPluginApk(context.applicationContext)
            if (apk == null) {
                Log.w(TAG, "未找到插件 APK（filesDir/ui-plugins），无法读取内置 three.js")
            }
            threeJsBytes = apk?.let { readEntry(it, THREE_JS_ENTRY) }
            if (threeJsBytes == null) {
                Log.w(TAG, "插件 APK 中缺少 assets/three.module.js，将回退到 CDN")
            }
            loaded = true
            return threeJsBytes
        }
    }

    private fun findPluginApk(context: Context): File? {
        val direct = File(context.filesDir, "ui-plugins/$PLUGIN_ID/$PLUGIN_APK_NAME")
        if (direct.isFile && direct.canRead()) return direct

        // Fallback: the host may have installed us under another directory name.
        val root = File(context.filesDir, "ui-plugins")
        val dirs = root.listFiles()?.filter { it.isDirectory } ?: return null
        for (dir in dirs) {
            val apk = File(dir, PLUGIN_APK_NAME)
            if (apk.isFile && apk.canRead() && declaresOurId(apk)) return apk
        }
        return null
    }

    private fun declaresOurId(apk: File): Boolean = runCatching {
        ZipFile(apk).use { zip ->
            val entry = zip.getEntry(PLUGIN_JSON_ENTRY) ?: return@use false
            val text = zip.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
            JSONObject(text).optString("id") == PLUGIN_ID
        }
    }.getOrDefault(false)

    private fun readEntry(apk: File, name: String): ByteArray? = runCatching {
        ZipFile(apk).use { zip ->
            val entry = zip.getEntry(name) ?: return@use null
            zip.getInputStream(entry).use { it.readBytes() }
        }
    }.onFailure { Log.w(TAG, "读取 APK 资源失败：$name", it) }.getOrNull()
}
