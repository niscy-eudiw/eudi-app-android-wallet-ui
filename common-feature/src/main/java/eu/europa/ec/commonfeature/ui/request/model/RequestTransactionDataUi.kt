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

package eu.europa.ec.commonfeature.ui.request.model

import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.wrap.ExpandableListItemUi

data class RequestTransactionDataUi(
    val type: TransactionDataType,
    val sectionTitle: String,
    val data: ExpandableListItemUi.NestedListItem,
)

sealed class TransactionDataType {
    data object Sign : TransactionDataType()

    companion object {
        fun TransactionDataType.getDescription(
            resourceProvider: ResourceProvider,
        ): String {
            return when (this) {
                is Sign -> resourceProvider.getString(R.string.request_transaction_data_description_sign)
            }
        }

        fun TransactionDataType.getSectionTitle(
            resourceProvider: ResourceProvider,
        ): String {
            return when (this) {
                is Sign -> resourceProvider.getString(R.string.request_transaction_data_section_title_sign)
            }
        }
    }
}