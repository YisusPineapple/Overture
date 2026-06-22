package io.github.zyrouge.symphony.services

import com.yisuspineapple.overture.BuildConfig
import io.github.zyrouge.symphony.utils.HttpClient
import io.github.zyrouge.symphony.utils.Logger
import okhttp3.CacheControl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

@Suppress("ConstPropertyName")
object AppMeta {
    const val appName = "Overture"
    const val author = "YisusPineapple"
    const val githubRepositoryOwner = "YisusPineapple"
    const val githubRepositoryName = "Overture"
    const val githubProfileUrl = "https://github.com/$githubRepositoryOwner"
    const val githubRepositoryUrl =
        "https://github.com/$githubRepositoryOwner/$githubRepositoryName"

    const val version = "v${BuildConfig.VERSION_NAME}"
    var latestVersion: String? = null
    const val githubLatestReleaseUrl = "$githubRepositoryUrl/releases/latest"
    const val githubIssuesUrl = "$githubRepositoryUrl/issues"
    const val discordUrl = "https://discord.gg/5k9Hdq7ycm "
    const val redditUrl = "https://reddit.com/r/symphony_app"
    const val contributingUrl = "$githubRepositoryUrl#contributing"

    const val packageName = "com.yisuspineapple.overture"
    const val izzyOnDroidUrl = "https://apt.izzysoft.de/fdroid/index/apk/$packageName"
    const val fdroidUrl = "https://f-droid.org/en/packages/$packageName"
    const val playStoreUrl = "https://play.google.com/store/apps/details?id=$packageName"

    fun isNightlyBuild() = version.contains("-nightly") || version.contains("-canary")

    fun fetchLatestVersion() = when {
        isNightlyBuild() -> fetchLatestNightlyVersion()
        else -> fetchLatestStableVersion()
    }

    fun fetchLatestStableVersion(): String? {
        try {
            val latestReleaseUrl =
                "https://api.github.com/repos/$githubRepositoryOwner/$githubRepositoryName/releases/latest"
            val req = Request.Builder()
                .url(latestReleaseUrl)
                .cacheControl(CacheControl.FORCE_NETWORK)
                .build()
            val res = HttpClient.newCall(req).execute()
            
            if (!res.isSuccessful) return null
            
            val content = res.body?.string() ?: return null
            val json = JSONObject(content)
            val tagName = json.optString("tag_name", "")
            val draft = json.optBoolean("draft", true)
            
            if (!draft && tagName.isNotEmpty()) {
                latestVersion = tagName
            }
        } catch (err: Exception) {
            Logger.warn("AppMeta", "version check failed: $err")
        }
        return latestVersion
    }

    fun fetchLatestNightlyVersion(): String? {
        try {
            val latestReleaseUrl =
                "https://api.github.com/repos/$githubRepositoryOwner/$githubRepositoryName/releases"
            val req = Request.Builder()
                .url(latestReleaseUrl)
                .cacheControl(CacheControl.FORCE_NETWORK)
                .build()
            val res = HttpClient.newCall(req).execute()
            
            if (!res.isSuccessful) return null
            
            val content = res.body?.string() ?: return null
            val json = JSONArray(content)
            
            for (i in 0 until json.length()) {
                val x = json.getJSONObject(i)
                val tagName = x.optString("tag_name", "")
                val prerelease = x.optBoolean("prerelease", false)
                
                if (prerelease && tagName.isNotEmpty()) {
                    latestVersion = tagName
                    break
                }
            }
        } catch (err: Exception) {
            Logger.warn("AppMeta", "nightly version check failed: $err")
        }
        return latestVersion
    }
}