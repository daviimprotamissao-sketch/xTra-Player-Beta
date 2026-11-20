/*
 * xTra Player
 * Desenvolvido por xTra Soluções
 */
@file:OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)

package com.xtrasolucoes.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

data class Play(val title: String, val url: String)
data class Card(val title: String, val img: String, val cat: String, val onClick: () -> Unit)

@Composable
fun Root(vm: AppVM) {
    if (vm.profile == null) ProfilePicker(vm) else Shell(vm)
}

// ---------- Perfis ----------

@Composable
fun Avatar(p: Profile, size: Dp = 80.dp) {
    val colors = listOf(Color(0xFFE50914), Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFB8C00), Color(0xFF8E24AA))
    Box(
        Modifier.size(size).clip(CircleShape).background(colors[abs(p.id.hashCode()) % colors.size]),
        contentAlignment = Alignment.Center
    ) { Text(p.name.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium, color = Color.White) }
}

@Composable
fun ProfilePicker(vm: AppVM) {
    var adding by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Quem está assistindo?", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(110.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            items(vm.profiles) { p ->
                Column(Modifier.clickable { vm.selectProfile(p) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(p)
                    Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (p.kids) Text("Infantil", style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                Column(Modifier.clickable { adding = true }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(80.dp).clip(CircleShape).background(Color(0xFF2A2A2A)), contentAlignment = Alignment.Center) {
                        Text("+", style = MaterialTheme.typography.headlineMedium)
                    }
                    Text("Novo perfil")
                }
            }
        }
    }
    if (adding) ProfileDialog({ adding = false }) { n, k -> vm.addProfile(n, k); adding = false }
}

@Composable
fun ProfileDialog(onDismiss: () -> Unit, onOk: (String, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var kids by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo perfil") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = kids, onCheckedChange = { kids = it })
                    Text("Perfil infantil (sem conteúdo +18)")
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onOk(name.trim(), kids) }) { Text("Criar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

// ---------- PIN ----------

@Composable
fun PinDialog(title: String, error: String? = null, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pin = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = { onOk(pin) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun GuardDialog(vm: AppVM, action: () -> Unit, onClose: () -> Unit) {
    var err by remember { mutableStateOf<String?>(null) }
    PinDialog("Digite o PIN", err, onClose) {
        if (vm.checkPin(it)) { vm.unlocked = true; onClose(); action() } else err = "PIN incorreto"
    }
}

// ---------- Estrutura principal ----------

@Composable
fun Shell(vm: AppVM) {
    var tab by remember { mutableStateOf(0) }
    var play by remember { mutableStateOf<Play?>(null) }
    var openSerie by remember { mutableStateOf<Serie?>(null) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }

    val onPlay: (Boolean, Play) -> Unit = { adult, p ->
        if (vm.locked(adult)) pending = { play = p } else play = p
    }

    pending?.let { a -> GuardDialog(vm, a) { pending = null } }

    val p = play
    if (p != null) {
        BackHandler { play = null }
        PlayerScreen(vm, p) { play = null }
        return
    }
    val s = openSerie
    if (s != null) {
        BackHandler { openSerie = null }
        SeriesDetail(vm, s, onPlay) { openSerie = null }
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf("🏠" to "Início", "📺" to "TV", "🎬" to "Filmes", "🍿" to "Séries", "⚙️" to "Ajustes")
                    .forEachIndexed { i, (e, l) ->
                        NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Text(e) }, label = { Text(l) })
                    }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            Status(vm)
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> Home(vm, { tab = it }, onPlay) { openSerie = it }
                    1 -> LiveScreen(vm, onPlay)
                    2 -> {
                        val cards = remember(vm.vod, vm.profile) {
                            vm.vod.filter { !vm.hidden(it.adult) }.map { m -> Card(m.name, m.poster, m.category) { onPlay(m.adult, Play(m.name, m.url)) } }
                        }
                        Catalog(cards, "Nenhum filme. Adicione um provedor com filmes (Xtream Codes) em Ajustes.")
                    }
                    3 -> {
                        val cards = remember(vm.series, vm.profile) {
                            vm.series.filter { !vm.hidden(it.adult) }.map { x -> Card(x.name, x.cover, x.category) { if (vm.locked(x.adult)) pending = { openSerie = x } else openSerie = x } }
                        }
                        Catalog(cards, "Nenhuma série. Adicione um provedor com séries (Xtream Codes) em Ajustes.")
                    }
                    else -> SettingsScreen(vm)
                }
            }
        }
    }
}

