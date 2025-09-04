package com.bilolib.bobshop.menu.marketmenueventlist

import com.bilolib.bobshop.BOBShop
import com.bilolib.bobshop.ShopItemHolo
import com.bilolib.bobshop.manager.LangManager
import com.bilolib.bobshop.market.Market
import com.bilolib.bobshop.market.MarketDatabase
import com.bilolib.bobshop.market.MarketOlusturma
import com.bilolib.bobshop.tabela.TabelaUpdate
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.block.Chest
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import java.util.UUID

object ChangeItemMenu : Listener {

    val waitingForItemSelection = mutableMapOf<UUID, Market>()

    // Oyuncunun envanter menüsünü aç
    fun openPlayerInventoryMenu(player: Player, market: Market) {
        val title = ChatColor.translateAlternateColorCodes('&', LangManager.getMessage("select-item-inventory-title"))
        val inv: Inventory = Bukkit.createInventory(player, 9, title)

        // Hotbar (slot bar) ekle
        for (i in 0..8) {
            val hotbarItem = player.inventory.getItem(i)
            if (hotbarItem != null && hotbarItem.type != Material.AIR) {
                inv.addItem(hotbarItem.clone())
            }
        }

        player.openInventory(inv)
        waitingForItemSelection[player.uniqueId] = market
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
            LangManager.getMessage("select-item-in-inventory")
        ))
    }

    @EventHandler
    fun onInventoryClick(e: InventoryClickEvent) {
        val player = e.whoClicked as? Player ?: return
        val title = e.view.title
        val menuTitle = ChatColor.translateAlternateColorCodes('&', LangManager.getMessage("select-item-inventory-title"))
        if (title != menuTitle) return

        e.isCancelled = true
        val clickedItem = e.currentItem ?: return
        if (clickedItem.type.isAir) return

        val market = waitingForItemSelection.remove(player.uniqueId) ?: return

        // Mevcut marketin itemini değiştiriyoruz
        val updatedMarket = market.copy(itemStack = clickedItem.clone())

        // Sadece RAM'deki marketin itemini güncelliyoruz, yeni market eklemiyoruz
        val index = MarketOlusturma.markets.indexOfFirst { it.chestLocation == market.chestLocation }
        if (index != -1) {
            // Mevcut marketi RAM'de güncelle
            MarketOlusturma.markets[index] = updatedMarket
        }

        // Veritabanındaki marketi güncelle
        MarketDatabase.saveOrUpdateMarket(updatedMarket)

        // Hologram ve tabela güncelleme
        ShopItemHolo.spawnOrUpdateHologram(
            BOBShop.instance,
            updatedMarket.chestLocation,
            updatedMarket.itemStack,
            updatedMarket.description
        )
        TabelaUpdate.updateSign(
            updatedMarket.chestLocation.block.state as? Chest ?: return,
            updatedMarket.itemStack,
            updatedMarket.price
        )

        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
            LangManager.getMessage("market-item-updated")
        ))
        player.closeInventory()
    }
}
