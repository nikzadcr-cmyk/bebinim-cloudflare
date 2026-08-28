package com.app.bebinim.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.bebinim.data.api.Plan
import com.app.bebinim.data.repository.PlanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class PlansState {
    object Loading : PlansState()
    data class Success(val plans: List<Plan>) : PlansState()
    data class Error(val message: String) : PlansState()
}

class PlanViewModel : ViewModel() {

    private val repository = PlanRepository()

    private val _state = MutableStateFlow<PlansState>(PlansState.Loading)
    val state: StateFlow<PlansState> = _state

    init {
        loadPlans()
    }

    fun loadPlans() {
        _state.value = PlansState.Loading
        viewModelScope.launch {
            val response = repository.loadPlans()
            if (response.status == "success" && response.data != null) {
                _state.value = PlansState.Success(response.data)
            } else {
                _state.value = PlansState.Error(response.message.ifBlank { "خطا در دریافت پلن‌ها" })
            }
        }
    }

    fun refresh() = loadPlans()
}
