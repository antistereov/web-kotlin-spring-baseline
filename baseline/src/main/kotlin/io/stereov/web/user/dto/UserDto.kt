package io.stereov.web.user.dto

import io.stereov.web.user.dto.response.DeviceInfoResponse
import io.stereov.web.user.model.Role
import kotlinx.serialization.Serializable

/**
 * # User Data Transfer Object (DTO).
 *
 * This data class represents a user in the system.
 * It contains various properties such as user ID, name, email,
 * roles, email verification status, device information,
 * last active time, two-factor authentication status,
 * and application information.
 *
 * @property id The unique identifier of the user.
 * @property name The name of the user (nullable).
 * @property email The email address of the user.
 * @property roles The list of roles assigned to the user (default is a list containing the USER role).
 * @property emailVerified Indicates whether the user's email is verified (default is false).
 * @property devices The list of devices associated with the user (default is an empty list).
 * @property lastActive The last active time of the user.
 * @property twoFactorAuthEnabled Indicates whether two-factor authentication is enabled for the user.
 * @property app The application information associated with the user (nullable).
 *
 * @author <a href="https://github.com/antistereov">antistereov</a>
 */
@Serializable
data class UserDto(
    val id: String,
    val name: String?,
    val email: String,
    val roles: List<Role> = listOf(Role.USER),
    val emailVerified: Boolean = false,
    val devices: List<DeviceInfoResponse> = listOf(),
    val lastActive: String,
    val twoFactorAuthEnabled: Boolean,
    val app: ApplicationInfoDto?,
)
