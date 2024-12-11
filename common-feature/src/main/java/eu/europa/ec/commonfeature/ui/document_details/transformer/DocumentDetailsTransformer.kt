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

package eu.europa.ec.commonfeature.ui.document_details.transformer

import eu.europa.ec.businesslogic.util.toDateFormatted
import eu.europa.ec.businesslogic.util.toList
import eu.europa.ec.commonfeature.model.DocumentUi
import eu.europa.ec.commonfeature.model.DocumentUiIssuanceState
import eu.europa.ec.commonfeature.model.toUiName
import eu.europa.ec.commonfeature.ui.document_details.domain.DocumentDetailsDomain
import eu.europa.ec.commonfeature.ui.document_details.domain.DocumentItem
import eu.europa.ec.commonfeature.ui.document_details.model.DocumentJsonKeys
import eu.europa.ec.commonfeature.ui.request.model.generateUniqueFieldId
import eu.europa.ec.commonfeature.util.documentHasExpired
import eu.europa.ec.commonfeature.util.extractFullNameFromDocumentOrEmpty
import eu.europa.ec.commonfeature.util.extractValueFromDocumentOrEmpty
import eu.europa.ec.commonfeature.util.keyIsPortrait
import eu.europa.ec.commonfeature.util.keyIsSignature
import eu.europa.ec.commonfeature.util.parseKeyValueUi
import eu.europa.ec.corelogic.model.toDocumentIdentifier
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.document.nameSpacedDataJSONObject
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.ListItemData
import eu.europa.ec.uilogic.component.ListItemLeadingContentData
import eu.europa.ec.uilogic.component.MainContentData
import org.json.JSONObject

object DocumentDetailsTransformer {

    fun transformToDocumentDetailsDomain(
        document: IssuedDocument,
        resourceProvider: ResourceProvider
    ): Result<DocumentDetailsDomain?> = runCatching {

        val documentIdentifierUi = document.toDocumentIdentifier()

        val documentJson =
            (document.nameSpacedDataJSONObject[documentIdentifierUi.nameSpace] as JSONObject)

        val documentKeysJsonArray =
            documentJson.names() ?: return@runCatching null

        val documentValuesJsonArray =
            documentJson.toJSONArray(documentKeysJsonArray)
                ?: return@runCatching null

        val documentItemsList = documentValuesJsonArray
            .toList()
            .withIndex()
            .associateBy {
                documentKeysJsonArray[it.index]
            }
            .map {
                val key = it.key.toString()
                val value = it.value.value

                val stringValue = try {
                    val values = StringBuilder()
                    parseKeyValueUi(
                        json = value,
                        groupIdentifier = key,
                        resourceProvider = resourceProvider,
                        allItems = values
                    )
                    values.toString()
                } catch (ex: Exception) {
                    ""
                }

                val readableName = resourceProvider.getReadableElementIdentifier(key)

                DocumentItem(
                    elementIdentifier = key,
                    value = stringValue,
                    readableName = readableName,
                    docId = document.id
                )
            }

        val documentExpirationDate = extractValueFromDocumentOrEmpty(
            document = document,
            key = DocumentJsonKeys.EXPIRY_DATE
        )

        val docHasExpired = documentHasExpired(documentExpirationDate)

        val documentImage = extractValueFromDocumentOrEmpty(
            document = document,
            key = DocumentJsonKeys.PORTRAIT
        )

        return@runCatching DocumentDetailsDomain(
            docName = document.toUiName(resourceProvider),
            docId = document.id,
            docNamespace = document.nameSpaces.keys.first(),
            documentIdentifier = document.toDocumentIdentifier(),
            documentExpirationDateFormatted = documentExpirationDate.toDateFormatted().orEmpty(),
            documentHasExpired = docHasExpired,
            documentImage = documentImage,
            userFullName = extractFullNameFromDocumentOrEmpty(document),
            detailsItems = documentItemsList
        )
    }

    fun DocumentDetailsDomain.transformToDocumentDetailsUi(): DocumentUi {
        val documentDetailsListItemData = this.detailsItems.map { documentItem ->
            documentItem.toListItemData()
        }
        return DocumentUi(
            documentId = this.docId,
            documentName = this.docName,
            documentIdentifier = this.documentIdentifier,
            documentExpirationDateFormatted = this.documentExpirationDateFormatted,
            documentHasExpired = this.documentHasExpired,
            documentImage = this.documentImage,
            documentDetails = documentDetailsListItemData,
            userFullName = this.userFullName,
            documentIssuanceState = DocumentUiIssuanceState.Issued,
        )
    }

    private fun DocumentItem.toListItemData(): ListItemData {

        val mainTextContentData = when {
            keyIsPortrait(key = this.elementIdentifier) -> {
                MainContentData.Text(text = "")
            }

            keyIsSignature(key = this.elementIdentifier) -> {
                MainContentData.Image(base64Image = this.value)
            }

            else -> {
                MainContentData.Text(text = this.value)
            }
        }

        val itemId = generateUniqueFieldId(
            elementIdentifier = this.elementIdentifier,
            documentId = this.docId
        )

        val leadingContent = if (keyIsPortrait(key = this.elementIdentifier)) {
            ListItemLeadingContentData.UserImage(userBase64Image = this.value)
        } else {
            null
        }

        return ListItemData(
            itemId = itemId,
            mainContentData = mainTextContentData,
            overlineText = this.readableName,
            leadingContentData = leadingContent
        )
    }
}