package com.bilolib.bobshop.menu

import com.bilolib.bobshop.manager.LangManager
import com.bilolib.bobshop.market.Market
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

import org.bukkit.ChatColor

class MarketMenu {
    companion object {
        fun open(player: Player, market: Market) {
            val title = ChatColor.translateAlternateColorCodes(
                '&',
                LangManager.getMessage("market-menu-title").replace("{owner}", player.name)
            )

            val inv: Inventory = Bukkit.createInventory(null, 27, title)

            val glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ")
            for (i in 0 until inv.size) inv.setItem(i, glass)

            inv.setItem(26, createItem(Material.BARRIER, LangManager.getMessage("remove-market-button")))
            inv.setItem(10, createItem(Material.NAME_TAG, LangManager.getMessage("change-price-button")))
            inv.setItem(11, createItem(Material.BUNDLE, LangManager.getMessage("change-item-button")))

            val modeItem = ItemStack(Material.EMERALD)
            val modeMeta = modeItem.itemMeta!!

            val buttonName = ChatColor.translateAlternateColorCodes('&', LangManager.getMessage("change-mode-button"))
            val BUY_KEY = LangManager.getMessage("market-modes.buy").uppercase()
            val SELL_KEY = LangManager.getMessage("market-modes.sell").uppercase()

            val currentMode = when (market.mode.uppercase()) {
                BUY_KEY -> ChatColor.translateAlternateColorCodes('&', LangManager.getMessage("market-modes.buy"))
                SELL_KEY -> ChatColor.translateAlternateColorCodes('&', LangManager.getMessage("market-modes.sell"))
                else -> "&7Unknown"
            }

            modeMeta.setDisplayName(buttonName)
            modeMeta.lore = LangManager.getMessageList("change-mode-lore").map { line ->
                ChatColor.translateAlternateColorCodes('&', line.replace("{mode}", currentMode))
            }
            modeItem.itemMeta = modeMeta
            inv.setItem(12, modeItem)

            val descItem = ItemStack(Material.PAINTING)
            val descMeta = descItem.itemMeta!!
            descMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', LangManager.getMessage("change-description-button")))
            descMeta.lore = LangManager.getMessageList("change-description-button-lore").map { line ->
                ChatColor.translateAlternateColorCodes('&', line)
            }
            descItem.itemMeta = descMeta
            inv.setItem(13, descItem)

            player.openInventory(inv)
        }

        private fun createItem(material: Material, name: String): ItemStack {
            val item = ItemStack(material)
            val meta = item.itemMeta
            meta?.setDisplayName(ChatColor.translateAlternateColorCodes('&', name))
            item.itemMeta = meta
            return item
        }
    }
}