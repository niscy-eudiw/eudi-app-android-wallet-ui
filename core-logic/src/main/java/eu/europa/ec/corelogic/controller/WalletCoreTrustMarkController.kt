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
import eu.europa.ec.corelogic.di.WalletCoreScope
import eu.europa.ec.corelogic.di.getOrCreateKoinScope
import eu.europa.ec.corelogic.extension.getLocalizedText
import eu.europa.ec.corelogic.model.TrustMarkDomain
import eu.europa.ec.eudi.wallet.EudiWallet
import eu.europa.ec.eudi.wallet.trustmark.TrustMark
import eu.europa.ec.eudi.wallet.trustmark.TrustMarkInformation
import eu.europa.ec.eudi.wallet.trustmark.TrustMarkResource
import eu.europa.ec.eudi.wallet.trustmark.TrustMarkSource
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.serialization.json.Json

interface WalletCoreTrustMarkController {

    /**
     * The wallet's Trust Mark. Its information comes from `WalletCoreConfig.trustMarkSource`; its
     * image and text from the resource bundled in the app or, with [TrustMarkResourceSource.Remote],
     * from the resource Wallet Core fetches.
     */
    suspend fun getTrustMark(): Result<TrustMarkDomain>
}

class WalletCoreTrustMarkControllerImpl(
    private val prefKeys: PrefKeys,
    private val resourceProvider: ResourceProvider,
    private val walletCoreConfig: WalletCoreConfig,
) : WalletCoreTrustMarkController {

    override suspend fun getTrustMark(): Result<TrustMarkDomain> = runCatching {
        val resource = walletCoreConfig.trustMarkResource
        val trustMark = when (resource) {
            is TrustMarkResourceSource.Bundled -> TrustMark(
                information = walletCoreConfig.trustMarkSource.information(),
                resource = Json.decodeFromString<TrustMarkResource>(
                    resourceProvider.getStringFromRaw(resource.resId)
                ),
            )

            TrustMarkResourceSource.Remote -> fetchedTrustMark()
        }
        TrustMarkDomain(
            resourceUrl = when (resource) {
                is TrustMarkResourceSource.Bundled -> ""
                TrustMarkResourceSource.Remote -> trustMark.information.trustMarkResourceURL
            },
            imageName = trustMark.resource.image.name,
            imageUrl = trustMark.resource.image.url,
            localisedText = trustMark.resource.text.getLocalizedText(
                userLocale = resourceProvider.getLocale()
            ),
            certifiedWalletsUrl = trustMark.information.listOfCertifiedWalletsURL,
            walletSolutionUrl = trustMark.information.walletSolutionInfoPageURL,
            isCertified = walletCoreConfig.isWalletCertified,
        )
    }

    /** The Trust Mark as Wallet Core's manager returns it, its resource fetched over the network. */
    private suspend fun fetchedTrustMark(): TrustMark {
        val sessionId = prefKeys.getSessionId()
        check(sessionId.isNotBlank()) { "Missing wallet session" }
        val wallet = getOrCreateKoinScope<WalletCoreScope>(sessionId).get<EudiWallet>()
        val manager = checkNotNull(wallet.trustMarkManager) { "Missing Trust Mark manager" }
        return manager.getTrustMark().getOrThrow()
    }

    /** The information the source gives, read as Wallet Core reads it. */
    private suspend fun TrustMarkSource.information(): TrustMarkInformation = when (this) {
        is TrustMarkSource.Static -> information
        is TrustMarkSource.Dynamic -> provider.getTrustMarkInformation().getOrThrow()
    }
}
