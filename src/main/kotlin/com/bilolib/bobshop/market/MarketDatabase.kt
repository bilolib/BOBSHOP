package com.bilolib.bobshop.market

import com.bilolib.bobshop.BOBShop
import com.bilolib.bobshop.ShopItemHolo
import com.bilolib.bobshop.manager.LangManager
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.Base64
import java.util.UUID

object MarketDatabase {
    private lateinit var connection: Connection

    // Cache mekanizması
    val cache = mutableMapOf<String, Market>()

    private fun marketKey(market: Market) =
        "${market.chestLocation.world.name}:${market.chestLocation.blockX}:${market.chestLocation.blockY}:${market.chestLocation.blockZ}"

    private fun marketKey(location: Location) =
        "${location.world.name}:${location.blockX}:${location.blockY}:${location.blockZ}"

    fun connect(dataFolder: File) {
        val dataDir = File(dataFolder, "data")
        if (!dataDir.exists()) dataDir.mkdirs()
        val dbFile = File(dataDir, "shop.db")
        if (!dbFile.exists()) dbFile.createNewFile()

        try {
            connection = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
            createTable()
            BOBShop.instance.logger.info("Veritabanı bağlantısı başarılı.")
        } catch (e: SQLException) {
            BOBShop.instance.logger.severe("Veritabanına bağlanırken hata oluştu: ${e.message}")
        }
    }

