package com.bilolib.bobshop

import eu.decentsoftware.holograms.api.DHAPI
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

object ShopItemHolo {

    // Her chest için tek hologram adı tutacağız
    private val holograms = mutableMapOf<Location, MutableList<String>>()

    private fun buildIconLine(item: ItemStack): String {
        val meta = item.itemMeta
        val base = if (meta != null && meta.hasCustomModelData()) {
            "#ICON: ${item.type.name}{CustomModelData:${meta.customModelData}} ${item.amount}"
        } else {
            "#ICON: ${item.type.name} ${item.amount}"
        }

        // Eğer büyülü ise !ENCHANTED ekle
        return if (meta != null && meta.hasEnchants()) {
            "$base !ENCHANTED"
        } else {
            base
        }
    }

    private fun isHologramPluginPresent(): Boolean {
        return BOBShop.instance.server.pluginManager.isPluginEnabled("DecentHolograms")
    }

    fun spawnOrUpdateHologram(
        plugin: JavaPlugin,
        chestLocation: Location,
        item: ItemStack,
        description: String? = null
    ) {
        if (!BOBShop.instance.config.getBoolean("hologram")) return
        if (!isHologramPluginPresent()) return // Plugin yoksa direkt çık
         // config false ise hiç hologram spawn etme


        val baseName = "shop_holo_${chestLocation.world.name}_${chestLocation.blockX}_${chestLocation.blockY}_${chestLocation.blockZ}"
        val itemLocation = chestLocation.clone().add(0.5, 1.5, 0.5) // Item hologramının konumu biraz daha yüksek

        // Eski hologram varsa kaldır
        holograms[chestLocation]?.forEach {
            runCatching { DHAPI.removeHologram(it) }
        }
        holograms.remove(chestLocation)

        // Tek hologram oluştur
        val holo = DHAPI.createHologram(baseName, itemLocation)
        val namesList = mutableListOf(baseName)

        // ---- Açıklama satırları (item üstünde, y:1, satır arası 0.5) ----
        description?.trim()?.takeIf { it.isNotEmpty() }?.let { desc ->
            val maxChars = BOBShop.instance.config.getInt("holo-description.maxlinechars", 12)
            val maxLines = BOBShop.instance.config.getInt("holo-description.maxlines", 3)

            val words = desc.split(" ")
            val wrapped = mutableListOf<String>()
            var current = StringBuilder()
            var currentLen = 0

            for (word in words) {
                val wlen = word.length
                if (currentLen + wlen > maxChars && current.isNotEmpty()) {
                    wrapped.add(current.toString().trim())
                    if (wrapped.size >= maxLines) break
                    current = StringBuilder()
                    currentLen = 0
                }
                current.append(word).append(' ')
                currentLen += wlen
            }

            if (current.isNotEmpty() && wrapped.size < maxLines) wrapped.add(current.toString().trim())

            // Satırları item hologramının üstünde y:1 ile başlat ve 0.5 arayla ekle
            for ((i, line) in wrapped.withIndex()) {
                val yOffset = 0.5 + 0.3 * (wrapped.size - i - 1) // en üst satır y:1
                val lineLocation = itemLocation.clone().add(0.0, yOffset, 0.0)
                val lineHolo = DHAPI.createHologram("${baseName}-desc$i", lineLocation)
                DHAPI.addHologramLine(lineHolo, ChatColor.translateAlternateColorCodes('&', line))
                namesList.add("${baseName}-desc$i")
            }
        }

        // ---- En altta ITEM satırı ----
        DHAPI.addHologramLine(holo, buildIconLine(item))

        // Listeyi kaydet
        holograms[chestLocation] = namesList
    }

    fun removeAll() {
        if (!isHologramPluginPresent()) return
        try {
            val dhapi = Class.forName("eu.decentsoftware.holograms.api.DHAPI")
            val removeMethod = dhapi.getMethod("removeHologram", String::class.java)
            holograms.values.flatten().forEach { runCatching { removeMethod.invoke(null, it) } }
            holograms.clear()
        } catch (_: Exception) {}
    }

    fun removeHologram(chestLocation: Location) {
        if (!isHologramPluginPresent()) return
        try {
            val dhapi = Class.forName("eu.decentsoftware.holograms.api.DHAPI")
            val removeMethod = dhapi.getMethod("removeHologram", String::class.java)
            holograms[chestLocation]?.forEach { runCatching { removeMethod.invoke(null, it) } }
            holograms.remove(chestLocation)
        } catch (_: Exception) {}
    }
}
