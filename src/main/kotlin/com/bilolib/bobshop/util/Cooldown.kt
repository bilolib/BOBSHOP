package com.bilolib.bobshop.util


/**
 * Basit ve güvenli cooldown yardımcı sınıfı.
 * - allow(key, ms): Aynı key için en az [ms] ms geçtiyse TRUE döner ve zamanı günceller.
 *   Geçmediyse FALSE döner (işlemi yoksay).
 *
 * Not: @Synchronized ile thread-safe.
 */
object Cooldown {
    private val last = HashMap<String, Long>()

    @Synchronized
    fun allow(key: String, ms: Long): Boolean {
        val now = System.currentTimeMillis()
        val prev = last[key] ?: 0L
        if (now - prev < ms) return false
        last[key] = now
        return true
    }

    @Synchronized
    fun clear(key: String) { last.remove(key) }

    @Synchronized
    fun clearAll() { last.clear() }
}
