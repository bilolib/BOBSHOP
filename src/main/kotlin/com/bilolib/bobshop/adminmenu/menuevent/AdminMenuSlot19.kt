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

        // 1) Sadece üst envanter tıklamalarını kabul et
        if (event.clickedInventory != event.view.topInventory) return

        // 2) Başlık eşleşmesini renksiz karşılaştır
        val expectedRaw = LangManager.getMessage("admin-menu-title").replace("{owner}", player.name)
        val expectedTitle = ChatColor.translateAlternateColorCodes('&', expectedRaw)
        val viewStripped = ChatColor.stripColor(event.view.title) ?: event.view.title
        val expectedStripped = ChatColor.stripColor(expectedTitle) ?: expectedTitle
        if (viewStripped != expectedStripped) return

        event.isCancelled = true

        when (event.slot) {
            19 -> toggleAdminShop(event, player, market) // AdminShop toggle
        }
    }

    private fun toggleAdminShop(event: InventoryClickEvent, player: Player, market: Market) {
        market.adminShop = !market.adminShop

        val status = if (market.adminShop)
            LangManager.getMessage("admin-shop.enabled")
        else
            LangManager.getMessage("admin-shop.disabled")

        // Butonu güncelle (3. adım: lore null-safe)
        val button = ItemStack(Material.DIAMOND)
        val meta = button.itemMeta
        if (meta != null) {
            meta.setDisplayName(LangManager.getMessage("admin-shop.name"))
            val loreLines = (LangManager.getMessageList("admin-shop.lore") ?: emptyList())
                .map { ChatColor.translateAlternateColorCodes('&', it.replace("{Status}", status)) }
            meta.lore = loreLines
            button.itemMeta = meta
        }
        event.inventory.setItem(19, button)

        // Oyuncuya mesaj
        player.sendMessage(
            ChatColor.translateAlternateColorCodes(
                '&',
                LangManager.getMessage("adminshop-toggle-message").replace("{Status}", status)
            )
        )

        // Tabela güncelle
        val chest = market.chestLocation.block.state as? Chest
        if (chest != null) {
            val ownerText = if (market.adminShop) LangManager.getMessage("server-name") else null
            val amountText = if (market.adminShop) LangManager.getMessage("unlimited") else null

            TabelaUpdate.updateSign(
                chest,
                market.itemStack.clone(),
                market.price,
                ownerName = ownerText,
                amountText = amountText
            )
        }

        // Veritabanına kaydet
        MarketDatabase.saveOrUpdateMarket(market)
    }
}
