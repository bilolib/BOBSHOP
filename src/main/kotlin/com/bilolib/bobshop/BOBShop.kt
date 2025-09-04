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
import com.bilolib.bobshop.tabela.*
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.event.Listener

class BOBShop : JavaPlugin() {

    companion object {
        lateinit var instance: BOBShop
            private set
    }

    lateinit var economy: Economy

    override fun onEnable() {
        instance = this

        // Config ve dil yükle
        saveDefaultConfig()
        LangManager.load(this) // sadece bir kere yükle
        // eventleri register et
        // Vault / ekonomi kontrolü
        if (!setupEconomy()) {
            logger.severe("Vault or economy plugin not found, disabling plugin!")
            server.pluginManager.disablePlugin(this)
            return
        }

        // Veritabanı bağlantısı
        try {
            MarketDatabase.connect(dataFolder)
        } catch (e: Exception) {
            logger.severe("Database connection failed: ${e.message}")
            server.pluginManager.disablePlugin(this)
            return
        }

        // Marketleri yükle ve cache’e ekle
        MarketOlusturma.markets.clear()
        MarketOlusturma.markets.addAll(MarketDatabase.loadMarkets())

        // Otomatik yedekleme başlat
        MarketBackupManager.startAutoBackup(30) // 30 dakika aralık

        // Event kayıtları
        val plugin = this
        val register: (Listener) -> Unit = { server.pluginManager.registerEvents(it, plugin) }

        register(MarketOlusturma())
        register(TabelaEvent())
        register(MarketBuyEvent(economy))
        register(MarketSellEvent(economy))
        register(MarketEvent())
        register(MarketMenuEvent())
        register(AyarMenuEvent(plugin))
        register(MarketInventoryListener())
        register(AyarMenu(instance))
        register(ChangeItemMenu)
        register(DescriptionManager)
        register(PriceChangeManager)
        register(AdminMenu(plugin))
        register(AdminMenuEvent())
        register(AdminShop(economy))
        register(AdminMenuSlot19())
        register(ShopLimitManager)

        server.pluginManager.registerEvents(ShopPriceChangeListener, this)

        // Hologram kontrolü
        logger.info("Hologram Enabled: ${config.getBoolean("hologram", true)}")

        // Command
        getCommand("bobshop")?.setExecutor(BobShopCommand(this))

        logger.info("BOBShop successfully loaded!")


        val backupsEnabled = config.getBoolean("backups.enabled", false)
        val backupInterval = config.getLong("backups.interval", 30)

        if (backupsEnabled) {
            MarketBackupManager.startAutoBackup(backupInterval)
        } else {
            logger.info("BOBSHOP backup disable")
        }
    }

    override fun onDisable() {
        // Marketleri güvenli şekilde kaydet
        MarketOlusturma.markets.forEach { market ->
            try {
                MarketDatabase.saveOrUpdateMarket(market)
            } catch (e: Exception) {
                logger.severe("Market save error: ${e.message}")
            }
        }
        Bukkit.getScheduler().cancelTasks(this)
        MarketDatabase.saveMarketsSync()

        // Hologramları temizle
        ShopItemHolo.removeAll()

        // DB bağlantısını kapat
        MarketDatabase.disconnect()
    }

    private fun setupEconomy(): Boolean {
        val rsp = server.servicesManager.getRegistration(Economy::class.java) ?: return false
        economy = rsp.provider
        return true
    }
}
