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

package eu.europa.ec.uilogic.component

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.europa.ec.uilogic.component.ClickableArea.ENTIRE_ROW
import eu.europa.ec.uilogic.component.ClickableArea.TRAILING_CONTENT
import eu.europa.ec.uilogic.component.ListItemTrailingContentData.Checkbox
import eu.europa.ec.uilogic.component.ListItemTrailingContentData.Icon
import eu.europa.ec.uilogic.component.MainContentData.Image
import eu.europa.ec.uilogic.component.MainContentData.Text
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.DEFAULT_ICON_SIZE
import eu.europa.ec.uilogic.component.utils.ICON_SIZE_40
import eu.europa.ec.uilogic.component.utils.SIZE_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.CheckboxData
import eu.europa.ec.uilogic.component.wrap.WrapCheckbox
import eu.europa.ec.uilogic.component.wrap.WrapIconButton
import eu.europa.ec.uilogic.component.wrap.WrapImage

/**
 * Represents the data displayed within a single item in a list.
 *
 * This class encapsulates all the information needed to render a list item,
 * including its content, optional visual elements like leading/trailing icons or checkboxes,
 * and any associated data.
 *
 * @param itemId A unique identifier for this specific list item. This is crucial for identifying
 * the item within the list, especially when handling interactions.
 * @param mainContentData The primary content displayed in the list item. This is typically text
 * but could be other UI elements. See [MainContentData] for details on how to structure
 * the main content.
 * @param overlineText Optional text displayed above the `mainContentData`, providing context
 * or a brief heading for the item.
 * @param supportingText Optional text displayed below the `mainContentData`, offering
 * additional details or description to supplement the main content.
 * @param leadingContentData Optional data for content displayed at the beginning of the list item.
 * This could be an icon, image, or other visual element. See [ListItemLeadingContentData]
 * for details on supported leading content types.
 * @param trailingContentData Optional data for content displayed at the end of the list item.
 * This could be an icon, checkbox, or other interactive element. See [ListItemTrailingContentData]
 * for details on supported trailing content types.
 */
data class ListItemData(
    val itemId: String,
    val mainContentData: MainContentData,
    val overlineText: String? = null,
    val supportingText: String? = null,
    val leadingContentData: ListItemLeadingContentData? = null,
    val trailingContentData: ListItemTrailingContentData? = null,
)

/**
 * Represents the data that can be displayed in the main content area.
 * This can be either plain text or an image encoded in base64 format.
 *
 * @see [Text]
 * @see [Image]
 */
sealed class MainContentData {
    data class Text(val text: String) : MainContentData()
    data class Image(val base64Image: String) : MainContentData()
}

/**
 * Represents the data that can be displayed as leading content in a list item.
 * This can be either an icon or a User image encoded in base64 format.
 */
sealed class ListItemLeadingContentData {
    data class Icon(val iconData: IconData) : ListItemLeadingContentData()
    data class UserImage(val userBase64Image: String) : ListItemLeadingContentData()
}

/**
 * Represents the data that can be displayed in the trailing content of a list item.
 *
 * This sealed class offers two options for the trailing content:
 * - [Icon]: Displays an icon.
 * - [Checkbox]: Displays a checkbox with associated data.
 */
sealed class ListItemTrailingContentData {
    data class Icon(val iconData: IconData) : ListItemTrailingContentData()
    data class Checkbox(val checkboxData: CheckboxData) : ListItemTrailingContentData()
}

/**
 * Represents the clickable area of a [ListItem].
 *
 * This enum defines the regions within a [ListItem] that respond to user clicks.
 *
 * @property ENTIRE_ROW  The entire row of the [ListItem] is clickable.
 * @property TRAILING_CONTENT The trailing content (e.g., an icon or checkbox) of the [ListItem] is clickable.
 */
enum class ClickableArea {
    ENTIRE_ROW, TRAILING_CONTENT,
}

