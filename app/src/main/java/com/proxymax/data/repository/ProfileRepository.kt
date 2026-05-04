package com.proxymax.data.repository

import android.util.Log
import com.proxymax.data.model.*
import com.proxymax.data.parser.SubscriptionParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
                val req  = Request.Builder().url(url)
                    .header("User-Agent", "ClashMeta/1.0 ProxyMax/1.0")
                    .build()
                val body = okhttp.newCall(req).execute().use { it.body?.string() ?: "" }
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

    /** 设为激活配置 */
    suspend fun setActiveProfile(profileId: Int) = withContext(Dispatchers.IO) {
        profileDao.clearActiveProfiles()
        val profile = profileDao.getAllProfiles().let {
            // blocking query
            profileDao.getActiveProfile() // temp
        }
        // update via query
        // In production, add a specific DAO method
    }

    /** 更新节点延迟 */
    suspend fun updateLatency(nodeId: String, latency: Int) =
        nodeDao.updateLatency(nodeId, latency)

    suspend fun deleteProfile(profile: ProxyProfile) = withContext(Dispatchers.IO) {
        nodeDao.deleteNodesForProfile(profile.id)
        profileDao.deleteProfile(profile)
    }
}
