package net.novauniverse.game.kotlindemo.game

import net.md_5.bungee.api.ChatColor
import net.novauniverse.game.kotlindemo.game.mapmodules.config.KotlinDemoGameConfig
import net.zeeraa.novacore.commons.log.Log
import net.zeeraa.novacore.commons.tasks.Task
import net.zeeraa.novacore.spigot.NovaCore
import net.zeeraa.novacore.spigot.abstraction.VersionIndependentUtils
import net.zeeraa.novacore.spigot.gameengine.module.modules.game.GameEndReason
import net.zeeraa.novacore.spigot.gameengine.module.modules.game.MapGame
import net.zeeraa.novacore.spigot.gameengine.module.modules.game.elimination.PlayerQuitEliminationAction
import net.zeeraa.novacore.spigot.gameengine.module.modules.game.triggers.GameTrigger
import net.zeeraa.novacore.spigot.gameengine.module.modules.game.triggers.RepeatingGameTrigger
import net.zeeraa.novacore.spigot.gameengine.module.modules.game.triggers.TriggerFlag
import net.zeeraa.novacore.spigot.tasks.SimpleTask
import net.zeeraa.novacore.spigot.utils.ItemBuilder
import net.zeeraa.novacore.spigot.utils.PlayerUtils
import net.zeeraa.novacore.spigot.utils.RandomFireworkEffect
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.plugin.Plugin
import java.util.function.Consumer


/**
 * This is our game class.
 *
 * GameManager will automatically register listeners if Listener is implemented
 * */
class KotlinDemoGame(plugin:Plugin): MapGame(plugin), Listener {
    private var started: Boolean = false
    private var ended: Boolean = false

    private var giveArrowDelay: Int = 5
    private var maxArrows: Int = 4

    private var saturationTask: SimpleTask? = null

    // To give the arrows we use game triggers. Game triggers are actions in the game that either can be manually triggered, triggered after a specified delay or act like a repeating timer for giving players items or handle reoccurring game events
    private var giveArrowTrigger: RepeatingGameTrigger? = null

    // We make an api to get the trigger so that we can make a tournament system integration for this game so that we can use this value in the scoreboard
    fun getGiveArrowTrigger(): RepeatingGameTrigger? {
        return giveArrowTrigger
    }

    override fun getName(): String {
        return "kotlindemogame"
    }

    override fun getDisplayName(): String {
        return "Kotlin Demo Game"
    }

    override fun getPlayerQuitEliminationAction(): PlayerQuitEliminationAction {
        // We allow players to reconnect to the game
        return PlayerQuitEliminationAction.DELAYED
    }

    override fun eliminatePlayerOnDeath(player: Player?): Boolean {
        // In this game we always eliminate the player on death
        return true
    }

    override fun isPVPEnabled(): Boolean {
        // PvP is always enabled. If we want pvp to not always be enabled we put the logic for that here
        return true
    }

    override fun autoEndGame(): Boolean {
        // Since this game is a simple last man standing game we enable auto ending
        return true
    }

    override fun hasStarted(): Boolean {
        return started
    }

    override fun hasEnded(): Boolean {
        return ended
    }

    override fun isFriendlyFireAllowed(): Boolean {
        // Friendly fire is never enabled in this game
        return false
    }

    override fun canAttack(p0: LivingEntity, p1: LivingEntity): Boolean {
        // We don't need to check if players can attack each other in this game. isFriendlyFireAllowed takes care of preventing friendly fire
        return true
    }

