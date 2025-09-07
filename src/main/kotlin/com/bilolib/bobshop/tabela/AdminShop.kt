package com.bilolib.bobshop.adminshop

import com.bilolib.bobshop.BOBShop
import com.bilolib.bobshop.manager.LangManager
import com.bilolib.bobshop.market.Market
import com.bilolib.bobshop.market.MarketMode
import com.bilolib.bobshop.market.MarketOlusturma
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import kotlin.math.min

class AdminShop(private val economy: Economy) : Listener {

    private val awaitingInput = mutableMapOf<UUID, Pair<Market, Boolean>>()
    private val timeouts = mutableMapOf<UUID, BukkitTask>()

    @EventHandler
    fun onSignClick(e: PlayerInteractEvent) {
        if (e.action != Action.RIGHT_CLICK_BLOCK) return
        val block = e.clickedBlock ?: return
        val sign = block.state as? org.bukkit.block.Sign ?: return

        val market = MarketOlusturma.markets.find { it.signLocation.block == sign.block } ?: return
        if (!market.adminShop) return
        if (e.player.isSneaking) return

        val player = e.player
        val uid = player.uniqueId

        // Eski akışı kapat
        timeouts.remove(uid)?.cancel()
        awaitingInput.remove(uid)

        e.isCancelled = true

        // DOĞRU EŞLEME:
        // SELL modu -> oyuncu admin'den SATIN ALIR (isBuy = true)
        // BUY  modu -> oyuncu admin'e SATAR      (isBuy = false)
        val isBuyMode = (market.mode == MarketMode.BUY)
        awaitingInput[uid] = market to isBuyMode

        player.sendMessage(
            if (isBuyMode) LangManager.getMessage("enter-amount.buy")
            else LangManager.getMessage("enter-amount.sell")
        )

        // 15 sn timeout
        val task = object : BukkitRunnable() {
            override fun run() {
                if (awaitingInput.remove(uid) != null) {
                    player.sendMessage(LangManager.getMessage("purchase-timeout"))
                    player.closeInventory()
                }
                timeouts.remove(uid)
            }
        }.runTaskLater(BOBShop.instance, 15 * 20L)
        timeouts[uid] = task
    }

    @EventHandler
    fun onPlayerChat(e: AsyncPlayerChatEvent) {
        val player = e.player
        val uid = player.uniqueId
        val entry = awaitingInput[uid] ?: return

        e.isCancelled = true
        val raw = e.message.trim()

        val cancelWord = LangManager.getMessage("cancel")
        if (raw.equals(cancelWord, ignoreCase = true)) {
            Bukkit.getScheduler().runTask(BOBShop.instance, Runnable {
                player.sendMessage(
                    if (entry.second) LangManager.getMessage("purchase-cancelled")
                    else LangManager.getMessage("sell-cancelled")
                )
                awaitingInput.remove(uid)
                timeouts.remove(uid)?.cancel()
                player.closeInventory()
            })
            return
        }

        val amount = raw.toIntOrNull()?.takeIf { it > 0 && it <= 1_000_000 }
        if (amount == null) {
            Bukkit.getScheduler().runTask(BOBShop.instance, Runnable {
                player.sendMessage(LangManager.getMessage("invalid-number"))
            })
            return
        }

        timeouts.remove(uid)?.cancel()
        val (market, isBuy) = entry
        Bukkit.getScheduler().runTask(BOBShop.instance, Runnable {
            awaitingInput.remove(uid)
            if (isBuy) handleBuy(player, market, amount) else handleSell(player, market, amount)
        })
    }

    @EventHandler
    fun onQuit(e: PlayerQuitEvent) {
        val uid = e.player.uniqueId
        awaitingInput.remove(uid)
        timeouts.remove(uid)?.cancel()
    }

    /* ================== İş mantığı ================== */

    private fun isSameItem(a: org.bukkit.inventory.ItemStack, b: org.bukkit.inventory.ItemStack): Boolean {
        if (a.type != b.type) return false
        val aMeta = a.itemMeta
        val bMeta = b.itemMeta
        val aCmd = aMeta?.let { if (it.hasCustomModelData()) it.customModelData else null }
        val bCmd = bMeta?.let { if (it.hasCustomModelData()) it.customModelData else null }
        return aCmd == bCmd
    }

    /** Admin shop BUY: oyuncu admin'den satın alır (stok sınırsız) */
    private fun handleBuy(player: Player, market: Market, amount: Int) {
        val totalPrice = market.price * amount
        val priceStr = String.format(java.util.Locale.US, "%.2f", totalPrice)

        val w = economy.withdrawPlayer(player, totalPrice)
        if (!w.transactionSuccess()) {
            player.sendMessage(LangManager.getMessage("economy-withdraw-failed"))
            return
        }

        var remaining = amount
        while (remaining > 0) {
            val stackSize = min(market.itemStack.maxStackSize, remaining)
            val stack = market.itemStack.clone().also { it.amount = stackSize }
            val leftovers = player.inventory.addItem(stack)
            leftovers.values.forEach { player.world.dropItemNaturally(player.location, it) }
            remaining -= stackSize
        }

        val itemName = market.itemStack.itemMeta?.displayName ?: market.itemStack.type.name
        player.sendMessage(
            LangManager.getMessage("purchase-success")
                .replace("{amount}", amount.toString())
                .replace("{item}", itemName)
                .replace("{price}", priceStr)
        )
    }

    /** Admin shop SELL: oyuncu admin'e satar (para sınırsız, stok kontrolü yok) */
    private fun handleSell(player: Player, market: Market, amount: Int) {
        var available = 0
        for (it in player.inventory.contents) {
            if (it != null && isSameItem(it, market.itemStack)) {
                available += it.amount
                if (available >= amount) break
            }
        }
        if (available < amount) {
            val itemName = market.itemStack.itemMeta?.displayName ?: market.itemStack.type.name
            player.sendMessage(
                LangManager.getMessage("not-enough-items")
                    .replace("{item}", itemName)
            )
            return
        }

        val removed = mutableListOf<Pair<Int, org.bukkit.inventory.ItemStack>>()
        var remaining = amount
        for (slot in 0 until player.inventory.size) {
            val s = player.inventory.getItem(slot) ?: continue
            if (!isSameItem(s, market.itemStack)) continue
            val take = min(s.amount, remaining)
            val before = s.clone()
            s.amount -= take
            remaining -= take
            if (s.amount <= 0) player.inventory.setItem(slot, null) else player.inventory.setItem(slot, s)
            removed += slot to before
            if (remaining <= 0) break
        }

        val totalPrice = market.price * amount
        val priceStr = String.format(java.util.Locale.US, "%.2f", totalPrice)

        val d = economy.depositPlayer(player, totalPrice)
        if (!d.transactionSuccess()) {
            for ((slot, before) in removed) player.inventory.setItem(slot, before)
            player.sendMessage(LangManager.getMessage("economy-deposit-failed-refunded"))
            return
        }

        val itemName = market.itemStack.itemMeta?.displayName ?: market.itemStack.type.name
        player.sendMessage(
            LangManager.getMessage("sold-success")
                .replace("{amount}", amount.toString())
                .replace("{item}", itemName)
                .replace("{price}", priceStr)
        )
    }
}
