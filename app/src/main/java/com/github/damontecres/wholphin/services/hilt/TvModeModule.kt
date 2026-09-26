package com.github.damontecres.wholphin.services.hilt

import com.github.damontecres.wholphin.services.WholphinTvModeHost
import com.github.damontecres.wholphin.tvmode.TvModeHost
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class TvModeModule {
    @Binds
    abstract fun tvModeHost(host: WholphinTvModeHost): TvModeHost
}
