package com.platinum.ott.core.di

import android.content.Context
import com.platinum.ott.data.local.ZenithDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Раньше `database` жил как `by lazy` внутри ServiceLocator и не зависел
 * от логина/реавторизации — вынесен отдельным модулем первым, до
 * SessionGraph, по той же причине: не участвует в reinitWithAuth(), значит
 * не обязан жить в графе, который умеет пересобираться на логине.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideZenithDatabase(@ApplicationContext context: Context): ZenithDatabase =
        ZenithDatabase.getInstance(context)
}
