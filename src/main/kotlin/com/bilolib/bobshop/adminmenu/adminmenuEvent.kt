package com.bilolib.bobshop.adminmenu

import com.bilolib.bobshop.ShopItemHolo
import com.bilolib.bobshop.manager.LangManager
import com.bilolib.bobshop.market.Market
import com.bilolib.bobshop.market.MarketDatabase
import com.bilolib.bobshop.market.MarketOlusturma
import com.bilolib.bobshop.menu.marketmenueventlist.ChangeItemMenu
import com.bilolib.bobshop.menu.marketmenueventlist.DescriptionManager
import com.bilolib.bobshop.menu.marketmenueventlist.PriceChangeManager
import com.bilolib.bobshop.tabela.TabelaUpdate
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.block.Chest
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import java.util.*

class AdminMenuEvent : Listener {

    companion object {
        // PUBLIC olmalı ki diğer sınıflar erişebilsin
        val openAdminMenus: MutableMap<UUID, Market> = mutableMapOf()
    }

    private val waitingForItemSelection = mutableMapOf<UUID, Market>()

    @EventHandler
    fun onInventoryClick(e: InventoryClickEvent) {
        val player = e.whoClicked as? Player ?: return
        val market = openAdminMenus[player.uniqueId] ?: return

        val expectedTitle = ChatColor.translateAlternateColorCodes(
            '&',
            LangManager.getMessage("admin-menu-title").replace("{owner}", player.name)
        )
        if (e.view.title != expectedTitle) return

        e.isCancelled = true
        val clickedItem = e.currentItem ?: return

        when (clickedItem.type) {
            Material.BARRIER -> {
                MarketDatabase.removeMarket(market)
                MarketOlusturma.markets.removeIf { it.chestLocation == market.chestLocation }
                ShopItemHolo.removeHologram(market.chestLocation)
                player.sendMessage(LangManager.getMessage("market-removed"))
                player.closeInventory()
            }
            Material.NAME_TAG -> {
                openAdminMenus.remove(player.uniqueId)
                PriceChangeManager.requestPriceChange(player, market)
            }
            Material.PAINTING -> {
                openAdminMenus.remove(player.uniqueId)
                DescriptionManager.requestDescription(player.uniqueId, market)
            }
            Material.EMERALD -> {
                openAdminMenus.remove(player.uniqueId)
                toggleMarketMode(player, market)
            }
            Material.BUNDLE -> {
                waitingForItemSelection[player.uniqueId] = market
                openAdminMenus.remove(player.uniqueId)
                player.closeInventory()
                ChangeItemMenu.openPlayerInventoryMenu(player, market)
            }
            else -> return
        }
    }

    private fun toggleMarketMode(player: Player, market: Market) {
        val buyMode = LangManager.getMessage("market-modes.buy")
        val sellMode = LangManager.getMessage("market-modes.sell")

        val newMode = if (market.mode.equals(buyMode, ignoreCase = true)) sellMode else buyMode
        val updatedMarket = market.copy(mode = newMode)

        MarketOlusturma.markets.remove(market)
        MarketOlusturma.markets.add(updatedMarket)
        MarketDatabase.saveOrUpdateMarket(updatedMarket)

        val chest = updatedMarket.chestLocation.block.state as? Chest
        if (chest != null) {
            TabelaUpdate.updateSign(chest, updatedMarket.itemStack, updatedMarket.price)
        }

        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
            LangManager.getMessage("market-mode-changed").replace("{mode}", newMode)
        ))
        player.closeInventory()
    }
}
