package com.hachi.ymobilemonitor.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hachi.ymobilemonitor.data.UserPreferences
import com.hachi.ymobilemonitor.data.YMobileData
import com.hachi.ymobilemonitor.data.YMobileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val data: YMobileData? = null,
    val isLoggedIn: Boolean = false,
    val isAutoLoginAttempted: Boolean = false // 自動ログイン試行済みフラグ
)

class MainViewModel(
    private val repository: YMobileRepository,
    private val preferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        attemptAutoLogin()
    }

    fun attemptAutoLogin() {
        val savedId = preferences.getUserId()
        val savedPass = preferences.getPassword()

        if (!savedId.isNullOrBlank() && !savedPass.isNullOrBlank()) {
            login(savedId, savedPass, isAuto = true)
        } else {
            _uiState.update { it.copy(isAutoLoginAttempted = true) }
        }
    }

    fun login(id: String, pass: String, isSave: Boolean = false, isAuto: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            val success = repository.login(id, pass)
            
            if (success) {
                if (isSave) {
                    preferences.saveCredentials(id, pass)
                }
                _uiState.update { it.copy(isLoading = false, isLoggedIn = true, isAutoLoginAttempted = true) }
                fetchData()
            } else {
                _uiState.update { it.copy(
                    isLoading = false,
                    error = if (isAuto) "自動ログインに失敗しました" else "ログインに失敗しました。IDまたはパスワードを確認してください。",
                    isAutoLoginAttempted = true
                ) }
            }
        }
    }

    fun fetchData() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = repository.fetchData()
            
            result.onSuccess { data ->
                _uiState.update { it.copy(isLoading = false, data = data) }
            }.onFailure { e ->
                // セッション切れの可能性 -> 再ログイン試行
                // メッセージに "Step 3" 等が含まれる場合はネットワーク/パースエラーの可能性が高いが、念のため再ログインも試す
                val savedId = preferences.getUserId()
                val savedPass = preferences.getPassword()
                
                if (savedId != null && savedPass != null) {
                    // 再ログイン試行
                    val reloginSuccess = repository.login(savedId, savedPass)
                    if (reloginSuccess) {
                        // 再取得
                        val retryResult = repository.fetchData()
                        retryResult.onSuccess { retryData ->
                            _uiState.update { it.copy(isLoading = false, data = retryData) }
                            return@launch
                        }.onFailure { retryEx ->
                             _uiState.update { it.copy(isLoading = false, error = "再取得失敗: ${retryEx.message}") }
                             return@launch
                        }
                    }
                }
                
                // 再ログイン失敗または対象外
                _uiState.update { it.copy(isLoading = false, error = "データ取得失敗: ${e.message}") }
            }
        }
    }

    fun logout() {
        preferences.clear()
        _uiState.update { UiState() } // リセット
    }
}

class MainViewModelFactory(
    private val repository: YMobileRepository,
    private val preferences: UserPreferences
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository, preferences) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
