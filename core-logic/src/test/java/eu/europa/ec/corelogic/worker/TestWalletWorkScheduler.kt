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

package eu.europa.ec.corelogic.worker

import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import eu.europa.ec.corelogic.config.WalletCoreConfig
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
class TestWalletWorkScheduler {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context: Context = RuntimeEnvironment.getApplication()

    private val walletCoreConfig: WalletCoreConfig = mock(defaultAnswer = RETURNS_DEEP_STUBS)

    @Before
    fun before() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
    }

    @After
    fun after() {
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test
    fun `Given no worker is working, When the work is stopped, Then it returns true`() {
        coroutineRule.testScope.runTest {
            val scheduler = buildScheduler(workerLocks = listOf(Mutex(), Mutex()))

            assertTrue(scheduler.stop())
        }
    }

    @Test
    fun `Given a worker is working, When the work is stopped, Then it waits until the worker has finished`() {
        coroutineRule.testScope.runTest {
            val workerLock = Mutex(locked = true)
            val scheduler = buildScheduler(workerLocks = listOf(Mutex(), workerLock))

            val stopped = async { scheduler.stop() }
            runCurrent()

            assertFalse(stopped.isCompleted)
            workerLock.unlock()
            assertTrue(stopped.await())
        }
    }

    @Test
    fun `Given a worker that does not finish, When the work is stopped, Then it returns false when the wait ends`() {
        coroutineRule.testScope.runTest {
            val scheduler = buildScheduler(workerLocks = listOf(Mutex(locked = true)))

            assertFalse(scheduler.stop())
        }
    }

    @Test
    fun `Given the periodic work is scheduled, When nothing else is called, Then no start-up check is queued`() {
        buildScheduler(workerLocks = emptyList()).schedule()

        assertEquals(0, startupCheckCount())
    }

    @Test
    fun `Given the start-up check is asked for twice in a process, When it is scheduled, Then it is queued once`() {
        val scheduler = buildScheduler(workerLocks = emptyList())

        scheduler.scheduleStartupCheck()
        val queued = startupCheckIds()
        scheduler.scheduleStartupCheck()

        assertEquals(1, queued.size)
        assertEquals(queued, startupCheckIds())
    }

    private fun startupCheckCount(): Int = startupCheckIds().size

    private fun startupCheckIds(): List<UUID> = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWork(RevocationWorkManager.REVOCATION_ON_START_WORK_NAME)
        .get()
        .map { it.id }

    private fun buildScheduler(workerLocks: List<Mutex>) = WalletWorkSchedulerImpl(
        context = context,
        walletCoreConfig = walletCoreConfig,
        stopTimeout = 30.seconds,
        workerLocks = workerLocks,
    )
}
