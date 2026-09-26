package pl.przekaznik.sms

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Reguła: KIEDY (nadawca / treść) → DOKĄD (lista numerów SMS). */
data class Rule(
    var id: String = UUID.randomUUID().toString(),
    var name: String = "Nowa reguła",
    var enabled: Boolean = true,
    /** Nadawcy oddzieleni przecinkami: numery lub nazwy (np. "ORLEN, 600123456"). Puste = dowolny. */
    var senders: String = "",
    /** Słowa kluczowe oddzielone przecinkami. Puste = dowolna treść. */
    var keywords: String = "",
    /** true = wszystkie słowa muszą wystąpić, false = wystarczy jedno. */
    var allKeywords: Boolean = false,
    var template: String = DEFAULT_TEMPLATE,
    /** Numery, na które SMS jest przekazywany. */
    var smsTargets: String = ""
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("enabled", enabled)
        .put("senders", senders).put("keywords", keywords).put("allKeywords", allKeywords)
        .put("template", template).put("smsTargets", smsTargets)

    fun summary(): String {
        val w = mutableListOf<String>()
        w += if (senders.isBlank()) "od: każdy" else "od: $senders"
        if (keywords.isNotBlank()) w += (if (allKeywords) "wszystkie: " else "któreś z: ") + keywords
        val targets = Rule.splitList(smsTargets)
        return w.joinToString(" | ") + "\n" + (if (targets.isEmpty()) "⚠ brak odbiorców" else "SMS → ${targets.joinToString(", ")}")
    }

    /** Czy SMS pasuje do reguły? Warunki nadawcy i treści łączone są przez I. */
    fun matches(sender: String, body: String): Boolean {
        if (!enabled) return false
        val senderOk = splitList(senders).let { list -> list.isEmpty() || list.any { senderMatches(sender, it) } }
        if (!senderOk) return false
        val keys = splitList(keywords)
        if (keys.isEmpty()) return true
        return if (allKeywords) keys.all { body.contains(it, ignoreCase = true) }
        else keys.any { body.contains(it, ignoreCase = true) }
    }

    fun render(sender: String, body: String, time: Long = System.currentTimeMillis()): String =
        template
            .replace("{nadawca}", sender)
            .replace("{tresc}", body)
            .replace("{czas}", SimpleDateFormat("dd.MM HH:mm", Locale("pl")).format(Date(time)))
            .replace("{regula}", name)

    companion object {
        const val DEFAULT_TEMPLATE = "SMS od {nadawca} ({czas}):\n{tresc}"

        fun fromJson(o: JSONObject) = Rule(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name"),
            enabled = o.optBoolean("enabled", true),
            senders = o.optString("senders"),
            keywords = o.optString("keywords"),
            allKeywords = o.optBoolean("allKeywords"),
            template = o.optString("template", DEFAULT_TEMPLATE),
            smsTargets = o.optString("smsTargets")
        )

        fun splitList(s: String): List<String> =
            s.split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }

        fun digits(s: String) = s.filter { it.isDigit() }

        /** Numery porównujemy po 9 ostatnich cyfrach (+48 600… = 600…), nazwy – po fragmencie tekstu. */
        fun senderMatches(sender: String, pattern: String): Boolean {
            val pd = digits(pattern)
            if (pd.length >= 6 && pd.length >= pattern.count { it.isLetterOrDigit() } - 1) {
                val sd = digits(sender)
                if (sd.isEmpty()) return false
                return sd.takeLast(9) == pd.takeLast(9)
            }
            return sender.contains(pattern, ignoreCase = true)
        }
    }
}

object Store {
    private const val PREFS = "przekaznik"
    private const val MAX_LOG = 80

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun readArray(ctx: Context, key: String): JSONArray =
        try { JSONArray(prefs(ctx).getString(key, "[]")) } catch (e: Exception) { JSONArray() }

    private fun writeArray(ctx: Context, key: String, arr: JSONArray) {
        prefs(ctx).edit().putString(key, arr.toString()).commit()
    }

    // ---------- Reguły ----------
    @Synchronized fun rules(ctx: Context): MutableList<Rule> {
        val a = readArray(ctx, "rules")
        return (0 until a.length()).map { Rule.fromJson(a.getJSONObject(it)) }.toMutableList()
    }

    @Synchronized fun saveRule(ctx: Context, rule: Rule) {
        val list = rules(ctx)
        val i = list.indexOfFirst { it.id == rule.id }
        if (i >= 0) list[i] = rule else list.add(rule)
        writeArray(ctx, "rules", JSONArray().apply { list.forEach { put(it.toJson()) } })
    }

    @Synchronized fun deleteRule(ctx: Context, id: String) {
        val list = rules(ctx).filter { it.id != id }
        writeArray(ctx, "rules", JSONArray().apply { list.forEach { put(it.toJson()) } })
    }

    // ---------- Dziennik ----------
    @Synchronized fun log(ctx: Context, msg: String) {
        val a = readArray(ctx, "log")
        val t = SimpleDateFormat("dd.MM HH:mm:ss", Locale("pl")).format(Date())
        val n = JSONArray().put("$t  $msg")
        for (i in 0 until minOf(a.length(), MAX_LOG - 1)) n.put(a.getString(i))
        writeArray(ctx, "log", n)
        android.util.Log.i("PrzekaznikSMS", msg)
    }

    @Synchronized fun logEntries(ctx: Context): List<String> {
        val a = readArray(ctx, "log")
        return (0 until a.length()).map { a.getString(it) }
    }

    @Synchronized fun clearLog(ctx: Context) = writeArray(ctx, "log", JSONArray())

    // ---------- Ustawienia ----------
    fun paused(ctx: Context) = prefs(ctx).getBoolean("paused", false)
    fun setPaused(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("paused", v).apply()
}
