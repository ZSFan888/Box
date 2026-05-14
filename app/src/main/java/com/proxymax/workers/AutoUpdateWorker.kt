package com.proxymax.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.proxymax.data.repository.ProfileRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class AutoUpdateWorker @AssistedInject constructor(
    @Assisted ctx:    Context,
    @Assisted params: WorkerParameters,
    private val repo: ProfileRepository
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            // 拉取所有开启了自动更新且有 URL 的订阅
            val profiles = repo.getAllProfiles().first()
                .filter { it.autoUpdate && it.url.isNotBlank() }

            if (profiles.isEmpty()) {
                Log.d(TAG, "No profiles need auto-update")
                return Result.success()
            }

            var failCount = 0
            profiles.forEach { profile ->
                repo.fetchAndSaveProfile(profile.name, profile.url)
                    .onSuccess { Log.d(TAG, "Auto-updated: ${profile.name}") }
                    .onFailure { e ->
                        failCount++
                        Log.w(TAG, "Failed to update ${profile.name}: ${e.message}")
                    }
            }

            // 全部失败才 retry，部分失败也算 success（避免无限重试）
            if (failCount == profiles.size) Result.retry() else Result.success()
        }.getOrElse { e ->
            Log.e(TAG, "AutoUpdateWorker crashed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "AutoUpdateWorker"
        const val WORK_NAME = "auto_update_subscriptions"

        fun schedule(context: Context, intervalHours: Int = 6) {
            val req = PeriodicWorkRequestBuilder<AutoUpdateWorker>(
                intervalHours.toLong(), TimeUnit.HOURS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,   // 间隔改变时更新
                req
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
