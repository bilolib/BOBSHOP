package com.bilolib.bobshop.ayarmenu.more

import com.bilolib.bobshop.BOBShop
import com.bilolib.bobshop.manager.LangManager
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import java.util.UUID

object ShopPriceChangeListener : Listener {

    private val waitingForPrice = mutableMapOf<UUID, Boolean>()

    fun requestShopPriceChange(player: Player) {
        waitingForPrice[player.uniqueId] = true
        player.sendMessage(LangManager.getMessage("shop-price-enter"))

        // 10 saniye timeout
        Bukkit.getScheduler().runTaskLater(BOBShop.instance, Runnable {
            if (waitingForPrice.containsKey(player.uniqueId)) {
                player.sendMessage(LangManager.getMessage("shop-price-timeout"))
                waitingForPrice.remove(player.uniqueId)
            }
        }, 200L) // 200 ticks = 10 saniye
    }

    @EventHandler
    fun onPlayerChat(e: AsyncPlayerChatEvent) {
        val player = e.player
        val uid = player.uniqueId
        if (!waitingForPrice.containsKey(uid)) return

        e.isCancelled = true
        val raw = e.message.trim()
        val cancelWord = LangManager.getMessage("cancel")

        // İptal
        if (raw.equals(cancelWord, ignoreCase = true)) {
            Bukkit.getScheduler().runTask(BOBShop.instance, Runnable {
                player.sendMessage(LangManager.getMessage("shop-price-change-cancelled"))
                waitingForPrice.remove(uid)
            })
            return
        }

        // Virgül desteği + güvenli parse
        val parsed = raw.replace(',', '.').toDoubleOrNull()
        val newPrice = parsed?.takeIf { it.isFinite() && it > 0.0 && it <= 1_000_000_000_000.0 }

        if (newPrice == null) {
            Bukkit.getScheduler().runTask(BOBShop.instance, Runnable {
                player.sendMessage(LangManager.getMessage("shop-price-invalid-number"))
            })
            return
        }

        // Bukkit API işlemleri main thread
        Bukkit.getScheduler().runTask(BOBShop.instance, Runnable {
            val cfg = BOBShop.instance.config
            cfg.set("shop-price", newPrice)
            BOBShop.instance.saveConfig()

            player.sendMessage(
                LangManager.getMessage("shop-price-updated")
                    .replace("{price}", String.format(java.util.Locale.US, "%.2f", newPrice))
            )
            waitingForPrice.remove(uid)
        })
    }
}
