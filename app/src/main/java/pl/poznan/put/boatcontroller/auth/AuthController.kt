package pl.poznan.put.boatcontroller.auth

import android.content.Context
import okhttp3.OkHttpClient
import pl.poznan.put.boatcontroller.dataclass.LoginRequest
import pl.poznan.put.boatcontroller.dataclass.LoginResponse
import pl.poznan.put.boatcontroller.dataclass.MissionCreateRequest
import pl.poznan.put.boatcontroller.dataclass.MissionDto
import pl.poznan.put.boatcontroller.dataclass.POICreateRequest
import pl.poznan.put.boatcontroller.dataclass.POIUpdateRequest
import pl.poznan.put.boatcontroller.dataclass.PointOfInterestDto
import pl.poznan.put.boatcontroller.dataclass.RunningCreateRequest
import pl.poznan.put.boatcontroller.dataclass.RunningDto
import pl.poznan.put.boatcontroller.dataclass.WaypointCreateRequest
import pl.poznan.put.boatcontroller.dataclass.WaypointDto
import pl.poznan.put.boatcontroller.dataclass.WaypointUpdateRequest
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
    // Mission endpoints
    @POST("missions")
    suspend fun createMission(
        @Body request: MissionCreateRequest
    ): Response<Unit>

    @GET("missions/{mission_id}")
    suspend fun getMissionData(
        @Path("mission_id") missionId: Int
    ): MissionDto

    @GET("missions")
    suspend fun getMissionList(): List<MissionDto>

    @DELETE("missions/{mission_id}")
    suspend fun deleteMission(
        @Path("mission_id") missionId: Int
    ): Response<Unit>


    // Waypoints endpoints
    @POST("waypoints")
    suspend fun createWaypoint(
        @Body request: WaypointCreateRequest
    ): Response<Unit>

    @GET("waypoints/{mission_id}")
    suspend fun getWaypointsList(
        @Path("mission_id") missionId: Int
    ): List<WaypointDto>

    @PUT("waypoints/{waypoint_id}")
    suspend fun updateWaypoint(
        @Path("waypoint_id") waypointId: Int,
        @Body request: WaypointUpdateRequest
    ): WaypointDto

    @DELETE("waypoints/{waypoint_id}")
    suspend fun deleteWaypoint(
        @Path("waypoint_id") waypointId: Int,
    ): Response<Unit>


    // Runnings endpoints
    @POST("runnings")
    suspend fun createRunning(
        @Body request: RunningCreateRequest
    ): Response<Unit>

    @GET("runnings/{mission_id}/last")
    suspend fun getLastRunning(
        @Path("mission_id") missionId: Int
    ): RunningDto

    @GET("runnings/{mission_id}")
    suspend fun getRunningsList(
        @Path("mission_id") missionId: Int
    ): List<RunningDto>


    // POI endpoints
    @POST("pois")
    suspend fun createPoi(
        @Body request: POICreateRequest
    ): Response<Unit>

    @GET("pois/{mission_id}")
    suspend fun getPoiList(
        @Path("mission_id") missionId: Int
    ): List<PointOfInterestDto>

    @PUT("pois")
    suspend fun updatePoi(
        @Path("poi_id") poiId: Int,
        @Body request: POIUpdateRequest
    ): PointOfInterestDto

    @DELETE("pois")
    suspend fun deletePoi(
        @Path("poi_id") poiId: Int
    ): Response<Unit>
}