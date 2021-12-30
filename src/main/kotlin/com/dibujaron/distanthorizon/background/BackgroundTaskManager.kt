package com.dibujaron.distanthorizon.background

import com.dibujaron.distanthorizon.DHModule
import java.util.concurrent.Executors

object BackgroundTaskManager: DHModule {
    private val asyncExecutor = Executors.newFixedThreadPool(1)

    fun executeInBackground(command: () -> Unit){
        asyncExecutor.execute(command)
    }

    override fun shutDown(){
        asyncExecutor.shutdown()
    }
}