package com.localruntime.core.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.localruntime.core.prefs.SecurePrefs
import com.localruntime.core.sms.SmsKillConfigStore
import com.localruntime.core.wipe.WipeGateway
import javax.inject.Singleton

/**
 * Hilt module for shared core singletons.
 *
 * Installed in [SingletonComponent], so each provider returns one instance
 * for the lifetime of the application process.
 */
@Module
@InstallIn(SingletonComponent::class)
object CoreModule {
    @Provides
    @Singleton
    fun provideSecurePrefs(
        @ApplicationContext context: Context,
    ): SecurePrefs =
        SecurePrefs.create(context)

    @Provides
    @Singleton
    fun provideSmsKillConfigStore(
        securePrefs: SecurePrefs,
    ): SmsKillConfigStore =
        SmsKillConfigStore(securePrefs)

    @Provides
    @Singleton
    fun provideWipeGateway(
        @ApplicationContext context: Context,
    ): WipeGateway =
        WipeGateway(context)
}
