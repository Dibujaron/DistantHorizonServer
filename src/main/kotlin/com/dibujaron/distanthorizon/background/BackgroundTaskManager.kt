package com.dibujaron.distanthorizon.background

import java.util.concurrent.Executors

object BackgroundTaskManager {
    private val asyncExecutor = Executors.newFixedThreadPool(1)

    fun executeInBackground(command: () -> Unit){
        asyncExecutor.execute(command)
    }

    fun shutdown(){
        asyncExecutor.shutdown()
    }
}