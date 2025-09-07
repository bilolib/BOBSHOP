package com.bilolib.bobshop.tabela

import com.bilolib.bobshop.BOBShop
import com.bilolib.bobshop.manager.LangManager
import com.bilolib.bobshop.market.Market
import com.bilolib.bobshop.market.MarketOlusturma
import com.bilolib.bobshop.util.displayMode
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.block.Chest
import org.bukkit.block.Sign
import java.util.concurrent.ConcurrentHashMap

object TabelaUpdate {

    // § kodlarıyla renk koruma (Adventure <-> legacy çevirisi)
    private val LEGACY = LegacyComponentSerializer.legacySection()

    // son yazılanı tut, aynı içeriği tekrar yazma
    private val lastSnapshot = ConcurrentHashMap<String, List<Component>>()

    private fun key(chest: Chest): String = buildString {
        append(chest.world.name).append(':')
            .append(chest.location.blockX).append(':')
            .append(chest.location.blockY).append(':')
            .append(chest.location.blockZ)
    }

    private fun isSameItem(a: org.bukkit.inventory.ItemStack, b: org.bukkit.inventory.ItemStack): Boolean {
        if (a.type != b.type) return false
        val am = a.itemMeta
        val bm = b.itemMeta
        val ac = am?.let { if (it.hasCustomModelData()) it.customModelData else null }
        val bc = bm?.let { if (it.hasCustomModelData()) it.customModelData else null }
        return ac == bc
        // daha sıkı istersen: return a.isSimilar(b)
    }

    private fun computeTotal(chest: Chest, proto: org.bukkit.inventory.ItemStack): Int =
        chest.inventory.contents.filterNotNull().filter { isSameItem(it, proto) }.sumOf { it.amount }

    /** Item görünen adını Component olarak üret; varsa custom name’i renkleriyle koru. */
    private fun itemDisplayName(item: org.bukkit.inventory.ItemStack): Component {
        val meta = item.itemMeta
        meta?.displayName()?.let { return it } // Adventure displayName varsa direkt
        if (meta?.hasDisplayName() == true) {
            return LEGACY.deserialize(meta.displayName) // eski string → Component
        }
        // varsayılan malzeme adı (çevirilebilir key)
        return Component.translatable(item.type.translationKey())
    }
    fun update(m: Market) {
        // Main thread garantisi
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(BOBShop.instance, Runnable { update(m) })
            return
        }

        val chest = m.chestLocation.block.state as? org.bukkit.block.Chest ?: return
        updateSign(chest, m.itemStack, m.price)
    }
    /** Ana giriş: tabelayı güncelle. Ana thread değilse kendini ana threade post eder. */
    fun updateSign(
        chest: Chest,
        item: org.bukkit.inventory.ItemStack,
        price: Double,
        ownerName: String? = null,
        amountText: String? = null
    ) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(BOBShop.instance, Runnable {
                updateSign(chest, item, price, ownerName, amountText)
            })
            return
        }

        val market = MarketOlusturma.markets.find { it.chestLocation == chest.location } ?: return
        val signBlock = chest.world.getBlockAt(market.signLocation)
        val sign = signBlock.state as? Sign ?: return

        // stok
        val totalAmount = computeTotal(chest, market.itemStack)

        // item adı (renkli korunarak)
        val itemComp = itemDisplayName(item)
        val itemLegacy = LEGACY.serialize(itemComp) // {item} placeholder’ına string basacağız

        // miktar & sahibi
        val displayAmount = when {
            market.adminShop -> LangManager.getMessage("Unlimited") // "∞"
            amountText != null -> amountText
            totalAmount <= 0 -> LangManager.getMessage("empty")
            else -> totalAmount.toString()
        }

        val ownerFromUuid = Bukkit.getOfflinePlayer(market.owner).name ?: "Unknown"
        val displayOwner = if (market.adminShop) {
            LangManager.getMessage("AdminShop")
        } else {
            ownerName ?: ownerFromUuid
        }


        val modeText = displayMode(market.mode)

        // fiyat
        val priceStr = String.format(java.util.Locale.US, "%.2f", price)

        // satırlar (Dil dosyası → placeholder doldur → Component)
        val l1 = LEGACY.deserialize(
            LangManager.getMessage("sign.1").replace("{owner}", displayOwner)
        )
        val l2 = LEGACY.deserialize(
            LangManager.getMessage("sign.2").replace("{item}", itemLegacy)
        )
        val l3 = LEGACY.deserialize(
            LangManager.getMessage("sign.3").replace("{price}", priceStr)
        )
        val l4 = LEGACY.deserialize(
            LangManager.getMessage("sign.4")
                .replace("{amount}", displayAmount)
                .replace("{mode}", modeText)
        )

        val lines = listOf(l1, l2, l3, l4)

        // değişmediyse yazma
        val k = key(chest)
        val last = lastSnapshot[k]
        if (last != null && last.size == 4 && (0..3).all { last[it] == lines[it] }) {
            return
        }

        // 1.20+ (Adventure) tarafı varsa onu kullan:
        try {
            val front = (sign as org.bukkit.block.Sign).getSide(org.bukkit.block.sign.Side.FRONT)
            front.line(0, l1); front.line(1, l2); front.line(2, l3); front.line(3, l4)
            sign.update(true, false)
        } catch (_: Throwable) {
            // Eski API fallback (setLine String)
            sign.setLine(0, LEGACY.serialize(l1))
            sign.setLine(1, LEGACY.serialize(l2))
            sign.setLine(2, LEGACY.serialize(l3))
            sign.setLine(3, LEGACY.serialize(l4))
            sign.update(true)
        }

        lastSnapshot[k] = lines
    }
}
