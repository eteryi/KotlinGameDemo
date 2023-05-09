package net.novauniverse.game.kotlindemo.game.mapmodules.config

import net.zeeraa.novacore.spigot.gameengine.module.modules.game.map.mapmodule.MapModule
import org.json.JSONObject
/**
* To allow us to configure the game differently for each map we create a map module that stores config
*/
class KotlinDemoGameConfig(json: JSONObject):MapModule(json) {
    val gameTime : Int = json.optInt("time_seconds", -1)
    val hudType : String = json.optString("hud_type", "ACTION_BAR")
}