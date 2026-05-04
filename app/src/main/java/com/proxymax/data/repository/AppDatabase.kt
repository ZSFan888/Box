package com.proxymax.data.repository

import androidx.room.*
import com.proxymax.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY isActive DESC, lastUpdated DESC")
    fun getAllProfiles(): Flow<List<ProxyProfile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProxyProfile): Long

    @Update
    suspend fun updateProfile(profile: ProxyProfile)

    @Delete
    suspend fun deleteProfile(profile: ProxyProfile)

    @Query("SELECT * FROM profiles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveProfile(): ProxyProfile?

    @Query("UPDATE profiles SET isActive = 0")
    suspend fun clearActiveProfiles()
}

@Dao
interface NodeDao {
    @Query("SELECT * FROM proxy_nodes WHERE profileId = :profileId ORDER BY isFavorite DESC, latency ASC")
    fun getNodesForProfile(profileId: Int): Flow<List<ProxyNode>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNodes(nodes: List<ProxyNode>)

    @Query("UPDATE proxy_nodes SET latency = :latency WHERE id = :id")
    suspend fun updateLatency(id: String, latency: Int)

    @Query("DELETE FROM proxy_nodes WHERE profileId = :profileId")
    suspend fun deleteNodesForProfile(profileId: Int)
}

@Database(
    entities    = [ProxyProfile::class, ProxyNode::class],
    version     = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun nodeDao():    NodeDao
}

class Converters {
    @TypeConverter fun fromProfileType(v: ProfileType) = v.name
    @TypeConverter fun toProfileType(v: String) = ProfileType.valueOf(v)
}
