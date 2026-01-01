package com.hachi.ymobilemonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.hachi.ymobilemonitor.data.UserPreferences
import com.hachi.ymobilemonitor.data.YMobileRepository
import com.hachi.ymobilemonitor.ui.MainViewModel
import com.hachi.ymobilemonitor.ui.MainViewModelFactory
import com.hachi.ymobilemonitor.ui.screens.DashboardScreen
import com.hachi.ymobilemonitor.ui.screens.LoginScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // DI (簡易)
        val preferences = UserPreferences(this)
        val repository = YMobileRepository()
        val viewModel: MainViewModel by viewModels {
            MainViewModelFactory(repository, preferences)
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState by viewModel.uiState.collectAsState()

                    // シンプルな画面遷移: ログイン状態に応じて切り替える
                    // 実際にはNavHostを使うことが推奨されるが、2画面かつ状態依存遷移なのでこれでも十分機能する
                    if (uiState.isLoggedIn) {
                        DashboardScreen(
                            uiState = uiState,
                            onRefresh = { viewModel.fetchData() },
                            onLogout = { viewModel.logout() }
                        )
                    } else {
                        LoginScreen(
                            uiState = uiState,
                            onLoginClick = { id, pass, isSave ->
                                viewModel.login(id, pass, isSave)
                            }
                        )
                    }
                }
            }
        }
    }
}
