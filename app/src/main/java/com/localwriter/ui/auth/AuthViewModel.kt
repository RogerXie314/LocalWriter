package com.localwriter.ui.auth

import androidx.lifecycle.*
import com.localwriter.data.db.entity.User
import com.localwriter.data.repository.AuthRepository
import kotlinx.coroutines.launch

class AuthViewModel(private val repo: AuthRepository) : ViewModel() {

    private val _state = MutableLiveData<AuthState>(AuthState.Loading)
    val state: LiveData<AuthState> = _state

    sealed class AuthState {
        object Loading : AuthState()
        object NoUser : AuthState()       // 未注册，去注册页
        data class NeedAuth(val user: User) : AuthState() // 已注册，需要验证
        data class Success(val userId: Long) : AuthState()
        data class Error(val message: String) : AuthState()
    }

    fun init() {
        viewModelScope.launch {
            val hasUser = repo.hasUser()
            _state.value = if (hasUser) {
                val user = repo.getFirstUser()
                if (user != null) AuthState.NeedAuth(user) else AuthState.NoUser
            } else {
                AuthState.NoUser
            }
        }
    }

    fun loginWithPassword(username: String, password: String) {
        viewModelScope.launch {
            val user = repo.loginWithPassword(username, password)
            _state.value = if (user != null) {
                AuthState.Success(user.id)
            } else {
                AuthState.Error("用户名或密码错误")
            }
        }
    }

    fun loginWithGesture(userId: Long, pattern: String) {
        viewModelScope.launch {
            val user = repo.loginWithGesture(userId, pattern)
            _state.value = if (user != null) {
                AuthState.Success(user.id)
            } else {
                AuthState.Error("手势密码错误")
            }
        }
    }

    fun register(username: String, password: String) {
        viewModelScope.launch {
            val userId = repo.register(username, password)
            _state.value = if (userId > 0) {
                AuthState.Success(userId)
            } else {
                AuthState.Error("注册失败，用户名已存在")
            }
        }
    }

    class Factory(private val repo: AuthRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(repo) as T
        }
    }
}
