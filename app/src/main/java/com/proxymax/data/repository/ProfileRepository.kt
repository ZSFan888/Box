package com.proxymax.data.repository

import android.util.Log
import com.proxymax.data.model.*
import com.proxymax.data.parser.SubscriptionParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val profileDao: ProfileDao,
    private val nodeDao:    NodeDao,
    private val okhttp:     OkHttpClient
) {
    fun getAllProfiles(): Flow<List<ProxyProfile>> = profileDao.getAllProfiles()

    fun getNodesForProfile(profileId: Int): Flow<List<ProxyNode>> =
        nodeDao.getNodesForProfile(profileId)

    /** 从 URL 下载并解析订阅 */
    suspend fun fetchAndSaveProfile(name: String, url: String): Result<ProxyProfile> =
        withContext(Dispatchers.IO) {
            runCatching {
                Log.d("ProfileRepo", "Fetching: $url")
                val req = Request.Builder().url(url).build()
                val resp = okhttp.newCall(req).execute()
                val code = resp.code
                val body = resp.use { it.body?.string().orEmpty() }
                Log.d("ProfileRepo", "Response $code, body length=${body.length}")
                when {
                    code == 401 || code == 403 ->
                        throw Exception("订阅链接需要认证（HTTP $code），请检查链接是否过期")
                    code == 404 ->
                        throw Exception("订阅链接不存在（HTTP 404），请重新获取")
                    code !in 200..299 ->
                        throw Exception("服务器返回错误（HTTP $code）")
                    body.isBlank() ->
                        throw Exception("订阅内容为空，请检查链接是否有效")
                    body.trimStart().startsWith("<html") ||
                    body.trimStart().startsWith("<!DOCTYPE") ->
                        throw Exception("订阅链接返回了 HTML 页面，而非配置文件，请确认链接正确")
                }
                saveRawConfig(name, url, body)
            }
        }

    /** 保存手动粘贴的配置 */
    suspend fun saveRawConfig(name: String, url: String = "", raw: String): ProxyProfile =
        withContext(Dispatchers.IO) {
            val (type, nodes) = SubscriptionParser.parse(raw, profileId = 0)
            val profile = ProxyProfile(
                name        = name,
                type        = type,
                url         = url,
                rawConfig   = raw,
                lastUpdated = System.currentTimeMillis()
            )
            val id = profileDao.insertProfile(profile).toInt()
            val profileWithId = profile.copy(id = id)
            if (nodes.isNotEmpty()) {
                nodeDao.deleteNodesForProfile(id)
                nodeDao.insertNodes(nodes.map { it.copy(profileId = id) })
            }
            Log.d("ProfileRepo", "Saved profile id=$id with ${nodes.size} nodes")
            profileWithId
        }

    /** 设为激活配置（同时自动激活首个配置） */
    suspend fun setActiveProfile(profileId: Int) = withContext(Dispatchers.IO) {
        profileDao.clearActiveProfiles()
        val profiles = profileDao.getAllProfiles().first()
        val target = profiles.find { it.id == profileId } ?: return@withContext
        profileDao.updateProfile(target.copy(isActive = true))
    }

    /** 更新节点延迟 */
    suspend fun updateLatency(nodeId: String, latency: Int) =
        nodeDao.updateLatency(nodeId, latency)

    /** 更新订阅字段（autoUpdate、name 等） */
    suspend fun updateProfile(profile: ProxyProfile) = withContext(Dispatchers.IO) {
        profileDao.updateProfile(profile)
    }

    suspend fun deleteProfile(profile: ProxyProfile) = withContext(Dispatchers.IO) {
        nodeDao.deleteNodesForProfile(profile.id)
        profileDao.deleteProfile(profile)
    }
}
