package com.bilolib.bobshop.market

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

object MarketBackupManager {

    private var taskId: Int = -1

    fun startAutoBackup(plugin: JavaPlugin, intervalMinutes: Long) {
        val initialDelay = 20L * 30L         // 30 sn sonra başlasın
        val periodTicks  = intervalMinutes * 60L * 20L

        File(plugin.dataFolder, "backups").apply { if (!exists()) mkdirs() }

        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, {
            runCatching { doBackup(plugin) }
                .onFailure { plugin.logger.severe("Backup failed: ${it.message}") }
        }, initialDelay, periodTicks)
    }

    private fun doBackup(plugin: JavaPlugin) {
        val dataFolder = plugin.dataFolder
        val backupsDir = File(dataFolder, "backups").apply { if (!exists()) mkdirs() }

        val ts = SimpleDateFormat("yyyyMMdd-HHmmss").format(Date())

        // --- sadece DB ---
        val dbSrc = File(File(dataFolder, "data"), "shop.db")
        if (dbSrc.exists()) {
            val dbDst = File(backupsDir, "shop-$ts.db")
            val dstPathSql = dbDst.absolutePath.replace("'", "''")
            MarketDatabase.execRaw("VACUUM INTO '$dstPathSql';")
        } else {
            plugin.logger.warning("DB not found: ${dbSrc.absolutePath}")
        }

        // eski yedekleri temizle
        val keep = plugin.config.getInt("backups.keep", 10).coerceAtLeast(1)
        cleanupOldBackups(backupsDir, keep)

        plugin.logger.info("Backup completed -> ${backupsDir.absolutePath}")
    }

    private fun cleanupOldBackups(backupsDir: File, keep: Int) {
        val files = backupsDir.listFiles()?.filter { it.isFile } ?: return
        val sorted = files.sortedByDescending { it.lastModified() }
        if (sorted.size <= keep) return
        sorted.drop(keep).forEach { runCatching { it.delete() } }
    }

    fun stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId)
            taskId = -1
        }
    }
}
