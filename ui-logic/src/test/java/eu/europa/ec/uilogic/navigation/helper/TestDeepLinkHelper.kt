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

package eu.europa.ec.uilogic.navigation.helper

import android.net.Uri
import androidx.navigation.NavController
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TestDeepLinkHelper {

    private val navController: NavController = mock {
        on { context } doReturn RuntimeEnvironment.getApplication()
    }

    @Test
    fun `Given no signing session, When an RQES sign-in callback arrives, Then it is ignored without a crash`() {
        handleDeepLinkAction(
            navController = navController,
            uri = Uri.parse("rqes://oauth/callback?code=forged-code"),
        )
    }
}