    override fun onStart() {
        if(started) {
            return
        }

        // Let's make sure the players don't run out of food
        // We have our own class called SimpleTask to handle repeating stuff like this
        // Also there might be another way to prevent food drain that does not involve changing difficulty, but I prefer to just do it like this anyway
        saturationTask = SimpleTask(plugin, {
            Bukkit.getServer().onlinePlayers.forEach{
                it.foodLevel = 20
                it.saturation = 20F
            }
        }, 20L)
        saturationTask!!.start()

        // Check if our config map module has been loaded and if so read the variables set in it
        if(activeMap.mapData.hasMapModule(KotlinDemoGameConfig::class.java)) {
            Log.info("Reading game settings from config module")
            val config = activeMap.mapData.getMapModule(KotlinDemoGameConfig::class.java) as KotlinDemoGameConfig

            if(config.giveArrowDelay > -1) {
                giveArrowDelay = config.giveArrowDelay
            }

            if(config.maxArrows > -1) {
                maxArrows = config.maxArrows
            }
        }

        giveArrowTrigger = RepeatingGameTrigger("kotlindemo.givearrows", 1L, giveArrowDelay * 20L) { _: GameTrigger?, _: TriggerFlag? ->
            run {
                // onlinePlayers from the game class return the online players that are still in the game
                onlinePlayers.forEach { p: Player ->
                    var totalArrows = 0
                    for (item in p.inventory.contents) {
                        if (item != null) {
                            if (item.type == Material.ARROW) {
                                totalArrows += item.amount
                            }
                        }
                    }

                    if (totalArrows < maxArrows) {
                        // To quickly create item stacks check out our ItemBuilder class
                        p.inventory.addItem(ItemBuilder.materialToItemStack(Material.ARROW, 1))
                    }
                }
            }
        }

        // By adding DISABLE_LOGGING we keep the console clean. If the trigger only runs once every minute or so it's ok to keep the logs
        // note that the log level of this is TRACE so by default these logs are not shown unless the player or console run nova log set TRACE
        giveArrowTrigger!!.addFlag(TriggerFlag.DISABLE_LOGGING)

        // We want to automatically start and stop this trigger, so we add these 2 flags
        giveArrowTrigger!!.addFlag(TriggerFlag.START_ON_GAME_START)
        giveArrowTrigger!!.addFlag(TriggerFlag.STOP_ON_GAME_END)

        // Now we register the trigger to the game
        addTrigger(giveArrowTrigger!!)

        // Now we teleport the players
        val spawnLocations : MutableList<Location> = ArrayList()
        Bukkit.getServer().onlinePlayers.forEach {
            PlayerUtils.clearPlayerInventory(it)
            PlayerUtils.clearPotionEffects(it)
            PlayerUtils.resetPlayerXP(it)

            // The reason why we use PlayerUtils here for these functions are to support both 1.8 and newer versions of the game since player.setMaxHealth is deprecated
            PlayerUtils.setMaxHealth(it, 20.0)
            PlayerUtils.fullyHealPlayer(it)

            // These are usually a good idea to reset
            it.fallDistance = 0F
            it.fireTicks = 0

            // If the player is participating in the game we teleport them to the game otherwise to the spectator location
            if(isPlayerInGame(it)) {
                // If we run out of spawn locations we reuse the old ones
                if(spawnLocations.size == 0) {
                    spawnLocations += activeMap.starterLocations
                    spawnLocations.shuffle()
                }

                val location: Location = spawnLocations.removeAt(0)

                it.teleport(location)

                // Give the player some items
                it.inventory.setItem(0, ItemBuilder(Material.BOW).setUnbreakable(true).build())
                it.inventory.setItem(8, ItemBuilder(Material.COMPASS).setName("${ChatColor.GOLD}Player Tracker").build())

                it.gameMode = GameMode.SURVIVAL
            } else {
                // tpToSpectator is a built-in function in the Game class
                tpToSpectator(it)
            }
        }

        // The reason we use this method is that they changed the way you set game rules in newer versions of the game
        VersionIndependentUtils.get().setGameRule(world, "keepInventory", "true")

        // We call sendBeginEvent here since our game starts instantly. If the game starts after a countdown call this once the countdown is over instead
        sendBeginEvent()
        started = true
    }

    override fun onEnd(reason: GameEndReason?) {
        if(ended) {
            return
        }

        // Stop the food task
        Task.tryStopTask(saturationTask)

        // Spawn some fireworks
        activeMap.starterLocations.forEach(Consumer { location: Location ->
            val fw = location.world.spawnEntity(location, EntityType.FIREWORK) as Firework
            val fwm = fw.fireworkMeta
            fwm.power = 2
            fwm.addEffect(RandomFireworkEffect.randomFireworkEffect())
            if (random.nextBoolean()) {
                fwm.addEffect(RandomFireworkEffect.randomFireworkEffect())
            }
            fw.fireworkMeta = fwm
        })

        // Heal players and set them to spectator mode
        Bukkit.getServer().onlinePlayers.forEach { player: Player ->
            VersionIndependentUtils.get().resetEntityMaxHealth(player)
            player.foodLevel = 20
            PlayerUtils.clearPlayerInventory(player)
            PlayerUtils.resetPlayerXP(player)
            player.gameMode = GameMode.SPECTATOR
        }

        ended = true
    }

    // Respawn in spectator mode
    override fun onPlayerRespawn(player: Player) {
        Bukkit.getScheduler().scheduleSyncDelayedTask(NovaCore.getInstance(), {
            Log.trace(name, "Calling tpToSpectator(" + player.name + ")")
            tpToSpectator(player)
        }, 5L)
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onPlayerDropItem(e: PlayerDropItemEvent) {
        if(!started) {
            return
        }

        e.isCancelled = true
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onEntityDamageByEntity(e: EntityDamageByEntityEvent) {
        if(!started) {
            return
        }

        if(e.damager is Arrow) {
            e.damage = 1000.0
        }
    }
}