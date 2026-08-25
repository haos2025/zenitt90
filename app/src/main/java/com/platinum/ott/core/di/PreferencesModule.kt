package com.platinum.ott.core.di

import android.content.Context
import com.platinum.ott.core.AuthPreferences
import com.platinum.ott.core.InterfacePreferences
import com.platinum.ott.core.NetworkPreferences
import com.platinum.ott.core.NotificationPreferences
import com.platinum.ott.core.RecentSearchPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Все четыре Preferences-класса в ServiceLocator были независимы от
 * авторизации (interfacePreferences — вообще нужен ещё на экране логина,
 * остальные три — `by lazy` вне initAuth()). Тот же критерий, что и для
 * database: не пересоздаются в reinitWithAuth(), значит не часть
 * SessionGraph, а обычные Hilt-синглтоны.
 */
@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {

    @Provides
    @Singleton
    fun provideInterfacePreferences(@ApplicationContext context: Context): InterfacePreferences =
        InterfacePreferences(context)

    @Provides
    @Singleton
    fun provideAuthPreferences(@ApplicationContext context: Context): AuthPreferences =
        AuthPreferences(context)

    @Provides
    @Singleton
    fun provideNetworkPreferences(@ApplicationContext context: Context): NetworkPreferences =
        NetworkPreferences(context)

    @Provides
    @Singleton
    fun provideNotificationPreferences(@ApplicationContext context: Context): NotificationPreferences =
        NotificationPreferences(context)

    @Provides
    @Singleton
    fun provideRecentSearchPreferences(@ApplicationContext context: Context): RecentSearchPreferences =
        RecentSearchPreferences(context)
}
