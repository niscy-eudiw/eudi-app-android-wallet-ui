/*
 * Copyright (c) 2025 European Commission
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

package eu.europa.ec.dashboardfeature.interactor

import eu.europa.ec.businesslogic.provider.UuidProvider
import eu.europa.ec.corelogic.controller.DeleteAllDocumentsPartialState
import eu.europa.ec.corelogic.controller.DeleteDocumentPartialState
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.model.ClaimDomain
import eu.europa.ec.corelogic.model.ClaimPathDomain
import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.dashboardfeature.ui.documents.detail.model.DocumentDetailsDomain
import eu.europa.ec.dashboardfeature.ui.documents.model.DocumentCredentialsInfoUi
import eu.europa.ec.dashboardfeature.util.mockedBasicMdlDomain
import eu.europa.ec.dashboardfeature.util.mockedBasicPidDomain
import eu.europa.ec.dashboardfeature.util.mockedBookmarkId
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.document.format.MsoMdocData
import eu.europa.ec.eudi.wallet.document.format.MsoMdocFormat
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testfeature.util.StringResourceProviderMocker
import eu.europa.ec.testfeature.util.copy
import eu.europa.ec.testfeature.util.createMockedNamespaceData
import eu.europa.ec.testfeature.util.getMockedMdlWithBasicFields
import eu.europa.ec.testfeature.util.getMockedOldestPidWithBasicFields
import eu.europa.ec.testfeature.util.getMockedPidWithBasicFields
import eu.europa.ec.testfeature.util.mockedDefaultLocale
import eu.europa.ec.testfeature.util.mockedDocumentAvailableCredentials
import eu.europa.ec.testfeature.util.mockedDocumentTotalCredentials
import eu.europa.ec.testfeature.util.mockedExceptionWithMessage
import eu.europa.ec.testfeature.util.mockedExceptionWithNoMessage
import eu.europa.ec.testfeature.util.mockedGenericErrorMessage
import eu.europa.ec.testfeature.util.mockedMdlId
import eu.europa.ec.testfeature.util.mockedMdocPidNameSpace
import eu.europa.ec.testfeature.util.mockedOldestPidId
import eu.europa.ec.testfeature.util.mockedPidDocName
import eu.europa.ec.testfeature.util.mockedPidId
import eu.europa.ec.testfeature.util.mockedPlainFailureMessage
import eu.europa.ec.testlogic.extension.runFlowTest
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.extension.toFlow
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import junit.framework.TestCase.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

class TestDocumentDetailsInteractor {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var walletCoreDocumentsController: WalletCoreDocumentsController

    @Mock
    private lateinit var resourceProvider: ResourceProvider

    @Mock
    private lateinit var uuidProvider: UuidProvider

    private lateinit var interactor: DocumentDetailsInteractor

    private lateinit var closeable: AutoCloseable

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)

        interactor = DocumentDetailsInteractorImpl(
            walletCoreDocumentsController = walletCoreDocumentsController,
            resourceProvider = resourceProvider,
            uuidProvider = uuidProvider,
        )

        whenever(resourceProvider.genericErrorMessage()).thenReturn(mockedGenericErrorMessage)
        whenever(resourceProvider.getLocale()).thenReturn(mockedDefaultLocale)
    }

    @After
    fun after() {
        closeable.close()
    }

    //region getDocumentDetails

    // Case 1:
    // 1. walletCoreDocumentsController.getDocumentById() returns a PID document.
    // 2. walletCoreDocumentsController.isDocumentBookmarked() returns false.
    // 3. walletCoreDocumentsController.isDocumentLowOnCredentials() returns false.

    // Case 1 Expected Result:
    // DocumentDetailsInteractorPartialState.Success state, with a PID document item and
    // documentIsBookmarked is false.
    @Test
    fun `Given Case 1, When getDocumentDetails is called, Then Case 1 Expected Result is returned`() {
        coroutineRule.runTest {
            // Given
            val mockedDocIsLowOnCredentials = false
            mockGetDocumentDetailsStrings(
                resourceProvider = resourceProvider,
                docIsLowOnCredentials = mockedDocIsLowOnCredentials,
            )

            val mockedPidWithBasicFields = getMockedPidWithBasicFields()
            mockGetDocumentByIdCall(response = mockedPidWithBasicFields)

            mockIsDocumentLowOnCredentialsCall(response = mockedDocIsLowOnCredentials)
            val documentCredentialsInfoUi = getMockedDocumentCredentialsInfoUi(
                resourceProvider = resourceProvider,
                docIsLowOnCredentials = mockedDocIsLowOnCredentials,
            )

            mockRetrieveBookmarkCall(response = false)
            mockIsDocumentRevoked(isRevoked = false)

            // When
            interactor.getDocumentDetails(
                documentId = mockedPidId,
            ).runFlowTest {
                // Then
                assertEquals(
                    DocumentDetailsInteractorPartialState.Success(
                        documentDetailsDomain = mockedBasicPidDomain,
                        documentIsBookmarked = false,
                        issuerName = null,
                        issuerLogo = null,
                        isRevoked = false,
                        documentCredentialsInfoUi = documentCredentialsInfoUi,
                    ),
                    awaitItem()
                )
            }
        }
    }

    // Case 2:
    // 1. walletCoreDocumentsController.getDocumentById() returns a PID document.
    // 2. walletCoreDocumentsController.isDocumentBookmarked() returns false.
    // 3. walletCoreDocumentsController.isDocumentLowOnCredentials() returns true.

    // Case 2 Expected Result:
    // DocumentDetailsInteractorPartialState.Success state, with
    // a PID document item,
    // documentIsLowOnCredentials.isExpanded true,
    // documentIsLowOnCredentials.expandedInfo.updateNowButtonText not null, and
    // documentIsBookmarked is false.
    @Test
    fun `Given Case 2, When getDocumentDetails is called, Then Case 2 Expected Result is returned`() {
        coroutineRule.runTest {
            // Given
            val mockedDocIsLowOnCredentials = true
            mockGetDocumentDetailsStrings(
                resourceProvider = resourceProvider,
                docIsLowOnCredentials = mockedDocIsLowOnCredentials,
            )

            val mockedPidWithBasicFields = getMockedPidWithBasicFields()
            mockGetDocumentByIdCall(response = mockedPidWithBasicFields)

            mockIsDocumentLowOnCredentialsCall(response = mockedDocIsLowOnCredentials)
            val documentCredentialsInfoUi = getMockedDocumentCredentialsInfoUi(
                resourceProvider = resourceProvider,
                docIsLowOnCredentials = mockedDocIsLowOnCredentials,
            )

            mockRetrieveBookmarkCall(response = false)
            mockIsDocumentRevoked(isRevoked = false)

            // When
            interactor.getDocumentDetails(
                documentId = mockedPidId,
            ).runFlowTest {
                // Then
                assertEquals(
                    DocumentDetailsInteractorPartialState.Success(
                        documentDetailsDomain = mockedBasicPidDomain,
                        documentIsBookmarked = false,
                        issuerName = null,
                        issuerLogo = null,
                        isRevoked = false,
                        documentCredentialsInfoUi = documentCredentialsInfoUi,
                    ),
                    awaitItem()
                )
            }
        }
    }

    // Case 3:
    // 1. walletCoreDocumentsController.getDocumentById() returns a PID document.
    // 2. walletCoreDocumentsController.isDocumentBookmarked() returns true.
    // 3. walletCoreDocumentsController.isDocumentLowOnCredentials() returns false.

    // Case 3 Expected Result:
    // DocumentDetailsInteractorPartialState.Success state, with a PID document item and
    // documentIsBookmarked is true.
    @Test
    fun `Given Case 3, When getDocumentDetails is called, Then Case 3 Expected Result is returned`() {
        coroutineRule.runTest {
            // Given
            val mockedDocIsLowOnCredentials = false
            mockGetDocumentDetailsStrings(
                resourceProvider = resourceProvider,
                docIsLowOnCredentials = mockedDocIsLowOnCredentials,
            )

            val mockedPidWithBasicFields = getMockedPidWithBasicFields()
            mockGetDocumentByIdCall(response = mockedPidWithBasicFields)

            mockIsDocumentLowOnCredentialsCall(response = mockedDocIsLowOnCredentials)
            val documentCredentialsInfoUi = getMockedDocumentCredentialsInfoUi(
                resourceProvider = resourceProvider,
                docIsLowOnCredentials = mockedDocIsLowOnCredentials,
            )

            mockRetrieveBookmarkCall(response = true)
            mockIsDocumentRevoked(isRevoked = false)

            // When
            interactor.getDocumentDetails(
                documentId = mockedPidId,
            ).runFlowTest {
                // Then
                assertEquals(
                    DocumentDetailsInteractorPartialState.Success(
                        documentDetailsDomain = mockedBasicPidDomain,
                        documentIsBookmarked = true,
                        issuerName = null,
                        issuerLogo = null,
                        isRevoked = false,
                        documentCredentialsInfoUi = documentCredentialsInfoUi,
                    ),
                    awaitItem()
                )
            }
        }
    }

    // Case 4:
    // 1. walletCoreDocumentsController.getDocumentById() returns an mDL document.
    // 2. walletCoreDocumentsController.isDocumentBookmarked() returns false.
    // 3. walletCoreDocumentsController.isDocumentLowOnCredentials() returns false.

    // Case 4 Expected Result:
    // DocumentDetailsInteractorPartialState.Success state, with an mDL document item and
    // documentIsBookmarked is false.
    @Test
    fun `Given Case 4, When getDocumentDetails is called, Then Case 4 Expected Result is returned`() {
        coroutineRule.runTest {
            // Given
            val mockedDocIsLowOnCredentials = false
            mockGetDocumentDetailsStrings(
                resourceProvider = resourceProvider,
                docIsLowOnCredentials = mockedDocIsLowOnCredentials,
            )

            val mockedMdlWithBasicFields = getMockedMdlWithBasicFields()
            mockGetDocumentByIdCall(response = mockedMdlWithBasicFields)

            mockIsDocumentLowOnCredentialsCall(response = mockedDocIsLowOnCredentials)
            val documentCredentialsInfoUi = getMockedDocumentCredentialsInfoUi(
                resourceProvider = resourceProvider,
                docIsLowOnCredentials = mockedDocIsLowOnCredentials,
            )

            mockRetrieveBookmarkCall(response = false)
            mockIsDocumentRevoked(isRevoked = false)

            // When
            interactor.getDocumentDetails(
                documentId = mockedMdlId,
            ).runFlowTest {
                // Then
                assertEquals(
                    DocumentDetailsInteractorPartialState.Success(
                        documentDetailsDomain = mockedBasicMdlDomain,
                        documentIsBookmarked = false,
                        issuerName = null,
                        issuerLogo = null,
                        isRevoked = false,
                        documentCredentialsInfoUi = documentCredentialsInfoUi,
                    ),
                    awaitItem()
                )
            }
        }
    }

    // Case 5:
    // 1. walletCoreDocumentsController.getDocumentById() returns null.

    // Case 5 Expected Result:
    // DocumentDetailsInteractorPartialState.Failed state,
    // with the generic error message.
    @Test
    fun `Given Case 5, When getDocumentDetails is called, Then Case 5 Expected Result is returned`() {
        coroutineRule.runTest {
            // Given
            mockGetDocumentByIdCall(response = null)

            // When
            interactor.getDocumentDetails(
                documentId = mockedPidId,
            ).runFlowTest {
                // Then
                assertEquals(
                    DocumentDetailsInteractorPartialState.Failure(
                        error = mockedGenericErrorMessage
                    ),
                    awaitItem()
                )
            }
        }
    }

    // Case 6:
    // 1. walletCoreDocumentsController.getDocumentById() returns a PID document, with:
    // no expiration date,
    // no image, and
    // no user name.
    // 2. walletCoreDocumentsController.isDocumentBookmarked() returns false.
    // 3. walletCoreDocumentsController.isDocumentLowOnCredentials() returns false.

    // Case 6 Expected Result:
    // DocumentDetailsInteractorPartialState.Success state, with a PID document item, with:
    // an empty string for documentExpirationDateFormatted,
    // an empty string for documentImage, and
    // an empty string for userFullName, and
    // documentIsBookmarked is false.
    @Test
    fun `Given Case 6, When getDocumentDetails is called, Then Case 6 Expected Result is returned`() {
        coroutineRule.runTest {
            // Given
            val mockedDocIsLowOnCredentials = false
            mockGetDocumentDetailsStrings(
                resourceProvider = resourceProvider,
                docIsLowOnCredentials = mockedDocIsLowOnCredentials,
            )

            val mockedPidWithBasicFields = getMockedPidWithBasicFields()

            mockGetDocumentByIdCall(
                response = mockedPidWithBasicFields.copy(
                    data = MsoMdocData(
                        format = MsoMdocFormat(mockedMdocPidNameSpace),
                        issuerMetadata = null,
                        nameSpacedData = createMockedNamespaceData(
                            mockedMdocPidNameSpace, mapOf(
                                "no_data_item" to byteArrayOf(0)
                            )
                        )
                    )
                )
            )

            mockIsDocumentLowOnCredentialsCall(response = mockedDocIsLowOnCredentials)
            val documentCredentialsInfoUi = getMockedDocumentCredentialsInfoUi(
                resourceProvider = resourceProvider,
                docIsLowOnCredentials = mockedDocIsLowOnCredentials,
            )

            mockRetrieveBookmarkCall(response = false)
            mockIsDocumentRevoked(isRevoked = false)

            // When
            interactor.getDocumentDetails(
                documentId = mockedPidId,
            ).runFlowTest {
                // Then
                assertEquals(
                    DocumentDetailsInteractorPartialState.Success(
                        documentDetailsDomain = DocumentDetailsDomain(
                            docName = mockedPidDocName,
                            docId = mockedPidId,
                            documentIdentifier = DocumentIdentifier.MdocPid,
                            documentClaims = listOf(
                                ClaimDomain.Primitive(
                                    key = "no_data_item",
                                    value = "0",
                                    displayTitle = "no_data_item",
                                    path = ClaimPathDomain(value = listOf("no_data_item")),
                                    isRequired = false,
                                ),
                            ),
                        ),
                        documentIsBookmarked = false,
                        issuerName = null,
                        issuerLogo = null,
                        isRevoked = false,
                        documentCredentialsInfoUi = documentCredentialsInfoUi,
                    ),
                    awaitItem()
                )
            }
        }
    }

    // Case 7:
    // 1. walletCoreDocumentsController.getDocumentById() throws an exception with a message.

    // Case 7 Expected Result:
    // DocumentDetailsInteractorPartialState.Failed state,
    // with the exception's localized message.
    @Test
    fun `Given Case 7, When getDocumentDetails is called, Then Case 7 Expected Result is returned`() {
        coroutineRule.runTest {
            // Given
            whenever(walletCoreDocumentsController.getDocumentById(mockedPidId))
                .thenThrow(mockedExceptionWithMessage)

            // When
            interactor.getDocumentDetails(
                documentId = mockedPidId,
            ).runFlowTest {
                // Then
                assertEquals(
                    DocumentDetailsInteractorPartialState.Failure(
                        error = mockedExceptionWithMessage.localizedMessage!!
                    ),
                    awaitItem()
                )
            }
        }
    }

    // Case 8:
    // 1. walletCoreDocumentsController.getDocumentById() throws an exception with no message.

    // Case 8 Expected Result:
    // DocumentDetailsInteractorPartialState.Failed state,
    // with the generic error message.
    @Test
    fun `Given Case 8, When getDocumentDetails is called, Then Case 8 Expected Result is returned`() {
        coroutineRule.runTest {
            // Given
            whenever(walletCoreDocumentsController.getDocumentById(mockedPidId))
                .thenThrow(mockedExceptionWithNoMessage)

            // When
            interactor.getDocumentDetails(
                documentId = mockedPidId,
            ).runFlowTest {
                // Then
                assertEquals(
                    DocumentDetailsInteractorPartialState.Failure(
                        error = mockedGenericErrorMessage
                    ),
                    awaitItem()
                )
            }
        }
    }
    //endregion

    //region deleteDocument

    // Case 1:

    // 1. A documentId and document is PID.
    // 2. walletCoreDocumentsController.getAllDocuments() returns 1 Document and it is PID.
    // 3. walletCoreDocumentsController.getDocumentById returns that PID Document.
    // 4. walletCoreDocumentsController.deleteAllDocuments() returns Failed.
    @Test
    fun `Given Case 1, When deleteDocument is called, Then it returns Failure with failure's error message`() {
        coroutineRule.runTest {
            // Given
            val mockedPidWithBasicFields = getMockedPidWithBasicFields()
            mockGetAllDocumentsCall(
                response = listOf(
                    mockedPidWithBasicFields
                )
            )
            mockDeleteAllDocumentsCall(
                response = DeleteAllDocumentsPartialState.Failure(
                    errorMessage = mockedPlainFailureMessage
                )
            )
            mockGetDocumentByIdCall(
                response = mockedPidWithBasicFields
            )

            // When
            interactor.deleteDocument(
                documentId = mockedPidId,
            ).runFlowTest {
                // Then
                assertEquals(
                    DocumentDetailsInteractorDeleteDocumentPartialState.Failure(
                        errorMessage = mockedPlainFailureMessage
                    ),
                    awaitItem()
                )
            }
        }
    }

    // Case 2:

    // 1. A documentId and document is PID.
    // 2. walletCoreDocumentsController.getAllDocuments() returns 1 Document and it is PID.
    // 3. walletCoreDocumentsController.getDocumentById returns that PID Document.
    // 4. walletCoreDocumentsController.deleteAllDocuments() returns Success.
    @Test
    fun `Given Case 2, When deleteDocument is called, Then it returns AllDocumentsDeleted`() {
        coroutineRule.runTest {
            // Given
            val mockedPidWithBasicFields = getMockedPidWithBasicFields()
            mockGetAllDocumentsCall(
                response = listOf(
                    mockedPidWithBasicFields
                )
            )
            mockDeleteAllDocumentsCall(response = DeleteAllDocumentsPartialState.Success)
            mockGetDocumentByIdCall(
                response = mockedPidWithBasicFields
            )

            // When
            interactor.deleteDocument(
                documentId = mockedPidId,
            ).runFlowTest {
                // Then
                assertEquals(
                    DocumentDetailsInteractorDeleteDocumentPartialState.AllDocumentsDeleted,
                    awaitItem()
                )
            }
        }
    }

    // Case 3:

    // 1. A documentId and document is PID.
    // 2. walletCoreDocumentsController.getAllDocuments() returns more than 1 PIDs
    // 3. walletCoreDocumentsController.getDocumentById returns the oldest Document.
    // 4. walletCoreDocumentsController.deleteAllDocuments() returns Success.
    @Test
    fun `Given Case 3, When deleteDocument is called, Then it returns AllDocumentsDeleted`() {
        coroutineRule.runTest {
            // Given
            val mockedMdlWithBasicFields = getMockedMdlWithBasicFields()
            val mockedPidWithBasicFields = getMockedPidWithBasicFields()
            val mockedOldestPidWithBasicFields = getMockedOldestPidWithBasicFields()

            mockGetAllDocumentsCall(
                response = listOf(
                    mockedMdlWithBasicFields,
                    mockedPidWithBasicFields,
                    mockedOldestPidWithBasicFields
                )
            )
            mockDeleteAllDocumentsCall(response = DeleteAllDocumentsPartialState.Success)
            mockGetDocumentByIdCall(
                response = mockedOldestPidWithBasicFields
            )

            // When
            interactor.deleteDocument(
                documentId = mockedOldestPidId,
            ).runFlowTest {
                // Then
                assertEquals(
                    DocumentDetailsInteractorDeleteDocumentPartialState.AllDocumentsDeleted,
                    awaitItem()
                )
            }
        }
    }

    // Case 4:

    // 1. A documentId and document is PID.
    // 2. walletCoreDocumentsController.getAllDocuments(documentIdentifier: DocumentIdentifier) returns more than 1 PIDs
    //      AND the documentId we are about to delete is NOT the one of the oldest PID.
    // 3. walletCoreDocumentsController.deleteDocument() returns Success.
    @Test
    fun `Given Case 4, When deleteDocument is called, Then it returns SingleDocumentDeleted`() {
        coroutineRule.runTest {
            // Given
            val mockedPidWithBasicFields = getMockedPidWithBasicFields()
            val mockedOldestPidWithBasicFields = getMockedOldestPidWithBasicFields()

            mockGetAllDocumentsWithTypeCall(
                response = listOf(
                    mockedPidWithBasicFields,
                    mockedOldestPidWithBasicFields
                )
            )
            mockDeleteDocumentCall(response = DeleteDocumentPartialState.Success)
            mockGetMainPidDocument(mockedOldestPidWithBasicFields)

            // When
            interactor.deleteDocument(
                documentId = mockedPidId,
            ).runFlowTest {
                // Then
                assertEquals(
                    DocumentDetailsInteractorDeleteDocumentPartialState.SingleDocumentDeleted,
                    awaitItem()
                )
            }
        }
    }

    // Case 5:

    // 1. A documentId and document is mDL.
    // 2. walletCoreDocumentsController.deleteDocument() returns Failed.
    @Test
    fun `Given Case 5, When deleteDocument is called, Then it returns Failure with failure's error message`() {
        coroutineRule.runTest {
            // Given
            mockDeleteDocumentCall(
                response = DeleteDocumentPartialState.Failure(
                    errorMessage = mockedPlainFailureMessage
                )
            )

            // When
            interactor.deleteDocument(
                documentId = mockedMdlId,
            ).runFlowTest {
                // Then
                assertEquals(
                    DocumentDetailsInteractorDeleteDocumentPartialState.Failure(
                        errorMessage = mockedPlainFailureMessage
                    ),
                    awaitItem()
                )
            }
        }
    }

    // Case 6:

    // 1. A documentId and document is mDL.
    // 2. walletCoreDocumentsController.deleteDocument() returns Success.
    @Test
    fun `Given Case 6, When deleteDocument is called, Then it returns SingleDocumentDeleted`() {
        coroutineRule.runTest {
            // Given
            mockDeleteDocumentCall(response = DeleteDocumentPartialState.Success)

            // When
            interactor.deleteDocument(
                documentId = mockedMdlId,
            ).runFlowTest {
                // Then
                assertEquals(
                    DocumentDetailsInteractorDeleteDocumentPartialState.SingleDocumentDeleted,
                    awaitItem()
                )
            }
        }
    }

    // Case 7:

    // 1. A documentId and document is mDL.
    // 2. walletCoreDocumentsController.deleteDocument() throws an exception with a message.
    @Test
    fun `Given Case 7, When deleteDocument is called, Then it returns Failure with the exception's localized message`() {
        coroutineRule.runTest {
            // Given
            whenever(walletCoreDocumentsController.deleteDocument(mockedMdlId))
                .thenThrow(mockedExceptionWithMessage)

            // When
            interactor.deleteDocument(
                documentId = mockedMdlId,
            ).runFlowTest {
                // Then
                assertEquals(
                    DocumentDetailsInteractorDeleteDocumentPartialState.Failure(
                        errorMessage = mockedExceptionWithMessage.localizedMessage!!
                    ),
                    awaitItem()
                )
            }
        }
    }

    // Case 8:

    // 1. A documentId and document is mDL.
    // 2. walletCoreDocumentsController.deleteDocument() throws an exception with no message.
    @Test
    fun `Given Case 8, When deleteDocument is called, Then it returns Failure with the generic error message`() {
        coroutineRule.runTest {
            // Given
            whenever(walletCoreDocumentsController.deleteDocument(mockedMdlId))
                .thenThrow(mockedExceptionWithNoMessage)

            // When
            interactor.deleteDocument(
                documentId = mockedMdlId,
            ).runFlowTest {
                // Then
                assertEquals(
                    DocumentDetailsInteractorDeleteDocumentPartialState.Failure(
                        errorMessage = mockedGenericErrorMessage
                    ),
                    awaitItem()
                )
            }
        }
    }
    //endregion

    //region storeBookmark()
    // Case 1:
    // 1. A valid bookmarkId is provided.
    // 2. walletCoreDocumentsController.storeBookmark() succeeds.
    // Expected result:
    // DocumentDetailsInteractorStoreBookmarkPartialState.Success state is returned with the bookmarkId.
    @Test
    fun `Given Case 1, When storeBookmark is called, Then the expected result is returned`() {
        coroutineRule.runTest {
            // Arrange
            mockStoreBookmarkCall(bookmarkId = mockedBookmarkId)

            // Act
            interactor.storeBookmark(documentId = mockedBookmarkId).runFlowTest {
                // Assert
                assertEquals(
                    DocumentDetailsInteractorStoreBookmarkPartialState.Success(
                        bookmarkId = mockedBookmarkId
                    ),
                    awaitItem()
                )
            }
        }
    }

    // Case 2:
    // 1. A valid bookmarkId is provided.
    // 2. When walletCoreDocumentsController.storeBookmark() is called, an exception is thrown.
    // Expected result:
    // DocumentDetailsInteractorStoreBookmarkPartialState.Failure state is returned.
    @Test
    fun `Given Case 2, When storeBookmark is called, Then the expected result is returned`() {
        coroutineRule.runTest {
            // Arrange
            mockStoreBookmarkCall(
                bookmarkId = mockedBookmarkId,
                throwable = mockedExceptionWithMessage
            )

            // Act
            interactor.storeBookmark(documentId = mockedBookmarkId).runFlowTest {
                // Assert
                assertEquals(
                    DocumentDetailsInteractorStoreBookmarkPartialState.Failure,
                    awaitItem()
                )
            }
        }
    }
    //endregion

    //region deleteBookmark()
    // Case 1:
    // 1. A valid bookmarkId is provided.
    // 2. walletCoreDocumentsController.deleteBookmark() succeeds.
    // Expected result:
    // DocumentDetailsInteractorDeleteBookmarkPartialState.Success state is returned.
    @Test
    fun `Given Case 1, When deleteBookmark is called, Then the expected result is returned`() {
        coroutineRule.runTest {
            // Arrange
            mockDeleteBookmarkCall(bookmarkId = mockedBookmarkId)

            // Act
            interactor.deleteBookmark(documentId = mockedBookmarkId).runFlowTest {
                // Assert
                assertEquals(
                    DocumentDetailsInteractorDeleteBookmarkPartialState.Success,
                    awaitItem()
                )
            }
        }
    }

    // Case 2:
    // 1. A valid bookmarkId is provided for the bookmark to be deleted.
    // 2. When walletCoreDocumentsController.deleteBookmark() is called, an exception is thrown.
    // Expected result:
    // DocumentDetailsInteractorDeleteBookmarkPartialState.Failure state is returned.
    @Test
    fun `Given Case 2, When deleteBookmark is called, Then the expected result is returned`() {
        coroutineRule.runTest {
            // Given
            mockDeleteBookmarkCall(
                bookmarkId = mockedBookmarkId,
                throwable = mockedExceptionWithMessage
            )

            // When
            interactor.deleteBookmark(documentId = mockedBookmarkId).runFlowTest {
                // Then
                assertEquals(
                    DocumentDetailsInteractorDeleteBookmarkPartialState.Failure,
                    awaitItem()
                )
            }
        }
    }
    //endregion

    //region helper functions
    private fun mockGetAllDocumentsCall(response: List<IssuedDocument>) {
        whenever(walletCoreDocumentsController.getAllDocuments())
            .thenReturn(response)
    }

    private fun mockGetAllDocumentsWithTypeCall(response: List<IssuedDocument>) {
        whenever(walletCoreDocumentsController.getAllDocumentsByType(documentIdentifiers = any()))
            .thenReturn(response)
    }

    private fun mockGetDocumentByIdCall(response: IssuedDocument?) {
        whenever(walletCoreDocumentsController.getDocumentById(ArgumentMatchers.anyString()))
            .thenReturn(response)
    }

    private fun mockGetMainPidDocument(response: IssuedDocument?) {
        whenever(walletCoreDocumentsController.getMainPidDocument())
            .thenReturn(response)
    }

    private fun mockDeleteAllDocumentsCall(response: DeleteAllDocumentsPartialState) {
        whenever(walletCoreDocumentsController.deleteAllDocuments())
            .thenReturn(response.toFlow())
    }

    private fun mockDeleteDocumentCall(response: DeleteDocumentPartialState) {
        whenever(walletCoreDocumentsController.deleteDocument(ArgumentMatchers.anyString()))
            .thenReturn(response.toFlow())
    }

    private suspend fun mockStoreBookmarkCall(bookmarkId: String, throwable: Throwable? = null) {
        whenever(walletCoreDocumentsController.storeBookmark(bookmarkId))
            .thenAnswer {
                throwable?.let { throw throwable }
                Unit
            }
    }

    private suspend fun mockDeleteBookmarkCall(bookmarkId: String, throwable: Throwable? = null) {
        whenever(walletCoreDocumentsController.deleteBookmark(bookmarkId))
            .thenAnswer {
                throwable?.let { throw throwable }
                Unit
            }
    }

    private suspend fun mockRetrieveBookmarkCall(response: Boolean) {
        whenever(walletCoreDocumentsController.isDocumentBookmarked(ArgumentMatchers.anyString()))
            .thenReturn(response)
    }

    private suspend fun mockIsDocumentRevoked(isRevoked: Boolean) {
        whenever(walletCoreDocumentsController.isDocumentRevoked(any())).thenReturn(isRevoked)
    }

    private suspend fun mockIsDocumentLowOnCredentialsCall(response: Boolean) {
        whenever(walletCoreDocumentsController.isDocumentLowOnCredentials(any()))
            .thenReturn(response)
    }

    private fun getMockedDocumentCredentialsInfoUi(
        resourceProvider: ResourceProvider,
        docIsLowOnCredentials: Boolean,
        availableCredentials: Int = mockedDocumentAvailableCredentials,
        totalCredentials: Int = mockedDocumentTotalCredentials,
    ): DocumentCredentialsInfoUi {
        return DocumentCredentialsInfoUi(
            availableCredentials = availableCredentials,
            totalCredentials = totalCredentials,
            title = resourceProvider.getString(
                R.string.document_details_document_credentials_info_text,
                availableCredentials,
                totalCredentials
            ),
            collapsedInfo = DocumentCredentialsInfoUi.CollapsedInfo(
                moreInfoText = resourceProvider.getString(R.string.document_details_document_credentials_info_more_info_text),
            ),
            expandedInfo = DocumentCredentialsInfoUi.ExpandedInfo(
                subtitle = resourceProvider.getString(R.string.document_details_document_credentials_info_expanded_text_subtitle),
                updateNowButtonText = if (docIsLowOnCredentials) {
                    resourceProvider.getString(R.string.document_details_document_credentials_info_expanded_button_update_now_text)
                } else {
                    null
                },
                hideButtonText = resourceProvider.getString(R.string.document_details_document_credentials_info_expanded_button_hide_text),
            ),
            isExpanded = docIsLowOnCredentials,
        )
    }

    private fun mockGetDocumentDetailsStrings(
        resourceProvider: ResourceProvider,
        docIsLowOnCredentials: Boolean,
        availableCredentials: Int = mockedDocumentAvailableCredentials,
        totalCredentials: Int = mockedDocumentTotalCredentials
    ) {
        StringResourceProviderMocker.mockGetDocumentDetailsStrings(
            resourceProvider = resourceProvider,
            docIsLowOnCredentials = docIsLowOnCredentials,
            availableCredentials = availableCredentials,
            totalCredentials = totalCredentials,
        )
    }
    //endregion
}