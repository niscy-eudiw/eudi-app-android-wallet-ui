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

package eu.europa.ec.authenticationlogic.controller.session

import android.os.SystemClock
import eu.europa.ec.authenticationlogic.config.AuthenticationConfig
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Whether the wallet is unlocked, and when it must lock again.
 *
 * The wallet starts locked. A successful login or PIN creation unlocks it, and it locks again
 * after [AuthenticationConfig.backgroundLockTimeout] in the background or
 * [AuthenticationConfig.idleLockTimeout] without interaction. While an issuance, presentation or
 * signing is in progress (recorded with [recordProtectedFlow] when the app's screen is hidden, or
 * marked with [beginExternalFlow]), the background limit is
 * [AuthenticationConfig.protectedFlowLockTimeout] instead, so that a visit to the issuer's website
 * does not end the session.
 *
 * Time is measured with a monotonic clock, so changing the device clock does not extend a session.
 */
interface SessionController {

    /** True between a successful authentication and the next lock. */
    val isUnlocked: Boolean

    /** Called when the user has authenticated to open the wallet. */
    fun unlock()

    /** Ends the session. */
    fun lock()

    /** Records user interaction, which restarts the idle timeout. */
    fun onUserInteraction()

    /** Records that the app has gone to the background. */
    fun onBackground()

    /**
     * Records, when the app's screen is hidden, whether it belonged to a protected flow. The
     * screen cannot change while the app is in the background, so this is the state the next
     * [onForeground] judges by, before anything on return can navigate.
     */
    fun recordProtectedFlow(inProtectedFlow: Boolean)

    /**
     * Called when the app returns to the foreground. Locks the wallet, and returns true, if it
     * stayed in the background for at least the background timeout, or the protected-flow timeout
     * when a protected flow was recorded or an external flow is in progress. Otherwise the idle
     * timeout restarts, so time spent in the background does not count towards it.
     */
    fun onForeground(): Boolean

    /** Locks the wallet, and returns true, if the idle timeout has expired. */
    fun lockIfIdle(): Boolean

    /** Time left before the idle timeout expires, for scheduling the next [lockIfIdle] check. */
    fun timeUntilIdleLock(): Duration

    /**
     * Marks a flow that leaves the app and returns, started from a screen that is not a
     * protected-flow screen, as in progress until [endExternalFlow] or the next lock.
     */
    fun beginExternalFlow()

    /** Ends the flow marked by [beginExternalFlow]. */
    fun endExternalFlow()
}

class SessionControllerImpl(
    private val authenticationConfig: AuthenticationConfig,
    private val elapsedRealtime: () -> Long = { SystemClock.elapsedRealtime() },
) : SessionController {

    @Volatile
    private var unlocked: Boolean = false

    @Volatile
    private var lastInteractionAt: Long = 0L

    @Volatile
    private var backgroundSince: Long? = null

    @Volatile
    private var externalFlowInProgress: Boolean = false

    @Volatile
    private var protectedFlowWhenHidden: Boolean = false

    override val isUnlocked: Boolean
        get() = unlocked

    override fun unlock() {
        unlocked = true
        lastInteractionAt = elapsedRealtime()
        backgroundSince = null
    }

    override fun lock() {
        unlocked = false
        externalFlowInProgress = false
    }

    override fun onUserInteraction() {
        lastInteractionAt = elapsedRealtime()
    }

    override fun onBackground() {
        backgroundSince = elapsedRealtime()
    }

    override fun recordProtectedFlow(inProtectedFlow: Boolean) {
        protectedFlowWhenHidden = inProtectedFlow
    }

    override fun onForeground(): Boolean {
        val since = backgroundSince ?: return false
        backgroundSince = null
        if (!unlocked) return false

        val now = elapsedRealtime()
        val limit = if (protectedFlowWhenHidden || externalFlowInProgress) {
            authenticationConfig.protectedFlowLockTimeout
        } else {
            authenticationConfig.backgroundLockTimeout
        }
        if ((now - since).milliseconds >= limit) {
            lock()
            return true
        }
        lastInteractionAt = now
        return false
    }

    override fun lockIfIdle(): Boolean {
        if (!unlocked) return false
        if ((elapsedRealtime() - lastInteractionAt).milliseconds >= authenticationConfig.idleLockTimeout) {
            lock()
            return true
        }
        return false
    }

    override fun timeUntilIdleLock(): Duration {
        if (!unlocked) return authenticationConfig.idleLockTimeout
        val idle = (elapsedRealtime() - lastInteractionAt).milliseconds
        return (authenticationConfig.idleLockTimeout - idle).coerceAtLeast(Duration.ZERO)
    }

    override fun beginExternalFlow() {
        externalFlowInProgress = true
    }

    override fun endExternalFlow() {
        externalFlowInProgress = false
    }
}
