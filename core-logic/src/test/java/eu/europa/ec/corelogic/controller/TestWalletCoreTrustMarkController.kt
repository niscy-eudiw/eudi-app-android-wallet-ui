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

package eu.europa.ec.corelogic.controller

import eu.europa.ec.businesslogic.controller.storage.PrefKeys
import eu.europa.ec.corelogic.config.TrustMarkResourceSource
import eu.europa.ec.corelogic.config.WalletCoreConfig
import eu.europa.ec.corelogic.model.TrustMarkDomain
import eu.europa.ec.eudi.wallet.trustmark.TrustMarkInformation
import eu.europa.ec.eudi.wallet.trustmark.TrustMarkProvider
import eu.europa.ec.eudi.wallet.trustmark.TrustMarkSource
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Locale

class TestWalletCoreTrustMarkController {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val prefKeys: PrefKeys = mock()

    private val resourceProvider: ResourceProvider = mock()

    private val walletCoreConfig: WalletCoreConfig = mock()

    private val controller = WalletCoreTrustMarkControllerImpl(
        prefKeys = prefKeys,
        resourceProvider = resourceProvider,
        walletCoreConfig = walletCoreConfig,
    )

    @Before
    fun before() {
        whenever(resourceProvider.getLocale()).thenReturn(Locale.ENGLISH)
        whenever(walletCoreConfig.trustMarkResource)
            .thenReturn(TrustMarkResourceSource.Bundled(resId = BUNDLED_RESOURCE))
        whenever(resourceProvider.getStringFromRaw(BUNDLED_RESOURCE)).thenReturn(RESOURCE_JSON)
    }

    @Test
    fun `Given a bundled resource, When getTrustMark is called, Then it is read from the app and Wallet Core is not asked`() =
        coroutineRule.runTest {
            whenever(walletCoreConfig.trustMarkSource).thenReturn(
                TrustMarkSource.Static(
                    information.copy(trustMarkResourceURL = "https://resource.example/TrustMarkResource.json")
                )
            )
            whenever(walletCoreConfig.isWalletCertified).thenReturn(false)

            val result = controller.getTrustMark().getOrThrow()

            assertEquals(
                TrustMarkDomain(
                    resourceUrl = "",
                    imageName = "logo.png",
                    imageUrl = "https://commission.example/logo.svg",
                    localisedText = "Sample Trust Mark",
                    certifiedWalletsUrl = information.listOfCertifiedWalletsURL,
                    walletSolutionUrl = information.walletSolutionInfoPageURL,
                    isCertified = false,
                ),
                result,
            )
            verify(prefKeys, never()).getSessionId()
        }

    @Test
    fun `Given information from a provider and a certified build, When getTrustMark is called, Then both are used`() =
        coroutineRule.runTest {
            val provided = information.copy(walletSolutionInfoPageURL = "https://commission.example/wallet/42")
            whenever(walletCoreConfig.trustMarkSource)
                .thenReturn(TrustMarkSource.Dynamic(provider = TrustMarkProvider { Result.success(provided) }))
            whenever(walletCoreConfig.isWalletCertified).thenReturn(true)

            val result = controller.getTrustMark().getOrThrow()

            assertEquals("https://commission.example/wallet/42", result.walletSolutionUrl)
            assertTrue(result.isCertified)
        }

    @Test
    fun `Given a bundled resource that cannot be read, When getTrustMark is called, Then it fails`() =
        coroutineRule.runTest {
            whenever(walletCoreConfig.trustMarkSource).thenReturn(TrustMarkSource.Static(information))
            whenever(resourceProvider.getStringFromRaw(BUNDLED_RESOURCE)).thenReturn("")

            val result = controller.getTrustMark()

            assertTrue(result.isFailure)
        }

    private val information = TrustMarkInformation(
        trustMarkResourceURL = "",
        listOfCertifiedWalletsURL = "https://commission.example/wallets",
        walletSolutionInfoPageURL = "https://commission.example/wallets?id=WALLET_SOLUTION_ID",
    )

    private companion object {
        const val BUNDLED_RESOURCE = 7
        const val RESOURCE_JSON = """{"text":{"name":"Sample","localisations":{"en":"Sample Trust Mark"}},""" +
                """"image":{"url":"https://commission.example/logo.svg","name":"logo.png"}}"""
    }
}
