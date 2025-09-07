package com.bilolib.bobshop

import com.bilolib.bobshop.adminmenu.AdminMenu
import com.bilolib.bobshop.adminmenu.AdminMenuEvent
import com.bilolib.bobshop.adminmenu.menuevent.AdminMenuSlot19
import com.bilolib.bobshop.adminshop.AdminShop
import com.bilolib.bobshop.manager.LangManager
import com.bilolib.bobshop.market.*
import com.bilolib.bobshop.menu.MarketMenuEvent
import com.bilolib.bobshop.ayarmenu.AyarMenu
import com.bilolib.bobshop.ayarmenu.AyarMenuEvent
import com.bilolib.bobshop.ayarmenu.more.ShopLimitManager
import com.bilolib.bobshop.ayarmenu.more.ShopPriceChangeListener
import com.bilolib.bobshop.menu.marketmenueventlist.ChangeItemMenu
import com.bilolib.bobshop.menu.marketmenueventlist.DescriptionManager
import com.bilolib.bobshop.menu.marketmenueventlist.PriceChangeManager
import com.bilolib.bobshop.shopholo.ShopItemHolo
import com.bilolib.bobshop.tabela.*
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin

class BOBShop : JavaPlugin() {

    companion object {
        lateinit var instance: BOBShop
            private set
    }

    lateinit var economy: Economy
        private set

    override fun onEnable() {
        instance = this

        // 1) Config + Dil
        saveDefaultConfig()
        LangManager.load(this)

        // 2) Vault
        if (!setupEconomy()) {
            logger.severe("Vault or economy plugin not found, disabling plugin!")
            server.pluginManager.disablePlugin(this)
            return
        }

        // 3) DB Bağlantısı (Hikari + SQLite)
        try {
            MarketDatabase.connect(dataFolder)
        } catch (e: Exception) {
            logger.severe("Database connection failed: ${e.message}")
            server.pluginManager.disablePlugin(this)
            return
        }
        // 4) Marketleri yükle (liste + hologramlar içeride güvenli şekilde kuruluyor)
        MarketOlusturma.markets.clear()
        try {
            MarketOlusturma.markets.addAll(MarketDatabase.loadMarkets())
        } catch (e: Exception) {
            logger.severe("Markets load failed: ${e.message}")
        }

         // 5) Yedekleme (sadece config'e göre)
        val backupsEnabled = config.getBoolean("backups.enabled", true)
        val backupInterval = config.getLong("backups.interval", 30L)
        val backupsDir = java.io.File(dataFolder, "backups")
        if (!backupsDir.exists()) backupsDir.mkdirs()
        if (backupsEnabled) {
            MarketBackupManager.startAutoBackup(this, backupInterval) // <--- plugin'i ver
            logger.info("BOBShop backups enabled (every $backupInterval min).")
        } else {
            logger.info("BOBShop backups disabled.")
        }

        // 6) Event kayıtları
        val plugin = this
        fun reg(l: Listener) = server.pluginManager.registerEvents(l, plugin)

        reg(MarketOlusturma())
        reg(TabelaEvent())
        reg(MarketBuyEvent(economy))
        reg(MarketSellEvent(economy))
        reg(MarketEvent())
        reg(MarketMenuEvent())
        reg(AyarMenuEvent(plugin))
        reg(MarketInventoryListener())
        reg(AyarMenu(instance))
        reg(ChangeItemMenu)
        reg(DescriptionManager)
        reg(PriceChangeManager)
        reg(AdminMenu(plugin))
        reg(AdminMenuEvent())
        reg(AdminShop(economy))
        reg(AdminMenuSlot19())
        reg(ShopLimitManager)
        reg(ShopPriceChangeListener)
        reg(com.bilolib.bobshop.shopholo.HologramCleanup)
        reg(com.bilolib.bobshop.shopholo.HologramRespawn)

        // 7) Hologram info
        logger.info("Hologram Enabled: ${config.getBoolean("hologram", true)}")

        // 8) Komut
        val cmd = getCommand("bobshop")
        if (cmd == null) {
            logger.severe("Command 'bobshop' not found in plugin.yml")
        } else {
            cmd.setExecutor(BobShopCommand(this))
        }

        logger.info("BOBShop successfully loaded!")


        //holo cunk işlemi
        var delay = 1L
        server.worlds.forEach { world ->
            world.loadedChunks.forEach { chunk ->
                val targets = MarketOlusturma.markets.filter { m ->
                    val loc = m.chestLocation
                    loc.world == world &&
                            (loc.blockX shr 4) == chunk.x &&
                            (loc.blockZ shr 4) == chunk.z
                }
                if (targets.isEmpty()) return@forEach

                server.scheduler.runTaskLater(this, Runnable {
                    targets.forEach { m ->
                        ShopItemHolo.removeHologram(m.chestLocation)
                        ShopItemHolo.spawnOrUpdateHologram(this, m.chestLocation, m.itemStack, m.description)
                    }
                }, delay)
                delay += 1L
            }
        }

    }

    override fun onDisable() {
        Bukkit.getScheduler().cancelTasks(this)

        try {
            MarketDatabase.saveAllSync(MarketOlusturma.markets)
        } catch (e: Exception) {
            logger.warning("Sync save failed: ${e.message}")
        }

        try { ShopItemHolo.removeAll() } catch (_: Exception) {}
        try { MarketDatabase.disconnect() } catch (_: Exception) {}
    }


    private fun setupEconomy(): Boolean {
        val rsp = server.servicesManager.getRegistration(Economy::class.java) ?: return false
        economy = rsp.provider
        return true
    }
}
