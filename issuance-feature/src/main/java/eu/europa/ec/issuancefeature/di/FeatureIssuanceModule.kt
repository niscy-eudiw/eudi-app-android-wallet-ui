/*
 * Copyright (c) 2023 European Commission
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

package eu.europa.ec.issuancefeature.di

import eu.europa.ec.commonfeature.interactor.DeviceAuthenticationInteractor
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.issuancefeature.interactor.document.AddDocumentInteractor
import eu.europa.ec.issuancefeature.interactor.document.AddDocumentInteractorImpl
import eu.europa.ec.issuancefeature.interactor.document.DocumentDetailsInteractor
import eu.europa.ec.issuancefeature.interactor.document.DocumentDetailsInteractorImpl
import eu.europa.ec.issuancefeature.interactor.document.DocumentIssuanceSuccessInteractor
import eu.europa.ec.issuancefeature.interactor.document.DocumentIssuanceSuccessInteractorImpl
import eu.europa.ec.issuancefeature.interactor.document.DocumentOfferInteractor
import eu.europa.ec.issuancefeature.interactor.document.DocumentOfferInteractorImpl
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.storagelogic.controller.BookmarkStorageController
import eu.europa.ec.uilogic.serializer.UiSerializer
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module

@Module
@ComponentScan("eu.europa.ec.issuancefeature")
class FeatureIssuanceModule

@Factory
fun provideAddDocumentInteractor(
    walletCoreDocumentsController: WalletCoreDocumentsController,
    resourceProvider: ResourceProvider,
    deviceAuthenticationInteractor: DeviceAuthenticationInteractor,
    uiSerializer: UiSerializer
): AddDocumentInteractor =
    AddDocumentInteractorImpl(
        walletCoreDocumentsController,
        deviceAuthenticationInteractor,
        resourceProvider,
        uiSerializer
    )

@Factory
fun provideDocumentDetailsInteractor(
    walletCoreDocumentsController: WalletCoreDocumentsController,
    bookmarkStorageController: BookmarkStorageController,
    resourceProvider: ResourceProvider,
): DocumentDetailsInteractor =
    DocumentDetailsInteractorImpl(
        walletCoreDocumentsController,
        bookmarkStorageController,
        resourceProvider
    )

@Factory
fun provideDocumentIssuanceSuccessInteractor(
    walletCoreDocumentsController: WalletCoreDocumentsController,
    resourceProvider: ResourceProvider
): DocumentIssuanceSuccessInteractor = DocumentIssuanceSuccessInteractorImpl(
    walletCoreDocumentsController,
    resourceProvider,
)

@Factory
fun provideDocumentOfferInteractor(
    walletCoreDocumentsController: WalletCoreDocumentsController,
    resourceProvider: ResourceProvider,
    deviceAuthenticationInteractor: DeviceAuthenticationInteractor,
    uiSerializer: UiSerializer
): DocumentOfferInteractor =
    DocumentOfferInteractorImpl(
        walletCoreDocumentsController,
        deviceAuthenticationInteractor,
        resourceProvider,
        uiSerializer
    )