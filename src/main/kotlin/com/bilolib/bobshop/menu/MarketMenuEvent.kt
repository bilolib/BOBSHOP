package com.bilolib.bobshop.menu

import com.bilolib.bobshop.BOBShop
import com.bilolib.bobshop.shopholo.ShopItemHolo
import com.bilolib.bobshop.manager.LangManager
import com.bilolib.bobshop.market.Market
import com.bilolib.bobshop.market.MarketDatabase
import com.bilolib.bobshop.market.MarketMode
import com.bilolib.bobshop.market.MarketOlusturma
import com.bilolib.bobshop.menu.marketmenueventlist.DescriptionManager
import com.bilolib.bobshop.menu.marketmenueventlist.ChangeItemMenu
import com.bilolib.bobshop.menu.marketmenueventlist.PriceChangeManager
import com.bilolib.bobshop.tabela.TabelaUpdate
import com.bilolib.bobshop.util.displayMode
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.block.Sign
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerInteractEvent
import java.util.UUID

class MarketMenuEvent : Listener {

    private val openMenus = mutableMapOf<java.util.UUID, Market>()
    private val waitingForItemSelection = mutableMapOf<UUID, Market>() // Change Item için

    @EventHandler
    fun onInventoryClick(e: InventoryClickEvent) {
        val player = e.whoClicked as? Player ?: return

        // Başlık eşleşmesi
        val expected = ChatColor.stripColor(
            LangManager.getMessage("market-menu-title").replace("{owner}", player.name)
        )
        val currentTitle = ChatColor.stripColor(e.view.title)
        if (currentTitle != expected) return

        // Sadece ÜST envanter slotlarını işle
        val top = e.view.topInventory
        if (e.clickedInventory != top) {
            // alt envanter tıkları ilgisiz
            return
        }

        // Sorunlu click tiplerini blokla
        when (e.click) {
            org.bukkit.event.inventory.ClickType.DOUBLE_CLICK,
            org.bukkit.event.inventory.ClickType.SHIFT_LEFT,
            org.bukkit.event.inventory.ClickType.SHIFT_RIGHT,
            org.bukkit.event.inventory.ClickType.NUMBER_KEY,
            org.bukkit.event.inventory.ClickType.SWAP_OFFHAND -> {
                e.isCancelled = true
                return
            }
            else -> { /* devam */ }
        }

        e.isCancelled = true

        val cached = openMenus[player.uniqueId] ?: return
        val market = MarketOlusturma.markets.find { it.chestLocation == cached.chestLocation } ?: cached

        val slot = e.rawSlot // ÜST envanter slot index’i
        when (slot) {
            26 -> { // Kaldır
                openMenus.remove(player.uniqueId)
                MarketDatabase.removeMarket(market)
                MarketOlusturma.markets.removeIf { it.chestLocation == market.chestLocation }
                ShopItemHolo.removeHologram(market.chestLocation)
                player.sendMessage(LangManager.getMessage("market-removed"))
                player.closeInventory()
            }
            10 -> { // Fiyat
                openMenus.remove(player.uniqueId)
                PriceChangeManager.requestPriceChange(player, market)
            }
            11 -> { // Item değiştir
                waitingForItemSelection[player.uniqueId] = market
                openMenus.remove(player.uniqueId)
                player.closeInventory()
                ChangeItemMenu.openPlayerInventoryMenu(player, market)
            }
            12 -> { // MOD BUTONU — slot bazlı!
                toggleMarketMode(player, market)
            }
            13 -> { // Açıklama
                val plugin = BOBShop.instance
                val descriptionEnabled = plugin.config.getBoolean("holo-description.enabled", true)
                if (!descriptionEnabled) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes(
                        '&', LangManager.getMessage("description-disabled-msg")
                    ))
                    return
                }
                openMenus.remove(player.uniqueId)
                DescriptionManager.requestDescription(player.uniqueId, market)
            }
            else -> return
        }
    }



    private fun toggleMarketMode(player: Player, market: Market) {
        // Main thread değilse ana threade dön
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(BOBShop.instance, Runnable { toggleMarketMode(player, market) })
            return
        }

        // 1) Mode'u çevir (enum)
        market.mode = if (market.mode == MarketMode.BUY) MarketMode.SELL else MarketMode.BUY

        // 2) RAM + DB (MarketDatabase cache’ini de günceller)
        MarketDatabase.saveOrUpdateMarket(market)

        // 3) Tabela/Hologram güncelle
        (market.chestLocation.block.state as? org.bukkit.block.Chest)?.let { chest ->
            TabelaUpdate.updateSign(chest, market.itemStack, market.price)
        }
        ShopItemHolo.spawnOrUpdateHologram(
            BOBShop.instance, market.chestLocation, market.itemStack, market.description
        )

        // 4) Oyuncuya mesaj
        player.sendMessage(
            LangManager.getMessage("market-mode-changed")
                .replace("{mode}", displayMode(market.mode)) // displayMode(MarketMode)
        )
        player.closeInventory()
    }


    @EventHandler
    fun onSignRightClick(e: PlayerInteractEvent) {
        if (e.action != Action.RIGHT_CLICK_BLOCK) return
        val block = e.clickedBlock ?: return
        val sign = block.state as? Sign ?: return
        if (e.player.isSneaking) return

        val market = MarketOlusturma.markets.find { it.signLocation.block == sign.block } ?: return
        if (market.adminShop) return
        if (e.player.uniqueId != market.owner) return

        e.isCancelled = true
        // Menüyü aç ve kaydet
        MarketMenu.open(e.player, market)
        openMenus[e.player.uniqueId] = market
    }
}
