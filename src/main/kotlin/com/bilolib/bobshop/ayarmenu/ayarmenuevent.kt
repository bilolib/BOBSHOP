package com.bilolib.bobshop.ayarmenu

import com.bilolib.bobshop.BOBShop
import com.bilolib.bobshop.shopholo.ShopItemHolo
import com.bilolib.bobshop.manager.LangManager
import com.bilolib.bobshop.ayarmenu.more.AyarMenuHelper
import com.bilolib.bobshop.ayarmenu.more.ShopLimitManager
import com.bilolib.bobshop.ayarmenu.more.ShopPriceChangeListener
import com.bilolib.bobshop.market.MarketOlusturma
import org.bukkit.ChatColor
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.Material
import org.bukkit.entity.Player

class AyarMenuEvent(private val plugin: BOBShop) : Listener {

    @EventHandler
    fun onInventoryClick(e: InventoryClickEvent) {
        val menuTitle = ChatColor.translateAlternateColorCodes('&', LangManager.getMessage("settings-menu-title"))
        if (e.view.title != menuTitle) return
        if (e.currentItem == null) return
        if (e.currentItem?.type == Material.AIR) return

        val player = e.whoClicked as? Player ?: return
        val slot = e.rawSlot
        e.isCancelled = true // Menüyü kırmamamak için

        when (slot) {
            10 -> {
                e.isCancelled = true
                player.closeInventory()
                ShopPriceChangeListener.requestShopPriceChange(player)
            }

            11 -> { // Hologram aç/kapat
                e.isCancelled = true

                val enabled = !plugin.config.getBoolean("hologram", true)
                AyarMenuHelper.toggleHologram(plugin, enabled)

                // Menüde item güncelle
                e.inventory.setItem(11, AyarMenuHelper.buildHologramToggleItem(plugin))
                player.updateInventory()
            }
            12 -> { // Description aç/kapat
                e.isCancelled = true

                // Config değerini tersine çevir
                val enabled = !plugin.config.getBoolean("holo-description.enabled", true)
                plugin.config.set("holo-description.enabled", enabled)
                plugin.saveConfig()

                // Hologram güncelle
                // Tüm marketler için uygulanacaksa MarketOlusturma.markets üzerinden dön
                MarketOlusturma.markets.forEach { market ->
                    ShopItemHolo.spawnOrUpdateHologram(
                        plugin,
                        market.chestLocation,
                        market.itemStack,
                        if (enabled) market.description else null
                    )
                }

                // Menü item güncelle (market parametresi kaldırıldı)
                e.inventory.setItem(12, AyarMenuHelper.buildDescriptionToggleItem(plugin))
                player.updateInventory()
            }

            13 -> {
                e.isCancelled = true // Tıklamayı iptal et
                player.closeInventory()
                ShopLimitManager.openLimitChangePrompt(player)
            }


        }
    }
}
