/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.authenticationlogic.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

interface AuthenticationConfig {

    /**
     * Number of consecutive wrong PIN attempts allowed before the user is locked out.
     */
    val maxFailedPinAttempts: Int

    /**
     * Lockout durations applied each time the user reaches [maxFailedPinAttempts].
     * The list indexes by lockout level (0 = first lockout). Once the user exceeds the
     * size of the list, the last entry is reused for every subsequent lockout.
     */
    val pinLockoutDurations: List<Duration>

    /**
     * How long the app may stay in the background before the wallet locks. Returning after
     * longer than this requires the PIN or a biometric again.
     */
    val backgroundLockTimeout: Duration

    /**
     * How long the app may stay in the foreground without user interaction before the wallet locks.
     * It applies on every screen, including those of an issuance or presentation.
     */
    val idleLockTimeout: Duration

    /**
     * How long the app may stay in the background while an issuance, presentation or signing is
     * in progress, in place of [backgroundLockTimeout]. Issuance and signing send the user to a
     * website and back. A flow is in progress while the screen in front is one of
     * `ConfigUILogic.protectedFlowScreens`, or while a flow started elsewhere, such as a
     * re-issuance from document details, is marked with `SessionController.beginExternalFlow`.
     * [Duration.INFINITE] never locks during such a flow.
     */
    val protectedFlowLockTimeout: Duration
}

class AuthenticationConfigImpl : AuthenticationConfig {
    override val maxFailedPinAttempts: Int = 3
    override val pinLockoutDurations: List<Duration> = listOf(
        30.seconds,
        90.seconds,
        5.minutes
    )
    override val backgroundLockTimeout: Duration = 60.seconds
    override val idleLockTimeout: Duration = 5.minutes
    override val protectedFlowLockTimeout: Duration = 10.minutes
}
