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

package eu.europa.ec.commonfeature.ui.request.transformer

import eu.europa.ec.commonfeature.ui.request.Event
import eu.europa.ec.commonfeature.ui.request.model.DocumentItemDomainPayload
import eu.europa.ec.commonfeature.ui.request.model.RequestDocumentItemUi
import eu.europa.ec.commonfeature.ui.request.model.RequestDocumentsUi
import eu.europa.ec.commonfeature.ui.request.model.produceDocUID
import eu.europa.ec.commonfeature.ui.request.model.toRequestDocumentItemUi
import eu.europa.ec.commonfeature.util.keyIsBase64
import eu.europa.ec.commonfeature.util.parseKeyValueUi
import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.eudi.iso18013.transfer.DisclosedDocument
import eu.europa.ec.eudi.iso18013.transfer.DisclosedDocuments
import eu.europa.ec.eudi.iso18013.transfer.DocItem
import eu.europa.ec.eudi.iso18013.transfer.RequestDocument
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.document.nameSpacedDataJSONObject
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.ListItemData
import eu.europa.ec.uilogic.component.ListItemTrailingContentData
import eu.europa.ec.uilogic.component.wrap.CheckboxData
import org.json.JSONObject

private fun getMandatoryFields(documentIdentifier: DocumentIdentifier): List<String> =
    when (documentIdentifier) {

        DocumentIdentifier.PID -> listOf(
            "issuance_date",
            "expiry_date",
            "issuing_authority",
            "document_number",
            "administrative_number",
            "issuing_country",
            "issuing_jurisdiction",
            "portrait",
            "portrait_capture_date"
        )

        DocumentIdentifier.AGE -> listOf(
            "issuance_date",
            "expiry_date",
            "issuing_country",
            "issuing_authority",
        )

        else -> emptyList()
    }

object RequestTransformer {

