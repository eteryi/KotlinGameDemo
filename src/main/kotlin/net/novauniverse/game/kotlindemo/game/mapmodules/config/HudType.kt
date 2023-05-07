package net.novauniverse.game.kotlindemo.game.mapmodules.config

import net.zeeraa.novacore.spigot.abstraction.VersionIndependentUtils
import org.bukkit.entity.Player

enum class HudType : HUD {
    CHAT {
        override fun getTaskTime(): Long { return (20 * 5).toLong() }
        override fun sendMessage(p: Player, m: String): Boolean { p.sendMessage(m); return true }
    },
    ACTION_BAR {
        override fun getTaskTime(): Long { return 10L }
        override fun sendMessage(p: Player, m: String): Boolean { VersionIndependentUtils.get().sendActionBarMessage(p, m); return true }
    }
}