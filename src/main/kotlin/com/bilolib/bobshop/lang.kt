package com.bilolib.bobshop

import org.bukkit.ChatColor
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

object Lang {
    private lateinit var messages: YamlConfiguration

    fun load(plugin: BOBShop) {
        val file = File(plugin.dataFolder, "lang/en.yml")
        if (!file.exists()) plugin.saveResource("lang/en.yml", false)
        messages = YamlConfiguration.loadConfiguration(file)
    }
    /**
     * @param path Config içindeki key
     * @param default Eğer config’te yoksa bu değeri döner
     * @param colorCodes Eğer true ise & renk kodlarını ChatColor ile çevirir
     */
    fun get(path: String, default: String = path, colorCodes: Boolean = true): String {
        val msg = messages.getString(path) ?: default
        return if (colorCodes) ChatColor.translateAlternateColorCodes('&', msg) else msg
    }
}
