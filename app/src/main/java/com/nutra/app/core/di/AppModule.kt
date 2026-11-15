package com.nutra.app.core.di

import android.content.Context
import androidx.room.Room
import com.nutra.app.core.database.NutraDao
import com.nutra.app.core.database.NutraDatabase
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

    @Provides
    @Singleton
    fun provideNutraDatabase(
        @ApplicationContext context: Context,
    ): NutraDatabase = Room.databaseBuilder(
        context,
        NutraDatabase::class.java,
        "nutra_database.db",
    )
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

    @Provides
    @Singleton
    fun provideNutraDao(database: NutraDatabase): NutraDao = database.nutraDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
}
