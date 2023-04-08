package net.novauniverse.game.kotlindemo.game.mapmodules.config

import net.zeeraa.novacore.spigot.gameengine.module.modules.game.map.mapmodule.MapModule
import org.json.JSONObject
/**
* To allow us to configure the game differently for each map we create a map module that stores config
*/
class KotlinDemoGameConfig(json: JSONObject):MapModule(json) {
    val giveArrowDelay: Int = json.optInt("give_arrow_delay", -1)
    val maxArrows: Int = json.optInt("max_arrows", -1)
}