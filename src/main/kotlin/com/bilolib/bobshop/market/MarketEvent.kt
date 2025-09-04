package com.bilolib.bobshop.market


import com.bilolib.bobshop.manager.LangManager
import org.bukkit.ChatColor
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.player.PlayerInteractEvent

class MarketEvent : Listener {

    @EventHandler
    fun onBlockBreak(e: BlockBreakEvent) {
        val block = e.block

        val marketChest = MarketOlusturma.markets.find { it.chestLocation.block == block }
        if (marketChest != null) {
            e.isCancelled = true
            val msg = LangManager.getMessage("cannot-break-chest")
            e.player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg))
            return
        }

    }
    @EventHandler
    fun onChestClick(e: PlayerInteractEvent) {
        if (e.action != Action.RIGHT_CLICK_BLOCK && e.action != Action.LEFT_CLICK_BLOCK) return
        val block = e.clickedBlock ?: return
        val market = MarketOlusturma.markets.find { it.chestLocation.block == block } ?: return
        val player = e.player

        // Eğer sahibi değilse açamasın
        if (market.owner != player.uniqueId) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', LangManager.getMessage("market-not-owner")))
            e.isCancelled = true
            return
        }

        // Sahibi ise normal işlemleri yapabilirsin
        // ...
    }

}
