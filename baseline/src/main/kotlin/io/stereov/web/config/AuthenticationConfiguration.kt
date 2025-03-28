package io.stereov.web.config

import io.stereov.web.auth.service.AuthenticationService
import io.stereov.web.auth.service.CookieService
import io.stereov.web.global.service.encryption.EncryptionService
import io.stereov.web.global.service.geolocation.GeoLocationService
import io.stereov.web.global.service.hash.HashService
import io.stereov.web.global.service.jwt.JwtService
import io.stereov.web.global.service.twofactorauth.TwoFactorAuthService
import io.stereov.web.properties.AppProperties
import io.stereov.web.properties.JwtProperties
import io.stereov.web.properties.TwoFactorAuthProperties
import io.stereov.web.user.controller.UserDeviceController
import io.stereov.web.user.controller.UserSessionController
import io.stereov.web.user.controller.UserTwoFactorAuthController
import io.stereov.web.user.repository.UserRepository
import io.stereov.web.user.service.*
import io.stereov.web.user.service.device.UserDeviceService
import io.stereov.web.user.service.token.TwoFactorAuthTokenService
import io.stereov.web.user.service.token.UserTokenService
import io.stereov.web.user.service.twofactor.UserTwoFactorAuthService
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
import org.springframework.boot.autoconfigure.data.web.SpringDataWebAutoConfiguration
import org.springframework.boot.autoconfigure.mongo.MongoReactiveAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories
import org.springframework.web.reactive.function.client.WebClient

/**
 * # Authentication configuration class.
 *
 * This class is responsible for configuring the authentication-related services
 * and components in the application.
 *
 * It runs after the [MongoReactiveAutoConfiguration], [SpringDataWebAutoConfiguration],
 * [RedisAutoConfiguration], and [ApplicationConfiguration] classes to ensure that
 * the necessary configurations are applied in the correct order.
 *
 * This class enables the following services:
 * - [AuthenticationService]
 * - [GeoLocationService]
 * - [HashService]
 * - [TwoFactorAuthService]
 * - [UserService]
 * - [UserSessionService]
 * - [CookieService]
 * - [UserDeviceService]
 * - [UserTwoFactorAuthService]
 *
 * It enabled the following controllers:
 * - [UserSessionController]
 * - [UserTwoFactorAuthController]
 * - [UserDeviceController]
 *
 * @author <a href="https://github.com/antistereov">antistereov</a>
 */
@Configuration
@AutoConfiguration(
    after = [
        MongoReactiveAutoConfiguration::class,
        SpringDataWebAutoConfiguration::class,
        RedisAutoConfiguration::class,
        ApplicationConfiguration::class,
    ]
)
@EnableReactiveMongoRepositories(
    basePackageClasses = [UserRepository::class]
)
class AuthenticationConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun authenticationService(userService: UserService): AuthenticationService {
        return AuthenticationService(userService)
    }

    @Bean
    @ConditionalOnMissingBean
    fun geoLocationService(webClient: WebClient): GeoLocationService {
        return GeoLocationService(webClient)
    }

    @Bean
    @ConditionalOnMissingBean
    fun hashService(): HashService {
        return HashService()
    }

    @Bean
    @ConditionalOnMissingBean
    fun twoFactorAuthService(): TwoFactorAuthService {
        return twoFactorAuthService()
    }

    @Bean
    @ConditionalOnMissingBean
    fun userService(userRepository: UserRepository): UserService {
        return UserService(userRepository)
    }

    @Bean
    @ConditionalOnMissingBean
    fun userSessionController(
        authenticationService: AuthenticationService,
        userService: UserService,
        userSessionService: UserSessionService,
        cookieService: CookieService,
    ): UserSessionController {
        return UserSessionController(authenticationService, userSessionService, cookieService)
    }

    @Bean
    @ConditionalOnMissingBean
    fun userSessionService(
        userService: UserService,
        hashService: HashService,
        jwtService: JwtService,
        authenticationService: AuthenticationService,
        deviceService: UserDeviceService,
        userTwoFactorAuthService: UserTwoFactorAuthService,
    ): UserSessionService {
        return UserSessionService(
            userService,
            hashService,
            authenticationService,
            deviceService,
            userTwoFactorAuthService
        )
    }

    @Bean
    @ConditionalOnMissingBean
    fun cookieService(
        userTokenService: UserTokenService,
        jwtProperties: JwtProperties,
        appProperties: AppProperties,
        geoLocationService: GeoLocationService,
        userService: UserService,
        twoFactorAuthTokenService: TwoFactorAuthTokenService
    ): CookieService {
        return CookieService(userTokenService, jwtProperties, appProperties, geoLocationService, userService, twoFactorAuthTokenService)
    }

    @Bean
    @ConditionalOnMissingBean
    fun userDeviceService(
        userService: UserService,
        authenticationService: AuthenticationService,
    ) : UserDeviceService {
        return UserDeviceService(userService, authenticationService)
    }

    @Bean
    @ConditionalOnMissingBean
    fun userTwoFactorAuthService(
        userService: UserService,
        twoFactorAuthService: TwoFactorAuthService,
        encryptionService: EncryptionService,
        authenticationService: AuthenticationService,
        twoFactorAuthProperties: TwoFactorAuthProperties,
        hashService: HashService,
        cookieService: CookieService
    ): UserTwoFactorAuthService {
        return UserTwoFactorAuthService(userService, twoFactorAuthService, encryptionService, authenticationService, twoFactorAuthProperties, hashService, cookieService)
    }

    @Bean
    @ConditionalOnMissingBean
    fun userTwoFactorAuthController(
        userTwoFactorAuthService: UserTwoFactorAuthService,
        cookieService: CookieService
    ): UserTwoFactorAuthController {
        return UserTwoFactorAuthController(userTwoFactorAuthService, cookieService)
    }

    @Bean
    @ConditionalOnMissingBean
    fun userDeviceController(
        userDeviceService: UserDeviceService
    ): UserDeviceController {
        return UserDeviceController(userDeviceService)
    }
}
