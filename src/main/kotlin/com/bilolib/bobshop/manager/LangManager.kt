package com.bilolib.bobshop.manager

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.ChatColor
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap

object LangManager {

    private lateinit var mainConfig: FileConfiguration
    private lateinit var langConfig: FileConfiguration
    private lateinit var fallbackConfig: FileConfiguration
    private var currentLang: String = "en"

    // Eksik anahtar uyarıları için "bir kez" log cache'i
    private val warnedKeys = ConcurrentHashMap.newKeySet<String>()

    fun load(plugin: JavaPlugin) {
        plugin.saveDefaultConfig()
        mainConfig = plugin.config

        currentLang = mainConfig.getString("lang", "en") ?: "en"
        // Fallback'ı en olarak hazırla
        fallbackConfig = loadLangFile(plugin, "en")
        langConfig = if (currentLang == "en") fallbackConfig else loadLangFile(plugin, currentLang)

        plugin.logger.info("Lang active='$currentLang' (fallback='en').")
    }

    fun reload(plugin: JavaPlugin) {
        // Hem ana config hem dilleri yenile
        plugin.reloadConfig()
        mainConfig = plugin.config
        currentLang = mainConfig.getString("lang", currentLang) ?: currentLang

        fallbackConfig = loadLangFile(plugin, "en")
        langConfig = if (currentLang == "en") fallbackConfig else loadLangFile(plugin, currentLang)
        warnedKeys.clear()
        plugin.logger.info("Lang reloaded. active='$currentLang'")
    }

    fun setLanguage(plugin: JavaPlugin, lang: String) {
        currentLang = lang
        langConfig = if (lang == "en") fallbackConfig else loadLangFile(plugin, lang)
        warnedKeys.clear()
        plugin.logger.info("Language switched to '$lang'")
    }

    fun getConfig(): FileConfiguration = mainConfig

    /** Basit kullanım */
    fun getMessage(key: String): String {
        val raw = findString(key) ?: keyWarn(key)
        // legacy & kodlarını çevir
        return ChatColor.translateAlternateColorCodes('&', raw)
    }

    /** Yer tutuculu kullanım: {name} → "Alex" */
    fun getMessage(key: String, vars: Map<String, String>): String {
        var s = findString(key) ?: keyWarn(key)
        for ((k, v) in vars) s = s.replace("{$k}", v)
        return ChatColor.translateAlternateColorCodes('&', s)
    }

    /** Liste (lore vb.) */
    fun getMessageList(key: String): List<String> {
        val list = when {
            ::langConfig.isInitialized && langConfig.isList(key) -> langConfig.getStringList(key)
            ::fallbackConfig.isInitialized && fallbackConfig.isList(key) -> fallbackConfig.getStringList(key)
            else -> listOf(keyWarn(key))
        }
        return list.map { ChatColor.translateAlternateColorCodes('&', it) }
    }

    /** Adventure Component isteyenler için (Paper 1.19+) */
    fun getComponent(key: String, vars: Map<String, String> = emptyMap()): Component {
        val serialized = getMessage(key, vars) // legacy renkli string
        return LegacyComponentSerializer.legacySection().deserialize(serialized)
    }

    // ---------- İç yardımcılar ----------

    private fun loadLangFile(plugin: JavaPlugin, lang: String): FileConfiguration {
        val langFolder = File(plugin.dataFolder, "lang").apply { if (!exists()) mkdirs() }
        val file = File(langFolder, "$lang.yml")

        if (!file.exists()) {
            val resPath = "lang/$lang.yml"
            val res = plugin.getResource(resPath)
            if (res != null) {
                try {
                    res.use { Files.copy(it, file.toPath()) }
                    plugin.logger.info("Lang file '$lang.yml' created.")
                } catch (ex: IOException) {
                    plugin.logger.severe("Failed to save lang file: $lang.yml (${ex.message})")
                }
            } else {
                // Kaynak yoksa, boş dosya oluşturmak yerine en'e düş
                plugin.logger.warning("Lang resource '$lang.yml' not found in JAR. Falling back to 'en'.")
                return if (::fallbackConfig.isInitialized) fallbackConfig else YamlConfiguration() // ilk yüklemede en
            }
        }
        return YamlConfiguration.loadConfiguration(file)
    }

    private fun findString(key: String): String? {
        if (::langConfig.isInitialized && langConfig.contains(key)) {
            return langConfig.getString(key)
        }
        if (::fallbackConfig.isInitialized && fallbackConfig.contains(key)) {
            return fallbackConfig.getString(key)
        }
        return null
    }

    private fun keyWarn(key: String): String {
        // Aynı anahtar için bir kez uyar
        if (warnedKeys.add(key)) {
            // Burada plugin'e erişim yok; çağıran loglamak isterse dışarıdan edebilir.
            // Sessiz kalmayı tercih ettim; istersen burada System.out ile de uyarı basabilirsin.
        }
        return key
    }
}
