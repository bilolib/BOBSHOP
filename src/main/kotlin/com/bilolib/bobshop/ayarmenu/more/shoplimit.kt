package com.bilolib.bobshop.ayarmenu.more

import com.bilolib.bobshop.BOBShop
import com.bilolib.bobshop.manager.LangManager
import org.bukkit.Bukkit
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
        val uid = player.uniqueId
        if (!waitingForLimit.containsKey(uid)) return

        e.isCancelled = true
        val raw = e.message.trim()

        // İptal kontrolü
        val cancelWord = LangManager.getMessage("cancel")
        if (raw.equals(cancelWord, ignoreCase = true) || raw.equals("cancel", ignoreCase = true)) {
            Bukkit.getScheduler().runTask(BOBShop.instance, Runnable {
                player.sendMessage(LangManager.getMessage("shop-limit-change-cancelled"))
                waitingForLimit.remove(uid)
            })
            return
        }

        // Sayı kontrolü
        val newLimit = raw.toIntOrNull()?.takeIf { it > 0 && it <= 1_000_000 }
        if (newLimit == null) {
            Bukkit.getScheduler().runTask(BOBShop.instance, Runnable  {
                player.sendMessage(LangManager.getMessage("invalid-number"))
            })
            return
        }

        // Config güncelleme
        Bukkit.getScheduler().runTask(BOBShop.instance, Runnable  {
            val config = BOBShop.instance.config
            config.set("shop-limit", newLimit)
            BOBShop.instance.saveConfig()

            val updatedMsg = LangManager
                .getMessage("updated-shop-limit")
                .replace("{limit}", newLimit.toString())
            player.sendMessage(updatedMsg)

            waitingForLimit.remove(uid)
        })
    }
}
