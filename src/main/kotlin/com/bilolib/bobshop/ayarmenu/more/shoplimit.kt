package com.bilolib.bobshop.ayarmenu.more

import com.bilolib.bobshop.BOBShop
import com.bilolib.bobshop.manager.LangManager
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import java.util.UUID

object ShopLimitManager : Listener {

    private val waitingForLimit = mutableMapOf<UUID, Boolean>()

    // Slot 12’ye tıklayınca çağır
    fun openLimitChangePrompt(player: Player) {
        val msg = LangManager.getMessage("enter-shop-limit")
        player.sendMessage(msg)
        waitingForLimit[player.uniqueId] = true
    }

    @EventHandler
    fun onChat(e: AsyncPlayerChatEvent) {
        val player = e.player
        if (!waitingForLimit.containsKey(player.uniqueId)) return

        e.isCancelled = true
        val newLimit = e.message.toIntOrNull()
        if (newLimit == null || newLimit <= 0) {
            player.sendMessage(LangManager.getMessage("invalid-number"))
            return
        }

        // Config güncelle
        val config = BOBShop.instance.config
        config.set("shop-limit", newLimit)
        BOBShop.instance.saveConfig()

        val updatedMsg = LangManager.getMessage("updated-shop-limit").replace("{limit}", newLimit.toString())
        player.sendMessage(updatedMsg)
        waitingForLimit.remove(player.uniqueId)
    }
}