    private fun createTable() {
        try {
            connection.createStatement().use { stmt ->
                stmt.executeUpdate(
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
                    )
                    """.trimIndent()
                )
            }
        } catch (e: SQLException) {
            BOBShop.instance.logger.severe("Tablo oluşturulurken hata oluştu: ${e.message}")
        }
    }

    private fun itemToBase64(item: ItemStack): String {
        return try {
            ByteArrayOutputStream().use { byteOut ->
                BukkitObjectOutputStream(byteOut).use { out ->
                    out.writeObject(item)
                }
                Base64.getEncoder().encodeToString(byteOut.toByteArray())
            }
        } catch (ex: Exception) {
            BOBShop.instance.logger.severe("ItemStack kaydedilirken hata oluştu: ${ex.message}")
            ""
        }
    }

    private fun itemFromBase64(data: String): ItemStack? {
        return try {
            val bytes = Base64.getDecoder().decode(data)
            ByteArrayInputStream(bytes).use { byteIn ->
                BukkitObjectInputStream(byteIn).use { inp ->
                    inp.readObject() as? ItemStack
                }
            }
        } catch (ex: Exception) {
            BOBShop.instance.logger.severe("ItemStack yüklenirken hata oluştu: ${ex.message}")
            null
        }
    }

    // Async save/update
    fun saveOrUpdateMarket(market: Market) {
        cache[marketKey(market)] = market // Cache’e ekle

        Bukkit.getScheduler().runTaskAsynchronously(BOBShop.instance, Runnable {
            val sql = """
            INSERT INTO markets 
            (owner, chestX, chestY, chestZ, chestWorld, signX, signY, signZ, signWorld, item, amount, price, mode, description, adminShop)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(owner, chestX, chestY, chestZ, chestWorld) 
            DO UPDATE SET
                signX = excluded.signX,
                signY = excluded.signY,
                signZ = excluded.signZ,
                signWorld = excluded.signWorld,
                item = excluded.item,
                amount = excluded.amount,
                price = excluded.price,
                mode = excluded.mode,
                description = excluded.description,
                adminShop = excluded.adminShop
        """.trimIndent()

            connection.prepareStatement(sql).use { pstmt ->
                pstmt.setString(1, market.owner.toString())
                pstmt.setInt(2, market.chestLocation.blockX)
                pstmt.setInt(3, market.chestLocation.blockY)
                pstmt.setInt(4, market.chestLocation.blockZ)
                pstmt.setString(5, market.chestLocation.world.name)
                pstmt.setInt(6, market.signLocation.blockX)
                pstmt.setInt(7, market.signLocation.blockY)
                pstmt.setInt(8, market.signLocation.blockZ)
                pstmt.setString(9, market.signLocation.world.name)
                pstmt.setString(10, itemToBase64(market.itemStack))
                pstmt.setInt(11, market.amount)
                pstmt.setDouble(12, market.price)
                pstmt.setString(13, market.mode.uppercase())
                pstmt.setString(14, market.description ?: "")
                pstmt.setInt(15, if (market.adminShop) 1 else 0)
                pstmt.executeUpdate()
            }
        })
    }

    // Async remove
    fun removeMarket(market: Market) {
        cache.remove(marketKey(market))
        ShopItemHolo.removeHologram(market.chestLocation)

        val signMaterial: Material = try {
            Material.valueOf(BOBShop.instance.config.getString("sign")?.uppercase() ?: "OAK_WALL_SIGN")
        } catch (e: IllegalArgumentException) {
            Material.OAK_WALL_SIGN
        }

        val signBlock = market.signLocation.block
        if (signBlock.type == signMaterial) signBlock.type = Material.AIR

        Bukkit.getScheduler().runTaskAsynchronously(BOBShop.instance, Runnable {
            val sql = """
                DELETE FROM markets
                WHERE owner = ? AND chestX = ? AND chestY = ? AND chestZ = ? AND chestWorld = ?
            """.trimIndent()

            try {
                connection.prepareStatement(sql).use { pstmt ->
                    pstmt.setString(1, market.owner.toString())
                    pstmt.setInt(2, market.chestLocation.blockX)
                    pstmt.setInt(3, market.chestLocation.blockY)
                    pstmt.setInt(4, market.chestLocation.blockZ)
                    pstmt.setString(5, market.chestLocation.world.name)
                    pstmt.executeUpdate()
                }
            } catch (_: SQLException) {}
        })
    }

    fun loadMarkets(): List<Market> {
        val list = mutableListOf<Market>()
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT * FROM markets").use { rs ->
                while (rs.next()) {
                    val worldChest = Bukkit.getWorld(rs.getString("chestWorld")) ?: continue
                    val chestLoc = Location(
                        worldChest,
                        rs.getInt("chestX").toDouble(),
                        rs.getInt("chestY").toDouble(),
                        rs.getInt("chestZ").toDouble()
                    )

                    if (MarketOlusturma.markets.any { it.chestLocation == chestLoc }) continue

                    val worldSign = Bukkit.getWorld(rs.getString("signWorld")) ?: continue
                    val signLoc = Location(
                        worldSign,
                        rs.getInt("signX").toDouble(),
                        rs.getInt("signY").toDouble(),
                        rs.getInt("signZ").toDouble()
                    )

                    val owner = UUID.fromString(rs.getString("owner"))
                    val itemStack = itemFromBase64(rs.getString("item")) ?: ItemStack(Material.STONE)
                    val amount = rs.getInt("amount")
                    val price = rs.getDouble("price")
                    val description = rs.getString("description") ?: ""
                    val adminShop = rs.getInt("adminShop") == 1
                    val modeFromDb = rs.getString("mode") ?: LangManager.getMessage("market-modes.buy").uppercase()

                    val market = Market(
                        owner = owner,
                        chestLocation = chestLoc,
                        signLocation = signLoc,
                        itemStack = itemStack,
                        amount = amount,
                        price = price,
                        mode = modeFromDb.uppercase(),
                        description = description,
                        adminShop = adminShop
                    )

                    list.add(market)
                    cache[marketKey(market)] = market

                    ShopItemHolo.spawnOrUpdateHologram(
                        BOBShop.instance,
                        chestLoc,
                        itemStack,
                        description
                    )
                }
            }
        }
        return list
    }

    fun getMarket(location: Location): Market? = cache[marketKey(location)]
    fun saveMarketsSync() {
        try {
            // direkt veritabanı dosyasına yaz
        } catch (e: Exception) {
            BOBShop.instance.logger.severe("Market save error: ${e.message}")
        }
    }
    fun disconnect() {
        try {
            if (::connection.isInitialized) {
                connection.close()
                BOBShop.instance.logger.info("DATABASE CONNECTION CLOSED.")
            }
        } catch (e: SQLException) {
            BOBShop.instance.logger.severe("DATA BASE ERROR: ${e.message}")
        }
    }
}
