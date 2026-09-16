package com.example.autoring.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.autoring.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 版本发布来源配置。
 * 改成你自己的 GitHub 用户名 / 仓库名即可；发布时在 Releases 上传 .apk，
 * tag 用 v + 版本号（如 v1.1.0），本 App 就能检测到并升级。
 */
object UpdateConfig {
    const val REPO_OWNER = "hk124cn"
    const val REPO_NAME = "autoring"
    const val LATEST_API = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"

    /** 自动检查的节流间隔：24 小时内不重复打扰 */
    const val AUTO_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
}

data class UpdateInfo(
    val versionName: String,
    val downloadUrl: String,
    val changelog: String,
    val tagName: String
)

/** 升级流程状态 */
sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class Downloading(val progress: Int) : UpdateState()
    data class Ready(val file: File) : UpdateState()
    data class Failed(val message: String) : UpdateState()
}

object UpdateManager {
    private const val TAG = "AutoRing"

    /** 当前 App 版本名，如 "1.0.0" */
    fun currentVersion(): String = BuildConfig.VERSION_NAME

    /**
     * 检查是否有新版本。返回 null 表示「无更新 / 检查失败 / 还没发布 Release」。
     * 注意：只有当远端 tag 版本 > 当前版本时才返回。
     */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(UpdateConfig.LATEST_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 10_000
                readTimeout = 10_000
                instanceFollowRedirects = true
            }
            try {
                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    Log.w(TAG, "检查更新失败 HTTP=${conn.responseCode}")
                    return@withContext null
                }
                val text = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(text)
                val tag = json.optString("tag_name", "")
                val version = tag.removePrefix("v").trim()
                if (version.isBlank()) return@withContext null

                if (!isNewer(version, BuildConfig.VERSION_NAME)) {
                    Log.i(TAG, "已是最新版本 ($version <= ${BuildConfig.VERSION_NAME})")
                    return@withContext null
                }

                // 找到 Releases 里的 .apk 附件
                var apkUrl: String? = null
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.optJSONObject(i) ?: continue
                        val name = a.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = a.optString("browser_download_url")
                            break
                        }
                    }
                }
                if (apkUrl.isNullOrBlank()) {
                    Log.w(TAG, "Release $tag 没有 .apk 附件")
                    return@withContext null
                }
                Log.i(TAG, "发现新版本 $version")
                UpdateInfo(
                    versionName = version,
                    downloadUrl = apkUrl,
                    changelog = json.optString("body", "").trim(),
                    tagName = tag
                )
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "检查更新异常", e)
            null
        }
    }

    /** 下载 APK 到私有缓存目录（无需存储权限），带进度回调 */
    suspend fun download(
        context: Context,
        info: UpdateInfo,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "update").apply { mkdirs() }
        val file = File(dir, "autoring-${info.versionName}.apk")
        if (file.exists()) file.delete()

        val conn = (URL(info.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            val total = conn.contentLength
            conn.inputStream.use { input ->
                FileOutputStream(file).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var sum = 0L
                    var last = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        sum += n
                        if (total > 0) {
                            val p = (sum * 100 / total).toInt()
                            if (p != last) {
                                last = p
                                onProgress(p)
                            }
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        file
    }

    /** 是否有「安装未知应用」权限（Android 8+ 必须，否则安装界面打不开） */
    fun canInstallPackages(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return context.packageManager.canRequestPackageInstalls()
    }

    /** 跳转到「允许安装未知应用」设置页 */
    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }.onFailure {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
    }

    /**
     * 拉起系统安装界面。
     * 这是**覆盖安装**：同包名 + 同签名时，系统只替换 APK，
     * /data 下的数据库与 SharedPreferences 原样保留 —— 也就是「升级保留原配置」。
     */
    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** 语义化版本比较：remote 是否比 current 新 */
    fun isNewer(remote: String, current: String): Boolean {
        val a = parts(remote)
        val b = parts(current)
        for (i in 0..2) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parts(v: String): List<Int> =
        v.trim().removePrefix("v")
            .split(".", "-", "_")
            .take(3)
            .map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
}
