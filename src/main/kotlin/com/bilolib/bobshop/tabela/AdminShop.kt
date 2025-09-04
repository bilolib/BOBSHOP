package com.bilolib.bobshop.adminshop

import com.bilolib.bobshop.BOBShop
import com.bilolib.bobshop.manager.LangManager
import com.bilolib.bobshop.market.Market
import com.bilolib.bobshop.market.MarketOlusturma
import net.milkbowl.vault.economy.Economy
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID

class AdminShop(private val economy: Economy) : Listener {

    private val awaitingInput = mutableMapOf<UUID, Pair<Market, Boolean>>() // true = buy, false = sell
    private val timeouts = mutableMapOf<UUID, BukkitRunnable>()

    @EventHandler
    fun onSignClick(e: PlayerInteractEvent) {
        if (e.action != Action.RIGHT_CLICK_BLOCK) return
        val block = e.clickedBlock ?: return
        val sign = block.state as? org.bukkit.block.Sign ?: return

        val market = MarketOlusturma.markets.find { it.signLocation.block == sign.block } ?: return
        if (!market.adminShop) return // sadece admin shop

        if (e.player.isSneaking) return

        e.isCancelled = true
        val player = e.player
        val id = player.uniqueId

        // Önce varsa eski giriş ve zamanlayıcıyı temizle
        awaitingInput.remove(id)
        timeouts.remove(id)?.cancel()

        val isBuy = market.mode.equals(LangManager.getMessage("market-modes.buy"), ignoreCase = true)
        awaitingInput[id] = Pair(market, isBuy)

        player.sendMessage(
            if (isBuy)
                LangManager.getMessage("enter-amount.buy")
            else
                LangManager.getMessage("enter-amount.sell")
        )

        // 15 saniyelik zamanlayıcı başlat
        val task = object : BukkitRunnable() {
            override fun run() {
                if (awaitingInput.remove(id) != null) {
                    player.sendMessage(LangManager.getMessage("purchase-timeout"))
                }
                timeouts.remove(id)
            }
        }
        task.runTaskLater(BOBShop.instance, 15 * 20L)
        timeouts[id] = task
    }

    @EventHandler
    fun onPlayerChat(e: AsyncPlayerChatEvent) {
        val player = e.player
        val entry = awaitingInput[player.uniqueId] ?: return

        e.isCancelled = true

        val amount = e.message.toIntOrNull()
        if (amount == null || amount <= 0) {
            player.sendMessage(LangManager.getMessage("invalid-number"))
            return
        }

        val market = entry.first
        val isBuy = entry.second

        if (isBuy) handleBuy(player, market, amount)
        else handleSell(player, market, amount)

        // İşlem tamamlandı, kaydı ve zamanlayıcıyı temizle
        awaitingInput.remove(player.uniqueId)
        timeouts.remove(player.uniqueId)?.cancel()
    }
    private fun isSameItem(a: org.bukkit.inventory.ItemStack, b: org.bukkit.inventory.ItemStack): Boolean {
        if (a.type != b.type) return false

        val aMeta = a.itemMeta
        val bMeta = b.itemMeta

        if (aMeta != null && bMeta != null) {
            val aData = if (aMeta.hasCustomModelData()) aMeta.customModelData else null
            val bData = if (bMeta.hasCustomModelData()) bMeta.customModelData else null
            return aData == bData
        }

        return true
    }
    private fun handleBuy(player: Player, market: Market, amount: Int) {
        val totalPrice = market.price * amount
        if (!economy.has(player, totalPrice.toDouble())) {
            player.sendMessage(LangManager.getMessage("not-enough-money").replace("{price}", totalPrice.toString()))
            return
        }

        val toGive = market.itemStack.clone().apply { this.amount = amount }
        val leftovers = player.inventory.addItem(toGive)
        leftovers.values.forEach { player.world.dropItemNaturally(player.location, it) }

        economy.withdrawPlayer(player, totalPrice.toDouble())
        player.sendMessage(
            LangManager.getMessage("purchase-success")
                .replace("{amount}", amount.toString())
                .replace("{item}", toGive.itemMeta?.displayName ?: toGive.type.name)
                .replace("{price}", totalPrice.toString())
        )
    }

    private fun handleSell(player: Player, market: Market, amount: Int) {
        val totalAvailable = player.inventory.contents
            .filterNotNull()
            .filter { isSameItem(it, market.itemStack) }
            .sumOf { it.amount }

        if (totalAvailable < amount) {
            player.sendMessage(
                LangManager.getMessage("not-enough-items")
                    .replace("{item}", market.itemStack.itemMeta?.displayName ?: market.itemStack.type.name)
            )
            return
        }

        var remaining = amount
        for (slot in 0 until player.inventory.size) {
            val stack = player.inventory.getItem(slot) ?: continue
            if (!stack.isSimilar(market.itemStack)) continue

            val take = minOf(stack.amount, remaining)
            stack.amount -= take
            remaining -= take

            if (stack.amount <= 0) player.inventory.setItem(slot, null)
            else player.inventory.setItem(slot, stack)

            if (remaining <= 0) break
        }

        val totalPrice = market.price * amount
        economy.depositPlayer(player, totalPrice.toDouble())

        player.sendMessage(
            LangManager.getMessage("sold-success")
                .replace("{amount}", amount.toString())
                .replace("{item}", market.itemStack.itemMeta?.displayName ?: market.itemStack.type.name)
                .replace("{price}", totalPrice.toString())
        )
    }
}

