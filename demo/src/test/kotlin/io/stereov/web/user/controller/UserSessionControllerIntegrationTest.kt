package io.stereov.web.user.controller

import io.stereov.web.config.Constants
import io.stereov.web.test.BaseIntegrationTest
import io.stereov.web.user.dto.*
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders

class UserSessionControllerIntegrationTest : BaseIntegrationTest() {

    @Test fun `getAccount returns user account`() = runTest {
        val user = registerUser()

        val responseBody = webTestClient.get()
            .uri("/user/me")
            .header(HttpHeaders.COOKIE, "${Constants.ACCESS_TOKEN_COOKIE}=${user.accessToken}")
            .exchange()
            .expectStatus().isOk
            .expectBody(UserDto::class.java)
            .returnResult()
            .responseBody

        requireNotNull(responseBody) { "Response has empty body" }

        assertEquals(user.info.email, responseBody.email)
    }
    @Test fun `getAccount needs authentication`() = runTest {
        webTestClient.get()
            .uri("/user/me")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test fun `login logs in user`() = runTest {
        val email = "test@email.com"
        val password = "password"
        val deviceId = "device"
        val user = registerUser(email, password, deviceId)
        val loginRequest = LoginRequest(email, password, DeviceInfoRequest(deviceId))

        val response = webTestClient.post()
            .uri("/user/login")
            .bodyValue(loginRequest)
            .exchange()
            .expectStatus().isOk
            .expectBody(LoginResponse::class.java)
            .returnResult()

        val accessToken = response.responseCookies[Constants.ACCESS_TOKEN_COOKIE]
            ?.firstOrNull()?.value
        val refreshToken = response.responseCookies[Constants.REFRESH_TOKEN_COOKIE]
            ?.firstOrNull()?.value
        val account = response.responseBody?.user

        requireNotNull(accessToken) { "No access token provided in response" }
        requireNotNull(refreshToken) { "No refresh token provided in response" }
        requireNotNull(account) { "No auth info provided in response" }

        assertTrue(accessToken.isNotBlank())
        assertTrue(refreshToken.isNotBlank())
        assertEquals(user.info.id, account.id)

        val userDto = webTestClient.get()
            .uri("user/me")
            .cookie(Constants.ACCESS_TOKEN_COOKIE, accessToken)
            .exchange()
            .expectStatus().isOk
            .expectBody(UserDto::class.java)
            .returnResult()
            .responseBody

        requireNotNull(userDto)

        assertEquals(user.info.id, userDto.id)
        assertEquals(user.info.email, userDto.email)

        assertEquals(1, userService.findAll().count())
    }
    @Test fun `login needs body`() = runTest {
        webTestClient.post()
            .uri("/user/login")
            .exchange()
            .expectStatus().isBadRequest
    }
    @Test fun `login needs valid credentials`() = runTest {
        val user = registerUser()

        webTestClient.post()
            .uri("/user/login")
            .bodyValue(LoginRequest(user.info.email, "wrong password", user.info.devices.first().toRequestDto()))
            .exchange()
            .expectStatus().isUnauthorized

        webTestClient.post()
            .uri("/user/login")
            .bodyValue(LoginRequest("another@email.com", "wrong password", user.info.devices.first().toRequestDto()))
            .exchange()
            .expectStatus().isUnauthorized
    }
    @Test fun `login from new device saves device`() = runTest {
        val email = "test@email.com"
        val password = "password"
        val deviceId = "device"
        val newDeviceId = "newDeviceId"

        registerUser(email, password, deviceId)

        val accessToken = webTestClient.post()
            .uri("/user/login")
            .bodyValue(LoginRequest(email, password, DeviceInfoRequest(newDeviceId)))
            .exchange()
            .expectStatus().isOk
            .expectBody(LoginResponse::class.java)
            .returnResult()
            .responseCookies[Constants.ACCESS_TOKEN_COOKIE]?.firstOrNull()?.value

        requireNotNull(accessToken) { "No access token provided in response" }

        val userInfo = webTestClient.get()
            .uri("/user/me")
            .cookie(Constants.ACCESS_TOKEN_COOKIE, accessToken)
            .exchange()
            .expectStatus().isOk
            .expectBody(UserDto::class.java)
            .returnResult()
            .responseBody

        requireNotNull(userInfo) { "No UserDetails provided in response" }

        val devices = userInfo.devices

        assertEquals(2, devices.size)
        assertTrue(devices.any { it.id == deviceId })
        assertTrue(devices.any { it.id == newDeviceId })
    }
    @Test fun `login with two factor works as expected`() = runTest {
        val email = "test@email.com"
        val password = "password"
        val deviceId = "device"

        val user = registerUser(email, password, deviceId, true)

        val loginRequest = LoginRequest(email, password, DeviceInfoRequest(deviceId))

        val response = webTestClient.post()
            .uri("/user/login")
            .bodyValue(loginRequest)
            .exchange()
            .expectStatus().isOk
            .expectBody(LoginResponse::class.java)
            .returnResult()

        val body = response.responseBody
        requireNotNull(body)

        requireNotNull(user.twoFactorToken)
        assertEquals(user.twoFactorToken, response.responseCookies[Constants.TWO_FACTOR_AUTH_COOKIE]?.firstOrNull()?.value)

        assertTrue(body.twoFactorRequired)
        assertNotNull(body.user)
    }

    @Test fun `register registers new user`() = runTest {
        val email = "test@email.com"
        val password = "password"
        val deviceId = "device"
        val deviceInfo = DeviceInfoRequest(id = deviceId)

        val response = webTestClient.post()
            .uri("/user/register")
            .bodyValue(RegisterUserRequest(email = email, password = password, device = deviceInfo))
            .exchange()
            .expectStatus().isOk
            .expectBody(UserDto::class.java)
            .returnResult()

        val accessToken = response.responseCookies[Constants.ACCESS_TOKEN_COOKIE]
            ?.firstOrNull()?.value
        val refreshToken = response.responseCookies[Constants.REFRESH_TOKEN_COOKIE]
            ?.firstOrNull()?.value
        val userDto = response.responseBody

        requireNotNull(accessToken) { "No access token provided in response" }
        requireNotNull(refreshToken) { "No refresh token provided in response" }
        requireNotNull(userDto) { "No user info provided in response" }

        assertTrue(accessToken.isNotBlank())
        assertTrue(refreshToken.isNotBlank())

        val userDetails = webTestClient.get()
            .uri("user/me")
            .cookie(Constants.ACCESS_TOKEN_COOKIE, accessToken)
            .exchange()
            .expectStatus().isOk
            .expectBody(UserDto::class.java)
            .returnResult()
            .responseBody

        requireNotNull(userDetails) { "No UserDetails provided in response" }

        assertEquals(userDto.id, userDetails.id)
        assertEquals(1, userDetails.devices.size)
        assertEquals(deviceId, userDetails.devices.first().id)

        assertEquals(1, userService.findAll().count())
    }
    @Test fun `register requires valid credentials`() = runTest {
        val deviceInfo = DeviceInfoRequest("device")
        webTestClient.post()
            .uri("/user/register")
            .bodyValue(RegisterUserRequest(email = "invalid", password = "password", device = deviceInfo))
            .exchange()
            .expectStatus().isBadRequest

        webTestClient.post()
            .uri("/user/register")
            .bodyValue(RegisterUserRequest(email = "", password = "password", device = deviceInfo))
            .exchange()
            .expectStatus().isBadRequest

        webTestClient.post()
            .uri("/user/register")
            .bodyValue(RegisterUserRequest(email = "test@email.com", password = "", device = deviceInfo))
            .exchange()
            .expectStatus().isBadRequest
    }
    @Test fun `register needs body`() = runTest {
        webTestClient.post()
            .uri("/user/login")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test fun `checkAuthentication requires authentication`() = runTest {
        webTestClient.get()
            .uri("/user/me")
            .exchange()
            .expectStatus().isUnauthorized
    }
    @Test fun `checkAuthentication returns user`() = runTest {
        val user = registerUser()

        val response = webTestClient.get()
            .uri("/user/me")
            .cookie(Constants.ACCESS_TOKEN_COOKIE, user.accessToken)
            .exchange()
            .expectStatus().isOk
            .expectBody(UserDto::class.java)
            .returnResult()
            .responseBody

        requireNotNull(response) { "Response body is empty" }

        assertEquals(user.info.id, response.id)
    }

    @Test fun `refresh requires body`() = runTest {
        webTestClient.post()
            .uri("/user/refresh")
            .exchange()
            .expectStatus().isBadRequest
    }
    @Test fun `refresh requires token`() = runTest {
        val deviceInfo = DeviceInfoRequest("device")
        webTestClient.post()
            .uri("/user/me")
            .bodyValue(deviceInfo)
            .exchange()
            .expectStatus().isUnauthorized
    }
    @Test fun `refresh requires valid token`() = runTest {
        val deviceInfo = DeviceInfoRequest("device")
        webTestClient.post()
            .uri("/user/me")
            .cookie(Constants.REFRESH_TOKEN_COOKIE, "Refresh")
            .bodyValue(deviceInfo)
            .exchange()
            .expectStatus().isUnauthorized
    }
    @Test fun `refresh requires associated token to account`() = runTest {
        val user = registerUser()
        val refreshToken = userTokenService.createRefreshToken(user.info.id!!, user.info.devices.first().id)
        webTestClient.post()
            .uri("/user/refresh")
            .cookie(Constants.REFRESH_TOKEN_COOKIE, refreshToken)
            .bodyValue(DeviceInfoRequest(user.info.devices.firstOrNull()?.id!!))
            .exchange()
            .expectStatus().isUnauthorized
    }
    @Test fun `refresh token is valid once`() = runTest {
        val user = registerUser()
        webTestClient.post()
            .uri("/user/refresh")
            .cookie(Constants.REFRESH_TOKEN_COOKIE, user.refreshToken)
            .bodyValue(DeviceInfoRequest(user.info.devices.firstOrNull()?.id!!))
            .exchange()
            .expectStatus().isOk

        webTestClient.post()
            .uri("/user/refresh")
            .cookie(Constants.REFRESH_TOKEN_COOKIE, user.refreshToken)
            .bodyValue(DeviceInfoRequest(user.info.devices.firstOrNull()?.id!!))
            .exchange()
            .expectStatus().isUnauthorized
    }
    @Test fun `refresh token requires associated device`() = runTest {
        val user = registerUser()
        webTestClient.post()
            .uri("/user/refresh")
            .cookie(Constants.REFRESH_TOKEN_COOKIE, user.refreshToken)
            .bodyValue(DeviceInfoRequest("another device"))
            .exchange()
            .expectStatus().isUnauthorized
    }
    @Test fun `refresh returns valid tokens`() = runTest {
        val user = registerUser()
        val response = webTestClient.post()
            .uri("/user/refresh")
            .cookie(Constants.REFRESH_TOKEN_COOKIE, user.refreshToken)
            .bodyValue(DeviceInfoRequest(user.info.devices.first().id))
            .exchange()
            .expectStatus().isOk
            .expectBody(UserDto::class.java)
            .returnResult()

        val account = response.responseBody
        val accessToken = response.responseCookies[Constants.ACCESS_TOKEN_COOKIE]
            ?.firstOrNull()?.value
        val refreshToken = response.responseCookies[Constants.REFRESH_TOKEN_COOKIE]
            ?.firstOrNull()?.value

        requireNotNull(account) { "No account provided in response" }
        requireNotNull(accessToken) { "No access token provided in response" }
        requireNotNull(refreshToken) { "No refresh token provided in response" }

        assertTrue(accessToken.isNotBlank())
        assertTrue(refreshToken.isNotBlank())

        assertEquals(user.info.id, account.id)

        webTestClient.post()
            .uri("/user/refresh")
            .cookie(Constants.REFRESH_TOKEN_COOKIE, refreshToken)
            .bodyValue(DeviceInfoRequest(user.info.devices.first().id))
            .exchange()
            .expectStatus().isOk

        webTestClient.get()
            .uri("/user/me")
            .cookie(Constants.ACCESS_TOKEN_COOKIE, accessToken)
            .exchange()
            .expectStatus().isOk
    }

    @Test fun `logout requires body`() = runTest {
        val user = registerUser()

        webTestClient.post()
            .uri("/user/logout")
            .cookie(Constants.ACCESS_TOKEN_COOKIE, user.accessToken)
            .exchange()
            .expectStatus().isBadRequest
    }
    @Test fun `logout deletes all cookies and logs out user`() = runTest {
        val user = registerUser()

        val response = webTestClient.post()
            .uri("/user/logout")
            .bodyValue(DeviceInfoRequest(user.info.devices.first().id))
            .cookie(Constants.ACCESS_TOKEN_COOKIE, user.accessToken)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .returnResult()

        val cookies = response.responseCookies

        val accessToken = cookies[Constants.ACCESS_TOKEN_COOKIE]?.firstOrNull()?.value
        val refreshToken = cookies[Constants.ACCESS_TOKEN_COOKIE]?.firstOrNull()?.value

        assertTrue(accessToken.isNullOrBlank())
        assertTrue(refreshToken.isNullOrBlank())

        val account = response.responseBody

        requireNotNull(account) { "No account provided in response" }
    }
    @Test fun `logout requires authentication`() = runTest {
        webTestClient.post()
            .uri("/account/logout")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test fun `delete requires authentication`() = runTest {
        webTestClient.delete()
            .uri("/user/me")
            .exchange()
            .expectStatus().isUnauthorized
    }
    @Test fun `delete deletes all cookies and deletes user`() = runTest {
        val user = registerUser()

        val response = webTestClient.delete()
            .uri("/user/me")
            .cookie(Constants.ACCESS_TOKEN_COOKIE, user.accessToken)
            .exchange()
            .expectBody()
            .returnResult()

        val cookies = response.responseCookies

        val accessToken = cookies[Constants.ACCESS_TOKEN_COOKIE]?.firstOrNull()?.value
        val refreshToken = cookies[Constants.ACCESS_TOKEN_COOKIE]?.firstOrNull()?.value

        assertTrue(accessToken.isNullOrBlank())
        assertTrue(refreshToken.isNullOrBlank())

        assertEquals(0, userService.findAll().count())
    }

    // TODO: Add tests for user updates
}
