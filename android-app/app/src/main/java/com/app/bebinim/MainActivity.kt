package com.app.bebinim

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.app.bebinim.ui.navigation.AppNavigation
import com.app.bebinim.ui.theme.BebinimTheme
import com.app.bebinim.viewmodel.AuthViewModel

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BebinimTheme {
                val isInitializing by authViewModel.isInitializing.collectAsState()
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (!isInitializing) {
                        AppNavigation(rememberNavController(), authViewModel)
                    }
                }
            }
        }
    }
}
