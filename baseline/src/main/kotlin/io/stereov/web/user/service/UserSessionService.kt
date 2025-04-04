package io.stereov.web.user.service

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.stereov.web.auth.exception.AuthException
import io.stereov.web.auth.exception.model.InvalidCredentialsException
import io.stereov.web.auth.service.AuthenticationService
import io.stereov.web.global.service.cache.AccessTokenCache
import io.stereov.web.global.service.hash.HashService
import io.stereov.web.global.service.twofactorauth.exception.model.InvalidTwoFactorCodeException
import io.stereov.web.user.dto.ApplicationInfoDto
import io.stereov.web.user.dto.request.*
import io.stereov.web.user.exception.model.EmailAlreadyExistsException
import io.stereov.web.user.exception.model.NoAppInfoFoundException
import io.stereov.web.user.model.UserDocument
import io.stereov.web.user.service.device.UserDeviceService
import io.stereov.web.user.service.twofactor.UserTwoFactorAuthService
import org.springframework.stereotype.Service

/**
 * # Service for managing user sessions and authentication.
 *
 * This service provides methods for user login, registration, email and password changes,
 * application info retrieval, and user logout.
 * It interacts with the [UserService] to manage user data,
 * the [HashService] for password hashing,
 * the [AuthenticationService] for authentication-related operations,
 * the [UserDeviceService] for managing user devices,
 * and the [UserTwoFactorAuthService] for two-factor authentication.
 *
 * @author <a href="https://github.com/antistereov">antistereov</a>
 */
@Service
class UserSessionService(
    private val userService: UserService,
    private val hashService: HashService,
    private val authenticationService: AuthenticationService,
    private val deviceService: UserDeviceService,
    private val userTwoFactorAuthService: UserTwoFactorAuthService,
    private val accessTokenCache: AccessTokenCache,
) {

    private val logger: KLogger
        get() = KotlinLogging.logger {}

    /**
     * Logs in a user and returns the user document.
     *
     * @param payload The login request containing the user's email and password.
     *
     * @return The [UserDocument] of the logged-in user.
     *
     * @throws InvalidCredentialsException If the email or password is invalid.
     * @throws AuthException If the user document does not contain an ID.
     */
    suspend fun checkCredentialsAndGetUser(payload: LoginRequest): UserDocument {
        logger.debug { "Logging in user ${payload.email}" }

        val user = userService.findByEmailOrNull(payload.email)
            ?: throw InvalidCredentialsException()

        if (!hashService.checkBcrypt(payload.password, user.password)) {
            throw InvalidCredentialsException()
        }

        if (user.id == null) {
            throw AuthException("Login failed: UserDocument contains no id")
        }

        return userService.save(user)
    }

    /**
     * Registers a new user and returns the user document.
     *
     * @param payload The registration request containing the user's email, password, and name.
     *
     * @return The [UserDocument] of the registered user.
     *
     * @throws EmailAlreadyExistsException If the email already exists in the system.
     * @throws AuthException If the user document does not contain an ID.
     */
    suspend fun registerAndGetUser(payload: RegisterUserRequest): UserDocument {
        logger.debug { "Registering user ${payload.email}" }

        if (userService.existsByEmail(payload.email)) {
            throw EmailAlreadyExistsException("Failed to register user ${payload.email}")
        }

        val userDocument = UserDocument(
            email = payload.email,
            password = hashService.hashBcrypt(payload.password),
            name = payload.name,
        )

        val savedUserDocument = userService.save(userDocument)

        if (savedUserDocument.id == null) {
            throw AuthException("Login failed: UserDocument contains no id")
        }

        return savedUserDocument
    }

    /**
     * Changes the user's email address and returns the updated user document.
     *
     * @param payload The request containing the new email, password, and two-factor code.
     *
     * @return The [UserDocument] of the updated user.
     *
     * @throws InvalidCredentialsException If the password is invalid.
     */
    suspend fun changeEmail(payload: ChangeEmailRequest): UserDocument {
        logger.debug { "Changing email" }

        val user = authenticationService.getCurrentUser()

        if (userService.existsByEmail(payload.newEmail)) {
            throw EmailAlreadyExistsException("Failed to register user ${payload.newEmail}")
        }

        if (!hashService.checkBcrypt(payload.password, user.password)) {
            throw InvalidCredentialsException()
        }

        if (user.security.twoFactor.enabled) {
            val twoFactorCode = payload.twoFactorCode
                ?: throw InvalidTwoFactorCodeException()
            userTwoFactorAuthService.validateTwoFactorCode(user, twoFactorCode)
        }

        user.email = payload.newEmail

        return userService.save(user)
    }

    /**
     * Changes the user's password and returns the updated user document.
     *
     * @param payload The request containing the old password, new password, and two-factor code.
     *
     * @return The [UserDocument] of the updated user.
     *
     * @throws InvalidCredentialsException If the old password is invalid.
     */
    suspend fun changePassword(payload: ChangePasswordRequest): UserDocument {
        logger.debug { "Changing password" }

        val user = authenticationService.getCurrentUser()

        if (!hashService.checkBcrypt(payload.oldPassword, user.password)) {
            throw InvalidCredentialsException()
        }

        if (user.security.twoFactor.enabled) {
            val twoFactorCode = payload.twoFactorCode
                ?: throw InvalidTwoFactorCodeException()
            userTwoFactorAuthService.validateTwoFactorCode(user, twoFactorCode)
        }

        user.password = hashService.hashBcrypt(payload.newPassword)

        return userService.save(user)
    }

    /**
     * Changes the user's name and returns the updated user document.
     *
     * @param payload The request containing the new name.
     *
     * @return The [UserDocument] of the updated user.
     */
    suspend fun changeUser(payload: ChangeUserRequest): UserDocument {
        val user = authenticationService.getCurrentUser()

        user.name = payload.name

        return userService.save(user)
    }

    /**
     * Retrieves the application info for the current user.
     *
     * @return The [ApplicationInfoDto] of the user's application info.
     *
     * @throws NoAppInfoFoundException If no application info is found for the user.
     */
    suspend fun getApplicationInfo(): ApplicationInfoDto {
        logger.debug { "Getting application info" }

        val user = authenticationService.getCurrentUser()

        return user.app?.toDto()
            ?: throw NoAppInfoFoundException(user.idX)
    }

    /**
     * Logs out the user from the specified device and returns the updated user document.
     *
     * @param deviceId The ID of the device to log out from.
     *
     * @return The [UserDocument] of the logged-out user.
     */
    suspend fun logout(deviceId: String): UserDocument {
        logger.debug { "Logging out user" }

        val userId = authenticationService.getCurrentUserId()
        val tokenId = authenticationService.getCurrentTokenId()

        accessTokenCache.removeTokenId(userId, tokenId)

        return deviceService.removeDevice(deviceId)
    }

    /**
     * Logs out the user from all devices and returns the updated user document.
     *
     * @return The [UserDocument] of the logged-out user.
     */
    suspend fun logoutAllDevices(): UserDocument {
        logger.debug { "Logging out all devices" }

        val userId = authenticationService.getCurrentUserId()
        accessTokenCache.invalidateAllTokens(userId)
        val user = deviceService.clearDevices()

        return user
    }

    /**
     * Deletes the user account and invalidates all tokens.
     */
    suspend fun deleteUser() {
        logger.debug { "Deleting user" }

        val userId = authenticationService.getCurrentUserId()
        accessTokenCache.invalidateAllTokens(userId)

        userService.deleteById(userId)
    }
}
