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

import android.os.Bundle
import eu.europa.ec.uilogic.navigation.helper.DeepLinkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class TestHostActivityDecisions {

    private val saved: Bundle = mock()

    @Test
    fun `Given a process that has not started its navigation, When the activity is recreated, Then no screens are restored`() {
        assertNull(stateToRestore(saved, navigationStartedInProcess = false))
    }

    @Test
    fun `Given a configuration change, When the activity is recreated, Then its screens are restored`() {
        assertSame(saved, stateToRestore(saved, navigationStartedInProcess = true))
    }

    @Test
    fun `Given a recreated activity, When it starts, Then its launch intent is not opened again`() {
        assertFalse(opensLaunchIntent(saved))
        assertTrue(opensLaunchIntent(null))
    }

    @Test
    fun `Given incoming links, When one starts no wallet flow, Then only that one is dropped`() {
        val dropped = DeepLinkType.entries.filter(::dropsIncomingLink)

        assertEquals(listOf(DeepLinkType.EXTERNAL), dropped)
    }

    @Test
    fun `Given a declined request, When it brought the wallet up, Then the activity finishes, and otherwise not`() {
        assertTrue(finishesOnDeclinedRequest(coldBoot = true, walletWasShown = true))
        assertTrue(finishesOnDeclinedRequest(coldBoot = false, walletWasShown = false))
        assertFalse(finishesOnDeclinedRequest(coldBoot = false, walletWasShown = true))
    }
}
