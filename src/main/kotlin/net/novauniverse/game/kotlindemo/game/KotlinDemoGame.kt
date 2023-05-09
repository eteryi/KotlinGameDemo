package net.novauniverse.game.kotlindemo.game

import net.md_5.bungee.api.ChatColor
import net.novauniverse.game.kotlindemo.game.mapmodules.config.HudType
import net.novauniverse.game.kotlindemo.game.mapmodules.config.KotlinDemoGameConfig
import net.zeeraa.novacore.commons.log.Log
import net.zeeraa.novacore.commons.tasks.Task
import net.zeeraa.novacore.spigot.abstraction.VersionIndependentUtils
import net.zeeraa.novacore.spigot.gameengine.module.modules.game.GameEndReason
import net.zeeraa.novacore.spigot.gameengine.module.modules.game.MapGame
import net.zeeraa.novacore.spigot.gameengine.module.modules.game.elimination.PlayerQuitEliminationAction
import net.zeeraa.novacore.spigot.gameengine.module.modules.game.triggers.GameTrigger
import net.zeeraa.novacore.spigot.gameengine.module.modules.game.triggers.RepeatingGameTrigger
import net.zeeraa.novacore.spigot.gameengine.module.modules.game.triggers.TriggerFlag
import net.zeeraa.novacore.spigot.tasks.SimpleTask
import net.zeeraa.novacore.spigot.teams.TeamManager
import net.zeeraa.novacore.spigot.utils.ItemBuilder
import net.zeeraa.novacore.spigot.utils.PlayerUtils
import net.zeeraa.novacore.spigot.utils.RandomFireworkEffect
import org.bukkit.*
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntitySpawnEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerPickupItemEvent
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.function.Consumer

/**
 * This is our game class.
 *
 * GameManager will automatically register listeners if Listener is implemented
 * */
class KotlinDemoGame(plugin:Plugin): MapGame(plugin), Listener {
    private var started: Boolean = false
    private var ended: Boolean = false

    // Player Stats, basically.
    private val kills = hashMapOf<UUID, Int>()
    private val respawn = hashMapOf<UUID, Boolean>()
    private val respawnCooldown = (20 * 5).toLong()

    private var gameTimeSeconds : Int = 60

    private var saturationTask: SimpleTask? = null
    private var endGameTask: SimpleTask? = null

    private var hudTask : SimpleTask? = null
    private var currentTime : Int = 0

    private var hudType : HudType = HudType.ACTION_BAR
    // To give the arrows we use game triggers. Game triggers are actions in the game that either can be manually triggered, triggered after a specified delay or act like a repeating timer for giving players items or handle reoccurring game events
    // // As I am not using any Game Triggers currently for this plugin, I have decided to just comment all references to it, in case I ever need them again.
    // private var giveArrowTrigger: RepeatingGameTrigger? = null

    // We make an api to get the trigger so that we can make a tournament system integration for this game so that we can use this value in the scoreboard
    /* fun getGiveArrowTrigger(): RepeatingGameTrigger? {
        return giveArrowTrigger
    }

     */

    override fun getName(): String {
        return "oitc"
    }

    override fun getDisplayName(): String {
        return "One In The Chamber"
    }

    override fun getPlayerQuitEliminationAction(): PlayerQuitEliminationAction {
        // We allow players to reconnect to the game
        return PlayerQuitEliminationAction.DELAYED
    }

    override fun eliminatePlayerOnDeath(player: Player?): Boolean {
        // In this game we DON'T always eliminate the player on death
        return false
    }

    override fun isPVPEnabled(): Boolean {
        // PvP is always enabled. If we want pvp to not always be enabled we put the logic for that here
        return true
    }

    override fun autoEndGame(): Boolean {
        return false
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
        // Code removed here to go to EntityDamage, mainly because of the respawn invincibility.
        return true
    }

    // Mostly just so I don't have to repeat myself trying to get the player with most kills and their kills. Not used much but still here.
    private fun getMostKills() : Pair<String, Int> {
        var name = "none"
        var v = 0
       kills.forEach { (t, u) ->
           if (u > v) {
               v = u
               name = Bukkit.getPlayer(t).name
           } else if (u == v) {
               name += " ${Bukkit.getPlayer(t).name}"
           }
       }
        return Pair(name, v)
    }

