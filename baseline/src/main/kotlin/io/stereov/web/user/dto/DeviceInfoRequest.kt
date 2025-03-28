package io.stereov.web.user.dto

import kotlinx.serialization.Serializable

@Serializable
data class DeviceInfoRequest(
    val id: String,
    val browser: String? = null,
    val os: String? = null,
)
