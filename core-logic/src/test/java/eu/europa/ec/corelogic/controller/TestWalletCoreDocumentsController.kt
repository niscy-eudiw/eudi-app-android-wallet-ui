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
import eu.europa.ec.corelogic.config.WalletCoreConfig
import eu.europa.ec.eudi.wallet.EudiWallet
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.document.Outcome
import eu.europa.ec.eudi.wallet.document.format.MsoMdocFormat
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.storagelogic.dao.BookmarkDao
import eu.europa.ec.storagelogic.dao.FailedReIssuedDocumentDao
import eu.europa.ec.storagelogic.dao.IssuerTrustDao
import eu.europa.ec.storagelogic.dao.RevokedDocumentDao
import eu.europa.ec.storagelogic.dao.StatusRefreshFailureDao
import eu.europa.ec.storagelogic.model.Bookmark
import eu.europa.ec.storagelogic.model.FailedReIssuedDocument
import eu.europa.ec.storagelogic.model.IssuerTrustRecord
import eu.europa.ec.storagelogic.model.IssuerTrustVerdict
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class TestWalletCoreDocumentsController {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var resourceProvider: ResourceProvider

    @Mock
    private lateinit var walletCoreConfig: WalletCoreConfig

    @Mock
    private lateinit var bookmarkDao: BookmarkDao

    @Mock
    private lateinit var revokedDocumentDao: RevokedDocumentDao

    @Mock
    private lateinit var failedReIssuedDocumentDao: FailedReIssuedDocumentDao

    @Mock
    private lateinit var issuerTrustDao: IssuerTrustDao

    @Mock
    private lateinit var statusRefreshFailureDao: StatusRefreshFailureDao

    @Mock
    private lateinit var walletCoreTransactionLogController: WalletCoreTransactionLogController

    @Mock
    private lateinit var prefKeys: PrefKeys

    @Mock
    private lateinit var eudiWallet: EudiWallet

    private lateinit var controller: WalletCoreDocumentsController

    private lateinit var closeable: AutoCloseable

    private val pidType = "eu.europa.ec.eudi.pid.1"
    private val pid: IssuedDocument = mock {
        on { id } doReturn "pid"
        on { format } doReturn MsoMdocFormat(pidType)
    }
    private val otherPid: IssuedDocument = mock {
        on { id } doReturn "otherPid"
        on { format } doReturn MsoMdocFormat(pidType)
    }

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        runBlocking {
            whenever(prefKeys.getSessionId()).thenReturn("session")
        }
        whenever(eudiWallet.getDocumentById("pid")).thenReturn(pid)
        controller = WalletCoreDocumentsControllerImpl(
            resourceProvider = resourceProvider,
            walletCoreConfig = walletCoreConfig,
            bookmarkDao = bookmarkDao,
            revokedDocumentDao = revokedDocumentDao,
            failedReIssuedDocumentDao = failedReIssuedDocumentDao,
            issuerTrustDao = issuerTrustDao,
            statusRefreshFailureDao = statusRefreshFailureDao,
            walletCoreTransactionLogController = walletCoreTransactionLogController,
            prefKeys = prefKeys,
            dispatcher = coroutineRule.testDispatcher,
            walletCore = eudiWallet,
        )
    }

    @After
    fun after() {
        closeable.close()
    }

    private fun implementation() = controller as WalletCoreDocumentsControllerImpl

    @Test
    fun `Given the last document of its type, When it is deleted, Then its records and the history of its type go`() =
        coroutineRule.runTest {
            whenever(eudiWallet.deleteDocumentById("pid")).thenReturn(Outcome.success(null))
            whenever(eudiWallet.getDocuments(anyOrNull())).thenReturn(emptyList())

            val result = controller.deleteDocument("pid").first()

            assertEquals(DeleteDocumentPartialState.Success, result)
            verify(revokedDocumentDao).delete("pid")
            verify(issuerTrustDao).delete("pid")
            verify(statusRefreshFailureDao).delete("pid")
            verify(bookmarkDao).delete("pid")
            verify(failedReIssuedDocumentDao).delete("pid")
            verify(walletCoreTransactionLogController).deleteTransactionLogsForAttestationType(pidType)
        }

    @Test
    fun `Given another document of the same type remains, When one is deleted, Then the history is kept`() =
        coroutineRule.runTest {
            whenever(eudiWallet.deleteDocumentById("pid")).thenReturn(Outcome.success(null))
            whenever(eudiWallet.getDocuments(anyOrNull())).thenReturn(listOf(otherPid))

            val result = controller.deleteDocument("pid").first()

            assertEquals(DeleteDocumentPartialState.Success, result)
            verify(bookmarkDao).delete("pid")
            verify(walletCoreTransactionLogController, never()).deleteTransactionLogsForAttestationType(any())
        }

    @Test
    fun `Given a storage error after Wallet Core deleted the document, When it is deleted, Then the deletion is still reported`() =
        coroutineRule.runTest {
            whenever(eudiWallet.deleteDocumentById("pid")).thenReturn(Outcome.success(null))
            whenever(eudiWallet.getDocuments(anyOrNull())).thenReturn(emptyList())
            whenever(bookmarkDao.delete("pid")).doThrow(IllegalStateException("database closed"))

            val result = controller.deleteDocument("pid").first()

            assertEquals(DeleteDocumentPartialState.Success, result)
        }

    @Test
    fun `Given a re-issuance replaced the document, When it is cleaned up, Then the bookmark moves and the original records go`() =
        coroutineRule.runTest {
            whenever(eudiWallet.getDocumentById("pid")).thenReturn(null)
            whenever(bookmarkDao.retrieve("pid")).thenReturn(Bookmark("pid"))

            implementation().cleanUpAfterReIssuance(
                originalId = "pid",
                state = IssueDocumentsPartialState.Success(documentIds = listOf("newPid")),
            )

            verify(bookmarkDao).store(Bookmark("newPid"))
            verify(bookmarkDao).delete("pid")
            verify(revokedDocumentDao).delete("pid")
            verify(issuerTrustDao).delete("pid")
        }

    @Test
    fun `Given a deferred re-issuance, When it is cleaned up, Then the bookmark is copied and the original is kept until replaced`() =
        coroutineRule.runTest {
            whenever(bookmarkDao.retrieve("pid")).thenReturn(Bookmark("pid"))

            implementation().cleanUpAfterReIssuance(
                originalId = "pid",
                state = IssueDocumentsPartialState.DeferredSuccess(deferredDocuments = mapOf("newPid" to pidType)),
            )

            verify(bookmarkDao).store(Bookmark("newPid"))
            verify(bookmarkDao, never()).delete(any())
            verify(revokedDocumentDao, never()).delete(any())
        }

    @Test
    fun `Given a failed re-issuance, When it is cleaned up, Then nothing changes`() =
        coroutineRule.runTest {
            implementation().cleanUpAfterReIssuance(
                originalId = "pid",
                state = IssueDocumentsPartialState.Failure(errorMessage = "error"),
            )

            verifyNoInteractions(bookmarkDao, revokedDocumentDao, issuerTrustDao)
        }

    @Test
    fun `Given records of a document Wallet Core no longer holds, When missing documents are cleaned up, Then only those records go`() =
        coroutineRule.runTest {
            whenever(revokedDocumentDao.retrieveAll()).thenReturn(emptyList())
            whenever(issuerTrustDao.retrieveAll()).thenReturn(
                listOf(
                    IssuerTrustRecord(identifier = "oldPid", verdict = IssuerTrustVerdict.TRUSTED),
                    IssuerTrustRecord(identifier = "otherPid", verdict = IssuerTrustVerdict.TRUSTED),
                )
            )
            whenever(statusRefreshFailureDao.retrieveAll()).thenReturn(emptyList())
            whenever(bookmarkDao.retrieveAll()).thenReturn(listOf(Bookmark("oldPid")))
            whenever(failedReIssuedDocumentDao.retrieveAll()).thenReturn(listOf(FailedReIssuedDocument("otherPid")))
            whenever(eudiWallet.getDocuments(anyOrNull())).thenReturn(listOf(otherPid))

            implementation().deleteRecordsOfMissingDocuments()

            verify(issuerTrustDao).delete("oldPid")
            verify(bookmarkDao).delete("oldPid")
            verify(issuerTrustDao, never()).delete("otherPid")
            verify(failedReIssuedDocumentDao, never()).delete("otherPid")
        }

    @Test
    fun `Given Wallet Core fails to delete the document, When it is deleted, Then nothing else is deleted`() =
        coroutineRule.runTest {
            whenever(eudiWallet.deleteDocumentById("pid"))
                .thenReturn(Outcome.failure(IllegalStateException("storage error")))
            whenever(resourceProvider.genericErrorMessage()).thenReturn("error")

            val result = controller.deleteDocument("pid").first()

            assertTrue(result is DeleteDocumentPartialState.Failure)
            verifyNoInteractions(
                revokedDocumentDao,
                issuerTrustDao,
                statusRefreshFailureDao,
                bookmarkDao,
                failedReIssuedDocumentDao,
                walletCoreTransactionLogController,
            )
        }
}
