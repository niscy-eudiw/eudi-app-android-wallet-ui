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

import eu.europa.ec.authenticationlogic.config.AuthenticationConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class TestSessionController {

    private var now: Long = 1_000_000L

    private val config = object : AuthenticationConfig {
        override val maxFailedPinAttempts: Int = 3
        override val pinLockoutDurations: List<Duration> = listOf(30.seconds)
        override val backgroundLockTimeout: Duration = 60.seconds
        override val idleLockTimeout: Duration = 5.minutes
        override val protectedFlowLockTimeout: Duration = 10.minutes
        override val commonPins: Set<String> = emptySet()
        override val requireDeviceSecure: Boolean = false
    }

    private lateinit var controller: SessionController

    @Before
    fun before() {
        controller = SessionControllerImpl(config) { now }
    }

    private fun advance(duration: Duration) {
        now += duration.inWholeMilliseconds
    }

    @Test
    fun `starts locked`() {
        assertFalse(controller.isUnlocked)
    }

    @Test
    fun `unlock then lock`() {
        controller.unlock()
        assertTrue(controller.isUnlocked)

        controller.lock()
        assertFalse(controller.isUnlocked)
    }

    @Test
    fun `returning before the background timeout keeps the session`() {
        controller.unlock()
        controller.onBackground()
        advance(59.seconds)

        assertFalse(controller.onForeground())
        assertTrue(controller.isUnlocked)
    }

    @Test
    fun `returning at the background timeout locks`() {
        controller.unlock()
        controller.onBackground()
        advance(60.seconds)

        assertTrue(controller.onForeground())
        assertFalse(controller.isUnlocked)
    }

    @Test
    fun `a protected flow keeps the session beyond the background timeout`() {
        controller.unlock()
        controller.recordProtectedFlow(inProtectedFlow = true)
        controller.onBackground()
        advance(9.minutes)

        assertFalse(controller.onForeground())
        assertTrue(controller.isUnlocked)
    }

    @Test
    fun `a protected flow locks at the protected-flow timeout`() {
        controller.unlock()
        controller.recordProtectedFlow(inProtectedFlow = true)
        controller.onBackground()
        advance(10.minutes)

        assertTrue(controller.onForeground())
        assertFalse(controller.isUnlocked)
    }

    @Test
    fun `an external flow uses the protected-flow timeout on any screen`() {
        controller.unlock()
        controller.beginExternalFlow()
        controller.onBackground()
        advance(9.minutes)

        assertFalse(controller.onForeground())
        assertTrue(controller.isUnlocked)
    }

    @Test
    fun `an ended external flow uses the background timeout again`() {
        controller.unlock()
        controller.beginExternalFlow()
        controller.endExternalFlow()
        controller.onBackground()
        advance(60.seconds)

        assertTrue(controller.onForeground())
    }

    @Test
    fun `a lock ends the external flow`() {
        controller.unlock()
        controller.beginExternalFlow()
        controller.lock()
        controller.unlock()
        controller.onBackground()
        advance(60.seconds)

        assertTrue(controller.onForeground())
    }

    @Test
    fun `foreground without a prior background does nothing`() {
        controller.unlock()
        advance(10.minutes)

        assertFalse(controller.onForeground())
        assertTrue(controller.isUnlocked)
    }

    @Test
    fun `foreground while locked reports no new lock`() {
        controller.onBackground()
        advance(10.minutes)

        assertFalse(controller.onForeground())
        assertFalse(controller.isUnlocked)
    }

    @Test
    fun `background time does not count towards the idle timeout`() {
        controller.unlock()
        advance(4.minutes)
        controller.onBackground()
        advance(50.seconds)
        controller.onForeground()
        advance(4.minutes)

        assertFalse(controller.lockIfIdle())
        assertTrue(controller.isUnlocked)
    }

    @Test
    fun `idle at the timeout locks`() {
        controller.unlock()
        advance(5.minutes)

        assertTrue(controller.lockIfIdle())
        assertFalse(controller.isUnlocked)
    }

    @Test
    fun `interaction restarts the idle timeout`() {
        controller.unlock()
        advance(4.minutes)
        controller.onUserInteraction()
        advance(4.minutes)

        assertFalse(controller.lockIfIdle())
        assertTrue(controller.isUnlocked)
    }

    @Test
    fun `idle while locked reports no new lock`() {
        advance(30.minutes)

        assertFalse(controller.lockIfIdle())
    }

    @Test
    fun `time until idle lock counts down and stops at zero`() {
        controller.unlock()
        advance(2.minutes)
        assertEquals(3.minutes, controller.timeUntilIdleLock())

        advance(10.minutes)
        assertEquals(Duration.ZERO, controller.timeUntilIdleLock())
    }

    @Test
    fun `time until idle lock is the full timeout while locked`() {
        assertEquals(5.minutes, controller.timeUntilIdleLock())
    }

    @Test
    fun `a protected flow recorded when hidden is replaced by the next recording`() {
        controller.unlock()
        controller.recordProtectedFlow(inProtectedFlow = true)
        controller.recordProtectedFlow(inProtectedFlow = false)
        controller.onBackground()
        advance(60.seconds)

        assertTrue(controller.onForeground())
    }
}
