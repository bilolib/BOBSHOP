package com.bilolib.bobshop.tabela

import com.bilolib.bobshop.BOBShop
import com.bilolib.bobshop.market.MarketOlusturma
import com.bilolib.bobshop.market.Market
import com.bilolib.bobshop.market.MarketMode
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.block.Chest
import org.bukkit.block.Sign
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import com.bilolib.bobshop.manager.LangManager
import org.bukkit.inventory.ItemStack

class MarketBuyEvent(private val economy: Economy) : Listener {

    private val pendingMarket = mutableMapOf<UUID, Market>()
    private val timeouts = mutableMapOf<UUID, BukkitTask>()

    @EventHandler
    fun onSignClick(e: PlayerInteractEvent) {
        if (e.action != Action.RIGHT_CLICK_BLOCK) return
        val block = e.clickedBlock ?: return
        val sign = block.state as? Sign ?: return

        val market = MarketOlusturma.markets.find { it.signLocation.block == sign.block } ?: return
        val player = e.player
        val id = player.uniqueId

        // Oyuncu marketten satın alacak → market SELL modunda olmalı
        if (market.mode != MarketMode.BUY) return

        // Satıcı kendi tabelasından satın almasın
        if (id == market.owner) return

        e.isCancelled = true

        // Eski akışı kapat
        timeouts.remove(id)?.cancel()
        pendingMarket.remove(id)

        pendingMarket[id] = market
        player.sendMessage(LangManager.getMessage("enter-amount.buy"))

        // 15 sn timeout
        val task = object : BukkitRunnable() {
            override fun run() {
                if (pendingMarket.remove(id) != null) {
                    timeouts.remove(id)
                    player.sendMessage(LangManager.getMessage("purchase-timeout"))
                    player.closeInventory()
                }
            }
        }.runTaskLater(BOBShop.instance, 15 * 20L)
        timeouts[id] = task
    }

    @EventHandler
    fun onChat(e: AsyncPlayerChatEvent) {
        val player = e.player
        val uid = player.uniqueId
        val market = pendingMarket[uid] ?: return

        e.isCancelled = true
        val raw = e.message.trim()

        // İptal
        val cancelWord = LangManager.getMessage("cancel")
        if (raw.equals(cancelWord, ignoreCase = true)) {
            Bukkit.getScheduler().runTask(BOBShop.instance, Runnable {
                player.sendMessage(LangManager.getMessage("purchase-cancelled"))
                pendingMarket.remove(uid)
                timeouts.remove(uid)?.cancel()
                player.closeInventory()
            })
            return
        }

        // Sayı
        val amount = raw.toIntOrNull()?.takeIf { it > 0 && it <= 1_000_000 }
        if (amount == null) {
            Bukkit.getScheduler().runTask(BOBShop.instance, Runnable {
                player.sendMessage(LangManager.getMessage("invalid-number"))
            })
            return
        }

        // Başarılı giriş → ana thread’de devam
        timeouts.remove(uid)?.cancel()
        Bukkit.getScheduler().runTask(BOBShop.instance, Runnable {
            pendingMarket.remove(uid)
            handlePurchase(player, amount, market)
        })
    }

    @EventHandler
    fun onQuit(e: org.bukkit.event.player.PlayerQuitEvent) {
        val uid = e.player.uniqueId
        pendingMarket.remove(uid)
        timeouts.remove(uid)?.cancel()
    }

    private fun isSameItem(a: ItemStack, b: ItemStack): Boolean {
        if (a.type != b.type) return false
        val aMeta = a.itemMeta
        val bMeta = b.itemMeta
        if (aMeta != null && bMeta != null) {
            val aData = if (aMeta.hasCustomModelData()) aMeta.customModelData else null
            val bData = if (bMeta.hasCustomModelData()) bMeta.customModelData else null
            if (aData != bData) return false
        }
        // Lore/enchant gereksinimin varsa burada genişlet
        return true
    }

    private fun handlePurchase(player: Player, amount: Int, market: Market) {
        // Ana thread varsayımı
        val chestState = market.chestLocation.block.state as? Chest
            ?: run {
                player.sendMessage(LangManager.getMessage("invalid-chest"))
                return
            }

        val inventory = chestState.inventory

        // Stok
        val totalAvailable = inventory.contents
            .filterNotNull()
            .filter { isSameItem(it, market.itemStack) }
            .sumOf { it.amount }

        if (totalAvailable < amount) {
            player.sendMessage(
                LangManager.getMessage("not-enough-stock")
                    .replace("{stock}", totalAvailable.toString())
            )
            return
        }

        val totalPrice = market.price * amount

        // Para yeterli mi?
        if (!economy.has(player, totalPrice)) {
            player.sendMessage(
                LangManager.getMessage("not-enough-money")
                    .replace("{price}", String.format(java.util.Locale.US, "%.2f", totalPrice))
            )
            return
        }

        // 1) Sandıktan çıkar (geçici)
        val removed = mutableListOf<Pair<Int, ItemStack>>() // slot, eskiStackClone
        var remaining = amount
        loop@ for (slot in 0 until inventory.size) {
            val stack = inventory.getItem(slot) ?: continue
            if (!isSameItem(stack, market.itemStack)) continue

            val take = minOf(stack.amount, remaining)
            val before = stack.clone()
            stack.amount -= take
            remaining -= take

            if (stack.amount <= 0) inventory.setItem(slot, null)
            else inventory.setItem(slot, stack)

            removed += slot to before
            if (remaining <= 0) break@loop
        }

        // 2) Para çek
        val w = economy.withdrawPlayer(player, totalPrice)
        if (!w.transactionSuccess()) {
            // rollback: sandığa geri koy
            for ((slot, before) in removed) inventory.setItem(slot, before)
            player.sendMessage(LangManager.getMessage("economy-withdraw-failed"))
            return
        }

        // 3) Eşyayı ver (slot dolarsa yere at)
        val toGive = market.itemStack.clone().also { it.amount = amount }
        val leftovers = player.inventory.addItem(toGive)
        leftovers.values.forEach { drop ->
            player.world.dropItemNaturally(player.location, drop)
        }

        // 4) Satıcıya yatır
        val seller = Bukkit.getOfflinePlayer(market.owner)
        val d = economy.depositPlayer(seller, totalPrice)
        if (!d.transactionSuccess()) {
            // rollback: oyuncuya iade, sandığa geri yükle
            economy.depositPlayer(player, totalPrice)
            for ((slot, before) in removed) inventory.setItem(slot, before)
            player.sendMessage(LangManager.getMessage("economy-deposit-failed-refunded"))
            return
        }

        // 5) Mesajlar
        player.sendMessage(
            LangManager.getMessage("purchase-success")
                .replace("{amount}", amount.toString())
                .replace("{item}", market.itemStack.itemMeta?.displayName ?: market.itemStack.type.name)
                .replace("{price}", String.format(java.util.Locale.US, "%.2f", totalPrice))
        )

        if (seller.isOnline) {
            seller.player?.sendMessage(
                LangManager.getMessage("sold-success")
                    .replace("{player}", player.name)
                    .replace("{amount}", amount.toString())
                    .replace("{item}", market.itemStack.itemMeta?.displayName ?: market.itemStack.type.name)
                    .replace("{price}", String.format(java.util.Locale.US, "%.2f", totalPrice))
            )
        }

        // 6) Tabela güncelle
        val displayItem = inventory.contents
            .filterNotNull()
            .firstOrNull { it.isSimilar(market.itemStack) } ?: market.itemStack.clone()

        TabelaUpdate.updateSign(chestState, displayItem, market.price)
    }
}
