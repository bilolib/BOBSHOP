package com.bilolib.bobshop.market

import org.bukkit.Location
import org.bukkit.inventory.ItemStack
import java.util.Locale
import java.util.UUID
enum class MarketMode { BUY, SELL }

data class Market(
    val owner: UUID,
    val chestLocation: Location,
    val signLocation: Location,
    var itemStack: ItemStack,   // var
    var amount: Int,
    var price: Double,
    var mode: MarketMode = MarketMode.BUY,
    var description: String? = null,
    var adminShop: Boolean = false,
    var descriptionEnabled: Boolean = true
)
object MarketManager {
    private val markets = mutableListOf<Market>()

    @Synchronized fun getAll(): List<Market> = markets.toList()
    @Synchronized fun setAll(list: Collection<Market>) { markets.clear(); markets.addAll(list) }

    @Synchronized
    fun save(market: Market) {
        markets.removeIf { it.chestLocation == market.chestLocation }
        markets.add(market)
    }

    @Synchronized fun removeByChest(chest: Location) = markets.removeIf { it.chestLocation == chest }
    @Synchronized fun removeBySign(sign: Location) = markets.removeIf { it.signLocation == sign }
    @Synchronized fun findByChest(chest: Location) = markets.firstOrNull { it.chestLocation == chest }
    @Synchronized fun findBySign(sign: Location) = markets.firstOrNull { it.signLocation == sign }

    fun canonicalMode(s: String?): MarketMode =
        try { MarketMode.valueOf((s ?: "BUY").trim().uppercase(Locale.ENGLISH)) }
        catch (_: Exception) { MarketMode.BUY }
}