@Composable
fun Status(vm: AppVM) {
    if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    vm.error?.let {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(it, Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { vm.load() }) { Text("Tentar de novo") }
        }
    }
}

// ---------- Início ----------

@Composable
fun PosterCard(title: String, img: String, modifier: Modifier = Modifier.width(120.dp), onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick)) {
        AsyncImage(
            model = img.ifBlank { null },
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A2A2A))
        )
        Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun Hero(title: String, img: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(220.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2A2A2A))) {
        AsyncImage(model = img.ifBlank { null }, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))))
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Button(onClick = onClick) { Text("▶ Assistir") }
        }
    }
}

@Composable
fun SectionTitle(title: String, onAll: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onAll) { Text("Ver todos") }
    }
}

@Composable
fun Home(vm: AppVM, goTab: (Int) -> Unit, onPlay: (Boolean, Play) -> Unit, onSerie: (Serie) -> Unit) {
    val movies = remember(vm.vod) { vm.vod.filter { !it.adult }.take(20) }
    val shows = remember(vm.series) { vm.series.filter { !it.adult }.take(20) }
    val chans = remember(vm.live, vm.favs) {
        val l = vm.live.filter { !it.adult }
        (l.filter { it.id in vm.favs } + l).distinct().take(15)
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Olá, ${vm.profile?.name ?: ""}", style = MaterialTheme.typography.headlineSmall)
                    Text(vm.provider.name, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { vm.switchProfile() }) { Text("Trocar perfil") }
            }
        }
        val m = movies.firstOrNull()
        val sh = shows.firstOrNull()
        if (m != null) item { Hero(m.name, m.poster) { onPlay(false, Play(m.name, m.url)) } }
        else if (sh != null) item { Hero(sh.name, sh.cover) { onSerie(sh) } }
        if (movies.isNotEmpty()) item {
            Column {
                SectionTitle("Filmes em destaque") { goTab(2) }
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(movies) { x -> PosterCard(x.name, x.poster) { onPlay(false, Play(x.name, x.url)) } }
                }
            }
        }
        if (shows.isNotEmpty()) item {
            Column {
                SectionTitle("Séries em destaque") { goTab(3) }
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(shows) { x -> PosterCard(x.name, x.cover) { onSerie(x) } }
                }
            }
        }
        if (movies.isEmpty() && shows.isEmpty()) item {
            Text("Este provedor não tem filmes e séries. Em Ajustes, adicione um provedor Xtream Codes (servidor, usuário e senha).",
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
        }
        if (chans.isNotEmpty()) item {
            Column {
                SectionTitle("Canais") { goTab(1) }
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(chans) { c ->
                        Column(Modifier.width(90.dp).clickable { onPlay(false, Play(c.display, c.url)) }, horizontalAlignment = Alignment.CenterHorizontally) {
                            AsyncImage(model = c.logo.ifBlank { null }, contentDescription = null,
                                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2A2A2A)))
                            Text(c.display, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

// ---------- TV ao vivo ----------

@Composable
fun LiveScreen(vm: AppVM, onPlay: (Boolean, Play) -> Unit) {
    var q by remember { mutableStateOf("") }
    var open by remember { mutableStateOf(setOf<String>()) }
    val chans = remember(vm.live, vm.profile) { vm.live.filter { !vm.hidden(it.adult) } }
    val filtered = remember(chans, q) {
        if (q.isBlank()) chans else chans.filter { Brand.norm(it.name).contains(Brand.norm(q), true) }
    }
    val favs = vm.favs
    val sections = remember(filtered, favs) {
        val fav = filtered.filter { it.id in favs }
        val groups = filtered.groupBy { it.group }.toList()
            .sortedWith(compareBy<Pair<String, List<Live>>>({ Brand.rank(it.first) }, { it.first }))
        (if (fav.isNotEmpty()) listOf("★ Favoritos" to fav) else emptyList()) + groups
    }
    Column {
        OutlinedTextField(
            value = q, onValueChange = { q = it }, label = { Text("Buscar canal") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        )
        if (sections.isEmpty()) Text("Nenhum canal.", Modifier.padding(16.dp))
        LazyColumn {
            for ((name, list) in sections) {
                val expanded = q.isNotBlank() || name in open
                item {
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { open = if (name in open) open - name else open + name }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("$name (${list.size})", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Text(if (expanded) "▾" else "▸")
                    }
                }
                if (expanded) items(list) { ch ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPlay(ch.adult, Play(ch.display, ch.url)) }.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(model = ch.logo.ifBlank { null }, contentDescription = null, modifier = Modifier.size(44.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(ch.display, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (ch.adult) Text("🔒 18+", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { vm.toggleFav(ch.id) }) { Text(if (ch.id in favs) "★" else "☆") }
                    }
                }
            }
        }
    }
}

// ---------- Filmes e séries ----------

@Composable
fun Catalog(cards: List<Card>, empty: String) {
    var q by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf<String?>(null) }
    val cats = remember(cards) { cards.map { it.cat }.distinct().sorted() }
    val shown = remember(cards, q, cat) {
        cards.filter { (cat == null || it.cat == cat) && Brand.norm(it.title).contains(Brand.norm(q), true) }
    }
    Column {
        OutlinedTextField(value = q, onValueChange = { q = it }, label = { Text("Buscar") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(12.dp))
        if (cards.isEmpty()) {
            Text(empty, Modifier.padding(16.dp))
            return@Column
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected = cat == null, onClick = { cat = null }, label = { Text("Todos") }) }
            items(cats) { c -> FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(c) }) }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(110.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(shown) { c -> PosterCard(c.title, c.img, Modifier.fillMaxWidth(), c.onClick) }
        }
    }
}

@Composable
fun SeriesDetail(vm: AppVM, s: Serie, onPlay: (Boolean, Play) -> Unit, onBack: () -> Unit) {
    var eps by remember { mutableStateOf<List<Episode>?>(null) }
    var season by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(s) {
        val r = withContext(Dispatchers.IO) { runCatching { Xtream(vm.provider).episodes(s.id) }.getOrDefault(emptyList()) }
        eps = r
        season = r.firstOrNull()?.season
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column {
                TextButton(onClick = onBack) { Text("← Voltar") }
                Row(Modifier.padding(16.dp)) {
                    AsyncImage(model = s.cover.ifBlank { null }, contentDescription = s.name, contentScale = ContentScale.Crop,
                        modifier = Modifier.width(120.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A2A2A)))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(s.name, style = MaterialTheme.typography.titleLarge)
                        Text(s.plot, maxLines = 8, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        val list = eps
        if (list == null) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        } else if (list.isEmpty()) {
            item { Text("Nenhum episódio encontrado.", Modifier.padding(16.dp)) }
        } else {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(list.map { it.season }.distinct()) { n ->
                        FilterChip(selected = season == n, onClick = { season = n }, label = { Text("Temporada $n") })
                    }
                }
            }
            items(list.filter { it.season == season }) { e ->
                Text(
                    "E${e.num}  ${e.title}",
                    Modifier.fillMaxWidth().clickable { onPlay(s.adult, Play("${s.name} T${e.season}E${e.num}", e.url)) }.padding(16.dp)
                )
            }
        }
    }
}

// ---------- Ajustes ----------

@Composable
fun SettingsScreen(vm: AppVM) {
    var addProvider by remember { mutableStateOf(false) }
    var addProfile by remember { mutableStateOf(false) }
    var guard by remember { mutableStateOf<(() -> Unit)?>(null) }
    var newPin by remember { mutableStateOf(false) }
    var pinErr by remember { mutableStateOf<String?>(null) }
    val needPin: (() -> Unit) -> Unit = { a -> if (vm.hasPin) guard = a else a() }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Provedores", style = MaterialTheme.typography.titleMedium) }
        items(vm.providers) { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = vm.provider.id == p.id, onClick = { vm.selectProvider(p) })
                Column(Modifier.weight(1f)) {
                    Text(p.name)
                    Text(when (p.type) { "xtream" -> "Xtream Codes"; "m3u" -> "Lista M3U"; else -> "Incluída no app" }, style = MaterialTheme.typography.bodySmall)
                }
                if (p.id != "builtin") TextButton(onClick = { needPin { vm.deleteProvider(p) } }) { Text("Remover") }
            }
        }
        item { Button(onClick = { addProvider = true }) { Text("+ Adicionar provedor") } }

        item { Spacer(Modifier.height(8.dp)); Text("Perfis", style = MaterialTheme.typography.titleMedium) }
        items(vm.profiles) { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(p, 40.dp)
                Spacer(Modifier.width(12.dp))
                Text(p.name, Modifier.weight(1f))
                Text("Infantil ", style = MaterialTheme.typography.bodySmall)
                Switch(checked = p.kids, onCheckedChange = { v -> if (v) vm.setKids(p, true) else needPin { vm.setKids(p, false) } })
                if (vm.profiles.size > 1) TextButton(onClick = { needPin { vm.deleteProfile(p) } }) { Text("✕") }
            }
        }
        item { Button(onClick = { addProfile = true }) { Text("+ Novo perfil") } }

        item { Spacer(Modifier.height(8.dp)); Text("Controle parental", style = MaterialTheme.typography.titleMedium) }
        item {
            Text("Perfis infantis nunca mostram canais, filmes ou séries +18. Nos demais perfis, o conteúdo +18 pede o PIN.",
                style = MaterialTheme.typography.bodySmall)
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Exigir PIN para conteúdo +18", Modifier.weight(1f))
                Switch(checked = vm.parental, onCheckedChange = { v -> if (v) vm.setParental(true) else needPin { vm.setParental(false) } })
            }
        }
        item {
            Button(onClick = { needPin { newPin = true } }) { Text(if (vm.hasPin) "Alterar PIN" else "Definir PIN") }
        }
        item { OutlinedButton(onClick = { vm.switchProfile() }) { Text("Trocar perfil") } }

        item {
            Column(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${AppInfo.NAME} ${AppInfo.VERSION}", style = MaterialTheme.typography.bodySmall)
                Text("Desenvolvido por ${AppInfo.AUTHOR}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (addProvider) ProviderDialog({ addProvider = false }) { vm.addProvider(it); addProvider = false }
    if (addProfile) ProfileDialog({ addProfile = false }) { n, k -> vm.addProfile(n, k); addProfile = false }
    guard?.let { a -> GuardDialog(vm, a) { guard = null } }
    if (newPin) PinDialog("Novo PIN (4 a 6 dígitos)", pinErr, { newPin = false; pinErr = null }) {
        if (it.length in 4..6) { vm.setPin(it); newPin = false; pinErr = null } else pinErr = "Use de 4 a 6 dígitos"
    }
}

@Composable
fun ProviderDialog(onDismiss: () -> Unit, onOk: (Provider) -> Unit) {
    var xtream by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar provedor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = xtream, onClick = { xtream = true }, label = { Text("Xtream Codes") })
                    FilterChip(selected = !xtream, onClick = { xtream = false }, label = { Text("Lista M3U") })
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome do provedor") }, singleLine = true)
                OutlinedTextField(value = server, onValueChange = { server = it },
                    label = { Text(if (xtream) "Servidor (http://host:porta)" else "URL da lista M3U") }, singleLine = true)
                if (xtream) {
                    OutlinedTextField(value = user, onValueChange = { user = it }, label = { Text("Usuário") }, singleLine = true)
                    OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text("Senha") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation())
                }
                if (err != null) Text(err!!, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val s = server.trim()
                val url = if (s.startsWith("http://") || s.startsWith("https://")) s else "http://$s"
                if (name.isBlank() || s.isBlank() || (xtream && (user.isBlank() || pass.isBlank()))) err = "Preencha todos os campos"
                else onOk(Provider("prov" + System.currentTimeMillis(), name.trim(), if (xtream) "xtream" else "m3u", url, user.trim(), pass))
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

// ---------- Player ----------

// Formas de ver a tela: cada modo guarda o rótulo mostrado ao usuário e o resize mode do
// Media3/ExoPlayer que produz aquele efeito visual.
enum class ScreenMode(val label: String, val resizeMode: Int) {
    // Ajustar: vídeo inteiro visível, respeitando a proporção original (padrão).
    FIT("Ajustar", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    // Esticada igual de PC (800x600): força uma moldura 4:3 centralizada e estica o vídeo
    // para preencher essa moldura, como um monitor de PC clássico.
    PC("Esticar (PC 800x600)", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    // Preencher: corta as bordas do vídeo para ocupar a tela toda, sem distorcer a imagem.
    FILL("Preencher", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    // Preencher com 4K: estica o vídeo para ocupar cada pixel da tela, de ponta a ponta,
    // sem faixas pretas, ideal para telas widescreen/4K.
    FILL_4K("Preencher 4K", AspectRatioFrameLayout.RESIZE_MODE_FILL);

    companion object {
        fun from(name: String): ScreenMode = entries.firstOrNull { it.name == name } ?: FIT
    }
}

@Composable
fun PlayerScreen(vm: AppVM, item: Play, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var playerError by remember(item) { mutableStateOf<String?>(null) }
    var mode by remember { mutableStateOf(ScreenMode.from(vm.store.screenMode)) }
    var modeMenu by remember { mutableStateOf(false) }

    val player = remember(item) {
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("Mozilla/5.0 (Linux; Android) xTra Player")
        ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(ctx, http)))
            .build()
    }

    DisposableEffect(player) {
        var triedHttp = false
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                if (!triedHttp && item.url.startsWith("https://")) {
                    // Muitos servidores de IPTV só funcionam em http; tenta de novo sem TLS.
                    triedHttp = true
                    player.setMediaItem(MediaItem.fromUri(item.url.replaceFirst("https://", "http://")))
                    player.prepare()
                    player.play()
                } else {
                    playerError = "Não foi possível reproduzir."
                }
            }
        }
        player.addListener(listener)
        player.setMediaItem(MediaItem.fromUri(item.url))
        player.prepare()
        player.playWhenReady = true
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        // No modo "PC (800x600)" a moldura do vídeo fica travada em 4:3 e centralizada;
        // nos demais modos ela ocupa a tela toda e o resizeMode decide como o vídeo se encaixa.
        val videoModifier = if (mode == ScreenMode.PC) Modifier.aspectRatio(4f / 3f) else Modifier.fillMaxSize()
        AndroidView(
            modifier = videoModifier,
            factory = { context ->
                PlayerView(context).apply {
                    this.player = player
                    useController = true
                    keepScreenOn = true
                    resizeMode = mode.resizeMode
                }
            },
            update = { view -> view.resizeMode = mode.resizeMode }
        )
        TextButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Text("← ${item.title}", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            TextButton(onClick = { modeMenu = true }) {
                Text("⛶ ${mode.label}", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded = modeMenu, onDismissRequest = { modeMenu = false }) {
                ScreenMode.entries.forEach { m ->
                    DropdownMenuItem(text = { Text(m.label) }, onClick = {
                        mode = m
                        vm.store.screenMode = m.name
                        modeMenu = false
                    })
                }
            }
        }
        playerError?.let {
            Text(it, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(24.dp))
        }
    }
}
