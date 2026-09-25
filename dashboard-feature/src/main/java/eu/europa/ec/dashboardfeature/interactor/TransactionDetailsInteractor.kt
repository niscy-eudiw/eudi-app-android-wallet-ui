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

package eu.europa.ec.dashboardfeature.interactor

import eu.europa.ec.businesslogic.extension.safeAsync
import eu.europa.ec.businesslogic.util.FULL_DATETIME_PATTERN
import eu.europa.ec.businesslogic.util.formatLocalDateTime
import eu.europa.ec.corelogic.controller.WalletCoreTransactionLogController
import eu.europa.ec.corelogic.extension.toPrivacyContactOrNull
import eu.europa.ec.corelogic.model.ClaimPathSegment
import eu.europa.ec.corelogic.model.ClaimRefDomain
import eu.europa.ec.corelogic.model.CommunicationMethodDomain
import eu.europa.ec.corelogic.model.CredentialClaimsDomain
import eu.europa.ec.corelogic.model.CredentialRefDomain
import eu.europa.ec.corelogic.model.DpaContactDomain
import eu.europa.ec.corelogic.model.InteractingPartyDomain
import eu.europa.ec.corelogic.model.IssuanceDetailsDomain
import eu.europa.ec.corelogic.model.TransactionLogDomain
import eu.europa.ec.corelogic.model.TransactionResultDomain
import eu.europa.ec.dashboardfeature.ui.transactions.detail.model.TransactionDetailsBodyUi
import eu.europa.ec.dashboardfeature.ui.transactions.detail.model.TransactionDetailsCardUi
import eu.europa.ec.dashboardfeature.ui.transactions.detail.model.TransactionDetailsFieldUi
import eu.europa.ec.dashboardfeature.ui.transactions.detail.model.TransactionDetailsGroupUi
import eu.europa.ec.dashboardfeature.ui.transactions.detail.model.TransactionDetailsItemUi
import eu.europa.ec.dashboardfeature.ui.transactions.detail.model.TransactionDetailsMetadataUi
import eu.europa.ec.dashboardfeature.ui.transactions.detail.model.TransactionDetailsSectionUi
import eu.europa.ec.dashboardfeature.ui.transactions.detail.model.TransactionDetailsUi
import eu.europa.ec.dashboardfeature.ui.transactions.model.TransactionStatusUi
import eu.europa.ec.dashboardfeature.ui.transactions.model.TransactionStatusUi.Companion.toUiText
import eu.europa.ec.dashboardfeature.ui.transactions.model.TransactionTypeUi.Companion.toUiText
import eu.europa.ec.dashboardfeature.ui.transactions.model.toTransactionPartyName
import eu.europa.ec.dashboardfeature.ui.transactions.model.toTransactionStatusUi
import eu.europa.ec.dashboardfeature.ui.transactions.model.toTransactionTypeUi
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.ListItemDataUi
import eu.europa.ec.uilogic.component.ListItemMainContentDataUi
import eu.europa.ec.uilogic.component.ListItemSupportingContentDataUi
import eu.europa.ec.uilogic.component.ListItemTrailingContentDataUi
import eu.europa.ec.uilogic.component.wrap.ExpandableListItemUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonPrimitive

sealed class TransactionDetailsInteractorPartialState {
    data class Success(
        val transactionDetailsUi: TransactionDetailsUi,
    ) : TransactionDetailsInteractorPartialState()

    data class Failure(
        val error: String
    ) : TransactionDetailsInteractorPartialState()
}

sealed class TransactionDetailsInteractorDeleteTransactionPartialState {
    data object Success : TransactionDetailsInteractorDeleteTransactionPartialState()
    data class Failure(
        val errorMessage: String
    ) : TransactionDetailsInteractorDeleteTransactionPartialState()
}

interface TransactionDetailsInteractor {
    fun getTransactionDetails(
        transactionId: String
    ): Flow<TransactionDetailsInteractorPartialState>

