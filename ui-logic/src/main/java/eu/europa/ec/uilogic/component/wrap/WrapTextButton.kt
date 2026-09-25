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

package eu.europa.ec.uilogic.component.wrap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.IconDataUi
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL

@Composable
fun WrapTextButton(
    modifier: Modifier,
    text: String,
    enabled: Boolean,
    contentAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    shape: Shape? = null,
    trailingIcon: IconDataUi?,
    isRippleEnabled: Boolean = true,
    onClick: () -> Unit,
) {
    val rippleConfiguration = if (isRippleEnabled) LocalRippleConfiguration.current else null

    CompositionLocalProvider(LocalRippleConfiguration provides rippleConfiguration) {
        TextButton(
            modifier = modifier,
            enabled = enabled,
            shape = shape ?: ButtonDefaults.textShape,
            onClick = onClick,
            contentPadding = PaddingValues(vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp, contentAlignment),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier.weight(weight = 1f, fill = false),
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Start,
                )
                trailingIcon?.let { safeTrailingIcon ->
                    WrapIcon(
                        iconData = safeTrailingIcon,
                        enabled = enabled,
                    )
                }
            }
        }
    }
}

@ThemeModePreviews
@Composable
private fun WrapTextButtonPreview() {
    PreviewTheme {
        Column(
            modifier = Modifier.padding(SPACING_MEDIUM.dp),
            verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp),
        ) {
            WrapTextButton(
                modifier = Modifier.fillMaxWidth(),
                text = "View previous data deletion requests (2)",
                enabled = true,
                contentAlignment = Alignment.Start,
                trailingIcon = AppIcons.KeyboardArrowRight,
                isRippleEnabled = false,
                onClick = {},
            )
            WrapTextButton(
                modifier = Modifier.fillMaxWidth(),
                text = "View previous transaction reports (2)",
                enabled = false,
                contentAlignment = Alignment.Start,
                trailingIcon = AppIcons.KeyboardArrowRight,
                isRippleEnabled = false,
                onClick = {},
            )
            WrapTextButton(
                modifier = Modifier.fillMaxWidth(),
                text = "View details",
                enabled = true,
                contentAlignment = Alignment.CenterHorizontally,
                shape = null,
                trailingIcon = null,
                onClick = {},
            )
        }
    }
}