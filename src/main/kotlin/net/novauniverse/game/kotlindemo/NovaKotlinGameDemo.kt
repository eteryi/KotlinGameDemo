package net.novauniverse.game.kotlindemo

import net.novauniverse.game.kotlindemo.game.KotlinDemoGame
import net.novauniverse.game.kotlindemo.game.mapmodules.config.KotlinDemoGameConfig
import net.zeeraa.novacore.commons.log.Log
import net.zeeraa.novacore.spigot.gameengine.NovaCoreGameEngine
import net.zeeraa.novacore.spigot.gameengine.module.modules.game.GameManager
import net.zeeraa.novacore.spigot.gameengine.module.modules.game.map.mapmodule.MapModuleManager
import net.zeeraa.novacore.spigot.gameengine.module.modules.game.mapselector.selectors.guivoteselector.GUIMapVote
import net.zeeraa.novacore.spigot.gameengine.module.modules.gamelobby.GameLobby
import net.zeeraa.novacore.spigot.module.ModuleManager
import net.zeeraa.novacore.spigot.module.modules.compass.CompassTracker
import org.apache.commons.io.FileUtils
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.io.File


class NovaKotlinGameDemo: JavaPlugin() {
	private var game: KotlinDemoGame? = null

	override fun onEnable() {
		saveDefaultConfig()

		// We enable the GameManager and the GameLobby since those are required in 99% of all cases when making minigames
		ModuleManager.require(GameManager::class.java)
		ModuleManager.require(GameLobby::class.java)

		// We also enable compass tracker since players will have trackers in this game
		ModuleManager.require(CompassTracker::class.java)

		val mapSelector = GUIMapVote()
		Bukkit.getServer().pluginManager.registerEvents(mapSelector, this)
		GameManager.getInstance().mapSelector = mapSelector

		// Here we prepare the data directories
		var mapFolder = File(dataFolder.path + File.separator + "Maps")
		var worldFolder = File(dataFolder.path + File.separator + "Worlds")

		// To support our docker based tournament system we need to obey the requested data directory if it's not null in game manager
		if (NovaCoreGameEngine.getInstance().requestedGameDataDirectory != null) {
			mapFolder = File (NovaCoreGameEngine.getInstance().requestedGameDataDirectory.absolutePath + File.separator + "KotlinGameDemo" + File.separator + "Maps")
			worldFolder = File (NovaCoreGameEngine.getInstance().requestedGameDataDirectory.absolutePath + File.separator + "KotlinGameDemo" + File.separator + "Worlds")
		}

		FileUtils.forceMkdir(mapFolder)
		FileUtils.forceMkdir(worldFolder)

		// Register map modules
		MapModuleManager.addMapModule("oitc.config", KotlinDemoGameConfig::class.java)

		//Might not matter in this game since arrows instantly kills but this is how you prevent those players who think combat logging is funny
		GameManager.getInstance().isUseCombatTagging = true

		// Init our game class and lod it
		game = KotlinDemoGame(this)
		ModuleManager.getModule(GameManager::class.java).loadGame(game)

		// We have our own logger class that allows players to get in game logs by using /nova log set LEVEL
		Log.info(name, "Scheduled loading maps from " + mapFolder.path)
		// Load the maps. We use readMapsFromFolderDelayed to allow addon plugins to load their own map modules before we load the maps
		GameManager.getInstance().readMapsFromFolderDelayed(mapFolder, worldFolder)
	}

	override fun onDisable() {
	}

	// Expose our game instance so that our tournament plugin can interact with the game
	fun getGame(): KotlinDemoGame? {
		return game
	}
}
