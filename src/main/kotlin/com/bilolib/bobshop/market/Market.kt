package com.bilolib.bobshop.market

import org.bukkit.Location
import org.bukkit.inventory.ItemStack
import java.util.UUID

data class Market(
    val owner: UUID,
    val chestLocation: Location,
    val signLocation: Location,
    val itemStack: ItemStack,
    val amount: Int,
    var price: Double, // <-- var, admin menüden değişebilir
    var mode: String,  // "BUY" veya "SELL"
    var description: String? = null,
    var adminShop: Boolean = false,
    var descriptionEnabled: Boolean = true
)

object MarketManager {
    private val markets = mutableListOf<Market>()

    fun addMarket(market: Market) {
        markets.add(market)
    }

    fun removeMarket(location: Location) {
        markets.removeIf { it.chestLocation == location }
    }

    fun getAllMarkets(): List<Market> = markets
}