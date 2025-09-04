package com.bilolib.bobshop.tabela

import com.bilolib.bobshop.BOBShop
import com.bilolib.bobshop.manager.LangManager
import com.bilolib.bobshop.market.MarketOlusturma
import org.bukkit.ChatColor
import org.bukkit.block.Chest
import org.bukkit.block.Sign
import org.bukkit.inventory.ItemStack

object TabelaUpdate {

    /**
     * ownerName ve amountText opsiyonel.
     * AdminShop için kullanılabilir.
     */
    private fun isSameItem(a: ItemStack, b: ItemStack): Boolean {
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
    fun updateSign(
        chest: Chest,
        item: ItemStack,
        price: Double,
        ownerName: String? = null,
        amountText: String? = null
    ) {
        val market = MarketOlusturma.markets.find { it.chestLocation == chest.location } ?: return
        val signBlock = chest.world.getBlockAt(market.signLocation)
        if (signBlock.state !is Sign) return
        val sign = signBlock.state as Sign

        // Sandıktaki toplam miktarı hesapla
        val totalAmount = chest.inventory.contents
            .filterNotNull()
            .filter { isSameItem(it, market.itemStack) }
            .sumOf { it.amount }

        // Item adı
        val itemName = if (item.hasItemMeta() && item.itemMeta?.hasDisplayName() == true) {
            item.itemMeta.displayName
        } else {
            item.type.name.lowercase().replace("_", " ").replaceFirstChar { it.uppercase() }
        }

        // AdminShop ise özel gösterimler
        val displayAmount = if (market.adminShop) LangManager.getMessage("Unlimited")
        else amountText ?: if (totalAmount <= 0) LangManager.getMessage("empty") else totalAmount.toString()

        val displayOwner = if (market.adminShop) LangManager.getMessage("AdminShop")
        else ownerName ?: BOBShop.instance.server.getPlayer(market.owner)?.name ?: "Unknown"

        val modeText = when (market.mode.uppercase()) {
            LangManager.getMessage("market-modes.buy").uppercase() -> LangManager.getMessage("market-modes.buy")
            LangManager.getMessage("market-modes.sell").uppercase() -> LangManager.getMessage("market-modes.sell")
            else -> LangManager.getMessage("market-modes.buy")
        }

        // Sign satırlarını LangManager üzerinden alıyoruz
        val signLines = listOf(
            LangManager.getMessage("sign.1").replace("{owner}", displayOwner),
            LangManager.getMessage("sign.2").replace("{item}", itemName),
            LangManager.getMessage("sign.3").replace("{price}", price.toString()),
            LangManager.getMessage("sign.4")
                .replace("{amount}", displayAmount)
                .replace("{mode}", modeText)
        )

        for (i in 0..3) {
            sign.setLine(i, ChatColor.translateAlternateColorCodes('&', signLines[i]))
        }

        sign.update(true)
    }
}
