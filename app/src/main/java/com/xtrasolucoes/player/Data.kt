/*
 * xTra Player
 * Desenvolvido por xTra Soluções
 */
package com.xtrasolucoes.player

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer

object AppInfo {
    const val NAME = "xTra Player"
    const val AUTHOR = "xTra Soluções"
    const val VERSION = "3.0"
}

data class Provider(val id: String, val name: String, val type: String, val server: String, val user: String, val pass: String) {
    val base: String get() = server.trim().trimEnd('/')
}
data class Profile(val id: String, val name: String, val kids: Boolean)
data class Live(val id: String, val name: String, val logo: String, val group: String, val url: String, val adult: Boolean) {
    val display: String get() = name.replace(Regex("\\s*\\((\\d{3,4}[pi])\\)"), "").trim()
}
data class Vod(val id: String, val name: String, val poster: String, val category: String, val url: String, val adult: Boolean)
data class Serie(val id: String, val name: String, val cover: String, val category: String, val plot: String, val adult: Boolean)
data class Episode(val title: String, val season: Int, val num: Int, val url: String)

class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("iptv", Context.MODE_PRIVATE)
    private val builtin = Provider("builtin", "Lista padrão", "builtin", "", "", "")

    var providers: List<Provider>
        get() = runCatching {
            val a = JSONArray(sp.getString("providers", "[]"))
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Provider(o.getString("id"), o.getString("name"), o.getString("type"), o.optString("server"), o.optString("user"), o.optString("pass"))
            }
        }.getOrDefault(emptyList()).let { listOf(builtin) + it }
        set(v) {
            val arr = JSONArray(v.filter { it.id != "builtin" }.map {
                JSONObject().put("id", it.id).put("name", it.name).put("type", it.type)
                    .put("server", it.server).put("user", it.user).put("pass", it.pass)
            })
            sp.edit().putString("providers", arr.toString()).apply()
        }

    var profiles: List<Profile>
        get() = runCatching {
            val a = JSONArray(sp.getString("profiles", "[]"))
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Profile(o.getString("id"), o.getString("name"), o.optBoolean("kids"))
            }
        }.getOrDefault(emptyList()).ifEmpty { listOf(Profile("p1", "Principal", false)) }
        set(v) {
            val arr = JSONArray(v.map { JSONObject().put("id", it.id).put("name", it.name).put("kids", it.kids) })
            sp.edit().putString("profiles", arr.toString()).apply()
        }

    var activeProvider: String
        get() = sp.getString("ap", "builtin") ?: "builtin"
        set(v) { sp.edit().putString("ap", v).apply() }
    var pin: String
        get() = sp.getString("pin", "") ?: ""
        set(v) { sp.edit().putString("pin", v).apply() }
    var parental: Boolean
        get() = sp.getBoolean("parental", true)
        set(v) { sp.edit().putBoolean("parental", v).apply() }
    var screenMode: String
        get() = sp.getString("screen_mode", "FIT") ?: "FIT"
        set(v) { sp.edit().putString("screen_mode", v).apply() }

    fun favs(profileId: String): Set<String> = sp.getStringSet("fav_$profileId", emptySet()) ?: emptySet()
    fun setFavs(profileId: String, s: Set<String>) { sp.edit().putStringSet("fav_$profileId", s.toSet()).apply() }
}

object Net {
    fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 40000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) xTra Player")
        return c.inputStream.bufferedReader().use { it.readText() }
    }
}

object Brand {
    private fun r(p: String) = Regex(p, RegexOption.IGNORE_CASE)
    private val rules = listOf(
        "Band" to r("^band"),
        "Record" to r("^record"),
        "SBT" to r("^sbt"),
        "Globo" to r("^(rede globo|tv globo|globo|gnt|gloob|sportv|combate|multishow)"),
        "Telecine" to r("^telecine"),
        "RedeTV!" to r("^rede ?tv"),
        "CNT" to r("^(rede )?cnt"),
        "Públicos" to r("^(tv brasil|canal gov|canal educacao|canal saude|tv cultura|rede minas|tve|canal futura|futura|sesc tv|nbr)"),
        "Legislativo e Justiça" to r("^(tv camara|tv senado|tv justica|tv assembleia)"),
        "ESPN" to r("^espn"),
        "Premiere" to r("^premiere"),
        "HBO" to r("^(hbo|max\\b)"),
        "Disney" to r("^(disney|playhouse)"),
        "Discovery" to r("^(discovery|tlc|animal planet|hgtv|food network)"),
        "A+E" to r("^(a&e|history|lifetime)"),
        "Warner" to r("^(warner|tnt|tcm|space)"),
        "Paramount" to r("^paramount"),
        "Sony" to r("^(sony|axn)"),
        "MTV" to r("mtv"),
        "Comedy Central" to r("^(comedy central|south park)"),
        "Nickelodeon" to r("^nick"),
        "Cartoon" to r("^(cartoon|tooncast|boomerang)"),
        "Pluto TV" to r("pluto tv"),
    )
    private val order = rules.map { it.first }
    private val adultRx = r("adult|xxx|\\+18|18\\+|erotic|porn|sexy|playboy|sexprive|sex hot|hot ?club|venus|brasileirinhas")

