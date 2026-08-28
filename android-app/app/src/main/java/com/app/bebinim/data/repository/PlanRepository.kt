package com.app.bebinim.data.repository

import com.app.bebinim.data.api.ApiResponse
import com.app.bebinim.data.api.BebinimApiService
import com.app.bebinim.data.api.Plan
import com.app.bebinim.data.api.PlanStatus
import com.app.bebinim.data.api.RetrofitClient
import retrofit2.Response

class PlanRepository {

    private val api: BebinimApiService = RetrofitClient.apiService

    suspend fun loadPlans(): ApiResponse<List<Plan>> = safe { api.getPlans() }

    suspend fun verifyPlan(): ApiResponse<PlanStatus> = safe { api.verifyPlan(emptyMap()) }

    suspend fun getMyActivePlan(token: String): ApiResponse<Plan> = safe { api.getMyActivePlan(token) }

    private inline fun <reified T> safe(block: () -> Response<ApiResponse<T>>): ApiResponse<T> {
        return try {
            val response = block()
            response.body() ?: ApiResponse(status = "error", message = "خطا در ارتباط با سرور")
        } catch (e: java.io.IOException) {
            ApiResponse(status = "error", message = "خطای شبکه. لطفاً اتصال اینترنت را بررسی کنید")
        } catch (e: Exception) {
            ApiResponse(status = "error", message = "خطا در ارتباط با سرور")
        }
    }
}
