package com.bilolib.bobshop.tabela

import com.bilolib.bobshop.market.Market
import com.bilolib.bobshop.market.MarketOlusturma
import org.bukkit.block.Chest
import org.bukkit.block.Sign
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerInteractEvent

class TabelaEvent : Listener {

    // Tabela tıklanırsa güncelle
    @EventHandler
    fun onTabelaClick(event: PlayerInteractEvent) {
        val action = event.action
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) return

        val block = event.clickedBlock ?: return
        if (block.state !is Sign) return
        val sign = block.state as Sign

        val market = MarketOlusturma.markets.find { it.signLocation.block == sign.block } ?: return
        val chest = market.chestLocation.block.state as? Chest ?: return

        updateMarketSign(market, chest)
        event.isCancelled = true
    }

    // Sandık kapandığında tabela güncellensin
    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val invHolder = event.inventory.holder as? Chest ?: return
        val market = MarketOlusturma.markets.find { it.chestLocation.block == invHolder.block } ?: return

        updateMarketSign(market, invHolder)
    }

    private fun updateMarketSign(market: Market, chest: Chest) {
        // Sandıktaki uygun itemi al
        val itemInChest = chest.inventory.contents
            .filterNotNull()
            .firstOrNull { it.isSimilar(market.itemStack) } ?: market.itemStack.clone()

        // Depodaki toplam miktarı hesapla
        val totalAmount = chest.inventory.contents
            .filterNotNull()
            .filter { it.isSimilar(market.itemStack) }
            .sumOf { it.amount }

        // TabelaUpdate ile güncelle
        com.bilolib.bobshop.tabela.TabelaUpdate.updateSign(chest, itemInChest, market.price)
    }
}
