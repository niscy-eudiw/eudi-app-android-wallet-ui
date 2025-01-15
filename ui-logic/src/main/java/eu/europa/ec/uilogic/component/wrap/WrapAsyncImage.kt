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

package eu.europa.ec.uilogic.component.wrap

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import eu.europa.ec.uilogic.component.IconData

@Composable
fun WrapAsyncImage(
    source: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    placeholder: IconData? = null,
    error: IconData? = null,
    fallback: IconData? = null,
) {
    AsyncImage(
        modifier = modifier,
        model = source,
        contentDescription = contentDescription,
        error = error?.resourceId?.let { painterResource(it) },
        fallback = fallback?.resourceId?.let { painterResource(it) },
        placeholder = placeholder?.resourceId?.let { painterResource(it) }
    )
}