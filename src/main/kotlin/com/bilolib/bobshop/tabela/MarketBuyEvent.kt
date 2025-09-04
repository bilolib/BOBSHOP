package com.bilolib.bobshop.tabela

import com.bilolib.bobshop.BOBShop
import com.bilolib.bobshop.market.MarketOlusturma
import com.bilolib.bobshop.market.Market
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.block.Chest
import org.bukkit.block.DoubleChest
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

        val buyMode = LangManager.getMessage("market-modes.buy").uppercase()
        if (market.mode.uppercase() != buyMode) return

        if (player.uniqueId == market.owner) {
            return
        }
        timeouts.remove(id)?.cancel()
        pendingMarket.remove(id)

        e.isCancelled = true
        pendingMarket[id] = market
        player.sendMessage(LangManager.getMessage("enter-amount.buy"))

        val task = object : BukkitRunnable() {
            override fun run() {
                if (pendingMarket.remove(id) != null) {
                    timeouts.remove(id)
                    player.sendMessage(LangManager.getMessage("purchase-timeout"))
                }
            }
        }.runTaskLater(BOBShop.instance, 15 * 20L)
        timeouts[id] = task
    }

    @EventHandler
    fun onChat(e: AsyncPlayerChatEvent) {
        val player = e.player
        val id = player.uniqueId
        val market = pendingMarket[id] ?: return

        e.isCancelled = true

        val amount = e.message.toIntOrNull()
        if (amount == null || amount <= 0) {
            pendingMarket.remove(id)
            timeouts.remove(id)?.cancel()
            player.sendMessage(LangManager.getMessage("invalid-number"))
            return
        }

        timeouts.remove(id)?.cancel()
        Bukkit.getScheduler().runTask(BOBShop.instance, Runnable {
            pendingMarket.remove(id)
            handlePurchase(player, amount, market)
        })
    }
    private fun isSameItem(a: ItemStack, b: ItemStack): Boolean {
        if (a.type != b.type) return false

        val aMeta = a.itemMeta
        val bMeta = b.itemMeta

        // CustomModelData varsa kontrol et
        if (aMeta != null && bMeta != null) {
            val aData = if (aMeta.hasCustomModelData()) aMeta.customModelData else null
            val bData = if (bMeta.hasCustomModelData()) bMeta.customModelData else null
            return aData == bData
        }

        return true
    }

    private fun handlePurchase(player: Player, amount: Int, market: Market) {
        val chestState = market.chestLocation.block.state
        val inventory = when (chestState) {
            is Chest -> chestState.inventory
            is DoubleChest -> chestState.inventory
            else -> {
                player.sendMessage(LangManager.getMessage("invalid-chest"))
                return
            }
        }

        val totalAvailable = inventory.contents
            .filterNotNull()
            .filter { isSameItem(it, market.itemStack) }
            .sumOf { it.amount }

        if (totalAvailable < amount) {
            player.sendMessage(LangManager.getMessage("not-enough-stock").replace("{stock}", totalAvailable.toString()))
            return
        }

        val totalPrice = market.price * amount
        if (!economy.has(player, totalPrice)) {
            player.sendMessage(LangManager.getMessage("not-enough-money").replace("{price}", totalPrice.toString()))
            return
        }

        // Sandıktan eşya düşür
        var remaining = amount
        for (slot in 0 until inventory.size) {
            val stack = inventory.getItem(slot) ?: continue
            if (!isSameItem(stack, market.itemStack)) continue

            val take = minOf(stack.amount, remaining)
            stack.amount -= take
            remaining -= take
            if (stack.amount <= 0) inventory.setItem(slot, null)
            else inventory.setItem(slot, stack)
            if (remaining <= 0) break
        }

        // Oyuncuya ver
        val toGive = market.itemStack.clone()
        toGive.amount = amount
        val leftovers = player.inventory.addItem(toGive)
        leftovers.values.forEach { drop ->
            player.world.dropItemNaturally(player.location, drop)
        }

        // Para transferi
        val seller = Bukkit.getOfflinePlayer(market.owner)
        economy.withdrawPlayer(player, totalPrice)
        economy.depositPlayer(seller, totalPrice)

        player.sendMessage(
            LangManager.getMessage("purchase-success")
                .replace("{amount}", amount.toString())
                .replace("{item}", market.itemStack.itemMeta?.displayName ?: market.itemStack.type.name)
                .replace("{price}", totalPrice.toString())
        )

        if (seller.isOnline) {
            seller.player?.sendMessage(
                LangManager.getMessage("sold-success")
                    .replace("{player}", player.name)
                    .replace("{amount}", amount.toString())
                    .replace("{item}", market.itemStack.itemMeta?.displayName ?: market.itemStack.type.name)
                    .replace("{price}", totalPrice.toString())
            )
        }

        // Tabelayı güncelle
        val chestForSign: Chest = when (chestState) {
            is Chest -> chestState
            is DoubleChest -> chestState.leftSide as Chest
            else -> return
        }

        val displayItem = inventory.contents
            .filterNotNull()
            .firstOrNull { it.isSimilar(market.itemStack) } ?: market.itemStack.clone()
        TabelaUpdate.updateSign(chestForSign, displayItem, market.price)
    }
}
