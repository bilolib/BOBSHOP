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
        if (!waitingForPrice.containsKey(player.uniqueId)) return

        e.isCancelled = true
        val message = e.message.trim()

        // İptal mesajı
        if (message.equals(LangManager.getMessage("cancel"), ignoreCase = true)) {
            player.sendMessage(LangManager.getMessage("shop-price-change-cancelled"))
            waitingForPrice.remove(player.uniqueId)
            return
        }

        val newPrice = message.toDoubleOrNull()
        if (newPrice == null || newPrice <= 0) {
            player.sendMessage(LangManager.getMessage("shop-price-invalid-number"))
            return
        }

        // Config'e kaydet
        val config = BOBShop.instance.config
        config.set("shop-price", newPrice)
        BOBShop.instance.saveConfig()

        player.sendMessage(
            LangManager.getMessage("shop-price-updated").replace("{price}", newPrice.toString())
        )

        waitingForPrice.remove(player.uniqueId)
    }
}
