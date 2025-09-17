package pl.poznan.put.boatcontroller.api

import android.content.Context
import okhttp3.OkHttpClient
import pl.poznan.put.boatcontroller.dataclass.LoginRequest
import pl.poznan.put.boatcontroller.dataclass.LoginResponse
import pl.poznan.put.boatcontroller.dataclass.MissionAddDto
import pl.poznan.put.boatcontroller.dataclass.MissionListItemDto
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body loginRequest: LoginRequest): LoginResponse
}

object AuthClient {
    private var BASE_URL = "http://10.0.2.2:8000/"

    fun setBaseUrl(url: String) {
        BASE_URL = url
    }

    val authApi: AuthApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApi::class.java)
    }
}

object ApiClient {
    private var BASE_URL = "http://10.0.2.2:8000/"

    fun setBaseUrl(url: String) {
        BASE_URL = url
    }

    fun create(context: Context): ApiService {
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(context))
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}

interface ApiService {
    @GET("missions")
    suspend fun getMissions(): List<MissionListItemDto>

    @POST("missions")
    suspend fun createMission(@Body mission: MissionAddDto): MissionListItemDto
}