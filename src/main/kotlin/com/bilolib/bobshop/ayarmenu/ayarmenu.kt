package com.bilolib.bobshop.ayarmenu

import com.bilolib.bobshop.BOBShop
import com.bilolib.bobshop.manager.LangManager
import com.bilolib.bobshop.ayarmenu.more.AyarMenuHelper
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import java.io.File

class AyarMenu(private val plugin: BOBShop) : CommandExecutor, Listener {

    init {
        plugin.getCommand("bobshop")?.setExecutor(this)
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            return true
        }

        // Eğer "/bobshop reload" yazılmışsa reload yap
        if (args.isNotEmpty() && args[0].equals("reload", ignoreCase = true)) {
            plugin.reloadConfig()
            sender.sendMessage("§aBobShop: Config ve dosyalar reload edildi.")
            return true
        }

        // Menü aç
        openShopMenu(sender)
        return true
    }

    fun openShopMenu(player: Player) {
        // Double chest boyut = 54 slot
        val title = ChatColor.translateAlternateColorCodes('&', LangManager.getMessage("settings-menu-title"))
        val inventory: Inventory = Bukkit.createInventory(null, 54, title)

        // Kenarları siyah cam ile doldur
        val blackGlass = ItemStack(Material.BLACK_STAINED_GLASS_PANE).apply {
            val meta: ItemMeta = itemMeta!!
            meta.setDisplayName(" ")
            itemMeta = meta
        }

        // Üst ve alt sıra + yanlar
        val borderSlots = (0..8) + (9..44 step 9) + (17..53 step 9) + (45..53)
        for (slot in borderSlots.distinct()) {
            inventory.setItem(slot, blackGlass)
        }

        // Ortalar boş, 11. slot hologram toggle
        inventory.setItem(11, AyarMenuHelper.buildHologramToggleItem(plugin))
        inventory.setItem(12, AyarMenuHelper.buildDescriptionToggleItem(plugin))



        val langFile = File(BOBShop.instance.dataFolder, "lang/en.yml")
        if (!langFile.exists()) BOBShop.instance.saveResource("lang/en.yml", false)
        val langConfig = YamlConfiguration.loadConfiguration(langFile)
        val shopPrice = BOBShop.instance.config.getDouble("shop-price", 10.0)
        val priceItem = ItemStack(Material.NAME_TAG)
        val priceMeta = priceItem.itemMeta!!
        val name = langConfig.getString("shop-price.name")!!
        val loreList = langConfig.getStringList("shop-price.lore")
        val lore = loreList.map { ChatColor.translateAlternateColorCodes('&', it.replace("{price}", shopPrice.toString())) }
        priceMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name))
        priceMeta.lore = lore

        priceItem.itemMeta = priceMeta
        inventory.setItem(10, priceItem)





        val toggleItem = ItemStack(Material.CHEST)
        val toggleMeta = toggleItem.itemMeta ?: return
        val toggleName = langConfig.getString("shop-limit.name")?.let {
            ChatColor.translateAlternateColorCodes('&', it)
        }
        val shopLimit = BOBShop.instance.config.getInt("shop-limit", 5) // Config’den limit al
        val toggleLore = langConfig.getStringList("shop-limit.lore").map {
            ChatColor.translateAlternateColorCodes('&', it.replace("{limit}", shopLimit.toString()))
        }
        toggleMeta.setDisplayName(toggleName)
        toggleMeta.lore = toggleLore
        toggleItem.itemMeta = toggleMeta

        inventory.setItem(13, toggleItem)

        player.openInventory(inventory)
    }

}
