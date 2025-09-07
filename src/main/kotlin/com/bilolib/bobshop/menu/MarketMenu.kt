package com.bilolib.bobshop.menu

import com.bilolib.bobshop.manager.LangManager
import com.bilolib.bobshop.market.Market
import com.bilolib.bobshop.market.MarketMode
import com.bilolib.bobshop.util.displayMode
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.Locale

object MarketMenu {

    fun open(player: Player, market: Market) {
        val title = color(
            LangManager.getMessage("market-menu-title").replace("{owner}", player.name)
        )
        val inv: Inventory = Bukkit.createInventory(null, 27, title)

        // Arka plan
        val glass = named(Material.GRAY_STAINED_GLASS_PANE, " ")
        for (i in 0 until inv.size) inv.setItem(i, glass)

        // Aksiyon butonları
        inv.setItem(26, named(Material.BARRIER,   LangManager.getMessage("remove-market-button")))
        inv.setItem(10, named(Material.NAME_TAG,  LangManager.getMessage("change-price-button")))
        inv.setItem(11, named(Material.BUNDLE,    LangManager.getMessage("change-item-button")))

// Mod butonu (slot 12)
        val modeMaterial = if (market.mode == MarketMode.BUY) Material.EMERALD else Material.REDSTONE
        val modeItem = ItemStack(modeMaterial).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName(color(LangManager.getMessage("change-mode-button")))
                val currentModeText = displayMode(market.mode) // oyuncuya görünen metin
                lore = LangManager.getMessageList("change-mode-lore").map {
                    color(it.replace("{mode}", currentModeText))
                }
            }
        }
        inv.setItem(12, modeItem)

        // Açıklama (slot 13)
        val descItem = ItemStack(Material.PAINTING).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName(color(LangManager.getMessage("change-description-button")))
                lore = LangManager.getMessageList("change-description-button-lore").map { color(it) }
            }
        }
        inv.setItem(13, descItem)

        player.openInventory(inv)
    }

    /* -------- utils -------- */

    private fun named(mat: Material, name: String): ItemStack =
        ItemStack(mat).apply {
            itemMeta = itemMeta?.apply { setDisplayName(color(name)) }
        }

    private fun color(s: String) = ChatColor.translateAlternateColorCodes('&', s)

    private fun canonicalMode(s: String?): String {
        val raw = ChatColor.stripColor(s ?: "")?.trim()?.uppercase(Locale.ENGLISH) ?: ""
        return if (raw == "BUY" || raw == "SELL") raw else "BUY"
    }

    private fun displayMode(mode: String?): String = when (canonicalMode(mode)) {
        "BUY"  -> LangManager.getMessage("market-modes.buy")
        "SELL" -> LangManager.getMessage("market-modes.sell")
        else   -> LangManager.getMessage("market-modes.buy")
    }
}
