package com.bilolib.bobshop.menu

import com.bilolib.bobshop.BOBShop
import com.bilolib.bobshop.ShopItemHolo
import com.bilolib.bobshop.manager.LangManager
import com.bilolib.bobshop.market.Market
import com.bilolib.bobshop.market.MarketDatabase
import com.bilolib.bobshop.market.MarketOlusturma
import com.bilolib.bobshop.menu.marketmenueventlist.DescriptionManager
import com.bilolib.bobshop.menu.marketmenueventlist.ChangeItemMenu
import com.bilolib.bobshop.menu.marketmenueventlist.PriceChangeManager
import com.bilolib.bobshop.tabela.TabelaUpdate
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.block.Chest
import org.bukkit.block.Sign
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerInteractEvent
import java.util.UUID

class MarketMenuEvent : Listener {

    private val openMenus = mutableMapOf<UUID, Market>() // hangi oyuncunun hangi market menüsü açık
    private val waitingForItemSelection = mutableMapOf<UUID, Market>() // Change Item için

    @EventHandler
    fun onInventoryClick(e: InventoryClickEvent) {
        val player = e.whoClicked as? Player ?: return
        val title = e.view.title
        val menuTitle = ChatColor.translateAlternateColorCodes(
            '&',
            LangManager.getMessage("market-menu-title").replace("{owner}", player.name)
        )
        if (!title.startsWith(menuTitle)) return

        e.isCancelled = true
        val clickedItem = e.currentItem ?: return
        val market = openMenus[player.uniqueId] ?: return

        when (clickedItem.type) {
            Material.BARRIER -> {
                MarketDatabase.removeMarket(market)
                MarketOlusturma.markets.removeIf { it.chestLocation == market.chestLocation }
                ShopItemHolo.removeHologram(market.chestLocation)
                player.sendMessage(LangManager.getMessage("market-removed"))
                player.closeInventory()
            }
            Material.NAME_TAG -> PriceChangeManager.requestPriceChange(player, market)
            Material.PAINTING -> {
                e.isCancelled = true

                val plugin = BOBShop.instance // <- plugin referansı

                val descriptionEnabled = plugin.config.getBoolean("holo-description.enabled", true)

                if (!descriptionEnabled) {
                    // Açıklama kapalı, mesaj göster
                    player.sendMessage(
                        ChatColor.translateAlternateColorCodes('&', LangManager.getMessage("description-disabled-msg"))
                    )
                    return
                }

                // Açıklama açık, requestDescription çağrılır
                DescriptionManager.requestDescription(player.uniqueId, market)
            }
            Material.EMERALD -> toggleMarketMode(player, market)
            Material.BUNDLE -> {
                waitingForItemSelection[player.uniqueId] = market
                player.closeInventory()
                ChangeItemMenu.openPlayerInventoryMenu(player, market)
            }
            else -> return
        }
    }



    private fun toggleMarketMode(player: Player, market: Market) {
        val buyMode = LangManager.getMessage("market-modes.buy")  // örn: "BUY"
        val sellMode = LangManager.getMessage("market-modes.sell") // örn: "SELL"

        val newMode = if (market.mode.equals(buyMode, ignoreCase = true)) sellMode else buyMode
        val updatedMarket = market.copy(mode = newMode)

        MarketOlusturma.markets.remove(market)
        MarketOlusturma.markets.add(updatedMarket)
        MarketDatabase.saveOrUpdateMarket(updatedMarket)

        MarketMenu.open(player, market)// Menü güncelle

        val chest = updatedMarket.chestLocation.block.state as? Chest
        if (chest != null) {
            TabelaUpdate.updateSign(chest, updatedMarket.itemStack, updatedMarket.price)
        }

        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
            LangManager.getMessage("market-mode-changed").replace("{mode}", newMode)
        ))
        player.closeInventory()
    }

    @EventHandler
    fun onSignRightClick(e: PlayerInteractEvent) {
        if (e.action != Action.RIGHT_CLICK_BLOCK) return
        val block = e.clickedBlock ?: return
        if (block.state !is Sign) return

        // shift basılıysa açma
        if (e.player.isSneaking) return

        val market = MarketOlusturma.markets.find { it.signLocation.block == block } ?: return

        if (market.adminShop) return
        if (e.player.uniqueId != market.owner) return

        e.isCancelled = true
        MarketMenu.open(e.player, market)
        openMenus[e.player.uniqueId] = market
    }
}
