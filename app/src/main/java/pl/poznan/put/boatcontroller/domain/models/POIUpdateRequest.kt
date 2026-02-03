package pl.poznan.put.boatcontroller.domain.models

data class POIUpdateRequest(
    val name: String?,
    val description: String?,
    val pictures: String? = null  // lista URL w formie JSON stringa np. '["url1","url2"]'
)