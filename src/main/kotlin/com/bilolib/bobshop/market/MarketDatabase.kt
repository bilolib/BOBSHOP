package com.bilolib.bobshop.market

import com.bilolib.bobshop.BOBShop
import com.bilolib.bobshop.shopholo.ShopItemHolo
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.sql.ResultSet
import java.util.Base64
import java.util.Locale
import java.util.UUID

object MarketDatabase {
    private lateinit var ds: HikariDataSource

    // basit bellek cache (lokasyona göre)
    val cache = mutableMapOf<String, Market>()

    private fun marketKey(m: Market) =
        "${m.chestLocation.world.name}:${m.chestLocation.blockX}:${m.chestLocation.blockY}:${m.chestLocation.blockZ}"

    private fun marketKey(loc: Location) =
        "${loc.world.name}:${loc.blockX}:${loc.blockY}:${loc.blockZ}"

    // --- MODE parse (DB -> Enum) ---
    private fun parseMode(s: String?): MarketMode {
        val raw = (s ?: "BUY").trim().uppercase(Locale.ENGLISH)
        return if (raw == "BUY") MarketMode.BUY else if (raw == "SELL") MarketMode.SELL else MarketMode.BUY
    }

    fun connect(dataFolder: File) {
        val dataDir = File(dataFolder, "data").apply { if (!exists()) mkdirs() }
        val dbFile = File(dataDir, "shop.db")

        val cfg = HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}"
            maximumPoolSize = 4
            poolName = "BOBShop-SQLite"
            addDataSourceProperty("journalMode", "WAL")
            addDataSourceProperty("busy_timeout", "3000")
            addDataSourceProperty("foreign_keys", "on")
        }
        ds = HikariDataSource(cfg)

        exec("PRAGMA journal_mode = WAL;")
        exec("PRAGMA synchronous = NORMAL;")
        exec("PRAGMA foreign_keys = ON;")

        createTable()
        BOBShop.instance.logger.info("SQLite/Hikari bağlantısı hazır.")
    }

    fun disconnect() {
        try {
            if (::ds.isInitialized) {
                ds.close()
                BOBShop.instance.logger.info("DATABASE POOL CLOSED.")
            }
        } catch (e: Exception) {
            BOBShop.instance.logger.severe("DATA BASE ERROR: ${e.message}")
        }
    }

    /* ---------------- helpers ---------------- */

    private inline fun <T> withConn(block: (java.sql.Connection) -> T): T {
        ds.connection.use { c -> return block(c) }
    }

    private fun exec(sql: String, binder: (java.sql.PreparedStatement.() -> Unit)? = null) {
        withConn { c ->
            c.prepareStatement(sql).use { ps ->
                binder?.invoke(ps)
                ps.execute()
            }
        }
    }

    private fun <T> query(
        sql: String,
        binder: (java.sql.PreparedStatement.() -> Unit)? = null,
        mapper: (ResultSet) -> T
    ): List<T> {
        return withConn { c ->
            c.prepareStatement(sql).use { ps ->
                binder?.invoke(ps)
                ps.executeQuery().use { rs ->
                    val out = mutableListOf<T>()
                    while (rs.next()) out += mapper(rs)
                    out
                }
            }
        }
    }

    private fun createTable() {
        exec(
            """
            CREATE TABLE IF NOT EXISTS markets(
                owner TEXT NOT NULL,
                chestX INTEGER NOT NULL,
                chestY INTEGER NOT NULL,
                chestZ INTEGER NOT NULL,
                chestWorld TEXT NOT NULL,
                signX INTEGER,
                signY INTEGER,
                signZ INTEGER,
                signWorld TEXT,
                item TEXT NOT NULL,
                amount INTEGER NOT NULL,
                price REAL NOT NULL,
                mode TEXT DEFAULT 'BUY',
                description TEXT DEFAULT '',
                adminShop INTEGER DEFAULT 0,
                PRIMARY KEY(owner, chestX, chestY, chestZ, chestWorld)
            );
            """.trimIndent()
        )
    }

    /* ---------------- item <-> base64 ---------------- */

    private fun itemToBase64(item: ItemStack): String = try {
        ByteArrayOutputStream().use { byteOut ->
            BukkitObjectOutputStream(byteOut).use { out -> out.writeObject(item) }
            Base64.getEncoder().encodeToString(byteOut.toByteArray())
        }
    } catch (ex: Exception) {
        BOBShop.instance.logger.severe("ItemStack kaydedilirken hata: ${ex.message}")
        ""
    }

    private fun itemFromBase64(data: String): ItemStack? = try {
        val bytes = Base64.getDecoder().decode(data)
        ByteArrayInputStream(bytes).use { byteIn ->
            BukkitObjectInputStream(byteIn).use { inp ->
                inp.readObject() as? ItemStack
            }
        }
    } catch (ex: Exception) {
        BOBShop.instance.logger.severe("ItemStack yüklenirken hata: ${ex.message}")
        null
    }

    /* ---------------- CRUD ---------------- */

    fun saveOrUpdateMarket(market: Market) {
        // CACHE: enum 'mode' aynen saklanır
        cache[marketKey(market)] = market

        // DB (async) – Bukkit API KULLANMA
        Bukkit.getScheduler().runTaskAsynchronously(BOBShop.instance, Runnable {
            val sql = """
                INSERT INTO markets 
                (owner, chestX, chestY, chestZ, chestWorld, signX, signY, signZ, signWorld, item, amount, price, mode, description, adminShop)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(owner, chestX, chestY, chestZ, chestWorld) DO UPDATE SET
                    signX = excluded.signX,
                    signY = excluded.signY,
                    signZ = excluded.signZ,
                    signWorld = excluded.signWorld,
                    item = excluded.item,
                    amount = excluded.amount,
                    price = excluded.price,
                    mode = excluded.mode,
                    description = excluded.description,
                    adminShop = excluded.adminShop;
            """.trimIndent()
            exec(sql) {
                setString(1, market.owner.toString())
                setInt(2, market.chestLocation.blockX)
                setInt(3, market.chestLocation.blockY)
                setInt(4, market.chestLocation.blockZ)
                setString(5, market.chestLocation.world.name)
                setInt(6, market.signLocation.blockX)
                setInt(7, market.signLocation.blockY)
                setInt(8, market.signLocation.blockZ)
                setString(9, market.signLocation.world.name)
                setString(10, itemToBase64(market.itemStack))
                setInt(11, market.amount)
                setDouble(12, market.price)
                setString(13, market.mode.name)          // <-- Enum -> String
                setString(14, market.description ?: "")
                setInt(15, if (market.adminShop) 1 else 0)
            }
        })
    }

    fun removeMarket(market: Market) {
        // oyun içi temizlik main thread
        Bukkit.getScheduler().runTask(BOBShop.instance, Runnable {
            cache.remove(marketKey(market))

            // RAM listesinden de çıkar
            MarketOlusturma.markets.removeIf { it.chestLocation == market.chestLocation }
            ShopItemHolo.removeHologram(market.chestLocation)

            // sign’ı sil
            val signMaterial = runCatching {
                Material.valueOf(BOBShop.instance.config.getString("sign")?.uppercase() ?: "OAK_WALL_SIGN")
            }.getOrElse { Material.OAK_WALL_SIGN }

            val signBlock = market.signLocation.block
            if (signBlock.type == signMaterial) signBlock.type = Material.AIR
        })

        // DB silme işi async
        Bukkit.getScheduler().runTaskAsynchronously(BOBShop.instance, Runnable {
            val sql = """
                DELETE FROM markets
                WHERE owner = ? AND chestX = ? AND chestY = ? AND chestZ = ? AND chestWorld = ?
            """.trimIndent()
            exec(sql) {
                setString(1, market.owner.toString())
                setInt(2, market.chestLocation.blockX)
                setInt(3, market.chestLocation.blockY)
                setInt(4, market.chestLocation.blockZ)
                setString(5, market.chestLocation.world.name)
            }
        })
    }

    /**
     * Tüm marketleri DB’den okur, RAM’e ekler, hologramı basar ve liste döndürür.
     * (onEnable gibi main thread’den çağır.)
     */
    fun loadMarkets(): List<Market> {
        val rows = query("SELECT * FROM markets") { rs ->
            DbRow(
                owner = rs.getString("owner"),
                chestWorld = rs.getString("chestWorld"),
                chestX = rs.getInt("chestX"),
                chestY = rs.getInt("chestY"),
                chestZ = rs.getInt("chestZ"),
                signWorld = rs.getString("signWorld"),
                signX = rs.getInt("signX"),
                signY = rs.getInt("signY"),
                signZ = rs.getInt("signZ"),
                item = rs.getString("item"),
                amount = rs.getInt("amount"),
                price = rs.getDouble("price"),
                mode = rs.getString("mode"),
                description = rs.getString("description"),
                adminShop = rs.getInt("adminShop") == 1
            )
        }

        val list = mutableListOf<Market>()

        // main thread'de çağrıldığı varsayımıyla Bukkit API kullanıyoruz
        for (r in rows) {
            val worldChest = Bukkit.getWorld(r.chestWorld) ?: continue
            val chestLoc = Location(worldChest, r.chestX.toDouble(), r.chestY.toDouble(), r.chestZ.toDouble())

            // RAM'de varsa tekrar ekleme
            if (MarketOlusturma.markets.any { it.chestLocation == chestLoc }) continue

            val worldSign = r.signWorld?.let { Bukkit.getWorld(it) } ?: worldChest
            val signLoc = Location(worldSign, r.signX.toDouble(), r.signY.toDouble(), r.signZ.toDouble())

            val owner = UUID.fromString(r.owner)
            val itemStack = itemFromBase64(r.item) ?: ItemStack(Material.STONE)

            val modeEnum = parseMode(r.mode) // DB -> Enum
            val market = Market(
                owner = owner,
                chestLocation = chestLoc,
                signLocation  = signLoc,
                itemStack = itemStack,
                amount = r.amount,
                price  = r.price,
                mode   = modeEnum,
                description = r.description ?: "",
                adminShop   = r.adminShop
            )

            list += market
            cache[marketKey(market)] = market
            MarketOlusturma.markets.add(market)

            // hologram
            ShopItemHolo.spawnOrUpdateHologram(BOBShop.instance, chestLoc, itemStack, market.description)
        }

        return list
    }

    fun getMarket(location: Location): Market? = cache[marketKey(location)]

    fun saveMarketsSync() {
        // SQLite kendi flush’ını yapar; ekstra bir şey yok.
    }

    /* --- DB row taşıyıcı --- */
    private data class DbRow(
        val owner: String,
        val chestWorld: String,
        val chestX: Int, val chestY: Int, val chestZ: Int,
        val signWorld: String?,
        val signX: Int, val signY: Int, val signZ: Int,
        val item: String,
        val amount: Int,
        val price: Double,
        val mode: String?,
        val description: String?,
        val adminShop: Boolean
    )

    fun saveAllSync(markets: Iterable<Market>) {
        for (m in markets) {
            try {
                val sql = """
                INSERT INTO markets 
                (owner, chestX, chestY, chestZ, chestWorld, signX, signY, signZ, signWorld, item, amount, price, mode, description, adminShop)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(owner, chestX, chestY, chestZ, chestWorld) DO UPDATE SET
                    signX = excluded.signX,
                    signY = excluded.signY,
                    signZ = excluded.signZ,
                    signWorld = excluded.signWorld,
                    item = excluded.item,
                    amount = excluded.amount,
                    price = excluded.price,
                    mode = excluded.mode,
                    description = excluded.description,
                    adminShop = excluded.adminShop;
            """.trimIndent()

                exec(sql) {
                    setString(1, m.owner.toString())
                    setInt(2, m.chestLocation.blockX)
                    setInt(3, m.chestLocation.blockY)
                    setInt(4, m.chestLocation.blockZ)
                    setString(5, m.chestLocation.world.name)
                    setInt(6, m.signLocation.blockX)
                    setInt(7, m.signLocation.blockY)
                    setInt(8, m.signLocation.blockZ)
                    setString(9, m.signLocation.world.name)
                    setString(10, itemToBase64(m.itemStack))
                    setInt(11, m.amount)
                    setDouble(12, m.price)
                    setString(13, m.mode.name)            // Enum -> String
                    setString(14, m.description ?: "")
                    setInt(15, if (m.adminShop) 1 else 0)
                }
            } catch (t: Throwable) {
                BOBShop.instance.logger.severe("Market sync save failed @ ${m.chestLocation}: ${t.message}")
            }
        }
    }

    fun execRaw(sql: String) {
        withConn { c ->
            c.createStatement().use { st ->
                st.execute(sql)
            }
        }
    }
}