/**
 * A composable function that displays a list item with various content options.
 *
 * This function provides a flexible way to display list items with customizable content,
 * including leading and trailing elements, main and supporting text, and optional image content.
 * It also supports hiding sensitive content by blurring it on devices with Android S and above.
 *
 * **Content Customization:**
 * - **Leading Content:** Can be an icon or a user image specified by [ListItemData.leadingContentData].
 * - **Main Content:** Can be text or an image specified by [ListItemData.mainContentData].
 * - **Supporting Text:** Provides additional information below the main content, specified by [ListItemData.supportingText].
 * - **Trailing Content:** Can be a checkbox or an icon specified by [ListItemData.trailingContentData].
 * - **Overline Text:**  Displays text above the main content, specified by [ListItemData.overlineText].
 *
 * **Sensitivity Handling:**
 * - If `hideSensitiveContent` is true and the device supports blurring (Android S and above), the content will be blurred.
 * - On devices that don't support blurring, sensitive content is either hidden or displayed as plain text
 *   depending on the content type (e.g., images are hidden, leading content is hidden, text is displayed).
 *
 * **Click Handling:**
 * - `onItemClick` is invoked when a clickable area of the item is clicked. It receives the [ListItemData] object as a parameter.
 *   This allows you to handle item clicks and perform actions based on the selected item.
 * - `clickableAreas` defines which areas of the list item are clickable. By default, only the trailing content is clickable.
 *    You can set it to [ClickableArea.ENTIRE_ROW] to make the entire row clickable, or provide a custom list of clickable areas.
 *
 * @param item The [ListItemData] object containing the data to display in the list item.
 * @param onItemClick An optional lambda function that is invoked when a clickable area of the item is clicked.
 * @param modifier A [Modifier] that can be used to customize the appearance of the list item.
 * @param hideSensitiveContent A boolean flag indicating whether to hide sensitive content by blurring it. Defaults to false.
 * @param mainContentVerticalPadding An optional value specifying the vertical padding */
