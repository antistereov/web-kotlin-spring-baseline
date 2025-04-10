package io.stereov.web.user.controller

import io.stereov.web.auth.exception.model.TwoFactorAuthDisabledException
import io.stereov.web.auth.service.CookieService
import io.stereov.web.global.service.jwt.exception.TokenException
import io.stereov.web.user.dto.UserDto
import io.stereov.web.user.dto.request.DeviceInfoRequest
import io.stereov.web.user.dto.request.TwoFactorSetupRequest
import io.stereov.web.user.dto.response.StepUpStatusResponse
import io.stereov.web.user.dto.response.TwoFactorSetupResponse
import io.stereov.web.user.dto.response.TwoFactorStatusResponse
import io.stereov.web.user.service.twofactor.UserTwoFactorAuthService
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ServerWebExchange

/**
 * # UserTwoFactorAuthController
 *
 * This controller handles two-factor authentication (2FA) for users.
 * It provides endpoints for setting up 2FA, verifying 2FA codes,
 * checking 2FA status, and recovering user accounts.
 *
 * @author <a href="https://github.com/antistereov">antistereov</a>
 */
@Controller
@RequestMapping("/user/2fa")
class UserTwoFactorAuthController(
    private val twoFactorService: UserTwoFactorAuthService,
    private val cookieService: CookieService,
) {

    @GetMapping("/setup")
    suspend fun setupTwoFactorAuth(): ResponseEntity<TwoFactorSetupResponse> {
        val res = twoFactorService.setUpTwoFactorAuth()

        return ResponseEntity.ok().body(res)
    }

    @PostMapping("/setup")
    suspend fun validateTwoFactorSetup(
        @RequestBody setupRequest: TwoFactorSetupRequest
    ): ResponseEntity<UserDto> {
        return ResponseEntity.ok(
            twoFactorService.validateSetup(setupRequest.token, setupRequest.code)
        )
    }

    @PostMapping("/recovery")
    suspend fun recoverUser(
        @RequestParam("code") code: String,
        exchange: ServerWebExchange,
        @RequestBody device: DeviceInfoRequest
    ): ResponseEntity<UserDto> {
        val user = twoFactorService.recoverUser(exchange, code)

        val ipAddress = exchange.request.remoteAddress?.address?.hostAddress

        val accessTokenCookie = cookieService.createAccessTokenCookie(user.idX, device.id)
        val refreshTokenCookie = cookieService.createRefreshTokenCookie(user.idX, device, ipAddress)

        val clearTwoFactorCookie = cookieService.clearLoginVerificationCookie()
        return ResponseEntity.ok()
            .header("Set-Cookie", clearTwoFactorCookie.toString())
            .header("Set-Cookie", accessTokenCookie.toString())
            .header("Set-Cookie", refreshTokenCookie.toString())
            .body(user.toDto())
    }

    @PostMapping("/verify-login")
    suspend fun verifyTwoFactorAuth(
        @RequestParam("code") code: Int,
        exchange: ServerWebExchange,
        @RequestBody device: DeviceInfoRequest
    ): ResponseEntity<UserDto> {
        val user = twoFactorService.validateTwoFactorCode(exchange, code)

        val ipAddress = exchange.request.remoteAddress?.address?.hostAddress

        val accessTokenCookie = cookieService.createAccessTokenCookie(user.idX, device.id)
        val refreshTokenCookie = cookieService.createRefreshTokenCookie(user.idX, device, ipAddress)

        val clearTwoFactorCookie = cookieService.clearLoginVerificationCookie()
        return ResponseEntity.ok()
            .header("Set-Cookie", clearTwoFactorCookie.toString())
            .header("Set-Cookie", accessTokenCookie.toString())
            .header("Set-Cookie", refreshTokenCookie.toString())
            .body(user.toDto())
    }

    @GetMapping("/login-status")
    suspend fun getTwoFactorAuthStatus(exchange: ServerWebExchange): ResponseEntity<TwoFactorStatusResponse> {
        val isPending = twoFactorService.twoFactorPending(exchange)

        val res = ResponseEntity.ok()

        if (!isPending) {
            val clearTwoFactorTokenCookie = cookieService.clearLoginVerificationCookie()
            res.header("Set-Cookie", clearTwoFactorTokenCookie.toString())
        }

        return res.body(TwoFactorStatusResponse(isPending))
    }

    /**
     * Set the step-up authentication status.
     *
     * @param code The step-up authentication code.
     *
     * @return A response indicating the success of the operation.
     */
    @PostMapping("/verify-step-up")
    suspend fun setStepUp(@RequestParam code: Int): ResponseEntity<StepUpStatusResponse> {
        val stepUpTokenCookie = cookieService.createStepUpCookie(code)

        return ResponseEntity.ok()
            .header("Set-Cookie", stepUpTokenCookie.toString())
            .body(StepUpStatusResponse(true))
    }

    /**
     * Get the step-up authentication status.
     *
     * @param exchange The server web exchange.
     *
     * @return The step-up authentication status as a [StepUpStatusResponse].
     */
    @GetMapping("/step-up-status")
    suspend fun getStepUpStatus(exchange: ServerWebExchange): ResponseEntity<StepUpStatusResponse> {
        val stepUpStatus = try {
            cookieService.validateStepUpCookie(exchange)
            true
        } catch (e: TokenException) {
            false
        } catch (e: TwoFactorAuthDisabledException) {
            true
        }

        return ResponseEntity.ok(StepUpStatusResponse(stepUpStatus))
    }

    /**
     * Disable two-factor authentication for the user.
     * This requires a step-up authentication token.
     *
     * @param exchange The server web exchange.
     *
     * @return The updated user information as a [UserDto].
     */
    @PostMapping("/disable")
    suspend fun disableTwoFactorAuth(exchange: ServerWebExchange): ResponseEntity<UserDto> {
        return ResponseEntity.ok(twoFactorService.disable(exchange))
    }
}
