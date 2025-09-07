package com.bilolib.bobshop.shopholo

import com.bilolib.bobshop.BOBShop
import com.bilolib.bobshop.shopholo.ShopItemHolo
import com.bilolib.bobshop.market.MarketOlusturma
import org.bukkit.Chunk
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.world.ChunkLoadEvent

/**
 * Chunk yüklendiğinde, o chunk içindeki marketlerin hologramlarını yeniden doğurur.
 * - 1–2 tick gecikme: tile entity/state tam hazır olsun.
 * - spawn'dan önce remove: çifte hologram ihtimali sıfırlanır.
 */
object HologramRespawn : Listener {

    @EventHandler
    fun onChunkLoad(e: ChunkLoadEvent) {
        val chunk: Chunk = e.chunk

        // Bu chunk içinde chest'i bulunan marketleri topla
        val targets = MarketOlusturma.markets.filter { m ->
            val loc = m.chestLocation
            loc.world == chunk.world &&
                    (loc.blockX shr 4) == chunk.x &&
                    (loc.blockZ shr 4) == chunk.z
        }

        if (targets.isEmpty()) return

        // 2 tick sonra çalıştır (daha güvenli)
        BOBShop.instance.server.scheduler.runTaskLater(BOBShop.instance, Runnable {
            targets.forEach { m ->
                // Güvenli: önce kaldır, sonra spawn
                ShopItemHolo.removeHologram(m.chestLocation)
                ShopItemHolo.spawnOrUpdateHologram(
                    BOBShop.instance,
                    m.chestLocation,
                    m.itemStack,
                    m.description
                )
            }
        }, 2L)
    }
}