    fun transformToUiItems(
        storageDocuments: List<IssuedDocument> = emptyList(),
        resourceProvider: ResourceProvider,
        requestDocuments: List<RequestDocument>,
    ): List<RequestDocumentsUi<Event>> {
        val documentsUi = mutableListOf<RequestDocumentsUi<Event>>()
        val documents = mutableListOf<RequestDocumentItemUi<Event>>()
        requestDocuments.forEachIndexed { docIndex, requestDocument ->
            // Add document item.
            /*items += RequestDocumentsUi.Document(
                documentItemUi = DocumentItemUi(
                    title = requestDocument.toUiName(resourceProvider)
                )
            )
            items += RequestDocumentsUi.Space()*/

            val required = mutableListOf<RequestDocumentItemUi<Event>>()
            val storageDocument = storageDocuments.first { it.id == requestDocument.documentId }


            // Add optional field items.
            requestDocument.docRequest.requestItems.forEachIndexed { itemIndex, docItem ->

                val (value, isAvailable) = try {
                    val values = StringBuilder()
                    parseKeyValueUi(
                        json = storageDocument.nameSpacedDataJSONObject.getDocObject(
                            nameSpace = docItem.namespace
                        )[docItem.elementIdentifier],
                        groupIdentifier = docItem.elementIdentifier,
                        resourceProvider = resourceProvider,
                        allItems = values
                    )
                    (values.toString() to true)
                } catch (ex: Exception) {
                    (resourceProvider.getString(R.string.request_element_identifier_not_available) to false)
                }


                val itemId = requestDocument.docRequest.produceDocUID(
                    elementIdentifier = docItem.elementIdentifier,
                    documentId = requestDocument.documentId
                )

                val listItem = ListItemData(
                    itemId = itemId,
                    mainText = value,
                    overlineText = value,//TODO()
                    supportingText = null,
                    leadingIcon = if (keyIsBase64(key = docItem.elementIdentifier)) AppIcons.User else null, //TODO extract from RequestDocumentItemUi.keyIsBase64 or remove it alltogether? Also, show actual user image here?
                    trailingContentData = ListItemTrailingContentData.Checkbox(
                        checkboxData = CheckboxData(
                            isChecked = isAvailable,
                            enabled = isAvailable,
                            onCheckedChange = {
                                println("Giannis Checked changed for ${docItem.elementIdentifier}")
                                //TODO
                                //onCheckedChange(docId)
                            }
                        )

                    )
                )

                //items.add(RequestDocumentsUi(documentDetails = document))

                val document: RequestDocumentItemUi<Event> = toRequestDocumentItemUi(
                    uID = itemIndex.toString(),// TODO do we need better way to calculate documentUi id?
                    docPayload = DocumentItemDomainPayload(
                        docId = requestDocument.documentId,
                        docRequest = requestDocument.docRequest,
                        docType = requestDocument.docType,
                        namespace = docItem.namespace,
                        elementIdentifier = docItem.elementIdentifier,
                    ),
                    documentDetailsUiItem = listItem,
                    event = Event.UserIdentificationClicked(itemId = itemId) //TODO?
                )

                documents.add(document)

                /*if (
                    getMandatoryFields(documentIdentifier = requestDocument.toDocumentIdentifier())
                        .contains(docItem.elementIdentifier)
                ) {
                    required.add(
                        docItem.toRequestDocumentItemUi(
                            uID = requestDocument.docRequest.produceDocUID(
                                docItem.elementIdentifier,
                                requestDocument.documentId
                            ),
                            docPayload = DocumentItemDomainPayload(
                                docId = requestDocument.documentId,
                                docRequest = requestDocument.docRequest,
                                docType = requestDocument.docType,
                                namespace = docItem.namespace,
                                elementIdentifier = docItem.elementIdentifier,
                            ),
                            optional = false,
                            isChecked = isAvailable,
                            event = null,
                            readableName = resourceProvider.getReadableElementIdentifier(docItem.elementIdentifier),
                            value = value
                        )
                    )
                } else {
                    val uID = requestDocument.docRequest.produceDocUID(
                        docItem.elementIdentifier,
                        requestDocument.documentId
                    )

                    items += RequestDocumentsUi.Space()
                    items += RequestDocumentsUi.OptionalField(
                        optionalFieldItemUi = OptionalFieldItemUi(
                            requestDocumentItemUi = docItem.toRequestDocumentItemUi(
                                uID = uID,
                                docPayload = DocumentItemDomainPayload(
                                    docId = requestDocument.documentId,
                                    docRequest = requestDocument.docRequest,
                                    docType = requestDocument.docType,
                                    namespace = docItem.namespace,
                                    elementIdentifier = docItem.elementIdentifier,
                                ),
                                optional = isAvailable,
                                isChecked = isAvailable,
                                event = Event.UserIdentificationClicked(itemId = uID),
                                readableName = resourceProvider.getReadableElementIdentifier(docItem.elementIdentifier),
                                value = value
                            )
                        )
                    )

                    if (itemIndex != requestDocument.docRequest.requestItems.lastIndex) {
                        items += RequestDocumentsUi.Space()
                        items += RequestDocumentsUi.Divider()
                    }
                }*/
            }

            //documentsUi.add(documents)
            documentsUi.add(
                RequestDocumentsUi(
                    documentsUi = documents
                )
            )

            /*items += RequestDocumentsUi.Space()

            // Add required fields item.
            if (required.isNotEmpty()) {
                items += RequestDocumentsUi.RequiredFields(
                    requiredFieldsItemUi = RequiredFieldsItemUi(
                        id = docIndex,
                        requestDocumentItemsUi = required,
                        expanded = false,
                        title = requiredFieldsTitle,
                        event = Event.ExpandOrCollapseRequiredDataList(id = docIndex)
                    )
                )
                items += RequestDocumentsUi.Space()
            }*/
        }

        return documentsUi
    }

    fun transformToDomainItems(uiItems: List<RequestDocumentsUi<Event>>): DisclosedDocuments {
        val selectedUiItems = uiItems
            .flatMap {
                it.documentsUi
            }
            // Get selected
            .filter {
                it.documentDetailsUiItem.trailingContentData is ListItemTrailingContentData.Checkbox
                        && (it.documentDetailsUiItem.trailingContentData as? ListItemTrailingContentData.Checkbox)?.checkboxData?.isChecked == true
            }
            // Create a Map with document as a key
            .groupBy {
                it.domainPayload
            }

        return DisclosedDocuments(
            selectedUiItems.map { entry ->
                val (document, selectedDocumentItems) = entry
                DisclosedDocument(
                    documentId = document.docId,
                    docType = document.docType,
                    selectedDocItems = selectedDocumentItems.map {
                        DocItem(
                            it.domainPayload.namespace,
                            it.domainPayload.elementIdentifier
                        )
                    },
                    docRequest = document.docRequest
                )
            }
        )
    }

    private fun JSONObject.getDocObject(nameSpace: String): JSONObject =
        this[nameSpace] as JSONObject
}