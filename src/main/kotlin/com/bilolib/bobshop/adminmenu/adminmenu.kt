package com.bilolib.bobshop.adminmenu

import com.bilolib.bobshop.manager.LangManager
import com.bilolib.bobshop.market.Market
import com.bilolib.bobshop.market.MarketMode
import com.bilolib.bobshop.market.MarketOlusturma
import com.bilolib.bobshop.util.displayMode
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.block.Sign
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.Locale

class AdminMenu(private val plugin: JavaPlugin) : Listener {

    init {
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }

    @EventHandler
    fun onAdminInteract(event: PlayerInteractEvent) {
        val player = event.player
        val block = event.clickedBlock ?: return
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        val sign = block.state as? Sign ?: return

        if (!player.isSneaking) return
        if (!player.hasPermission("bobshop.admin")) return

        val market = MarketOlusturma.markets.find { it.signLocation.block == sign.block } ?: return

        event.isCancelled = true
        openAdminMenu(player, market)
    }

    private fun openAdminMenu(player: Player, market: Market) {
        val ownerName: String = runCatching {
            val op: OfflinePlayer = Bukkit.getOfflinePlayer(market.owner)
            op.name ?: "Unknown"
        }.getOrDefault("Unknown")

        val title = color(LangManager.getMessage("admin-menu-title").replace("{owner}", ownerName))
        val inv: Inventory = Bukkit.createInventory(null, 36, title)

        // Filler
        val glass = named(Material.GRAY_STAINED_GLASS_PANE, " ")
        for (i in 0 until inv.size) inv.setItem(i, glass)

        // Butonlar
        inv.setItem(35, named(Material.BARRIER,  LangManager.getMessage("remove-market-button")))
        inv.setItem(10, named(Material.NAME_TAG, LangManager.getMessage("change-price-button")))
        inv.setItem(11, named(Material.BUNDLE,   LangManager.getMessage("change-item-button")))

        // AdminShop toggle (19)
        val adminStatus = if (market.adminShop)
            LangManager.getMessage("admin-shop.enabled")
        else
            LangManager.getMessage("admin-shop.disabled")

        val adminButtonLore = LangManager.getMessageList("admin-shop.lore").map {
            color(it.replace("{Status}", adminStatus))
        }
        inv.setItem(19, named(Material.DIAMOND, LangManager.getMessage("admin-shop.name"), adminButtonLore))


// --- MODE butonu (12) ---
        val modeMaterial = if (market.mode == MarketMode.BUY) Material.EMERALD else Material.REDSTONE
        val currentModeText = displayMode(market.mode) // oyuncuya görünen çeviri (&aBuy / &cSell gibi)

        val modeItem = ItemStack(modeMaterial).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName(color(LangManager.getMessage("change-mode-button")))
                lore = LangManager.getMessageList("change-mode-lore").map {
                    color(it.replace("{mode}", currentModeText))
                }
            }
        }
        inv.setItem(12, modeItem)


        // Açıklama (13)
        inv.setItem(
            13,
            named(
                Material.PAINTING,
                LangManager.getMessage("change-description-button"),
                LangManager.getMessageList("change-description-button-lore").map { color(it) }
            )
        )

        AdminMenuEvent.openAdminMenus[player.uniqueId] = market
        player.openInventory(inv)
    }

    /* ================= Helpers ================= */

    private fun named(mat: Material, name: String, lore: List<String>? = null): ItemStack =
        ItemStack(mat).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName(color(name))
                if (lore != null) this.lore = lore.map { color(it) }
            }
        }

    private fun color(s: String) = ChatColor.translateAlternateColorCodes('&', s)

    private fun canonicalMode(s: String?): String {
        val raw = ChatColor.stripColor(s ?: "")?.trim()?.uppercase(Locale.ENGLISH) ?: ""
        return if (raw == "BUY" || raw == "SELL") raw else "BUY"
    }

    private fun displayMode(mode: String?): String = when (canonicalMode(mode)) {
        "BUY"  -> LangManager.getMessage("market-modes.buy")
        "SELL" -> LangManager.getMessage("market-modes.sell")
        else   -> LangManager.getMessage("market-modes.buy")
    }
}
