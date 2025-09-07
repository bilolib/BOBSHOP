package com.bilolib.bobshop.menu.marketmenueventlist

import com.bilolib.bobshop.BOBShop
import com.bilolib.bobshop.market.Market
import com.bilolib.bobshop.market.MarketDatabase
import com.bilolib.bobshop.tabela.TabelaUpdate
import org.bukkit.Bukkit
import org.bukkit.ChatColor
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
        val uid = player.uniqueId
        val market = waitingForPrice[uid] ?: return

        e.isCancelled = true
        val raw = e.message.trim()

        // İptal anahtarları
        val cancelWord = LangManager.getMessage("cancel")
        if (raw.equals(cancelWord, ignoreCase = true) || raw.equals("cancel", ignoreCase = true)) {
            Bukkit.getScheduler().runTask(BOBShop.instance, Runnable {
                player.sendMessage(LangManager.getMessage("price-cancelled"))
                waitingForPrice.remove(uid)
                player.closeInventory()
            })
            return
        }

        // Güvenli sayı parse (virgül desteği + limit + NaN/Inf koruması)
        val parsed = raw.replace(',', '.').toDoubleOrNull()
        val newPrice = parsed?.takeIf { it.isFinite() && it > 0.0 && it <= 1_000_000_000_000.0 }
        if (newPrice == null) {
            Bukkit.getScheduler().runTask(BOBShop.instance, Runnable {
                player.sendMessage(LangManager.getMessage("price-invalid"))
            })
            return
        }

        // Bukkit API işlemleri ana thread
        Bukkit.getScheduler().runTask(BOBShop.instance, Runnable {
            // Modeli güncelle
            market.price = newPrice

            // DB yazımı (MarketDatabase kendi içinde async çalışıyor)
            MarketDatabase.saveOrUpdateMarket(market)

            // Tabela/Chest güncelle (Bukkit API -> main thread)
            val chest = market.chestLocation.block.state as? org.bukkit.block.Chest
            if (chest != null) {
                TabelaUpdate.updateSign(chest, market.itemStack, market.price)
            }

            // Mesaj (tek dil katmanı kullanalım)
            val msg = LangManager.getMessage("price-updated")
                .replace("{price}", String.format(java.util.Locale.US, "%.2f", newPrice))
            player.sendMessage(msg)

            // İş bitti
            waitingForPrice.remove(uid)
            player.closeInventory()
        })
    }

}

