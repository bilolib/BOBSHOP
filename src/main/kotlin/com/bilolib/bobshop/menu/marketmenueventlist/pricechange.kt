package com.bilolib.bobshop.menu.marketmenueventlist

import com.bilolib.bobshop.BOBShop
import com.bilolib.bobshop.Lang
import com.bilolib.bobshop.market.Market
import com.bilolib.bobshop.market.MarketDatabase
import com.bilolib.bobshop.tabela.TabelaUpdate
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.block.Chest
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import java.util.UUID
import com.bilolib.bobshop.manager.LangManager
import kotlin.text.replace

object PriceChangeManager : Listener {

    val waitingForPrice = mutableMapOf<UUID, Market>()

    fun requestPriceChange(player: Player, market: Market) {
        waitingForPrice[player.uniqueId] = market

        // Ana thread üzerinde çalıştır
        Bukkit.getScheduler().runTask(BOBShop.instance, Runnable {
            player.closeInventory()
            player.sendMessage(ChatColor.translateAlternateColorCodes(
                '&',
                LangManager.getMessage("price-prompt")
            ))
        })

        // 15 saniye sonra otomatik temizleme (ana thread’de)
        Bukkit.getScheduler().runTaskLater(BOBShop.instance, Runnable {
            waitingForPrice.remove(player.uniqueId)
        }, 15 * 20L)
    }

    @EventHandler
    fun onChat(e: AsyncPlayerChatEvent) {
        val player = e.player
        val market = waitingForPrice[player.uniqueId] ?: return

        e.isCancelled = true
        val msg = e.message.trim()

        val cancel = LangManager.getMessage("cancel")
        // İptal
        if (msg.equals(cancel, ignoreCase = true)) {
            player.sendMessage(LangManager.getMessage("price-cancelled"))
            waitingForPrice.remove(player.uniqueId)
            return
        }

        val newPrice = msg.toDoubleOrNull()
        if (newPrice == null || newPrice <= 0) {
            player.sendMessage(LangManager.getMessage("price-invalid"))
            return
        }

        market.price = newPrice

        // Ana thread’de yap
        Bukkit.getScheduler().runTask(BOBShop.instance, Runnable {
            MarketDatabase.saveOrUpdateMarket(market)

            val chest = market.chestLocation.block.state as? Chest
            if (chest != null) {
                TabelaUpdate.updateSign(chest, market.itemStack, market.price)
            }

            player.sendMessage(Lang.get("price-updated").replace("{price}", newPrice.toString()))
        })

        // Map’i sadece başarılı değişim sonrası temizle
        waitingForPrice.remove(player.uniqueId)
    }
}

