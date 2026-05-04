package com.proxymax.di

import android.content.Context
import androidx.room.Room
import com.proxymax.data.repository.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "proxymax.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides @Singleton
    fun provideProfileDao(db: AppDatabase) = db.profileDao()

    @Provides @Singleton
    fun provideNodeDao(db: AppDatabase) = db.nodeDao()

    @Provides @Singleton
    fun provideOkHttp(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
}
