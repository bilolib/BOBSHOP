package com.bilolib.bobshop.tabela

import com.bilolib.bobshop.BOBShop
import com.bilolib.bobshop.manager.LangManager
import com.bilolib.bobshop.market.Market
import com.bilolib.bobshop.market.MarketOlusturma
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
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import kotlin.math.min

class MarketSellEvent(private val economy: Economy) : Listener {

    private val pendingMarket = mutableMapOf<UUID, Market>()
    private val timeouts = mutableMapOf<UUID, BukkitTask>()

    @EventHandler
    fun onSignClick(e: PlayerInteractEvent) {
        if (e.action != Action.RIGHT_CLICK_BLOCK) return
        val block = e.clickedBlock ?: return
        val sign = block.state as? Sign ?: return

        val market = MarketOlusturma.markets.find { it.signLocation.block == sign.block } ?: return
        val player = e.player
        val uid = player.uniqueId

        // Satış akışı: market BUY modunda olmalı (shop oyuncudan satın alır)
        if (market.mode != MarketMode.SELL) return

        // Kendine satışı engelle
        if (uid == market.owner) return

        e.isCancelled = true

        timeouts.remove(uid)?.cancel()
        pendingMarket.remove(uid)

        pendingMarket[uid] = market
        player.sendMessage(LangManager.getMessage("enter-amount.sell"))

        val task = object : BukkitRunnable() {
            override fun run() {
                if (pendingMarket.remove(uid) != null) {
                    timeouts.remove(uid)
                    player.sendMessage(LangManager.getMessage("purchase-timeout"))
                    player.closeInventory()
                }
            }
        }.runTaskLater(BOBShop.instance, 15 * 20L)
        timeouts[uid] = task
    }

    @EventHandler
    fun onChat(e: AsyncPlayerChatEvent) {
        val player = e.player
        val uid = player.uniqueId
        val market = pendingMarket[uid] ?: return

        e.isCancelled = true
        val raw = e.message.trim()

        // cancel desteği
        val cancelWord = LangManager.getMessage("cancel")
        if (raw.equals(cancelWord, ignoreCase = true)) {
            Bukkit.getScheduler().runTask(BOBShop.instance, Runnable {
                player.sendMessage(LangManager.getMessage("sell-cancelled"))
                pendingMarket.remove(uid)
                timeouts.remove(uid)?.cancel()
                player.closeInventory()
            })
            return
        }

        // sayı kontrolü
        val amount = raw.toIntOrNull()?.takeIf { it > 0 && it <= 1_000_000 }
        if (amount == null) {
            Bukkit.getScheduler().runTask(BOBShop.instance, Runnable {
                player.sendMessage(LangManager.getMessage("invalid-number"))
            })
            return
        }

        // başarı: timeout iptal + ana thread’e geç
        timeouts.remove(uid)?.cancel()
        Bukkit.getScheduler().runTask(BOBShop.instance, Runnable {
            handleSell(player, amount, market)
            pendingMarket.remove(uid)
        })
    }

    @EventHandler
    fun onQuit(e: PlayerQuitEvent) {
        val uid = e.player.uniqueId
        pendingMarket.remove(uid)
        timeouts.remove(uid)?.cancel()
    }

    /* ================== İş mantığı ================== */

    private fun isSameItem(a: ItemStack, b: ItemStack): Boolean {
        if (a.type != b.type) return false
        val aMeta = a.itemMeta
        val bMeta = b.itemMeta
        // gerekliyse burada lore/enchant/displayName eşitlemesi de yap
        val aCmd = aMeta?.let { if (it.hasCustomModelData()) it.customModelData else null }
        val bCmd = bMeta?.let { if (it.hasCustomModelData()) it.customModelData else null }
        return aCmd == bCmd
    }

    private fun playerHasAmountOf(player: Player, proto: ItemStack, amount: Int): Boolean {
        var cnt = 0
        for (it in player.inventory.contents) {
            if (it != null && isSameItem(it, proto)) {
                cnt += it.amount
                if (cnt >= amount) return true
            }
        }
        return false
    }

    private fun chestFreeSpaceFor(inventory: org.bukkit.inventory.Inventory, proto: ItemStack, amount: Int): Int {
        var free = 0
        val max = proto.maxStackSize
        for (i in 0 until inventory.size) {
            val s = inventory.getItem(i)
            if (s == null || s.type.isAir) {
                free += max
            } else if (s.isSimilar(proto) && s.amount < max) {
                free += (max - s.amount)
            }
            if (free >= amount) return free
        }
        return free
    }

    private fun removeFromPlayer(player: Player, proto: ItemStack, amount: Int): List<Pair<Int, ItemStack>> {
        var remaining = amount
        val removed = mutableListOf<Pair<Int, ItemStack>>() // slot, beforeClone
        for (slot in 0 until player.inventory.size) {
            val stack = player.inventory.getItem(slot) ?: continue
            if (!isSameItem(stack, proto)) continue
            val take = min(stack.amount, remaining)
            val before = stack.clone()
            stack.amount -= take
            remaining -= take
            if (stack.amount <= 0) player.inventory.setItem(slot, null) else player.inventory.setItem(slot, stack)
            removed += slot to before
            if (remaining <= 0) break
        }
        return removed
    }

