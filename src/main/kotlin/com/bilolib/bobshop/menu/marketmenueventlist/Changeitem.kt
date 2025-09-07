package com.bilolib.bobshop.menu.marketmenueventlist

import com.bilolib.bobshop.BOBShop
import com.bilolib.bobshop.shopholo.ShopItemHolo
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
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import java.util.UUID

object ChangeItemMenu : Listener, InventoryHolder {

    private val waitingForItemSelection = mutableMapOf<UUID, Market>()
    private val openMenus = mutableMapOf<UUID, Inventory>()

    override fun getInventory(): Inventory {
        // Sadece kimlik; gerçek menü openPlayerInventoryMenu'de oluşturuluyor.
        return Bukkit.createInventory(null, 36)
    }

    fun openPlayerInventoryMenu(player: Player, market: Market) {
        // İstersen bu kontrolü kaldırabilirsin
        if (player.uniqueId != market.owner) {
            player.sendMessage(LangManager.getMessage("no-permission"))
            return
        }

        val title = ChatColor.translateAlternateColorCodes(
            '&',
            LangManager.getMessage("select-item-inventory-title")
        )

        // 36 slotluk üst menü – oyuncunun ana envanterinin (0..35) birebir kopyası
        val inv: Inventory = Bukkit.createInventory(this, 36, title)

        val contents = player.inventory.contents
        // 0..35: main + hotbar (zırh/offhand dahil değil)
        for (slot in 0..35) {
            val it = contents.getOrNull(slot)
            if (it != null && it.type != Material.AIR) {
                inv.setItem(slot, it.clone())
            }
        }

        waitingForItemSelection[player.uniqueId] = market
        openMenus[player.uniqueId] = inv
        player.openInventory(inv)

        player.sendMessage(
            ChatColor.translateAlternateColorCodes('&',
                LangManager.getMessage("select-item-in-inventory")
            )
        )
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onInventoryClick(e: InventoryClickEvent) {
        val player = e.whoClicked as? Player ?: return
        val menu = openMenus[player.uniqueId] ?: return
        if (e.view.topInventory != menu) return  // sadece bizim 36’lık menü

        // Üst envantere tıklanmadıysa görmezden gel
        if (e.clickedInventory != e.view.topInventory) {
            e.isCancelled = true
            return
        }

        // Item taşımayı engelle (sadece seçim)
        if (e.click in setOf(
                ClickType.SHIFT_LEFT,
                ClickType.SHIFT_RIGHT,
                ClickType.NUMBER_KEY,
                ClickType.SWAP_OFFHAND,
                ClickType.DOUBLE_CLICK
            )
        ) {
            e.isCancelled = true
            return
        }

        val clicked = e.currentItem
        e.isCancelled = true
        if (clicked == null || clicked.type.isAir) return

        val market = waitingForItemSelection.remove(player.uniqueId) ?: return
        openMenus.remove(player.uniqueId)

        // Şablon: miktarı 1 olsun
        val newTemplate = clicked.clone().also { it.amount = 1 }
        val updatedMarket = market.copy(itemStack = newTemplate)

        // RAM listesini güncelle
        val idx = MarketOlusturma.markets.indexOfFirst { it.chestLocation == market.chestLocation }
        if (idx != -1) MarketOlusturma.markets[idx] = updatedMarket

        // DB (async)
        MarketDatabase.saveOrUpdateMarket(updatedMarket)

        // Hologram & tabela
        ShopItemHolo.spawnOrUpdateHologram(
            BOBShop.instance,
            updatedMarket.chestLocation,
            updatedMarket.itemStack,
            updatedMarket.description
        )

        (updatedMarket.chestLocation.block.state as? Chest)?.let { chest ->
            TabelaUpdate.updateSign(chest, updatedMarket.itemStack, updatedMarket.price)
        }

        player.sendMessage(
            ChatColor.translateAlternateColorCodes(
                '&',
                LangManager.getMessage("market-item-updated")
            )
        )
        player.closeInventory()
    }

    @EventHandler
    fun onInventoryClose(e: InventoryCloseEvent) {
        val player = e.player as? Player ?: return
        val menu = openMenus[player.uniqueId] ?: return
        if (e.view.topInventory != menu) return

        openMenus.remove(player.uniqueId)
        waitingForItemSelection.remove(player.uniqueId)
    }

    @EventHandler
    fun onQuit(e: PlayerQuitEvent) {
        openMenus.remove(e.player.uniqueId)
        waitingForItemSelection.remove(e.player.uniqueId)
    }
}
