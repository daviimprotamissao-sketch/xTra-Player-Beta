/*
 * xTra Player
 * Desenvolvido por xTra Soluções
 */
package com.xtrasolucoes.player

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppVM(app: Application) : AndroidViewModel(app) {
    val store = Store(app)
    var providers by mutableStateOf(store.providers)
    var profiles by mutableStateOf(store.profiles)
    var profile by mutableStateOf<Profile?>(null)
    var provider by mutableStateOf(providers.firstOrNull { it.id == store.activeProvider } ?: providers.first())
    var live by mutableStateOf<List<Live>>(emptyList())
    var vod by mutableStateOf<List<Vod>>(emptyList())
    var series by mutableStateOf<List<Serie>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var favs by mutableStateOf<Set<String>>(emptySet())
    var unlocked by mutableStateOf(false)
    var parental by mutableStateOf(store.parental)
    var hasPin by mutableStateOf(store.pin.isNotEmpty())

    init { load() }

    fun load() {
        val p = provider
        loading = true
        error = null
        viewModelScope.launch {
            try {
                val r = withContext(Dispatchers.IO) { fetch(p) }
                live = r.first; vod = r.second; series = r.third
            } catch (e: Exception) {
                live = emptyList(); vod = emptyList(); series = emptyList()
                error = "Falha ao carregar: ${e.message ?: "erro de conexão"}"
            }
            loading = false
        }
    }

    private fun fetch(p: Provider): Triple<List<Live>, List<Vod>, List<Serie>> = when (p.type) {
        "xtream" -> {
            val x = Xtream(p)
            if (!x.auth()) throw Exception("usuário ou senha inválidos")
            Triple(x.live(), runCatching { x.vod() }.getOrDefault(emptyList()), runCatching { x.series() }.getOrDefault(emptyList()))
        }
        "m3u" -> Triple(M3u.parse(Net.get(p.server)), emptyList(), emptyList())
        else -> Triple(
            M3u.parse(getApplication<Application>().assets.open("playlist.txt").bufferedReader().use { it.readText() }),
            emptyList(), emptyList()
        )
    }

    // Perfis
    fun selectProfile(p: Profile) { profile = p; unlocked = false; favs = store.favs(p.id) }
    fun switchProfile() { profile = null }
    fun addProfile(name: String, kids: Boolean) {
        profiles = profiles + Profile("p" + System.currentTimeMillis(), name, kids)
        store.profiles = profiles
    }
    fun setKids(p: Profile, kids: Boolean) {
        profiles = profiles.map { if (it.id == p.id) it.copy(kids = kids) else it }
        store.profiles = profiles
        if (profile?.id == p.id) profile = profiles.first { it.id == p.id }
    }
    fun deleteProfile(p: Profile) {
        if (profiles.size <= 1) return
        profiles = profiles.filter { it.id != p.id }
        store.profiles = profiles
    }

    // Provedores
    fun selectProvider(p: Provider) { provider = p; store.activeProvider = p.id; load() }
    fun addProvider(p: Provider) {
        providers = providers + p
        store.providers = providers
        selectProvider(p)
    }
    fun deleteProvider(p: Provider) {
        if (p.id == "builtin") return
        providers = providers.filter { it.id != p.id }
        store.providers = providers
        if (provider.id == p.id) selectProvider(providers.first())
    }

    // Favoritos
    fun toggleFav(id: String) {
        val p = profile ?: return
        favs = if (id in favs) favs - id else favs + id
        store.setFavs(p.id, favs)
    }

    // Controle parental
    fun hidden(adult: Boolean) = adult && profile?.kids == true
    fun locked(adult: Boolean) = adult && parental && hasPin && profile?.kids != true && !unlocked
    fun checkPin(pin: String) = pin == store.pin
    fun setPin(pin: String) { store.pin = pin; hasPin = pin.isNotEmpty() }
    fun setParental(v: Boolean) { parental = v; store.parental = v }
}
