package com.bilolib.bobshop.market

import com.bilolib.bobshop.BOBShop
import com.bilolib.bobshop.ShopItemHolo
import com.bilolib.bobshop.manager.LangManager
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.block.*
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
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID

class MarketOlusturma : Listener {

    private val economy: Economy = BOBShop.instance.server.servicesManager.getRegistration(Economy::class.java)?.provider
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

        // Check if market already exists on this chest
        if (markets.any { it.chestLocation.block == block }) {
            player.sendMessage(LangManager.getMessage("market-already-exists"))
            return
        }

        // -------------------------------
        // Market limit kontrolü
        // -------------------------------
        val shopLimit = BOBShop.instance.config.getInt("shop-limit", 5)
        val isAdmin = player.hasPermission("bobshop.admin")
        if (!isAdmin) {
            val playerMarketCount = markets.count { it.owner == player.uniqueId }
            if (playerMarketCount >= shopLimit) {
                val message = LangManager.getMessage("market-limit-reached").replace("{limit}", shopLimit.toString())
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', message))
                return
            }
        }

        val item = player.inventory.itemInMainHand
        if (item.type == Material.AIR) return

        waitingForPrice[player.uniqueId] = ShopSetup(holder, item.clone())
        player.sendMessage(LangManager.getMessage("enter-price"))
    }

    @EventHandler
    fun onChat(e: AsyncPlayerChatEvent) {
        val player = e.player
        val setup = waitingForPrice.remove(player.uniqueId) ?: return

        e.isCancelled = true
        val price = e.message.toDoubleOrNull()
        if (price == null || price <= 0) {
            player.sendMessage(LangManager.getMessage("invalid-number"))
            return
        }

        val marketCreationCost = BOBShop.instance.config.getDouble("shop-price", 10.0)
        if (economy.getBalance(player) < marketCreationCost) {
            player.sendMessage(LangManager.getMessage("not-enough-money").replace("{price}", marketCreationCost.toString()))
            return
        }

        economy.withdrawPlayer(player, marketCreationCost)

        Bukkit.getScheduler().runTask(BOBShop.instance, Runnable {
            val chest = when (setup.chest) {
                is Chest -> setup.chest
                is DoubleChest -> (setup.chest as DoubleChest).leftSide as? Chest
                    ?: (setup.chest as DoubleChest).rightSide as? Chest
                    ?: run { player.sendMessage(LangManager.getMessage("invalid-chest")); return@Runnable }
                else -> { player.sendMessage(LangManager.getMessage("invalid-chest")); return@Runnable }
            }

            placeShopSign(player, chest, setup.item, price)
        })
    }

    private fun placeShopSign(player: Player, chest: Chest, item: ItemStack, price: Double) {
        val chestData = chest.block.blockData as? ChestData ?: run {
            player.sendMessage(LangManager.getMessage("invalid-chest"))
            return
        }

        val chestFacing = chestData.facing
        val signBlock = chest.block.getRelative(chestFacing)

        // Sign material'ı almak için config'den veya default
        val signTypeName = LangManager.getConfig().getString("sign") ?: "OAK_WALL_SIGN"
        val signMaterial: Material = Material.getMaterial(signTypeName.uppercase()) ?: Material.OAK_WALL_SIGN

        signBlock.type = signMaterial

        // WallSign verisini ayarlama
        (signBlock.blockData as? WallSign)?.let {
            it.facing = chestFacing
            signBlock.blockData = it
        } ?: run {
            player.sendMessage(LangManager.getMessage("invalid-sign"))
            return
        }

        val state = signBlock.state as Sign

        val inventory = (chest.inventory.holder as? DoubleChest)?.inventory ?: chest.inventory
        val totalAmount = inventory.contents.filterNotNull().filter { it.isSimilar(item) }.sumOf { it.amount }

        val itemName = if (item.hasItemMeta() && item.itemMeta?.hasDisplayName() == true) {
            item.itemMeta.displayName
        } else {
            item.type.name.lowercase().replace("_", " ").replaceFirstChar { it.uppercase() }
        }
        val amountText = if (totalAmount <= 0) LangManager.getMessage("empty") else totalAmount.toString()

        val BUY_KEY = LangManager.getMessage("market-modes.buy").uppercase()
        val SELL_KEY = LangManager.getMessage("market-modes.sell").uppercase()

        val marketMode = BUY_KEY // default olarak Buy

        // Sign satırlarını dil dosyasından al
        val lines = (1..4).map { i ->
            val template = LangManager.getMessage("sign.$i")
            val modeText = when (marketMode.uppercase()) {
                BUY_KEY -> LangManager.getMessage("market-modes.buy")
                SELL_KEY -> LangManager.getMessage("market-modes.sell")
                else -> LangManager.getMessage("market-modes.buy")
            }
            template.replace("{owner}", player.name)
                .replace("{item}", itemName)
                .replace("{price}", price.toString())
                .replace("{amount}", amountText)
                .replace("{mode}", modeText)
                .let { ChatColor.translateAlternateColorCodes('&', it) }
        }

        for (i in 0..3) state.setLine(i, lines[i])
        state.update(true)

        val newMarket = Market(player.uniqueId, chest.location, signBlock.location, item.clone(), totalAmount, price, marketMode)
        markets.removeIf { it.chestLocation.block == chest.block }
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
