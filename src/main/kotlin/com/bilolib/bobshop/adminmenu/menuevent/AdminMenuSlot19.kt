package com.bilolib.bobshop.adminmenu.menuevent

import com.bilolib.bobshop.adminmenu.AdminMenuEvent
import com.bilolib.bobshop.manager.LangManager
import com.bilolib.bobshop.market.Market
import com.bilolib.bobshop.market.MarketDatabase
import com.bilolib.bobshop.tabela.TabelaUpdate
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.block.Chest
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack

class AdminMenuSlot19 : Listener {

    @EventHandler
    fun onAdminMenuClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val market = AdminMenuEvent.openAdminMenus[player.uniqueId] ?: return

        val expectedTitle = ChatColor.translateAlternateColorCodes(
            '&', LangManager.getMessage("admin-menu-title").replace("{owner}", player.name)
        )
        if (event.view.title != expectedTitle) return

        event.isCancelled = true

        when (event.slot) {
            19 -> toggleAdminShop(event, player, market) // AdminShop toggle
        }
    }

    private fun toggleAdminShop(event: InventoryClickEvent, player: Player, market: Market) {
        market.adminShop = !market.adminShop
        val status = if (market.adminShop) LangManager.getMessage("admin-shop.enabled")
        else LangManager.getMessage("admin-shop.disabled")

        // Butonu güncelle
        val button = ItemStack(Material.DIAMOND)
        val meta = button.itemMeta!!
        meta.setDisplayName(LangManager.getMessage("admin-shop.name"))
        meta.lore = LangManager.getMessageList("admin-shop.lore").map {
            ChatColor.translateAlternateColorCodes('&', it.replace("{Status}", status))
        }
        button.itemMeta = meta
        event.inventory.setItem(19, button)

        // Oyuncuya mesaj
        player.sendMessage(ChatColor.translateAlternateColorCodes(
            '&', LangManager.getMessage("adminshop-toggle-message").replace("{Status}", status)
        ))

        // Tabelayı güncelle
        val chest = market.chestLocation.block.state as? Chest
        if (chest != null) {
            TabelaUpdate.updateSign(
                chest,
                market.itemStack.clone(),
                market.price,
                ownerName = if (market.adminShop) "Server" else null,
                amountText = if (market.adminShop) LangManager.getMessage("unlimited") else null
            )
        }

        // Veritabanına kaydet
        MarketDatabase.saveOrUpdateMarket(market)
    }

}
