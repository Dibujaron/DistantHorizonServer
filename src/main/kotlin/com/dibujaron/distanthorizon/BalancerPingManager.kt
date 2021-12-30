package com.dibujaron.distanthorizon

import com.dibujaron.distanthorizon.player.PlayerManager
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.httpPost
import org.json.JSONObject
import java.util.*

object BalancerPingManager: DHModule {

    override fun moduleInit(serverProperties: Properties) {
        sendBalancerPing()
    }

    var lastBalancerPing = 0
    override fun tick() {
        val ticksSinceLastBalancerPing = DHServer.getTickCount() - lastBalancerPing
        if (ticksSinceLastBalancerPing >= DHServer.balancerPingsEveryTicks) {
            lastBalancerPing = DHServer.getTickCount()
            sendBalancerPing()
        }
    }

    private fun sendBalancerPing() {
        val payload = JSONObject()
        payload.put("secret", DHServer.serverSecret)
        payload.put("player_count", PlayerManager.playerCount())
        payload.put("server_limit", DHServer.maxPlayers)
        "http://distant-horizon.io/server_heartbeat"
            .httpPost()
            .jsonBody(payload.toString())
            .responseString { result -> result.get() }
    }
}