// AyarMenuHelper.kt
package com.bilolib.bobshop.ayarmenu.more

import com.bilolib.bobshop.BOBShop
import com.bilolib.bobshop.shopholo.ShopItemHolo
import com.bilolib.bobshop.market.MarketOlusturma
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

object AyarMenuHelper {

    fun buildHologramToggleItem(plugin: BOBShop): ItemStack {
        val enabled = plugin.config.getBoolean("hologram", true)
        val item = ItemStack(if (enabled) Material.LIME_DYE else Material.GRAY_DYE)
        val meta = item.itemMeta!!
        meta.setDisplayName(if (enabled) "§aHologram On" else "§cHologram Off")
        item.itemMeta = meta
        return item
    }

    fun toggleHologram(plugin: BOBShop, enabled: Boolean) {
        plugin.config.set("hologram", enabled)
        plugin.saveConfig()

        // Tüm mevcut hologramları kaldır
        for (market in MarketOlusturma.markets) {
            ShopItemHolo.removeHologram(market.chestLocation)
        }

        // Eğer açmak isteniyorsa tekrar oluştur
        if (enabled) {
            for (market in MarketOlusturma.markets) {
                ShopItemHolo.spawnOrUpdateHologram(
                    plugin,
                    market.chestLocation,
                    market.itemStack,
                    market.description
                )
            }
        }
    }

    fun buildDescriptionToggleItem(plugin: BOBShop): ItemStack {
        val enabled = plugin.config.getBoolean("holo-description.enabled", true)
        val item = ItemStack(if (enabled) Material.LIME_DYE else Material.GRAY_DYE)
        val meta = item.itemMeta!!
        meta.setDisplayName(if (enabled) "§aDescription On" else "§cDescription Off")
        item.itemMeta = meta
        return item
    }
}

