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

package eu.europa.ec.uilogic.container

import android.content.Intent
import eu.europa.ec.authenticationlogic.controller.session.SessionController
import eu.europa.ec.businesslogic.controller.storage.PrefKeys
import eu.europa.ec.businesslogic.provider.UuidProvider
import eu.europa.ec.corelogic.provider.DCAPISettingProvider
import eu.europa.ec.corelogic.worker.WalletWorkScheduler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TestEudiComponentActivityViewModel {

    private val sessionController: SessionController = mock()

    private val uuidProvider: UuidProvider = mock {
        on { provideUuid() } doReturn "session"
    }

    private val prefKeys: PrefKeys = mock()

    private val walletWorkScheduler: WalletWorkScheduler = mock()

    private val viewModel = EudiComponentActivityViewModel(
        prefKeys = prefKeys,
        dcapiSettingProvider = mock<DCAPISettingProvider>(),
        sessionController = sessionController,
        walletWorkScheduler = walletWorkScheduler,
        uuidProvider = uuidProvider,
    )

    private val link: Intent = mock()

    @Test
    fun `Given the activity is created, When it starts, Then the session id is set before the start-up check runs`() {
        viewModel.onCreate()

        inOrder(prefKeys, walletWorkScheduler) {
            runBlocking { verify(prefKeys).setSessionId("session") }
            verify(walletWorkScheduler).scheduleStartupCheck()
        }
    }

    @Test
    fun `Given a locked wallet with a kept link, When it is read or cleared, Then it is withheld and kept until login`() {
        whenever(sessionController.isUnlocked).thenReturn(true)
        viewModel.cacheIntent(link)
        whenever(sessionController.isUnlocked).thenReturn(false)

        assertNull(viewModel.getCachedIntent())
        viewModel.cacheIntent(null)

        whenever(sessionController.isUnlocked).thenReturn(true)
        assertSame(link, viewModel.getCachedIntent())
    }

    @Test
    fun `Given an unlocked wallet with a kept link, When it is cleared, Then it is gone`() {
        whenever(sessionController.isUnlocked).thenReturn(true)
        viewModel.cacheIntent(link)

        viewModel.cacheIntent(null)

        assertNull(viewModel.getCachedIntent())
    }

    @Test
    fun `Given a locked wallet with a kept link, When a factory reset forgets it, Then it is gone after login too`() {
        whenever(sessionController.isUnlocked).thenReturn(false)
        viewModel.cacheIntent(link)

        viewModel.forgetCachedIntent()

        whenever(sessionController.isUnlocked).thenReturn(true)
        assertNull(viewModel.getCachedIntent())
    }
}
