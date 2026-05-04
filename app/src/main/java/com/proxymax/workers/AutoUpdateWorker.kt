package com.proxymax.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.proxymax.data.repository.ProfileRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class AutoUpdateWorker @AssistedInject constructor(
    @Assisted ctx:    Context,
    @Assisted params: WorkerParameters,
    private val repo: ProfileRepository
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            // TODO: 从 DB 读出所有带 URL 且 autoUpdate=true 的 profile 逐一刷新
        }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    companion object {
        const val WORK_NAME = "auto_update_subscriptions"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<AutoUpdateWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                req
            )
        }
    }
}
