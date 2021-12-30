package com.dibujaron.distanthorizon.discord

import com.dibujaron.distanthorizon.DHModule
import com.dibujaron.distanthorizon.event.EventHandler
import com.dibujaron.distanthorizon.event.EventManager
import com.dibujaron.distanthorizon.event.PlayerChatEvent
import com.dibujaron.distanthorizon.player.PlayerManager
import com.jessecorbett.diskord.dsl.bot
import com.jessecorbett.diskord.dsl.command
import com.jessecorbett.diskord.dsl.commands
import com.jessecorbett.diskord.util.ClientStore
import com.jessecorbett.diskord.util.sendMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*
import kotlin.collections.HashMap

object DiscordManager : DHModule, EventHandler {

    private var CHANNEL_ID = ""
    private var BOT_TOKEN = ""
    private var BOT_USERNAME = ""
    private var expectingMessageFromSelf = false
    private val scope = CoroutineScope(Dispatchers.Default)

    override fun moduleInit(serverProperties: Properties) {
        BOT_TOKEN =
            serverProperties.getProperty("discord.bot.token", "")
        CHANNEL_ID = serverProperties.getProperty("discord.bot.channel.id", "")
        BOT_USERNAME = serverProperties.getProperty("discord.bot.username", "Ingame Chat")
        if (BOT_TOKEN.isNotEmpty() && CHANNEL_ID.isNotEmpty()) {
            EventManager.registerEvents(this)
            println("Discord integration enabled, launching bot.")
            scope.launch { initializeBot() }
        } else {
            println("Warning: Discord integration is disabled, bot token is not set.")
        }
    }

    private suspend fun initializeBot() {
        bot(BOT_TOKEN) {
            commands { //uses default prefix of "."
                command("ping") {
                    println("got bot command!")
                    reply("pong")
                    delete()
                }
                messageCreated {
                    if (it.channelId == CHANNEL_ID) {
                        if (it.author.username == BOT_USERNAME ){
                            if(expectingMessageFromSelf) {
                                expectingMessageFromSelf = false //got it
                            } else {
                                PlayerManager.broadcast(it.content) //message includes author
                            }
                        } else {
                            PlayerManager.broadcast(it.author.username, it.content)
                        }
                    }
                }
            }
        }
        println("Discord integration initialized.")
    }

    override fun onPlayerChat(event: PlayerChatEvent) {
        if (BOT_TOKEN.isNotEmpty() && CHANNEL_ID.isNotEmpty()) {
            val sender = event.player.getDisplayName()
            val message = event.message
            expectingMessageFromSelf = true
            scope.launch {
                relayChat(sender, message)
            }
        }
    }

    private suspend fun relayChat(sender: String, message: String) {
        ClientStore(BOT_TOKEN).channels[CHANNEL_ID].sendMessage("$sender: $message")
    }
}