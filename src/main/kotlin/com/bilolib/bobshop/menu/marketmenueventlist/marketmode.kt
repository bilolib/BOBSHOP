package com.bilolib.bobshop.util

import com.bilolib.bobshop.manager.LangManager
import com.bilolib.bobshop.market.MarketMode
import java.util.Locale

/**
 * DB veya eski kayıtlar string olarak gelirse -> güvenli enum'a çevir.
 */
fun parseMode(s: String?): MarketMode {
    return when ((s ?: "BUY").trim().uppercase(Locale.ENGLISH)) {
        "SELL" -> MarketMode.SELL
        else   -> MarketMode.BUY
    }
}

/**
 * Enum'u oyuncuya gösterilecek dile çevir.
 */
fun displayMode(mode: MarketMode): String =
    when (mode) {
        MarketMode.BUY  -> LangManager.getMessage("market-modes.buy")
        MarketMode.SELL -> LangManager.getMessage("market-modes.sell")
    }
