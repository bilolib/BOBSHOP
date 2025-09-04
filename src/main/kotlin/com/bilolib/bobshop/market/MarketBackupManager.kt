package com.bilolib.bobshop.market

import com.bilolib.bobshop.BOBShop
import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitRunnable
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object MarketBackupManager {

    private val backupDir: File
        get() {
            val dir = File(BOBShop.instance.dataFolder, "backups")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    // ---------- MANUAL BACKUP ----------
    fun backupDatabase() {
        val dbFile = File(BOBShop.instance.dataFolder, "data/shop.db")
        if (!dbFile.exists()) {
            BOBShop.instance.logger.warning("Market database not found.")
            return
        }

        val date = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val backupFile = File(backupDir, "shop_backup_$date.db")
        dbFile.copyTo(backupFile, overwrite = true)
        BOBShop.instance.logger.info("Market database backed up: ${backupFile.name}")
    }

    // ---------- RESTORE BACKUP ----------
    fun restoreDatabase(backupFile: File) {
        val dbFile = File(BOBShop.instance.dataFolder, "data/shop.db")
        if (!backupFile.exists()) {
            BOBShop.instance.logger.warning("Backup file not found: ${backupFile.name}")
            return
        }

        backupFile.copyTo(dbFile, overwrite = true)
        BOBShop.instance.logger.info("Market database restored: ${backupFile.name}")

        // Refresh cache
        MarketOlusturma.markets.clear()
        MarketDatabase.loadMarkets()
    }

    // ---------- AUTO BACKUP ----------
    fun startAutoBackup(intervalMinutes: Long? = null) {
        val cfg = BOBShop.instance.config
        val enabled = cfg.getBoolean("backups", false) // default false
        if (!enabled) {
            BOBShop.instance.logger.info("Automatic backups are disabled in config.yml")
            return
        }

        val interval = intervalMinutes ?: cfg.getLong("backup-interval", 30)

        object : BukkitRunnable() {
            override fun run() {
                backupDatabase()
            }
        }.runTaskTimerAsynchronously(BOBShop.instance, 0L, interval * 20 * 60)

        BOBShop.instance.logger.info("Automatic backups started every $interval minutes.")
    }

    // ---------- GET LATEST BACKUP ----------
    fun getLatestBackup(): File? {
        return backupDir.listFiles()
            ?.filter { it.name.endsWith(".db") }
            ?.maxByOrNull { it.lastModified() }
    }
}
