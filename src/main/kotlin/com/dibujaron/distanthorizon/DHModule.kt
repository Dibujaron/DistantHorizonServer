package com.dibujaron.distanthorizon

import java.util.*

interface DHModule {
    fun moduleInit(serverProperties: Properties){}
    fun tick(){}
    fun shutDown(){}
}