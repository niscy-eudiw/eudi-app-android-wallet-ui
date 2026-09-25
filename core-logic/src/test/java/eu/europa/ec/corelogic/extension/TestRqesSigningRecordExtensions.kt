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

import eu.europa.ec.corelogic.model.InteractingPartyDomain
import eu.europa.ec.corelogic.model.LocalizedTextDomain
import eu.europa.ec.corelogic.model.TransactionLogDomain
import eu.europa.ec.corelogic.model.TransactionResultDomain
import eu.europa.ec.corelogic.util.mockedEnglishLocale
import eu.europa.ec.corelogic.util.mockedTransactionId
import eu.europa.ec.corelogic.util.mockedTransactionTime
import eu.europa.ec.eudi.rqes.core.RqesSigningRecord
import eu.europa.ec.eudi.wallet.transactionLogging.model.MultiLangString
import eu.europa.ec.eudi.wallet.transactionLogging.model.TransactionEntry
import eu.europa.ec.eudi.wallet.transactionLogging.model.TransactionResult
import eu.europa.ec.eudi.wallet.transactionLogging.toJson
import eu.europa.ec.eudi.wallet.transactionLogging.toTransactionEntryOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class TestRqesSigningRecordExtensions {

    //region toSigningEntries

    // Case 1:
    // 1. A completed signing record contains all available metadata.
    //
    // Case 1 Expected Result:
    // One entry preserves the metadata, language, supplied identity and time.
    @Test
    fun `Given a completed record, When toSigningEntries is called, Then all recorded metadata is preserved`() {
        // Given
        val record = mockedRecord

        // When
        val entries = record.toSigningEntries(
            idProvider = { mockedTransactionId },
            time = mockedTransactionTime,
        )

        // Then
        assertEquals(listOf(mockedEntry), entries)
    }

    // Case 2:
    // 1. Failed records contain a reason or no reason.
    //
    // Case 2 Expected Result:
    // Every entry remains failed with the supplied reason and attempted file.
    @Test
    fun `Given failed records, When toSigningEntries is called, Then nullable reasons and attempted documents are preserved`() {
        // Given
        val reasons = listOf(mockedFailureReason, null)

        // When
        val entries = reasons.map { reason ->
            mockedRecord.copy(outcome = RqesSigningRecord.Outcome.Failed(reason))
                .toSigningEntries(
                    idProvider = { mockedTransactionId },
                    time = mockedTransactionTime,
                ).single()
        }

        // Then
        assertEquals(
            listOf(
                TransactionResult.NotCompleted(mockedFailureReason),
                TransactionResult.NotCompleted(null),
            ),
            entries.map { entry -> entry.transactionResult },
        )
        assertEquals(listOf(mockedFileName, mockedFileName), entries.map { entry -> entry.fileName })
    }

    // Case 3:
    // 1. A record has no optional metadata and its document label is empty.
    //
    // Case 3 Expected Result:
    // No identifiers, contacts, certificate, digest, size or name are invented.
    @Test
    fun `Given missing optional metadata, When toSigningEntries is called, Then absent fields remain absent`() {
        // Given
        val record = mockedRecord.copy(
            certificateSerialNumber = null,
            documents = listOf(
                RqesSigningRecord.SignedDocument(
                    label = "",
                    dtbsr = null,
                    sizeBytes = null
                )
            ),
            serviceName = null,
        )

        // When
        val entry = record.toSigningEntries(
            idProvider = { mockedTransactionId },
            time = mockedTransactionTime,
        ).single()

        // Then
        assertNull(entry.certificateIdentifier)
        assertNull(entry.dtbsr)
        assertNull(entry.fileSize)
        assertNull(entry.interactingPartyName)
        assertNull(entry.signingTransactionIdentifier)
        assertNull(entry.fileIdentifier)
        assertNull(entry.interactingPartyIdentifier)
        assertNull(entry.interactingPartyContact)
        assertEquals("", entry.fileName)
        assertEquals(
            TransactionEntry.SigningSealing.INTERACTING_PARTY_TYPE,
            entry.interactingPartyType
        )
    }

    // Case 4:
    // 1. Three documents include repeated names and digests.
    //
    // Case 4 Expected Result:
    // Three entries retain input order and one callback time, with separate supplied IDs.
    @Test
    fun `Given repeated document metadata, When toSigningEntries is called, Then entries retain order and distinct identities`() {
        // Given
        val record = mockedRecord.copy(
            documents = listOf(
                mockedDocument,
                mockedDocument,
                mockedDocument.copy(label = mockedOtherFileName),
            ),
        )
        val ids = mockedEntryIds.iterator()

        // When
        val entries = record.toSigningEntries(
            idProvider = ids::next,
            time = mockedTransactionTime,
        )

        // Then
        assertEquals(mockedEntryIds, entries.map { entry -> entry.transactionIdentifier })
        assertEquals(
            listOf(mockedFileName, mockedFileName, mockedOtherFileName),
            entries.map { entry -> entry.fileName })
        assertEquals(listOf(mockedDigest, mockedDigest, mockedDigest), entries.map { entry -> entry.dtbsr })
        assertEquals(
            listOf(mockedTransactionTime, mockedTransactionTime, mockedTransactionTime),
            entries.map { entry -> entry.time })
        assertFalse(ids.hasNext())
    }

    // Case 5:
    // 1. A callback contains no documents.
    //
    // Case 5 Expected Result:
    // No entries or IDs are created.
    @Test
    fun `Given no documents, When toSigningEntries is called, Then no signing entry is fabricated`() {
        // Given
        val record = mockedRecord.copy(documents = emptyList())

        // When
        val entries = record.toSigningEntries(
            idProvider = { throw AssertionError("An empty record must not request an ID") },
            time = mockedTransactionTime,
        )

        // Then
        assertTrue(entries.isEmpty())
    }

    // Case 6:
    // 1. Documents have zero and maximum Long byte sizes.
    //
    // Case 6 Expected Result:
    // Sizes remain lossless decimal strings.
    @Test
    fun `Given boundary file sizes, When toSigningEntries is called, Then byte sizes remain exact`() {
        // Given
        val record = mockedRecord.copy(
            documents = listOf(
                mockedDocument.copy(sizeBytes = 0L),
                mockedDocument.copy(sizeBytes = Long.MAX_VALUE),
            ),
        )
        val ids = mockedEntryIds.iterator()

        // When
        val entries = record.toSigningEntries(idProvider = ids::next, time = mockedTransactionTime)

        // Then
        assertEquals(listOf("0", "9223372036854775807"), entries.map { entry -> entry.fileSize })
    }

    // Case 7:
    // 1. A mapped entry is serialized and read through the existing transaction domain mapper.
    //
    // Case 7 Expected Result:
    // Storage preserves the complete entry, and the domain retains the signing display fields.
    @Test
    fun `Given a mapped signing, When stored and read, Then the existing signing domain preserves its content`() {
        // Given
        val entry = mockedRecord.toSigningEntries(
            idProvider = { mockedTransactionId },
            time = mockedTransactionTime,
        ).single()

        // When
        val restored = entry.toJson().toTransactionEntryOrNull()
        val domain = restored?.toTransactionLogDomain(
            id = mockedTransactionId,
            userLocale = mockedEnglishLocale,
            parentPresentationId = null,
            communicationMethod = null,
        )

        // Then
        assertEquals(entry, restored)
        assertEquals(
            TransactionLogDomain.SigningSealing(
                id = mockedTransactionId,
                time = mockedTransactionTime.atZone(ZoneId.systemDefault()).toLocalDateTime(),
                result = TransactionResultDomain.Completed,
                service = InteractingPartyDomain(
                    name = LocalizedTextDomain(
                        mockedServiceName.languageTag,
                        mockedServiceName.name
                    ),
                    identifier = null,
                    contacts = emptyList(),
                ),
                serviceType = TransactionEntry.SigningSealing.INTERACTING_PARTY_TYPE,
                signingTransactionId = null,
                certificateSerialNumber = mockedCertificate,
                fileName = mockedFileName,
                fileSizeBytes = mockedFileSize,
                dtbsr = mockedDigest,
            ),
            domain,
        )
    }

    // Case 8:
    // 1. Identical records arrive as separate callbacks with different receipt times.
    //
    // Case 8 Expected Result:
    // Each callback uses its supplied time and requests a fresh ID.
    @Test
    fun `Given separate callbacks, When toSigningEntries is called, Then attempts do not share an identity`() {
        // Given
        val ids = mockedEntryIds.iterator()
        val laterTime = mockedTransactionTime.plusSeconds(1)

        // When
        val first =
            mockedRecord.toSigningEntries(idProvider = ids::next, time = mockedTransactionTime)
                .single()
        val second =
            mockedRecord.toSigningEntries(idProvider = ids::next, time = laterTime).single()

        // Then
        assertEquals(
            mockedEntryIds.take(2),
            listOf(first.transactionIdentifier, second.transactionIdentifier)
        )
        assertEquals(listOf(mockedTransactionTime, laterTime), listOf(first.time, second.time))
    }

    //endregion

    //region helper data

    private val mockedFileName = "agreement.pdf"
    private val mockedOtherFileName = "annex.pdf"
    private val mockedDigest = "AAECA/8="
    private val mockedCertificate = "00A12F"
    private val mockedFileSize = 12345L
    private val mockedFailureReason = "Signing was declined"
    private val mockedEntryIds = listOf(mockedTransactionId, "second-entry", "third-entry")
    private val mockedServiceName = RqesSigningRecord.LocalizedName(
        languageTag = "el-GR",
        name = "Πάροχος υπογραφής",
    )
    private val mockedDocument = RqesSigningRecord.SignedDocument(
        label = mockedFileName,
        dtbsr = mockedDigest,
        sizeBytes = mockedFileSize,
    )
    private val mockedRecord = RqesSigningRecord(
        outcome = RqesSigningRecord.Outcome.Completed,
        certificateSerialNumber = mockedCertificate,
        documents = listOf(mockedDocument),
        serviceName = mockedServiceName,
    )
    private val mockedEntry = TransactionEntry.SigningSealing(
        transactionIdentifier = mockedTransactionId,
        time = mockedTransactionTime,
        transactionResult = TransactionResult.Completed,
        certificateIdentifier = mockedCertificate,
        dtbsr = mockedDigest,
        fileName = mockedFileName,
        fileSize = "12345",
        interactingPartyName = MultiLangString(
            mockedServiceName.languageTag,
            mockedServiceName.name
        ),
    )

    //endregion
}