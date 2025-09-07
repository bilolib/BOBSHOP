package com.bilolib.bobshop.shopholo

import com.bilolib.bobshop.BOBShop
import eu.decentsoftware.holograms.api.DHAPI
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

object ShopItemHolo {

    // baseName -> oluşturulan hologram adlarının listesi
    private val holograms = mutableMapOf<String, MutableList<String>>()

    private fun baseNameOf(loc: Location): String =
        "shop_holo_${loc.world.name}_${loc.blockX}_${loc.blockY}_${loc.blockZ}"

    private fun buildIconLine(item: ItemStack): String {
        val meta = item.itemMeta
        val type = item.type.name
        val cmd = if (meta != null && meta.hasCustomModelData()) "{CustomModelData:${meta.customModelData}}" else ""
        val amountPart = if (item.amount > 1) " ${item.amount}" else ""

        val base = if (cmd.isNotEmpty()) {
            "#ICON: $type$cmd$amountPart"
        } else {
            "#ICON: $type$amountPart"
        }

        return if (meta != null && meta.hasEnchants()) "$base !ENCHANTED" else base
    }

    private fun isHologramPluginPresent(): Boolean {
        return BOBShop.Companion.instance.server.pluginManager.isPluginEnabled("DecentHolograms")
    }

    private fun stripColorCodes(s: String): String {
        return s.replace(Regex("&[0-9a-frkoblmn]"), "")
    }

    private fun wrapMultiline(desc: String, maxChars: Int, maxLines: Int): List<String> {
        val words = desc.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        var currentLen = 0

        fun pushLine() {
            if (current.isNotEmpty()) {
                lines.add(current.toString().trim())
                current = StringBuilder()
                currentLen = 0
            }
        }

        for (word in words) {
            val visibleLen = stripColorCodes(word).length
            if (visibleLen > maxChars) {
                var remaining = word
                while (stripColorCodes(remaining).length > maxChars && lines.size < maxLines) {
                    var take = 0
                    var visible = 0
                    while (take < remaining.length && visible < maxChars) {
                        if (take + 1 < remaining.length && remaining[take] == '&' &&
                            remaining[take + 1].lowercaseChar() in "0123456789afrkoblmn"
                        ) {
                            take += 2
                        } else {
                            take += 1
                            visible += 1
                        }
                    }
                    lines.add(remaining.substring(0, take))
                    remaining = remaining.substring(take)
                }
                if (remaining.isNotEmpty()) {
                    current.append(remaining).append(' ')
                    currentLen = stripColorCodes(current.toString()).length
                }
                if (lines.size >= maxLines) break
                continue
            }

            if (currentLen + visibleLen > maxChars && current.isNotEmpty()) {
                pushLine()
                if (lines.size >= maxLines) break
            }
            current.append(word).append(' ')
            currentLen += visibleLen
        }

        if (lines.size < maxLines) pushLine()
        return lines.take(maxLines)
    }

    fun spawnOrUpdateHologram(
        plugin: JavaPlugin,
        chestLocation: Location,
        item: ItemStack,
        description: String? = null
    ) {
        if (!BOBShop.Companion.instance.config.getBoolean("hologram")) return
        if (!isHologramPluginPresent()) return

        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, Runnable {
                spawnOrUpdateHologram(plugin, chestLocation, item, description)
            })
            return
        }

        val baseName = baseNameOf(chestLocation)
        val itemLocation = chestLocation.clone().add(0.5, 1.5, 0.5)

        // eski hologramları temizle
        holograms[baseName]?.forEach { runCatching { DHAPI.removeHologram(it) } }
        holograms.remove(baseName)

        val namesList = mutableListOf<String>()

        val maxChars = BOBShop.Companion.instance.config.getInt("holo-description.maxlinechars", 12)
        val maxLines = BOBShop.Companion.instance.config.getInt("holo-description.maxlines", 3)
        val lineSpacing = BOBShop.Companion.instance.config.getDouble("holo-description.linespacing", 0.3)

// açıklamayı wrap et
        val wrapped = description?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { wrapMultiline(it, maxChars, maxLines) }
            ?: emptyList()

        if (wrapped.isNotEmpty()) {
            // açıklama hologramı, item’ın üstünde:
            // (toplam satır yüksekliği) + (1 satır ekstra boşluk) kadar yukarı
            val descStartLoc = itemLocation.clone().add(0.0, lineSpacing * wrapped.size + lineSpacing, 0.0)
            val descHolo = DHAPI.createHologram("${baseName}-desc", descStartLoc)
            for (line in wrapped) {
                DHAPI.addHologramLine(descHolo, ChatColor.translateAlternateColorCodes('&', line))
            }
            namesList.add("${baseName}-desc")
        }

// item hologramı (sabit konum)
        val itemHolo = DHAPI.createHologram("${baseName}-item", itemLocation)
        DHAPI.addHologramLine(itemHolo, buildIconLine(item))
        namesList.add("${baseName}-item")

        holograms[baseName] = namesList
    }

    fun removeAll() {
        if (!isHologramPluginPresent()) return
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(BOBShop.Companion.instance, Runnable { removeAll() })
            return
        }
        holograms.values.flatten().forEach { runCatching { DHAPI.removeHologram(it) } }
        holograms.clear()
    }

    fun removeHologram(chestLocation: Location) {
        if (!isHologramPluginPresent()) return
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(BOBShop.Companion.instance, Runnable { removeHologram(chestLocation) })
            return
        }
        val baseName = baseNameOf(chestLocation)
        holograms.remove(baseName)?.forEach { runCatching { DHAPI.removeHologram(it) } }
    }
}