    override fun onStart() {
        if(started) {
            return
        }

        // Check if our config map module has been loaded and if so read the variables set in it
        if(activeMap.mapData.hasMapModule(KotlinDemoGameConfig::class.java)) {
            Log.info("Reading game settings from config module")
            val config = activeMap.mapData.getMapModule(KotlinDemoGameConfig::class.java) as KotlinDemoGameConfig

            if(config.gameTime > -1) {
                gameTimeSeconds = config.gameTime
            }

            // Tries to check if it's a valid HUDType
            try {
                hudType = HudType.valueOf(config.hudType)
            } catch (e : IllegalArgumentException) {
                hudType = HudType.CHAT
                Log.error("Invalid HUDType.")
            }
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

        // Also, let's just start the countdown until the game ends, probably a good idea.
        endGameTask = SimpleTask(plugin, {
            currentTime++
            if (currentTime >= gameTimeSeconds) {
                endGame(GameEndReason.TIME)
                endGameTask?.stop()
            }
        }, 20L)

        // Oh, and there's also the HUD task, which will display current game information to the player depending on which config the map is set on.
        hudTask = SimpleTask(plugin, {
            Bukkit.getServer().onlinePlayers.forEach {
                hudType.sendMessage(it, "${ChatColor.GREEN}${it.name}${ChatColor.WHITE}: ${ChatColor.RED}${kills[it.uniqueId] ?: 0} ${ChatColor.WHITE}| ${ChatColor.GREEN}Top Player(s)${ChatColor.WHITE}: ${ChatColor.GOLD}${getMostKills().first} ${ChatColor.WHITE}(${ChatColor.RED}${getMostKills().second}${ChatColor.WHITE}) | ${ChatColor.GREEN}Time Left${ChatColor.WHITE}: ${ChatColor.RED}${gameTimeSeconds - currentTime}s")
            }
        }, hudType.getTaskTime())

        hudTask!!.start()
        endGameTask!!.start()

        /*
        giveArrowTrigger = RepeatingGameTrigger("oitc.givearrows", 1L, giveArrowDelay * 20L) { _: GameTrigger?, _: TriggerFlag? ->
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

         */

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

                // Give the player the kit items
                it.inventory.setItem(0, ItemBuilder(Material.STONE_SWORD).setName("${ChatColor.RESET}Sword").setUnbreakable(true).build())
                it.inventory.setItem(1, ItemBuilder(Material.BOW).setUnbreakable(true).build())
                it.inventory.setItem(2, ItemBuilder(Material.ARROW).setAmount(1).build())
                it.inventory.setItem(8, ItemBuilder(Material.COMPASS).setName("${ChatColor.GOLD}Player Tracker").build())

                it.world.difficulty = Difficulty.NORMAL
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

        // Stop all the tasks
        Task.tryStopTask(saturationTask)
        Task.tryStopTask(endGameTask)
        Task.tryStopTask(hudTask)

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

            // Sends a message warning who won the game
            player.sendMessage("${getMostKills().first} won the game with ${ChatColor.RED}${getMostKills().second}${ChatColor.WHITE} kills.")
        }

        ended = true
    }

    // Respawn back in one of the spawn locations.
    override fun onPlayerRespawn(player: Player) {
        PlayerUtils.clearPotionEffects(player)
        PlayerUtils.resetPlayerXP(player)

        PlayerUtils.setMaxHealth(player, 20.0)
        PlayerUtils.fullyHealPlayer(player)
        if (!player.inventory.contains(Material.ARROW)) {
            player.inventory.addItem(ItemBuilder(Material.ARROW).setAmount(1).build())
        }

        var task : SimpleTask? = null
        task = SimpleTask(plugin, {
            player.teleport(activeMap.starterLocations.random())
            Task.tryStopTask(task)
        }, 2L, 20L)

        respawn[player.uniqueId] = true
        var respawnTimer : SimpleTask? = null
        respawnTimer = SimpleTask(plugin, {
            respawn[player.uniqueId] = false
            respawnTimer?.stop()
        }, respawnCooldown,40L)

        respawnTimer.start()
        task.start()
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onPlayerDamage(e: EntityDamageEvent) {
        if (e.cause == EntityDamageEvent.DamageCause.FALL) {
            e.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onPlayerDamage(e: EntitySpawnEvent) {
        if (e.entity is Creature) {
            e.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onPlayerDropItem(e: PlayerDropItemEvent) {
        if(!started) {
            return
        }

        e.isCancelled = true
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onArrowPickup(e : PlayerPickupItemEvent) {
        e.isCancelled = true
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onEntityDamageByEntity(e: EntityDamageByEntityEvent) {
        if (!started) {
            return
        }

        if (e.entity is Player) {
            if (e.damager is Arrow) {
                e.damage = 1000.0
            }

            if (respawn[e.entity.uniqueId] == true) { e.isCancelled = true }

            if (e.damager is Player) {
                if (respawn[e.damager.uniqueId] == true) { respawn[e.damager.uniqueId] = false }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onDeath(e : PlayerDeathEvent) {
        if (e.entity is Player) {
            if (e.entity.killer is Player) {
                val i = kills[e.entity.killer.uniqueId] ?: 0
                kills[e.entity.killer.uniqueId] = i + 1

                var task : SimpleTask? = null
                task = SimpleTask(plugin, {
                    PlayerUtils.fullyHealPlayer(e.entity.killer)
                    if (!e.entity.killer.inventory.contains(Material.ARROW)) {
                        e.entity.killer.inventory.addItem(ItemBuilder(Material.ARROW).build())
                        e.entity.killer.sendMessage("${ChatColor.GREEN}Your arrow has respawned")
                    }
                    // Uh, I don't really know  the difference between task.stop() and Task.tryStopTask(task) yet, I'll ask it later.
                    Task.tryStopTask(task)
                }, 5L, 20L)
                task.start()

                if (TeamManager.getTeamManager().getPlayerTeam(e.entity.killer.uniqueId) != null && TeamManager.getTeamManager().getPlayerTeam(e.entity.uniqueId) != null) {
                    Bukkit.getServer().onlinePlayers.forEach { it.sendMessage("${TeamManager.getTeamManager().getPlayerTeam(e.entity.uniqueId)!!.teamColor ?: ChatColor.WHITE}${e.entity.name} ${ChatColor.RED}was killed by ${TeamManager.getTeamManager().getPlayerTeam(e.entity.killer.uniqueId)!!.teamColor ?: ChatColor.WHITE}${e.entity.killer.name}") }
                    VersionIndependentUtils.get().sendTitle(e.entity.killer, "", "[${ChatColor.RED}⚔${ChatColor.RESET}] ${TeamManager.getTeamManager().getPlayerTeam(e.entity.uniqueId)!!.teamColor ?: ChatColor.WHITE}${e.entity.name}", 0, 40, 0)
                }
            }
        }
    }
}