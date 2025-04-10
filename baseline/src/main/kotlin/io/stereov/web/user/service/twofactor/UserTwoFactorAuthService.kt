package io.stereov.web.user.service.twofactor

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.stereov.web.auth.exception.AuthException
import io.stereov.web.auth.service.AuthenticationService
import io.stereov.web.auth.service.CookieService
import io.stereov.web.global.service.cache.AccessTokenCache
import io.stereov.web.global.service.encryption.EncryptionService
import io.stereov.web.global.service.hash.HashService
import io.stereov.web.global.service.random.RandomService
import io.stereov.web.global.service.twofactorauth.TwoFactorAuthService
import io.stereov.web.properties.TwoFactorAuthProperties
import io.stereov.web.user.dto.UserDto
import io.stereov.web.user.dto.response.TwoFactorSetupResponse
import io.stereov.web.user.exception.model.InvalidUserDocumentException
import io.stereov.web.user.model.UserDocument
import io.stereov.web.user.service.UserService
import io.stereov.web.user.service.token.TwoFactorAuthTokenService
import org.springframework.stereotype.Service
import org.springframework.web.server.ServerWebExchange

/**
 * # Service for managing two-factor authentication (2FA) for users.
 *
 * This service provides methods to set up, validate, and recover two-factor authentication for users.
 * It uses the [TwoFactorAuthService] to generate and validate codes,
 * the [EncryptionService] to encrypt and decrypt secrets,
 * and the [HashService] to hash and check recovery codes.
 * It interacts with the [UserService] to save user data and the [AuthenticationService] to get the current user.
 *
 * @author <a href="https://github.com/antistereov">antistereov</a>
 */
@Service
class UserTwoFactorAuthService(
    private val userService: UserService,
    private val twoFactorAuthService: TwoFactorAuthService,
    private val encryptionService: EncryptionService,
    private val authenticationService: AuthenticationService,
    private val twoFactorAuthProperties: TwoFactorAuthProperties,
    private val hashService: HashService,
    private val cookieService: CookieService,
    private val twoFactorAuthTokenService: TwoFactorAuthTokenService,
    private val accessTokenCache: AccessTokenCache,
) {

    private val logger: KLogger
        get() = KotlinLogging.logger {}

    /**
     * Sets up two-factor authentication for the current user.
     * It generates a secret key, an OTP auth URL, a recovery code, and a token.
     * The token is used to validate the setup process and enable two-factor authentication for the current user.
     *
     * @return A [TwoFactorSetupResponse] containing the secret, OTP auth URL, recovery code and setup token.
     */
    suspend fun setUpTwoFactorAuth(): TwoFactorSetupResponse {
        logger.debug { "Setting up two factor authentication" }

        val user = authenticationService.getCurrentUser()

        val secret = twoFactorAuthService.generateSecretKey()
        val otpAuthUrl = twoFactorAuthService.getOtpAuthUrl(user.email, secret)
        val recoveryCodes = List(twoFactorAuthProperties.recoveryCodeCount) {
            RandomService.generateCode(twoFactorAuthProperties.recoveryCodeLength)
        }

        val setupToken = twoFactorAuthTokenService.createSetupToken(user.idX, secret, recoveryCodes)

        return TwoFactorSetupResponse(secret, otpAuthUrl, recoveryCodes, setupToken)
    }

    /**
     * Validates the setup token and enables two-factor authentication for the current user.
     *
     * @param token The setup token to validate.
     * @param code The two-factor authentication code to validate.
     *
     * @throws InvalidUserDocumentException If the user document does not contain a two-factor authentication secret.
     * @throws AuthException If the setup token is invalid.
     *
     * @return The updated user document.
     */
    suspend fun validateSetup(token: String, code: Int): UserDto {
        val user = authenticationService.getCurrentUser()
        val setupToken = twoFactorAuthTokenService.validateAndExtractSetupToken(token)

        if (!twoFactorAuthService.validateCode(setupToken.secret, code)) {
            throw AuthException("Invalid two-factor authentication code")
        }

        val encryptedSecret = encryptionService.encrypt(setupToken.secret)
        val hashedRecoveryCodes = setupToken.recoveryCodes.map {
            hashService.hashBcrypt(it)
        }

        user.setupTwoFactorAuth(encryptedSecret, hashedRecoveryCodes)
            .clearDevices()

        userService.save(user)
        accessTokenCache.invalidateAllTokens(user.idX)

        return user.toDto()
    }

    /**
     * Validates the two-factor code for the current user.
     *
     * @param exchange The server web exchange containing the request and response.
     * @param code The two-factor code to validate.
     *
     * @throws InvalidUserDocumentException If the user document does not contain a two-factor authentication secret.
     * @throws AuthException If the two-factor code is invalid.
     */
    suspend fun validateTwoFactorCode(exchange: ServerWebExchange, code: Int): UserDocument {
        logger.debug { "Validating two factor code" }

        val userId = cookieService.validateLoginVerificationCookieAndGetUserId(exchange)

        val user = userService.findById(userId)

        return twoFactorAuthService.validateTwoFactorCode(user, code)
    }

    /**
     * Checks if the user has two-factor authentication pending.
     *
     * @param exchange The server web exchange containing the request and response.
     *
     * @return True if two-factor authentication is pending, false otherwise.
     */
    suspend fun twoFactorPending(exchange: ServerWebExchange): Boolean {
        logger.debug { "Checking two factor authentication status" }

        return try {
            cookieService.validateLoginVerificationCookieAndGetUserId(exchange)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Recovers the user by validating the recovery code and clearing all devices and
     * therefore, signing out the user on all devices.
     *
     * @param exchange The server web exchange containing the request and response.
     * @param recoveryCode The recovery code to validate.
     *
     * @throws AuthException If the recovery code is invalid.
     *
     * @return The user document after recovery.
     */
    suspend fun recoverUser(exchange: ServerWebExchange, recoveryCode: String): UserDocument {
        logger.debug { "Recovering user and clearing all devices" }

        val userId = try {
            authenticationService.getCurrentUserId()
        } catch (e: Exception) {
            cookieService.validateLoginVerificationCookieAndGetUserId(exchange)
        }

        val user = userService.findById(userId)
        val recoveryCodeHashes = user.security.twoFactor.recoveryCodes

        val match = recoveryCodeHashes.removeAll { hash ->
            hashService.checkBcrypt(recoveryCode, hash)
        }

        if (!match) {
            throw AuthException("Invalid recovery code")
        }

        return userService.save(user)
    }

    /**
     * Disables two-factor authentication for the current user.
     *
     * @return The updated user document.
     */
    suspend fun disable(exchange: ServerWebExchange): UserDto {
        logger.debug { "Disabling 2FA" }

        cookieService.validateStepUpCookie(exchange)

        val user = authenticationService.getCurrentUser()

        user.disableTwoFactorAuth()

        return userService.save(user).toDto()
    }
}
