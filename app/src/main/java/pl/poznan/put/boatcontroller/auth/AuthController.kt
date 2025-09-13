package pl.poznan.put.boatcontroller.auth

import android.content.Context
import okhttp3.OkHttpClient
import pl.poznan.put.boatcontroller.dataclass.LoginRequest
import pl.poznan.put.boatcontroller.dataclass.LoginResponse
import pl.poznan.put.boatcontroller.dataclass.MissionDto
import pl.poznan.put.boatcontroller.dataclass.PointOfInterestDto
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body loginRequest: LoginRequest): LoginResponse
}

object AuthClient {
    private const val BASE_URL = "http://10.0.2.2:8000/"

    val authApi: AuthApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApi::class.java)
    }
}

object ApiClient {
    private const val BASE_URL = "http://10.0.2.2:8000/"

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
    @GET("missions/{id}")
    suspend fun getMission(@Path("id") id: Int): MissionDto

    @GET("pois/{mission_id}")
    suspend fun getPoiList(@Path("mission_id") id: Int): List<PointOfInterestDto>
}