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

package eu.europa.ec.corelogic.extension

import eu.europa.ec.eudi.wallet.transactionLogging.model.TransactionEntry
import eu.europa.ec.eudi.wallet.transactionLogging.toTransactionEntryOrNull
import eu.europa.ec.storagelogic.model.TransactionLog as StorageTransaction

// Returns null if the stored transaction cannot be read.
internal fun StorageTransaction.toCoreTransactionLog(): TransactionEntry? =
    this.value.toTransactionEntryOrNull()

/**
 * Whether this entry concerns attestations of [attestationType] (a docType or vct): a presentation
 * that requested or disclosed claims of that type, or an issuance or re-issuance of it. Wallet Core
 * records the attestation type, not the document, so an entry cannot be tied to one document. An
 * entry that covers several types matches each of them. No other entry is matched, which keeps
 * deletion records, signing entries, presentation actions, and issuances that record no type.
 */
internal fun TransactionEntry.referencesAttestationType(attestationType: String): Boolean =
    when (this) {
        is TransactionEntry.Presentation ->
            (listOfClaimsRequested + listOfClaimsPresented).any { it.credentialIdentifier == attestationType }

        is TransactionEntry.CredentialIssuance -> attestationType in details.credentialIdentifier
        is TransactionEntry.CredentialReissuance -> attestationType in details.credentialIdentifier
        else -> false
    }