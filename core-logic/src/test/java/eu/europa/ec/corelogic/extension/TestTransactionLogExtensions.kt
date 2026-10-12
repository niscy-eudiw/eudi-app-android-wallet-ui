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

import eu.europa.ec.corelogic.util.mockedTransactionId
import eu.europa.ec.corelogic.util.mockedTransactionTime
import eu.europa.ec.eudi.wallet.transactionLogging.model.ClaimInfo
import eu.europa.ec.eudi.wallet.transactionLogging.model.ClaimPath
import eu.europa.ec.eudi.wallet.transactionLogging.model.TransactionEntry
import eu.europa.ec.eudi.wallet.transactionLogging.model.TransactionResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TestTransactionLogExtensions {

    private val pidType = "eu.europa.ec.eudi.pid.1"
    private val mdlType = "org.iso.18013.5.1.mDL"

    @Test
    fun `Given a presentation that disclosed claims of the type, When referencesAttestationType is called, Then it matches`() {
        val entry = presentation(requested = emptyList(), presented = listOf(pidType))

        assertTrue(entry.referencesAttestationType(pidType))
    }

    @Test
    fun `Given a presentation that only requested claims of the type, When referencesAttestationType is called, Then it matches`() {
        val entry = presentation(requested = listOf(pidType), presented = emptyList())

        assertTrue(entry.referencesAttestationType(pidType))
    }

    @Test
    fun `Given a presentation of another type, When referencesAttestationType is called, Then it does not match`() {
        val entry = presentation(requested = listOf(mdlType), presented = listOf(mdlType))

        assertFalse(entry.referencesAttestationType(pidType))
    }

    @Test
    fun `Given an issuance and a re-issuance of the type, When referencesAttestationType is called, Then both match`() {
        val details = TransactionEntry.CredentialIssuanceDetails(
            credentialNumberRequested = 1,
            credentialNumberIssued = 1,
            credentialIdentifier = listOf(pidType),
        )

        assertTrue(
            TransactionEntry.CredentialIssuance(mockedTransactionId, mockedTransactionTime, TransactionResult.Completed, details)
                .referencesAttestationType(pidType)
        )
        assertTrue(
            TransactionEntry.CredentialReissuance(mockedTransactionId, mockedTransactionTime, TransactionResult.Completed, details)
                .referencesAttestationType(pidType)
        )
    }

    @Test
    fun `Given the record of a deletion of the type, When referencesAttestationType is called, Then it is kept`() {
        val entry = TransactionEntry.CredentialDeletion(
            mockedTransactionId,
            mockedTransactionTime,
            TransactionResult.Completed,
            pidType,
        )

        assertFalse(entry.referencesAttestationType(pidType))
    }

    private fun presentation(requested: List<String>, presented: List<String>) =
        TransactionEntry.Presentation(
            transactionIdentifier = mockedTransactionId,
            time = mockedTransactionTime,
            transactionResult = TransactionResult.Completed,
            listOfClaimsRequested = requested.map { claimInfo(it) },
            listOfClaimsPresented = presented.map { claimInfo(it) },
        )

    private fun claimInfo(attestationType: String) = ClaimInfo(
        credentialIdentifier = attestationType,
        claims = listOf(ClaimPath(listOf(ClaimPath.Segment.Key("family_name")))),
    )
}
