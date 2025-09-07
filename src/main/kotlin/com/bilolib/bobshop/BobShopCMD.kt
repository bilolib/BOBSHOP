package com.bilolib.bobshop

import com.bilolib.bobshop.ayarmenu.AyarMenu
import com.bilolib.bobshop.manager.LangManager
import com.bilolib.bobshop.market.MarketOlusturma
import com.bilolib.bobshop.shopholo.ShopItemHolo
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class BobShopCommand(private val plugin: BOBShop) : CommandExecutor {

    init {
        plugin.getCommand("bobshop")?.setExecutor(this)
    }

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {

        // Admin izni kontrolü
        if (!sender.hasPermission("bobshop.admin")) {
            sender.sendMessage(LangManager.getMessage("no-permission"))
            return true
        }

        // Reload komutu
        if (args.isNotEmpty() && args[0].equals("reload", true)) {
            plugin.reloadConfig()
            LangManager.reload(plugin)

            // Hologram güncelleme
            val hologramEnabled = plugin.config.getBoolean("hologram", true)
            for (market in MarketOlusturma.markets) {
                if (hologramEnabled) {
                    ShopItemHolo.spawnOrUpdateHologram(plugin, market.chestLocation, market.itemStack)
                } else {
                    ShopItemHolo.removeHologram(market.chestLocation)
                }
            }

            sender.sendMessage(LangManager.getMessage("reload-success"))
            return true
        }

        // Menü açma (sadece oyuncular için)
        if (sender is Player) {
            val menu = AyarMenu(plugin)
            menu.openShopMenu(sender)
        }

        return true
    }
}
