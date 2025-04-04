package io.stereov.web.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * # Two-Factor Authentication properties.
 *
 * This class is responsible for holding the two-factor authentication properties
 * and is annotated with [ConfigurationProperties]
 * to bind the properties from the application configuration file.
 *
 * It is prefixed with `baseline.security.two-factor` in the configuration file.
 *
 * @property recoveryCodeLength The length of the recovery codes used for two-factor authentication.
 *
 * @author <a href="https://github.com/antistereov">antistereov</a>
 */
@ConfigurationProperties(prefix = "baseline.security.two-factor")
data class TwoFactorAuthProperties(
    val recoveryCodeLength: Int = 20
)
