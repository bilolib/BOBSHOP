package com.bilolib.bobshop.shopholo

import com.bilolib.bobshop.shopholo.ShopItemHolo
import com.bilolib.bobshop.market.MarketOlusturma
import org.bukkit.Chunk
import org.bukkit.block.Block
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.world.ChunkUnloadEvent

object HologramCleanup : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onBlockBreak(e: BlockBreakEvent) {
        val b: Block = e.block
        // Bu block bir market chest'i ya da market sign'ı mı?
        val market = MarketOlusturma.markets.firstOrNull {
            it.chestLocation.block == b || it.signLocation.block == b
        } ?: return

        // Chest kırıldıysa hologramı kaldır
        ShopItemHolo.removeHologram(market.chestLocation)
        // Not: Market silme kararı oyun tasarımına bağlı; burada sadece hologramı temizliyoruz.
    }

    @EventHandler
    fun onChunkUnload(e: ChunkUnloadEvent) {
        val chunk: Chunk = e.chunk
        // Bu chunk içinde chest'i olan marketlerin hologramlarını temizle
        MarketOlusturma.markets.forEach { market ->
            val loc = market.chestLocation
            if (loc.world == chunk.world &&
                loc.blockX shr 4 == chunk.x &&
                loc.blockZ shr 4 == chunk.z
            ) {
                ShopItemHolo.removeHologram(loc)
            }
        }
    }
}