package io.stereov.web.user.model

import io.stereov.web.global.service.random.RandomService

/**
 * # UserSecurityDetails
 *
 * This class represents the security details of a user, including two-factor authentication and email verification.
 *
 * @property twoFactor The two-factor authentication details of the user.
 * @property mail The email verification details of the user.
 *
 * @author <a href="https://github.com/antistereov">antistereov</a>
 */
data class UserSecurityDetails(
    val twoFactor: TwoFactorDetails = TwoFactorDetails(),
    val mail: MailVerificationDetails = MailVerificationDetails(),
) {

    /**
     * ## TwoFactorDetails
     *
     * This class represents the two-factor authentication details of a user.
     *
     * @property enabled Indicates whether two-factor authentication is enabled.
     * @property secret The secret key for the user.
     * @property recoveryCodes The list of recovery codes for the user.
     */
    data class TwoFactorDetails(
        var enabled: Boolean = false,
        var secret: String? = null,
        var recoveryCodes: MutableList<String> = mutableListOf()
    )

    /**
     * ## MailVerificationDetails
     *
     * This class represents the email verification details of a user.
     *
     * @property verified Indicates whether the email is verified.
     * @property verificationSecret The secret key for email verification.
     * @property passwordResetSecret The secret key for password reset.
     */
    data class MailVerificationDetails(
        var verified: Boolean = false,
        var verificationSecret: String = RandomService.generateCode(20),
        var passwordResetSecret: String = RandomService.generateCode(20),
    )
}
