package com.bilolib.bobshop.market

import com.bilolib.bobshop.manager.LangManager
import org.bukkit.ChatColor
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.block.Block
import org.bukkit.inventory.Inventory
import org.bukkit.block.Sign


class MarketInventoryListener : Listener {

    @EventHandler
    fun onInventoryOpen(event: InventoryOpenEvent) {
        val inv: Inventory = event.inventory
        val block: Block? = inv.location?.block
        block?.let {
            val market = MarketOlusturma.markets.find { it.chestLocation == block.location }
            market?.let { updateMarketSign(it) }
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val inv: Inventory = event.inventory
        val block: Block? = inv.location?.block
        block?.let {
            val market = MarketOlusturma.markets.find { it.chestLocation == block.location }
            market?.let { updateMarketSign(it) }
        }
    }

    fun updateMarketSign(market: Market) {
        val sellText = LangManager.getConfig().getString("market-modes.sell") ?: return
        val buyText = LangManager.getConfig().getString("market-modes.buy") ?: return

        val modeText = when (market.mode) {
            MarketMode.SELL -> ChatColor.translateAlternateColorCodes('&', sellText)
            MarketMode.BUY  -> ChatColor.translateAlternateColorCodes('&', buyText)
        }

        val signBlock: Block = market.signLocation.block
        if (signBlock.type.name.endsWith("SIGN")) { // OAK_SIGN, OAK_WALL_SIGN vb.
            val state = signBlock.state
            if (state is Sign) {
                state.setLine(0, modeText)
                state.setLine(1, market.itemStack.type.name)
                state.setLine(2, "${market.amount} | ${market.price}")
                state.update(true) // true ile client da güncellenir
            }
        }
    }
}