@Composable
fun ListItem(
    item: ListItemData,
    onItemClick: ((item: ListItemData) -> Unit)?,
    modifier: Modifier = Modifier,
    hideSensitiveContent: Boolean = false,
    mainContentVerticalPadding: Dp? = null,
    clickableAreas: List<ClickableArea> = listOf(TRAILING_CONTENT),
    overlineTextStyle: TextStyle = MaterialTheme.typography.labelMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant
    ),
) {
    val maxSecondaryTextLines = 1
    val textOverflow = TextOverflow.Ellipsis
    val mainTextStyle = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurface
    )

    // API check
    val supportsBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val blurModifier = remember(supportsBlur, hideSensitiveContent) {
        if (supportsBlur && hideSensitiveContent) {
            Modifier.blur(10.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
        } else {
            Modifier
        }
    }

    with(item) {
        Row(
            modifier = if (clickableAreas.contains(ENTIRE_ROW)) {
                Modifier.clickable {
                    onItemClick?.let { safeOnItemClick ->
                        safeOnItemClick(item)
                    }
                }
            } else {
                Modifier
            }.then(
                other = modifier.padding(horizontal = SPACING_MEDIUM.dp)
            ),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Leading Content
            if (!hideSensitiveContent || supportsBlur) {
                leadingContentData?.let { safeLeadingContentData ->
                    val leadingContentModifier = Modifier
                        .padding(end = SIZE_MEDIUM.dp)
                        .size(ICON_SIZE_40.dp)
                        .then(blurModifier)

                    when (safeLeadingContentData) {
                        is ListItemLeadingContentData.Icon -> WrapImage(
                            modifier = leadingContentModifier,
                            iconData = safeLeadingContentData.iconData,
                        )

                        is ListItemLeadingContentData.UserImage -> ImageOrPlaceholder(
                            modifier = leadingContentModifier,
                            base64Image = safeLeadingContentData.userBase64Image,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = mainContentVerticalPadding ?: SPACING_SMALL.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
            ) {
                // Overline Text
                overlineText?.let { safeOverlineText ->
                    Text(
                        text = safeOverlineText,
                        style = if (hideSensitiveContent && !supportsBlur) mainTextStyle else overlineTextStyle,
                        maxLines = maxSecondaryTextLines,
                        overflow = textOverflow,
                    )
                }

                // Main Content
                if (!hideSensitiveContent || supportsBlur) {
                    when (mainContentData) {
                        is Image -> ImageOrPlaceholder(
                            modifier = Modifier
                                .wrapContentWidth()
                                .padding(top = SPACING_SMALL.dp)
                                .then(blurModifier),
                            base64Image = mainContentData.base64Image,
                            contentScale = ContentScale.Fit,
                        )

                        is Text -> Text(
                            modifier = blurModifier,
                            text = mainContentData.text,
                            style = mainTextStyle,
                            overflow = textOverflow,
                        )
                    }
                }

                // Supporting Text
                supportingText?.let { safeSupportingText ->
                    Text(
                        text = safeSupportingText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        maxLines = maxSecondaryTextLines,
                        overflow = textOverflow,
                    )
                }
            }

            // Trailing Content
            trailingContentData?.let { safeTrailingContentData ->
                when (safeTrailingContentData) {
                    is Checkbox -> WrapCheckbox(
                        checkboxData = safeTrailingContentData.checkboxData.copy(
                            onCheckedChange = if (clickableAreas.contains(TRAILING_CONTENT)) {
                                { onItemClick?.invoke(item) }
                            } else null
                        ),
                        modifier = Modifier.padding(start = SIZE_MEDIUM.dp),
                    )

                    is Icon -> WrapIconButton(
                        modifier = Modifier
                            .padding(start = SIZE_MEDIUM.dp)
                            .size(DEFAULT_ICON_SIZE.dp),
                        iconData = safeTrailingContentData.iconData,
                        customTint = MaterialTheme.colorScheme.primary,
                        onClick = if (clickableAreas.contains(TRAILING_CONTENT)) {
                            { onItemClick?.invoke(item) }
                        } else null,
                        throttleClicks = false,
                    )
                }
            }
        }
    }
}

@ThemeModePreviews
@Composable
private fun ListItemPreview() {
    PreviewTheme {
        val modifier = Modifier.fillMaxWidth()
        Column(
            modifier = modifier
                .padding(SPACING_MEDIUM.dp),
            verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp)
        ) {
            // Basic ListItem with only mainText
            ListItem(
                item = ListItemData(
                    itemId = "1",
                    mainContentData = Text(text = "Basic Item")
                ),
                modifier = modifier,
                onItemClick = {},
            )

            // ListItem with overlineText and supportingText
            ListItem(
                item = ListItemData(
                    itemId = "2",
                    mainContentData = Text(text = "Item with Overline and Supporting Text"),
                    overlineText = "Overline Text",
                    supportingText = "Supporting Text"
                ),
                modifier = modifier,
                onItemClick = {},
            )

            // ListItem with leadingIcon
            ListItem(
                item = ListItemData(
                    itemId = "3",
                    mainContentData = Text(text = "Item with Leading Icon"),
                    leadingContentData = ListItemLeadingContentData.Icon(iconData = AppIcons.Add),
                ),
                modifier = modifier,
                onItemClick = {},
            )

            // ListItem with trailing icon
            ListItem(
                item = ListItemData(
                    itemId = "4",
                    mainContentData = Text(text = "Item with Trailing Icon"),
                    trailingContentData = Icon(
                        iconData = AppIcons.KeyboardArrowDown,
                    )
                ),
                modifier = modifier,
                onItemClick = {},
            )

            // ListItem with trailing enabled checkbox
            ListItem(
                item = ListItemData(
                    itemId = "5",
                    mainContentData = Text(text = "Item with Trailing Enabled Checkbox"),
                    trailingContentData = Checkbox(
                        checkboxData = CheckboxData(
                            isChecked = true,
                        )
                    )
                ),
                modifier = modifier,
                onItemClick = {},
            )

            // ListItem with trailing disabled checkbox
            ListItem(
                item = ListItemData(
                    itemId = "5",
                    mainContentData = Text(text = "Item with Trailing Disabled Checkbox"),
                    trailingContentData = Checkbox(
                        checkboxData = CheckboxData(
                            isChecked = true,
                            enabled = false,
                        )
                    )
                ),
                modifier = modifier,
                onItemClick = {},
            )

            // ListItem with all elements
            ListItem(
                item = ListItemData(
                    itemId = "6",
                    mainContentData = Text(text = "Full Item Example"),
                    overlineText = "Overline Text",
                    supportingText = "Supporting Text",
                    leadingContentData = ListItemLeadingContentData.Icon(iconData = AppIcons.Add),
                    trailingContentData = Icon(
                        iconData = AppIcons.KeyboardArrowDown,
                    )
                ),
                modifier = modifier,
                onItemClick = {},
            )
        }
    }
}