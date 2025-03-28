package io.stereov.web.user.dto

import io.stereov.web.user.model.DeviceInfo
import kotlinx.serialization.Serializable

@Serializable
data class DeviceInfoResponse(
    val id: String,
    val browser: String? = null,
    val os: String? = null,
    val ipAddress: String?,
    val location: DeviceInfo.LocationInfo?,
    val lastActive: String
)
