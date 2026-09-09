package com.myothuonion.languagetalk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.myothuonion.languagetalk.ui.AppViewModel
import com.myothuonion.languagetalk.ui.LanguageTalkApp

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LanguageTalkApp(viewModel) }
    }
}