    fun norm(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
    private fun clean(name: String) = norm(name).replace(r("^[\\[(|\\s]*(br|brasil|bra|pt)\\s*[\\])|:\\-]+\\s*"), "").trim()
    fun of(name: String, fallback: String): String {
        val n = clean(name)
        for ((g, rx) in rules) if (rx.containsMatchIn(n)) return g
        return fallback.ifBlank { "Outros" }
    }
    fun rank(g: String): Int { val i = order.indexOf(g); return if (i < 0) 999 else i }
    fun isAdult(vararg s: String) = s.any { adultRx.containsMatchIn(norm(it)) }
}

object M3u {
    private val attr = Regex("([\\w-]+)=\"([^\"]*)\"")
    fun parse(text: String): List<Live> {
        val out = mutableListOf<Live>()
        var info: String? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTINF")) info = line
            else if (line.startsWith("#")) continue
            else {
                val i = info
                if (i != null) {
                    val a = attr.findAll(i).associate { it.groupValues[1] to it.groupValues[2] }
                    val idx = i.lastIndexOf("\",")
                    val name = (if (idx >= 0) i.substring(idx + 2) else i.substringAfter(',', "")).trim().ifBlank { "Canal" }
                    val cat = a["group-title"].orEmpty().split(';').first()
                    out.add(Live(line.hashCode().toString(), name, a["tvg-logo"].orEmpty(), Brand.of(name, cat), line, Brand.isAdult(name, cat)))
                    info = null
                }
            }
        }
        return out
    }
}

class Xtream(private val p: Provider) {
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private val root get() = "${p.base}/player_api.php?username=${enc(p.user)}&password=${enc(p.pass)}"
    private fun api(action: String, extra: String = "") = Net.get("$root&action=$action$extra")

    fun auth(): Boolean = JSONObject(Net.get(root)).optJSONObject("user_info")?.optInt("auth", 0) == 1

    private fun cats(action: String): Map<String, String> {
        val a = JSONArray(api(action))
        return (0 until a.length()).associate {
            val o = a.getJSONObject(it)
            o.optString("category_id") to o.optString("category_name")
        }
    }

    fun live(): List<Live> {
        val c = cats("get_live_categories")
        val a = JSONArray(api("get_live_streams"))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            val id = o.optString("stream_id")
            val name = o.optString("name")
            val cat = c[o.optString("category_id")] ?: "Outros"
            Live(id, name, o.optString("stream_icon"), Brand.of(name, cat), "${p.base}/live/${p.user}/${p.pass}/$id.m3u8", Brand.isAdult(name, cat))
        }
    }

    fun vod(): List<Vod> {
        val c = cats("get_vod_categories")
        val a = JSONArray(api("get_vod_streams"))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            val id = o.optString("stream_id")
            val name = o.optString("name")
            val cat = c[o.optString("category_id")] ?: "Outros"
            val ext = o.optString("container_extension", "mp4").ifBlank { "mp4" }
            Vod(id, name, o.optString("stream_icon"), cat, "${p.base}/movie/${p.user}/${p.pass}/$id.$ext", Brand.isAdult(name, cat))
        }
    }

    fun series(): List<Serie> {
        val c = cats("get_series_categories")
        val a = JSONArray(api("get_series"))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            val name = o.optString("name")
            val cat = c[o.optString("category_id")] ?: "Outros"
            Serie(o.optString("series_id"), name, o.optString("cover"), cat, o.optString("plot"), Brand.isAdult(name, cat))
        }
    }

    fun episodes(seriesId: String): List<Episode> {
        val j = JSONObject(api("get_series_info", "&series_id=$seriesId"))
        val eps = j.optJSONObject("episodes") ?: return emptyList()
        val out = mutableListOf<Episode>()
        for (k in eps.keys()) {
            val season = k.toIntOrNull() ?: 0
            val a = eps.optJSONArray(k) ?: continue
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val ext = o.optString("container_extension", "mp4").ifBlank { "mp4" }
                out.add(Episode(o.optString("title", "Episódio ${i + 1}"), season, o.optInt("episode_num", i + 1),
                    "${p.base}/series/${p.user}/${p.pass}/${o.optString("id")}.$ext"))
            }
        }
        return out.sortedWith(compareBy<Episode>({ it.season }, { it.num }))
    }
}
