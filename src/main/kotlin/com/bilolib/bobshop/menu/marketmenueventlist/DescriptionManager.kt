package com.bilolib.bobshop.menu.marketmenueventlist

import com.bilolib.bobshop.BOBShop
import com.bilolib.bobshop.ShopItemHolo
import com.bilolib.bobshop.manager.LangManager
import com.bilolib.bobshop.market.Market
import com.bilolib.bobshop.market.MarketDatabase
import com.bilolib.bobshop.market.MarketOlusturma
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import java.util.UUID

object DescriptionManager : Listener {

    private val waitingForDescription = mutableMapOf<UUID, Market>()

    /**
     * Açıklama girişi isteyen oyuncuyu ekler.
     * Eğer zaten açıklama varsa menüden tekrar basıldığında kaldırır.
     */
    fun requestDescription(playerUUID: UUID, market: Market) {
        val player = BOBShop.instance.server.getPlayer(playerUUID) ?: return

        if (!market.description.isNullOrBlank()) {
            // Açıklama varsa kaldır
            val updatedMarket = market.copy(description = null)

            MarketOlusturma.markets.remove(market)
            MarketOlusturma.markets.add(updatedMarket)
            MarketDatabase.saveOrUpdateMarket(updatedMarket)

            ShopItemHolo.spawnOrUpdateHologram(
                BOBShop.instance,
                updatedMarket.chestLocation,
                updatedMarket.itemStack,
                updatedMarket.description
            )

            val removedMsg = LangManager.getMessage("market-description-removed")
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', removedMsg))
        } else {
            // Açıklama yoksa eklemek için chat bekle
            waitingForDescription[playerUUID] = market

            player.sendMessage(ChatColor.translateAlternateColorCodes(
                '&',
                LangManager.getMessage("enter-description")
            ))

            // 15 saniye sonra otomatik temizleme
            Bukkit.getScheduler().runTaskLater(BOBShop.instance, Runnable {
                waitingForDescription.remove(playerUUID)
            }, 15 * 20L)
        }
        player.closeInventory()
    }

    @EventHandler
    fun onChat(e: AsyncPlayerChatEvent) {
        val player = e.player
        val market = waitingForDescription.remove(player.uniqueId) ?: return

        e.isCancelled = true
        val newDesc = e.message.trim()
        val maxChars = LangManager.getConfig().getInt("holo-description.maxchars", 30)

        if (newDesc.length > maxChars) {
            val msg = LangManager.getMessage("description-too-long").replace("{max}", maxChars.toString())
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg))
            return
        }

        Bukkit.getScheduler().runTask(BOBShop.instance, Runnable {
            val updatedMarket = market.copy(description = newDesc)

            MarketOlusturma.markets.remove(market)
            MarketOlusturma.markets.add(updatedMarket)
            MarketDatabase.saveOrUpdateMarket(updatedMarket)

            ShopItemHolo.spawnOrUpdateHologram(
                BOBShop.instance,
                updatedMarket.chestLocation,
                updatedMarket.itemStack,
                updatedMarket.description
            )

            val updatedMsg = LangManager.getMessage("market-description-updated").replace("{desc}", newDesc)
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', updatedMsg))
            player.closeInventory()
        })
    }
}
