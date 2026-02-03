package pl.poznan.put.boatcontroller.domain.models

data class LoginResponse(
    val access_token: String,
    val token_type: String,
)