    fun deleteTransaction(transactionId: String): Flow<TransactionDetailsInteractorDeleteTransactionPartialState>

}

class TransactionDetailsInteractorImpl(
    private val walletCoreTransactionLogController: WalletCoreTransactionLogController,
    private val resourceProvider: ResourceProvider,
) : TransactionDetailsInteractor {

    private val genericErrorMsg
        get() = resourceProvider.genericErrorMessage()

    override fun getTransactionDetails(transactionId: String): Flow<TransactionDetailsInteractorPartialState> =
        flow {
            val transaction =
                walletCoreTransactionLogController.getTransactionLog(id = transactionId)
            if (transaction == null) {
                emit(TransactionDetailsInteractorPartialState.Failure(error = genericErrorMsg))
                return@flow
            }

            val transactionUiStatus = transaction.result.toTransactionStatusUi()
            val transactionUiDate = transaction.time.formatLocalDateTime(
                pattern = FULL_DATETIME_PATTERN
            )
            val transactionDetailsUi = TransactionDetailsUi(
                transactionId = transaction.id,
                transactionDetailsCardUi = TransactionDetailsCardUi(
                    transactionTypeLabel = transaction
                        .toTransactionTypeUi()
                        .toUiText(resourceProvider),
                    transactionStatusLabel = transactionUiStatus.toUiText(resourceProvider),
                    transactionIsCompleted = transactionUiStatus == TransactionStatusUi.Completed,
                    transactionDate = transactionUiDate,
                    partyName = transaction.toTransactionPartyName(),
                    providerType = transaction.toProviderType(),
                    metadata = transaction.toCardMetadata(),
                    nonCompletionReason = when (val result = transaction.result) {
                        is TransactionResultDomain.Completed -> null
                        is TransactionResultDomain.NotCompleted ->
                            result.reason?.takeIf { reason -> reason.isNotBlank() }
                    },
                ),
                body = transaction.toDetailsBody(),
            )
            emit(
                TransactionDetailsInteractorPartialState.Success(
                    transactionDetailsUi = transactionDetailsUi
                )
            )
        }.safeAsync {
            TransactionDetailsInteractorPartialState.Failure(
                error = it.localizedMessage ?: genericErrorMsg
            )
        }

    override fun deleteTransaction(transactionId: String): Flow<TransactionDetailsInteractorDeleteTransactionPartialState> =
        flow {
            walletCoreTransactionLogController.deleteTransactionLog(id = transactionId)
            emit(TransactionDetailsInteractorDeleteTransactionPartialState.Success)
        }.safeAsync {
            TransactionDetailsInteractorDeleteTransactionPartialState.Failure(
                errorMessage = it.localizedMessage ?: genericErrorMsg
            )
        }

    private fun TransactionLogDomain.toProviderType(): String? {
        return when (this) {
            is TransactionLogDomain.Presentation -> partyType
            is TransactionLogDomain.CredentialIssuance -> details.issuerType
            is TransactionLogDomain.CredentialReissuance -> details.issuerType
            is TransactionLogDomain.SigningSealing -> serviceType
            is TransactionLogDomain.CredentialDeletion,
            is TransactionLogDomain.DataDeletionRequest,
            is TransactionLogDomain.DpaReport -> null
        }?.takeIf { type -> type.isNotBlank() }
    }

    private fun TransactionLogDomain.toCardMetadata(): List<TransactionDetailsMetadataUi> {
        return when (this) {
            is TransactionLogDomain.Presentation -> listOfNotNull(
                metadataGroup(
                    fields = listOfNotNull(
                        textField(
                            "purpose",
                            R.string.transaction_details_purpose_label,
                            registration?.purpose
                        ),
                    ),
                ),
                metadataGroup(
                    fields = registration?.privacyPolicyUrls.orEmpty()
                        .mapIndexedNotNull { index, url ->
                            webField(
                                "privacy:$index",
                                R.string.transaction_details_privacy_policy_label,
                                url
                            )
                        },
                ),
                metadataGroup(
                    fields = contactFields(
                        prefix = "party",
                        contacts = party.contacts,
                        labelRes = R.string.transaction_details_contact_label,
                    ),
                ),
                metadataGroup(
                    fields = listOfNotNull(
                        textField(
                            "intermediary:name",
                            R.string.transaction_details_intermediary_name_label,
                            intermediary?.name?.text
                        ),
                    ) + contactFields(
                        prefix = "intermediary",
                        contacts = intermediary?.contacts.orEmpty(),
                        labelRes = R.string.transaction_details_intermediary_contact_label,
                    ),
                ),
            )

            is TransactionLogDomain.CredentialIssuance -> listOfNotNull(
                metadataGroup(fields = details.toCountFields()),
                metadataGroup(
                    fields = contactFields(
                        prefix = "issuer",
                        contacts = details.issuer.contacts,
                        labelRes = R.string.transaction_details_contact_label,
                    ),
                ),
            )

            is TransactionLogDomain.CredentialReissuance -> listOfNotNull(
                metadataGroup(fields = details.toCountFields()),
                metadataGroup(
                    fields = listOfNotNull(details.toTriggerField()),
                ),
                metadataGroup(
                    fields = contactFields(
                        prefix = "issuer",
                        contacts = details.issuer.contacts,
                        labelRes = R.string.transaction_details_contact_label,
                    ),
                ),
            )

            is TransactionLogDomain.SigningSealing -> listOfNotNull(
                metadataGroup(
                    fields = listOfNotNull(
                        textField(
                            id = "signing-transaction",
                            labelRes = R.string.transaction_details_signing_identifier_label,
                            value = signingTransactionId,
                        ),
                    ),
                ),
            )

            is TransactionLogDomain.CredentialDeletion,
            is TransactionLogDomain.DataDeletionRequest,
            is TransactionLogDomain.DpaReport -> emptyList()
        }
    }

    private fun metadataGroup(
        fields: List<TransactionDetailsFieldUi>,
    ): TransactionDetailsMetadataUi? {
        return fields
            .takeIf { availableFields -> availableFields.isNotEmpty() }
            ?.let { availableFields ->
                TransactionDetailsMetadataUi(fields = availableFields)
            }
    }

    private fun TransactionLogDomain.toDetailsBody(): TransactionDetailsBodyUi = when (this) {
        is TransactionLogDomain.Presentation -> TransactionDetailsBodyUi.Presentation(
            requested = claimsSection(
                prefix = "requested",
                titleRes = R.string.transaction_details_data_requested_section_title,
                claims = claimsRequested,
                emptyRes = R.string.transaction_details_no_data_requested,
            ),
            shared = claimsSection(
                prefix = "shared",
                titleRes = R.string.transaction_details_data_shared_section_title,
                claims = claimsPresented,
                emptyRes = R.string.transaction_details_no_data_shared,
            ),
        )

        is TransactionLogDomain.CredentialIssuance -> TransactionDetailsBodyUi.Issuance(
            credentials = credentialsSection(
                titleRes = R.string.transaction_details_credentials_issued_section_title,
                credentials = details.credentials,
            ),
        )

        is TransactionLogDomain.CredentialReissuance -> TransactionDetailsBodyUi.Reissuance(
            credentials = credentialsSection(
                titleRes = R.string.transaction_details_credentials_issued_section_title,
                credentials = details.credentials,
            ),
        )

        is TransactionLogDomain.CredentialDeletion -> TransactionDetailsBodyUi.Deletion(
            credential = credentialsSection(
                titleRes = R.string.transaction_details_credentials_section_title,
                credentials = listOf(credential),
            ),
        )

        is TransactionLogDomain.SigningSealing -> TransactionDetailsBodyUi.Signing(
            document = textField("filename", R.string.transaction_details_filename_label, fileName)
                ?.let { filename ->
                    fieldsSection(
                        titleRes = R.string.transaction_details_data_signed_section_title,
                        fields = listOf(filename),
                    )
                },
        )

        is TransactionLogDomain.DataDeletionRequest -> TransactionDetailsBodyUi.DataDeletionRequest(
            party = fieldsSection(
                titleRes = R.string.transaction_details_relying_party_section_title,
                fields = party.toDetailsFields(prefix = "party"),
            ),
            claims = claimsSection(
                prefix = "deletion",
                titleRes = R.string.transaction_details_data_deletion_section_title,
                claims = claims,
                emptyRes = R.string.transaction_details_no_claims,
            ),
        )

        is TransactionLogDomain.DpaReport -> TransactionDetailsBodyUi.DpaReport(
            authority = DpaContactDomain(
                name = dpaName,
                country = dpaCountry,
                contacts = emptyList()
            ).toAuthoritySection()
        )
    }

    private fun InteractingPartyDomain.toDetailsFields(prefix: String): List<TransactionDetailsFieldUi> {
        return listOfNotNull(
            textField("$prefix:name", R.string.transaction_details_name_label, name?.text),
            textField(
                "$prefix:id",
                R.string.transaction_details_identifier_label,
                identifier?.value
            ),
            textField(
                "$prefix:scheme",
                R.string.transaction_details_identifier_scheme_label,
                identifier?.schemeUri
            ),
        ) + contactFields(
            prefix = prefix,
            contacts = contacts,
            labelRes = R.string.transaction_details_contact_label,
        )
    }

    private fun DpaContactDomain.toAuthoritySection(): TransactionDetailsSectionUi {
        return fieldsSection(
            titleRes = R.string.transaction_details_authority_section_title,
            fields = listOfNotNull(
                textField("authority:name", R.string.transaction_details_name_label, name?.text),
                textField(
                    "authority:country",
                    R.string.transaction_details_country_label,
                    country?.text
                ),
            ) + contactFields(
                prefix = "authority",
                contacts = contacts,
                labelRes = R.string.transaction_details_contact_label,
            ),
        )
    }

    private fun IssuanceDetailsDomain.toCountFields(): List<TransactionDetailsFieldUi> {
        return listOfNotNull(
            textField(
                id = "requested-count",
                labelRes = R.string.transaction_details_requested_count_label,
                value = requestedCount.toString(),
            ),
            textField(
                id = "issued-count",
                labelRes = R.string.transaction_details_issued_count_label,
                value = issuedCount.toString(),
            ),
        )
    }

    private fun IssuanceDetailsDomain.toTriggerField(): TransactionDetailsFieldUi? {
        val triggerLabel = when (isUserTriggered) {
            true -> resourceProvider.getString(R.string.transaction_details_requested_by_you)
            false -> resourceProvider.getString(R.string.transaction_details_renewed_by_wallet)
            null -> null
        }
        return textField("trigger", R.string.transaction_details_trigger_label, triggerLabel)
    }

    private fun credentialsSection(
        titleRes: Int,
        credentials: List<CredentialRefDomain>,
    ): TransactionDetailsSectionUi? {
        val fields = credentials.mapIndexedNotNull { index, credential ->
            credential.identifier.takeIf { identifier -> identifier.isNotBlank() }
                ?.let { identifier ->
                    TransactionDetailsFieldUi(
                        id = "credential:$index",
                        label = null,
                        value = identifier,
                        url = null,
                    )
                }
        }
        return fields.takeIf { availableFields -> availableFields.isNotEmpty() }
            ?.let { availableFields ->
                fieldsSection(
                    titleRes = titleRes,
                    fields = availableFields,
                )
            }
    }

    private fun claimsSection(
        prefix: String,
        titleRes: Int,
        claims: List<CredentialClaimsDomain>,
        emptyRes: Int,
    ): TransactionDetailsSectionUi {
        return TransactionDetailsSectionUi(
            title = resourceProvider.getString(titleRes),
            items = emptyList(),
            groups = claims.mapIndexed { credentialIndex, credentialClaims ->
                val groupId = "$prefix:$credentialIndex"
                TransactionDetailsGroupUi(
                    header = ListItemDataUi(
                        itemId = groupId,
                        mainContentData = ListItemMainContentDataUi.Text(credentialClaims.credential.identifier),
                        supportingContentData = ListItemSupportingContentDataUi.Text(
                            resourceProvider.getString(R.string.transaction_details_collapsed_supporting_text),
                        ),
                        trailingContentData = ListItemTrailingContentDataUi.Icon(AppIcons.KeyboardArrowDown),
                    ),
                    items = credentialClaims.claims.mapIndexed { claimIndex, claim ->
                        ExpandableListItemUi.SingleListItem(
                            header = ListItemDataUi(
                                itemId = "$groupId:$claimIndex",
                                mainContentData = ListItemMainContentDataUi.Text(claim.toIdentifierPath()),
                            ),
                        )
                    }.ifEmpty {
                        listOf(
                            ExpandableListItemUi.SingleListItem(
                                header = ListItemDataUi(
                                    itemId = "$groupId:empty",
                                    mainContentData = ListItemMainContentDataUi.Text(
                                        resourceProvider.getString(R.string.transaction_details_no_claims),
                                    ),
                                ),
                            ),
                        )
                    },
                )
            },
            emptyItem = if (claims.isEmpty()) {
                ListItemDataUi(
                    itemId = "$prefix:empty",
                    mainContentData = ListItemMainContentDataUi.Text(
                        resourceProvider.getString(emptyRes),
                    ),
                )
            } else {
                null
            },
        )
    }

    private fun ClaimRefDomain.toIdentifierPath(): String {
        if (segments.isEmpty()) {
            return resourceProvider.getString(R.string.transaction_details_unknown_claim)
        }
        return segments.joinToString(separator = "") { segment ->
            when (segment) {
                is ClaimPathSegment.Key -> "[${JsonPrimitive(segment.name)}]"
                is ClaimPathSegment.Index -> "[${segment.index}]"
                is ClaimPathSegment.AllElements -> "[*]"
            }
        }
    }

    private fun fieldsSection(
        titleRes: Int,
        fields: List<TransactionDetailsFieldUi>,
    ): TransactionDetailsSectionUi {
        return TransactionDetailsSectionUi(
            title = resourceProvider.getString(titleRes),
            items = fields.map { field ->
                TransactionDetailsItemUi(item = field.item, url = field.url)
            },
            groups = emptyList(),
            emptyItem = if (fields.isEmpty()) {
                ListItemDataUi(
                    itemId = "section:$titleRes:empty",
                    mainContentData = ListItemMainContentDataUi.Text(
                        resourceProvider.getString(R.string.transaction_details_no_information),
                    ),
                )
            } else {
                null
            },
        )
    }

    private fun textField(
        id: String,
        labelRes: Int,
        value: String?
    ): TransactionDetailsFieldUi? {
        return value
            ?.takeIf { value -> value.isNotBlank() }
            ?.let { text ->
                TransactionDetailsFieldUi(
                    id = id,
                    label = resourceProvider.getString(labelRes),
                    value = text,
                    url = null,
                )
            }
    }

    private fun webField(
        id: String,
        labelRes: Int,
        value: String?
    ): TransactionDetailsFieldUi? {
        return textField(
            id = id,
            labelRes = labelRes,
            value = value
        )?.let { field ->
            field.copy(url = field.value.toWebUrlOrNull())
        }
    }

    private fun contactFields(
        prefix: String,
        contacts: List<String>,
        labelRes: Int,
    ): List<TransactionDetailsFieldUi> {
        return contacts.mapIndexedNotNull { index, contact ->
            textField(
                "$prefix:contact:$index",
                labelRes,
                contact
            )?.copy(url = contact.toContactUrlOrNull())
        }
    }

    private fun String.toWebUrlOrNull(): String? =
        toPrivacyContactOrNull()?.takeIf { contact ->
            contact.method == CommunicationMethodDomain.Website
        }?.url

    private fun String.toContactUrlOrNull(): String? = toPrivacyContactOrNull()?.url
}