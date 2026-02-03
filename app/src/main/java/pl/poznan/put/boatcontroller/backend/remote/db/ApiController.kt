package pl.poznan.put.boatcontroller.backend.remote.db

import android.content.Context
import okhttp3.OkHttpClient
import pl.poznan.put.boatcontroller.domain.models.LoginRequest
import pl.poznan.put.boatcontroller.domain.models.LoginResponse
import pl.poznan.put.boatcontroller.domain.models.MissionCreateRequest
import pl.poznan.put.boatcontroller.backend.dto.MissionDto
import pl.poznan.put.boatcontroller.backend.dto.MissionListItemDto
import pl.poznan.put.boatcontroller.domain.models.POICreateRequest
import pl.poznan.put.boatcontroller.domain.models.POIUpdateRequest
import pl.poznan.put.boatcontroller.backend.dto.PointOfInterestDto
import pl.poznan.put.boatcontroller.domain.models.RunningCreateRequest
import pl.poznan.put.boatcontroller.backend.dto.RunningDto
import pl.poznan.put.boatcontroller.domain.models.WaypointCreateRequest
import pl.poznan.put.boatcontroller.backend.dto.WaypointDto
import pl.poznan.put.boatcontroller.domain.models.WaypointUpdateRequest
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body loginRequest: LoginRequest): LoginResponse
}

object AuthClient {
    private var BASE_URL = "http://10.0.2.2:8000/"
    private var retrofit: Retrofit? = null

    fun setBaseUrl(url: String) {
        BASE_URL = url
        retrofit = null
    }

    val authApi: AuthApi
        get() {
            if (retrofit == null) {
                retrofit = Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
            }
            return retrofit!!.create(AuthApi::class.java)
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
    @POST("missions")
    suspend fun createMission(
        @Body request: MissionCreateRequest
    ): Response<Unit>

    @GET("missions/{mission_id}")
    suspend fun getMissionData(
        @Path("mission_id") missionId: Int
    ): Response<MissionDto>

    @GET("missions")
    suspend fun getMissions(): Response<List<MissionListItemDto>>

    @DELETE("missions/{mission_id}")
    suspend fun deleteMission(
        @Path("mission_id") missionId: Int
    ): Response<Unit>

    @POST("waypoints")
    suspend fun createWaypoint(
        @Body request: WaypointCreateRequest
    ): Response<Unit>

    @GET("waypoints/{mission_id}")
    suspend fun getWaypointsList(
        @Path("mission_id") missionId: Int
    ): Response<List<WaypointDto>>

    @PUT("waypoints/{waypoint_id}")
    suspend fun updateWaypoint(
        @Path("waypoint_id") waypointId: Int,
        @Body request: WaypointUpdateRequest
    ): Response<WaypointDto>

    @DELETE("waypoints/{waypoint_id}")
    suspend fun deleteWaypoint(
        @Path("waypoint_id") waypointId: Int,
    ): Response<Unit>

    @POST("runnings")
    suspend fun createRunning(
        @Body request: RunningCreateRequest
    ): Response<Unit>

    @GET("runnings/{mission_id}/last")
    suspend fun getLastRunning(
        @Path("mission_id") missionId: Int
    ): Response<RunningDto>

    @GET("runnings/{mission_id}")
    suspend fun getRunningsList(
        @Path("mission_id") missionId: Int
    ): Response<List<RunningDto>>


    @POST("pois")
    suspend fun createPoi(
        @Body request: POICreateRequest
    ): Response<Unit>

    @GET("pois/{mission_id}")
    suspend fun getPoiList(
        @Path("mission_id") missionId: Int
    ): Response<List<PointOfInterestDto>>

    @PUT("pois/{poi_id}")
    suspend fun updatePoi(
        @Path("poi_id") poiId: Int,
        @Body request: POIUpdateRequest
    ): Response<PointOfInterestDto>

    @DELETE("pois/{poi_id}")
    suspend fun deletePoi(
        @Path("poi_id") poiId: Int
    ): Response<Unit>
}