package com.bilolib.bobshop.tabela

import com.bilolib.bobshop.market.Market
import com.bilolib.bobshop.market.MarketOlusturma
import org.bukkit.block.Chest
import org.bukkit.block.DoubleChest
import org.bukkit.block.Sign
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

class TabelaEvent : Listener {

    @EventHandler
    fun onTabelaClick(event: PlayerInteractEvent) {
        val block = event.clickedBlock ?: return
        val action = event.action
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) return

        val state = block.state
        if (state !is Sign) return

        val market = MarketOlusturma.markets.find { it.signLocation.block == state.block } ?: return
        val chestState = market.chestLocation.block.state
        val chest = when (chestState) {
            is Chest -> chestState
            is DoubleChest -> chestState.leftSide as? Chest ?: chestState.rightSide as? Chest ?: return
            else -> return
        }

        updateMarketSign(market, chest)
        event.isCancelled = true
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val holder = event.inventory.holder
        val chest: Chest = when (holder) {
            is Chest -> holder
            is DoubleChest -> holder.leftSide as? Chest ?: holder.rightSide as? Chest ?: return
            else -> return
        }

        val market = MarketOlusturma.markets.find { it.chestLocation.block == chest.block } ?: return
        updateMarketSign(market, chest)
    }

    private fun updateMarketSign(market: Market, chest: Chest) {
        val inv = mergedInventory(chest) ?: return

        var firstMatch: ItemStack? = null
        var totalAmount = 0

        for (item in inv.storageContents) {
            if (item == null || item.type.isAir) continue
            if (item.isSimilar(market.itemStack)) {
                if (firstMatch == null) firstMatch = item
                totalAmount += item.amount
            }
        }

        // sandıkta yoksa market.itemStack’i göstermek için klonla
        val itemToShow = (firstMatch ?: market.itemStack).clone()

        TabelaUpdate.updateSign(chest, itemToShow, market.price)
    }

    /**
     * DoubleChest ise birleşik envanteri döndür, değilse tek chest’in envanteri.
     */
    private fun mergedInventory(chest: Chest): Inventory? {
        val holder = chest.inventory.holder
        return when (holder) {
            is DoubleChest -> holder.inventory
            is Chest -> holder.inventory
            else -> null
        }
    }
}
