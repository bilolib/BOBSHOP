package com.bilolib.bobshop.menu.marketmenueventlist

import com.bilolib.bobshop.BOBShop
import com.bilolib.bobshop.shopholo.ShopItemHolo
import com.bilolib.bobshop.manager.LangManager
import com.bilolib.bobshop.market.Market
import com.bilolib.bobshop.market.MarketDatabase
import com.bilolib.bobshop.market.MarketOlusturma
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import java.util.UUID
import com.bilolib.bobshop.util.Cooldown


object DescriptionManager : Listener {

    private val waitingForDescription = mutableMapOf<UUID, Market>()

    /**
     * Toggle davranışı:
     * - description yoksa => tek seferlik chat girişi iste.
     * - description varsa => anında kaldır.
     */
    fun requestDescription(playerUUID: UUID, market: Market) {
        val cdKey = "descbtn:${playerUUID}"
        if (!Cooldown.allow(cdKey, 150L)) return
        val player = BOBShop.instance.server.getPlayer(playerUUID) ?: return

        if (market.description.isNullOrBlank()) {
            // AÇIKLAMA YOK → BİR KERELİK GİRİŞ İSTE
            waitingForDescription[playerUUID] = market

            player.sendMessage(
                ChatColor.translateAlternateColorCodes(
                    '&',
                    // lang.yml: enter-description: "&eEnter a description (type &c'cancel'&e to abort)."
                    LangManager.getMessage("enter-description")
                )
            )

            // 15 sn. sonra beklemeyi temizle (otomatik zaman aşımı)
            Bukkit.getScheduler().runTaskLater(BOBShop.instance, Runnable {
                waitingForDescription.remove(playerUUID)
            }, 15 * 20L)

            player.closeInventory()
        } else {
            // AÇIKLAMA VAR → ANINDA KALDIR
            val updatedMarket = market.copy(description = null)

            // markets listesini güvenli güncelle
            MarketOlusturma.markets.removeIf { it.chestLocation == market.chestLocation }
            MarketOlusturma.markets.add(updatedMarket)

            // DB yaz
            MarketDatabase.saveOrUpdateMarket(updatedMarket)

            // Hologramı önce sil, sonra yeniden oluştur (yalın item satırı kalır)
            ShopItemHolo.removeHologram(updatedMarket.chestLocation)
            ShopItemHolo.spawnOrUpdateHologram(
                BOBShop.instance,
                updatedMarket.chestLocation,
                updatedMarket.itemStack,
                updatedMarket.description
            )

            // Mesaj
            val removedMsg = LangManager.getMessage("market-description-removed")
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', removedMsg))

            player.closeInventory()
        }
    }

    @EventHandler
    fun onChat(e: AsyncPlayerChatEvent) {
        val player = e.player
        val uid = player.uniqueId
        val market = waitingForDescription[uid] ?: return // sadece beklemede ise yakala

        e.isCancelled = true
        val raw = e.message.trim()

        val cancelWord = LangManager.getMessage("cancel")
        if (raw.equals(cancelWord, ignoreCase = true) || raw.equals("cancel", ignoreCase = true)) {
            // İPTAL
            Bukkit.getScheduler().runTask(BOBShop.instance, Runnable {
                val msg = LangManager.getMessage("cancelled")
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg))
                waitingForDescription.remove(uid)
                player.closeInventory()
            })
            return
        }

        // Ana threade al ve işleme
        Bukkit.getScheduler().runTask(BOBShop.instance, Runnable {
            val maxChars = LangManager.getConfig().getInt("holo-description.maxchars", 30)

            if (raw.length > maxChars) {
                val msg = LangManager.getMessage("description-too-long")
                    .replace("{max}", maxChars.toString())
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg))
                // Bekleme devam etsin ki oyuncu tekrar yazabilsin
                return@Runnable
            }

            val updatedMarket = market.copy(description = raw)

            // markets listesini güvenli güncelle
            MarketOlusturma.markets.removeIf { it.chestLocation == market.chestLocation }
            MarketOlusturma.markets.add(updatedMarket)

            // DB yaz
            MarketDatabase.saveOrUpdateMarket(updatedMarket)

            // Hologram: önce sil, sonra günceli oluştur
            ShopItemHolo.removeHologram(updatedMarket.chestLocation)
            ShopItemHolo.spawnOrUpdateHologram(
                BOBShop.instance,
                updatedMarket.chestLocation,
                updatedMarket.itemStack,
                updatedMarket.description
            )

            // Mesaj
            val updatedMsg = LangManager.getMessage("market-description-updated")
                .replace("{desc}", raw)
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', updatedMsg))

            // İş tamam
            waitingForDescription.remove(uid)
            player.closeInventory()
        })
    }
}
