package net.novauniverse.game.kotlindemo.game.mapmodules.config

import org.bukkit.entity.Player

interface HUD {
    fun getTaskTime() : Long
    fun sendMessage(p : Player, m : String) : Boolean
}