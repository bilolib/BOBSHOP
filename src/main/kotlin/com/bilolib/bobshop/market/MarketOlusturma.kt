package com.bilolib.bobshop.market

import com.bilolib.bobshop.BOBShop
import com.bilolib.bobshop.shopholo.ShopItemHolo
import com.bilolib.bobshop.manager.LangManager
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.block.Chest
import org.bukkit.block.DoubleChest
import org.bukkit.block.Sign
import org.bukkit.block.data.type.Chest as ChestData
import org.bukkit.block.data.type.WallSign
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import java.util.Locale
import java.util.UUID

class MarketOlusturma : Listener {

    private val economy: Economy = BOBShop.instance.server.servicesManager
        .getRegistration(Economy::class.java)?.provider
        ?: throw IllegalStateException("Vault Economy bulunamadı!")

    companion object {
        data class ShopSetup(val chest: InventoryHolder, val item: ItemStack)
        val waitingForPrice = mutableMapOf<UUID, ShopSetup>()
        val markets = mutableListOf<Market>()
    }

    @EventHandler
    fun onChestClick(e: PlayerInteractEvent) {
        if (e.action != Action.LEFT_CLICK_BLOCK) return
        val block = e.clickedBlock ?: return
        if (block.type != Material.CHEST) return

        val holder = block.state as? InventoryHolder ?: return
        val player = e.player

        // Bu sandıkta zaten market var mı?
        if (markets.any { it.chestLocation.block == block }) {
            return
        }

        // Market limiti
        val shopLimit = BOBShop.instance.config.getInt("shop-limit", 5)
        if (!player.hasPermission("bobshop.admin")) {
            val count = markets.count { it.owner == player.uniqueId }
            if (count >= shopLimit) {
                val msg = LangManager.getMessage("market-limit-reached")
                    .replace("{limit}", shopLimit.toString())
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg))
                return
            }
        }

        // Elde item yoksa
        val item = player.inventory.itemInMainHand
        if (item.type.isAir) return

        waitingForPrice[player.uniqueId] = ShopSetup(holder, item.clone())
        player.sendMessage(LangManager.getMessage("enter-price"))
    }

    @EventHandler
    fun onChat(e: AsyncPlayerChatEvent) {
        val player = e.player
        val uid = player.uniqueId
        val setup = waitingForPrice[uid] ?: return

        e.isCancelled = true
        val raw = e.message.trim()

        // İptal
        val cancelWord = LangManager.getMessage("cancel")
        if (raw.equals(cancelWord, ignoreCase = true)) {
            Bukkit.getScheduler().runTask(BOBShop.instance, Runnable {
                waitingForPrice.remove(uid)
                player.sendMessage(LangManager.getMessage("price-cancelled"))
                player.closeInventory()
            })
            return
        }

        // Fiyat parse (virgül desteği)
        val parsed = raw.replace(',', '.').toDoubleOrNull()
        val price = parsed?.takeIf { it.isFinite() && it > 0.0 && it <= 1_000_000_000.0 }
        if (price == null) {
            Bukkit.getScheduler().runTask(BOBShop.instance, Runnable {
                player.sendMessage(LangManager.getMessage("shop-price-invalid-number"))
            })
            return
        }

        // Ana thread: ekonomi ve kurulum
        Bukkit.getScheduler().runTask(BOBShop.instance, Runnable {
            val creationCost = BOBShop.instance.config.getDouble("shop-price", 10.0)

            // Bakiye kontrol
            if (!economy.has(player, creationCost)) {
                player.sendMessage(
                    LangManager.getMessage("not-enough-money")
                        .replace("{price}", String.format(Locale.US, "%.2f", creationCost))
                )
                return@Runnable
            }

            // Para çek
            val tx = economy.withdrawPlayer(player, creationCost)
            if (!tx.transactionSuccess()) {
                player.sendMessage(LangManager.getMessage("economy-withdraw-failed"))
                return@Runnable
            }

            // Chest resolve (DoubleChest güvenli)
            val chest = when (val h = setup.chest) {
                is Chest -> h
                is DoubleChest -> (h.leftSide as? Chest) ?: (h.rightSide as? Chest)
                else -> null
            }
            if (chest == null) {
                player.sendMessage(LangManager.getMessage("invalid-chest"))
                // İade
                economy.depositPlayer(player, creationCost)
                return@Runnable
            }

            waitingForPrice.remove(uid)
            // Tabela yerleştir ve marketi oluştur
            placeShopSign(player, chest, setup.item, price)
        })
    }

    private fun normalizeToWallSign(materialName: String): Material {
        // "OAK_SIGN" gelirse "OAK_WALL_SIGN" yap
        val upper = materialName.uppercase(Locale.ENGLISH)
        if (upper.endsWith("_WALL_SIGN")) {
            return Material.getMaterial(upper) ?: Material.OAK_WALL_SIGN
        }
        val candidate = if (upper.endsWith("_SIGN")) {
            upper.replace("_SIGN", "_WALL_SIGN")
        } else {
            "${upper}_WALL_SIGN"
        }
        return Material.getMaterial(candidate) ?: Material.OAK_WALL_SIGN
    }

    private fun placeShopSign(player: Player, chest: Chest, item: ItemStack, price: Double) {
        val chestData = chest.block.blockData as? ChestData ?: run {
            player.sendMessage(LangManager.getMessage("invalid-chest"))
            return
        }
        val chestFacing = chestData.facing
        val target = chest.block.getRelative(chestFacing)

        val signCfg = LangManager.getConfig().getString("sign") ?: "OAK_WALL_SIGN"
        val signMaterial = normalizeToWallSign(signCfg)

        if (!target.type.isAir && target.type != signMaterial) {
            player.sendMessage(LangManager.getMessage("invalid-sign"))
            return
        }

        target.type = signMaterial

        val wall = (target.blockData as? WallSign) ?: run {
            player.sendMessage(LangManager.getMessage("invalid-sign"))
            return
        }
        wall.facing = chestFacing
        target.blockData = wall

        val state = target.state as? Sign ?: run {
            player.sendMessage(LangManager.getMessage("invalid-sign"))
            return
        }

        val inv = (chest.inventory.holder as? DoubleChest)?.inventory ?: chest.inventory
        val totalAmount = inv.contents.filterNotNull().filter { it.isSimilar(item) }.sumOf { it.amount }

        val itemName = if (item.itemMeta?.hasDisplayName() == true)
            item.itemMeta!!.displayName
        else
            item.type.name.lowercase().replace("_", " ").replaceFirstChar { it.uppercase() }

        val amountText = if (totalAmount <= 0) LangManager.getMessage("empty") else totalAmount.toString()

        // Mode: enum
        val marketMode = MarketMode.BUY
        val modeText = LangManager.getMessage("market-modes.buy")
        val priceStr = String.format(Locale.US, "%.2f", price)

        val lines = (1..4).map { i ->
            val template = LangManager.getMessage("sign.$i")
            ChatColor.translateAlternateColorCodes(
                '&',
                template.replace("{owner}", player.name)
                    .replace("{item}", itemName)
                    .replace("{price}", priceStr)
                    .replace("{amount}", amountText)
                    .replace("{mode}", modeText)
            )
        }

        for (i in 0..3) state.setLine(i, lines[i])
        state.update(true)

        val newMarket = Market(
            owner = player.uniqueId,
            chestLocation = chest.location,
            signLocation = target.location,
            itemStack = item.clone().apply { amount = 1 },
            amount = totalAmount,
            price = price,
            mode = marketMode,
            description = null,
            adminShop = false
        )

        markets.removeIf { it.chestLocation == chest.location }
        markets.add(newMarket)
        MarketDatabase.saveOrUpdateMarket(newMarket)

        if (BOBShop.instance.config.getBoolean("hologram", true)) {
            ShopItemHolo.spawnOrUpdateHologram(BOBShop.instance, chest.location, item)
        } else {
            ShopItemHolo.removeHologram(chest.location)
        }

        player.sendMessage(LangManager.getMessage("market-created"))
    }
}
