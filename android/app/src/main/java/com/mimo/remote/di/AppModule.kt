package com.mimo.remote.di

import com.mimo.remote.data.remote.WebSocketClient
import com.mimo.remote.data.repository.MimoRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideWebSocketClient(): WebSocketClient = WebSocketClient()

    @Provides
    @Singleton
    fun provideMimoRepository(webSocketClient: WebSocketClient): MimoRepository =
        MimoRepository(webSocketClient)
}
