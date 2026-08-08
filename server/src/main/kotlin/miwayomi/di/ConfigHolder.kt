package miwayomi.di

import miwayomi.ServerConfig

object ConfigHolder {
    @Volatile
    var config: ServerConfig = ServerConfig()
}
