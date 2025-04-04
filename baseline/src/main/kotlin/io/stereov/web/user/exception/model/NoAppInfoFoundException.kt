package io.stereov.web.user.exception.model

import io.stereov.web.user.exception.UserException

/**
 * # No application info found exception.
 *
 * This exception is thrown when no application info is found for a user.
 *
 * @param userId The ID of the user for whom no application info was found.
 *
 * @author <a href="https://github.com/antistereov">antistereov</a>
 */
class NoAppInfoFoundException(userId: String) : UserException(
    message = "No application info found for user $userId"
)
