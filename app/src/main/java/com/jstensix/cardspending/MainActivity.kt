package com.jstensix.cardspending

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jstensix.cardspending.ui.AppNavHost
import com.jstensix.cardspending.ui.theme.CardSpendingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CardSpendingApp).container
        setContent { CardSpendingTheme { AppNavHost(container) } }
    }
}
