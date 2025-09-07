package com.bilolib.bobshop

import org.bukkit.ChatColor
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

object Lang {
    private lateinit var messages: YamlConfiguration
    private var currentLang: String = "en"

    fun load(plugin: BOBShop) {
        // config.yml içinden language oku
        currentLang = plugin.config.getString("language", "en").toString()

        val file = File(plugin.dataFolder, "lang/$currentLang.yml")
        if (!file.exists()) {
            // Eğer seçilen dil yoksa fallback en.yml yükle
            plugin.logger.warning("Dil dosyası bulunamadı: $currentLang.yml, en.yml kullanılacak.")
            plugin.saveResource("lang/en.yml", false)
            messages = YamlConfiguration.loadConfiguration(
                File(plugin.dataFolder, "lang/en.yml")
            )
            return
        }

        // Dosya varsa yükle
        messages = try {
            YamlConfiguration.loadConfiguration(file)
        } catch (ex: Exception) {
            plugin.logger.severe("Lang dosyası ($currentLang) yüklenemedi: ${ex.message}")
            YamlConfiguration()
        }
        plugin.logger.info("Dil yüklendi: $currentLang")
    }

    fun get(path: String, default: String = path, colorCodes: Boolean = true): String {
        val msg = messages.getString(path) ?: default
        return if (colorCodes) ChatColor.translateAlternateColorCodes('&', msg) else msg
    }
}
