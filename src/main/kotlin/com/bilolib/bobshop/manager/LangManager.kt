package com.bilolib.bobshop.manager

import org.bukkit.ChatColor
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.IOException
import java.nio.file.Files

object LangManager {

    private lateinit var mainConfig: FileConfiguration
    private lateinit var langConfig: FileConfiguration
    private var currentLang: String = "en"

    /**
     * Plugin açılırken config ve seçili dili yükler
     */
    fun load(plugin: JavaPlugin) {
        plugin.saveDefaultConfig()
        mainConfig = plugin.config

        currentLang = mainConfig.getString("lang", "en") ?: "en"
        loadLang(plugin, currentLang)
    }

    /**
     * Tek bir dil dosyasını yükler
     */
    private fun loadLang(plugin: JavaPlugin, lang: String) {
        val langFolder = File(plugin.dataFolder, "lang")
        if (!langFolder.exists()) langFolder.mkdirs()

        val file = File(langFolder, "$lang.yml")
        if (!file.exists()) {
            try {
                plugin.getResource("lang/$lang.yml")?.use { inputStream ->
                    Files.copy(inputStream, file.toPath())
                    plugin.logger.info("Lang file '$lang.yml' created in lang folder.")
                } ?: run {
                    plugin.logger.severe("Lang resource '$lang.yml' not found in plugin jar!")
                }
            } catch (ex: IOException) {
                plugin.logger.severe("Failed to save lang file: $lang.yml")
                ex.printStackTrace()
            }
        }

        langConfig = YamlConfiguration.loadConfiguration(file)
        plugin.logger.info("Lang file '$lang.yml' loaded.")
    }

    /**
     * Reload sadece seçili dili tekrar yükler
     */
    fun reloadLang(plugin: JavaPlugin) {
        loadLang(plugin, currentLang)
    }

    /**
     * Ana config döndürür
     */
    fun getConfig(): FileConfiguration = mainConfig

    /**
     * Tek satırlık mesaj döndürür
     * Eğer LangManager load edilmemişse hata vermesin, key'i döndürsün
     */
    fun getMessage(key: String): String {
        return if (::langConfig.isInitialized) {
            ChatColor.translateAlternateColorCodes('&', langConfig.getString(key, key) ?: key)
        } else {
            key
        }
    }

    /**
     * Liste halinde mesaj döndürür (lore için)
     */
    fun getMessageList(key: String): List<String> {
        return if (::langConfig.isInitialized) {
            langConfig.getStringList(key).map { ChatColor.translateAlternateColorCodes('&', it) }
        } else {
            listOf(key)
        }
    }
}
