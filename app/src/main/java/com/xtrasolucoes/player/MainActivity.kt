/*
 * xTra Player
 * Desenvolvido por xTra Soluções
 */
package com.xtrasolucoes.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFE50914), background = Color(0xFF0E0E10), surface = Color(0xFF0E0E10))) {
                Surface(Modifier.fillMaxSize()) {
                    val vm: AppVM = viewModel()
                    Root(vm)
                }
            }
        }
    }
}