    private fun restorePlayerInventory(player: Player, removed: List<Pair<Int, ItemStack>>) {
        for ((slot, before) in removed) {
            player.inventory.setItem(slot, before)
        }
    }

    private fun addToChest(inventory: org.bukkit.inventory.Inventory, proto: ItemStack, amount: Int): Int {
        var remaining = amount
        // önce benzer stack'leri doldur
        for (slot in 0 until inventory.size) {
            val s = inventory.getItem(slot) ?: continue
            if (!s.isSimilar(proto)) continue
            val space = s.maxStackSize - s.amount
            if (space <= 0) continue
            val add = min(space, remaining)
            s.amount += add
            remaining -= add
            inventory.setItem(slot, s)
            if (remaining <= 0) return 0
        }
        // sonra boş slotlara
        for (slot in 0 until inventory.size) {
            val s = inventory.getItem(slot)
            if (s == null || s.type.isAir) {
                val put = proto.clone().also { it.amount = min(it.maxStackSize, remaining) }
                inventory.setItem(slot, put)
                remaining -= put.amount
                if (remaining <= 0) return 0
            }
        }
        return remaining // 0 değilse yer kalmamış demek
    }

    private fun handleSell(player: Player, amount: Int, market: Market) {
        // Chest state/Inventory
        val chest = market.chestLocation.block.state as? Chest
            ?: run {
                player.sendMessage(LangManager.getMessage("invalid-chest"))
                return
            }
        val inv = chest.inventory

        // 0) VALIDASYON
        if (!playerHasAmountOf(player, market.itemStack, amount)) {
            val have = player.inventory.contents.filterNotNull().filter { isSameItem(it, market.itemStack) }.sumOf { it.amount }
            player.sendMessage(LangManager.getMessage("not-enough-item").replace("{stock}", have.toString()))
            return
        }

        val free = chestFreeSpaceFor(inv, market.itemStack, amount)
        if (free < amount) {
            player.sendMessage(LangManager.getMessage("chest-not-enough-space").replace("{space}", free.toString()))
            return
        }

        val totalPrice = market.price * amount
        val seller = Bukkit.getOfflinePlayer(market.owner)
        val sellerBal = economy.getBalance(seller)
        if (sellerBal + 1e-6 < totalPrice) { // double toleransı
            player.sendMessage(LangManager.getMessage("owner-not-enough-money"))
            return
        }

        // 1) Oyuncudan item çıkar (rollback için state tut)
        val removed = removeFromPlayer(player, market.itemStack, amount)

        // 2) Satıcıdan para çek
        val w = economy.withdrawPlayer(seller, totalPrice)
        if (!w.transactionSuccess()) {
            restorePlayerInventory(player, removed)
            player.sendMessage(LangManager.getMessage("economy-withdraw-failed"))
            return
        }

        // 3) Sandığa item ekle (teoride yer var; yine de kontrol)
        val leftover = addToChest(inv, market.itemStack, amount)
        if (leftover > 0) {
            // rollback: satıcıya iade + oyuncuya item geri
            economy.depositPlayer(seller, totalPrice)
            restorePlayerInventory(player, removed)
            player.sendMessage(LangManager.getMessage("chest-space-race-condition"))
            return
        }

        // 4) Oyuncuya para yatır
        val d = economy.depositPlayer(player, totalPrice)
        if (!d.transactionSuccess()) {
            // rollback best-effort
            economy.depositPlayer(seller, totalPrice)
            // oyuncudan alınan itemleri sandıktan geri çıkarmak zor olabilir; en azından bildir
            player.sendMessage(LangManager.getMessage("economy-deposit-failed-refunded"))
            return
        }

        // 5) Mesajlar
        val priceStr = String.format(java.util.Locale.US, "%.2f", totalPrice)
        val itemName = market.itemStack.itemMeta?.displayName ?: market.itemStack.type.name

        player.sendMessage(
            LangManager.getMessage("sell-success")
                .replace("{amount}", amount.toString())
                .replace("{item}", itemName)
                .replace("{price}", priceStr)
        )

        if (seller.isOnline) {
            seller.player?.sendMessage(
                LangManager.getMessage("bought-item")
                    .replace("{player}", player.name)
                    .replace("{amount}", amount.toString())
                    .replace("{item}", itemName)
                    .replace("{price}", priceStr)
            )
        }

        // 6) Tabela güncelle (Chest yeterli)
        val displayItem = inv.contents.filterNotNull().firstOrNull { it.isSimilar(market.itemStack) } ?: market.itemStack.clone()
        TabelaUpdate.updateSign(chest, displayItem, market.price)
    }
}
