package io.stereov.web.user.dto.response

import io.stereov.web.user.model.DeviceInfo
import kotlinx.serialization.Serializable

/**
 * # Device information response.
 *
 * This data class represents the response containing information about a device used for authentication.
 * It includes the device ID, browser and OS information, IP address,
 * location information, and the last active time.
 *
 * @property id The ID of the device.
 * @property browser The browser used on the device.
 * @property os The operating system of the device.
 * @property ipAddress The IP address of the device.
 * @property location The location information of the device.
 * @property lastActive The last active time of the device.
 *
 * @author <a href="https://github.com/antistereov">antistereov</a>
 */
@Serializable
data class DeviceInfoResponse(
    val id: String,
    val browser: String? = null,
    val os: String? = null,
    val ipAddress: String?,
    val location: DeviceInfo.LocationInfo?,
    val lastActive: String
)
