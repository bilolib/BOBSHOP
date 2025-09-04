package com.bilolib.bobshop.adminmenu

import com.bilolib.bobshop.manager.LangManager
import com.bilolib.bobshop.market.Market
import com.bilolib.bobshop.market.MarketOlusturma
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.block.Sign
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

class AdminMenu(private val plugin: JavaPlugin) : Listener {

    init {
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }

    @EventHandler
    fun onAdminInteract(event: PlayerInteractEvent) {
        val player = event.player
        val block = event.clickedBlock ?: return
        if (block.state !is Sign) return
        if (event.action != Action.RIGHT_CLICK_BLOCK) return

        // shift basılı değilse çık
        if (!player.isSneaking) return

        // Admin yetkisi yoksa çık
        if (!player.hasPermission("bobshop.admin")) return

        // Market bul
        val adminMarket = MarketOlusturma.markets.find { it.signLocation.block == block } ?: return

        // Diğer eventleri engelle
        event.isCancelled = true

        // Admin menüyü aç
        openAdminMenu(player, adminMarket)
    }

    private fun openAdminMenu(player: Player, market: Market) {
        val title = ChatColor.translateAlternateColorCodes(
            '&',
            LangManager.getMessage("admin-menu-title").replace("{owner}", player.name)
        )

        val inv: Inventory = Bukkit.createInventory(null, 36, title)

        val glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ")
        for (i in 0 until inv.size) inv.setItem(i, glass)

        inv.setItem(35, createItem(Material.BARRIER, LangManager.getMessage("remove-market-button")))
        inv.setItem(10, createItem(Material.NAME_TAG, LangManager.getMessage("change-price-button")))
        inv.setItem(11, createItem(Material.BUNDLE, LangManager.getMessage("change-item-button")))

        val status = if (market.adminShop) LangManager.getMessage("admin-shop.enabled")
        else LangManager.getMessage("admin-shop.disabled")
        val button = ItemStack(Material.DIAMOND)
        val meta = button.itemMeta!!
        meta.setDisplayName(LangManager.getMessage("admin-shop.name"))
        meta.lore = LangManager.getMessageList("admin-shop.lore").map { ChatColor.translateAlternateColorCodes('&', it.replace("{Status}", status)) }
        button.itemMeta = meta
        inv.setItem(19, button)


        val modeItem = ItemStack(Material.EMERALD)
        val modeMeta = modeItem.itemMeta!!
        val buttonName = ChatColor.translateAlternateColorCodes('&', LangManager.getMessage("change-mode-button"))
        val buyKey = LangManager.getMessage("market-modes.buy").uppercase()
        val sellKey = LangManager.getMessage("market-modes.sell").uppercase()

        val currentMode = when (market.mode.uppercase()) {
            buyKey -> ChatColor.translateAlternateColorCodes('&', LangManager.getMessage("market-modes.buy"))
            sellKey -> ChatColor.translateAlternateColorCodes('&', LangManager.getMessage("market-modes.sell"))
            else -> "&7Unknown"
        }

        modeMeta.setDisplayName(buttonName)
        modeMeta.lore = LangManager.getMessageList("change-mode-lore").map { line ->
            ChatColor.translateAlternateColorCodes('&', line.replace("{mode}", currentMode))
        }
        modeItem.itemMeta = modeMeta
        inv.setItem(12, modeItem)

        val descItem = ItemStack(Material.PAINTING)
        val descMeta = descItem.itemMeta!!
        descMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', LangManager.getMessage("change-description-button")))
        descMeta.lore = LangManager.getMessageList("change-description-button-lore").map { line ->
            ChatColor.translateAlternateColorCodes('&', line)
        }
        descItem.itemMeta = descMeta
        inv.setItem(13, descItem)

        // Tüm yetkililer admin menüyü açabiliyor, market sahibi olma kontrolü kaldırıldı
        AdminMenuEvent.openAdminMenus[player.uniqueId] = market

        player.openInventory(inv)
    }

    private fun createItem(material: Material, name: String, lore: List<String>? = null): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name))
            if (lore != null) {
                meta.lore = lore.map { ChatColor.translateAlternateColorCodes('&', it) }
            }
            item.itemMeta = meta
        }
        return item
    }
}
