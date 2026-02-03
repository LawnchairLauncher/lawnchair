/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.widgetpicker.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.launcher3.widgetpicker.R
import com.android.launcher3.widgetpicker.ui.theme.WidgetPickerTheme
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * A search bar shown in widget picker for searching widgets.
 *
 * By default shows a leading search icon, followed by the placeholder text. When user focuses on
 * the search bar, keyboard (if supported) is automatically opened, the search icon turns in to a
 * back button and user can either use this back button or the predictive back to go back.
 *
 * @param text currently entered text
 * @param isSearching whether user is actively searching and search results are shown
 * @param onSearch callback invoked when users types in the search bar; as an effect, [isSearching]
 *   should be set to true
 * @param onToggleSearchMode callback invoked with `true` when user focuses on search bar to start a
 *   search; false when user either presses back button within search bar or uses predictive back to
 *   exit search.
 * @param modifier modifier for the top level search bar
 */
@Composable
fun WidgetsSearchBar(
    text: String,
    isSearching: Boolean,
    onSearch: (String) -> Unit,
    onToggleSearchMode: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val exitSearchMode = {
        onSearch("")
        focusManager.clearFocus()
        onToggleSearchMode(false)
    }

    BasicTextField(
        modifier =
            modifier
                .heightIn(min = WidgetsSearchBarDimens.minHeight)
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        onToggleSearchMode(true)
                    }
                },
        value = text,
        onValueChange = { onSearch(it) },
        singleLine = true,
        cursorBrush = SolidColor(WidgetPickerTheme.colors.searchBarCursor),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            keyboardController?.hide()
            onSearch(text)
        }),
        interactionSource = interactionSource,
        textStyle =
            WidgetPickerTheme.typography.searchBarText.copy(
                color = WidgetPickerTheme.colors.searchBarText
            ),
        decorationBox =
            @Composable { innerTextField ->
                WidgetsSearchBarContent(
                    text = text,
                    innerTextField = innerTextField,
                    interactionSource = interactionSource,
                    isSearching = isSearching,
                    exitSearchMode = exitSearchMode,
                    onClear = { onSearch("") },
                )
            },
    )

    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    PredictiveBackHandler(isSearching) { progress: Flow<BackEventCompat> ->
        try {
            progress.collect {}
            exitSearchMode()
        } catch (_: CancellationException) {}
    }
}

@Composable
private fun WidgetsSearchBarContent(
    text: String,
    innerTextField: @Composable () -> Unit,
    interactionSource: MutableInteractionSource,
    isSearching: Boolean,
    exitSearchMode: () -> Unit,
    onClear: () -> Unit,
) {
    TextFieldDefaults.DecorationBox(
        value = text,
        enabled = true,
        singleLine = true,
        innerTextField = innerTextField,
        interactionSource = interactionSource,
        visualTransformation = VisualTransformation.None,
        contentPadding = WidgetsSearchBarDimens.paddingValues,
        container = {
            TextFieldDefaults.Container(
                enabled = true,
                isError = false,
                interactionSource = interactionSource,
                shape = CircleShape,
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = WidgetPickerTheme.colors.searchBarBackground,
                        unfocusedContainerColor = WidgetPickerTheme.colors.searchBarBackground,
                    ),
                focusedIndicatorLineThickness = 0.dp,
                unfocusedIndicatorLineThickness = 0.dp,
            )
        },
        placeholder = { PlaceholderText() },
        leadingIcon = { LeadingButton(isSearching = isSearching, onBack = exitSearchMode) },
        trailingIcon = {
            if (text.isNotEmpty()) {
                ClearButton(onClick = onClear)
            }
        },
    )
}

@Composable
private fun LeadingButton(isSearching: Boolean, onBack: () -> Unit) {
    AnimatedContent(
        contentAlignment = Alignment.Center,
        modifier = Modifier.minimumInteractiveComponentSize(),
        targetState = isSearching,
        transitionSpec = {
            fadeIn(animationSpec = tween(durationMillis = 300)) togetherWith
                fadeOut(animationSpec = tween(durationMillis = 300))
        },
    ) { showBackButton ->
        if (showBackButton) {
            BackButton(onClick = onBack)
        } else {
            SearchIcon()
        }
    }
}

@Composable
private fun SearchIcon() {
    Icon(
        imageVector = Icons.Filled.Search,
        tint = WidgetPickerTheme.colors.searchBarSearchIcon,
        contentDescription = null, // decorative
    )
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    IconButton(
        colors =
            IconButtonDefaults.iconButtonColors()
                .copy(
                    containerColor = Color.Transparent,
                    contentColor = WidgetPickerTheme.colors.searchBarBackButtonIcon,
                ),
        onClick = onClick,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = stringResource(R.string.widget_search_bar_back_button_label),
        )
    }
}

@Composable
private fun PlaceholderText() {
    Text(
        text = stringResource(R.string.widgets_search_bar_hint),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = WidgetPickerTheme.colors.searchBarPlaceholderText,
        style = WidgetPickerTheme.typography.searchBarPlaceholderText,
    )
}

@Composable
private fun ClearButton(onClick: () -> Unit) {
    IconButton(
        colors =
            IconButtonDefaults.iconButtonColors()
                .copy(
                    containerColor = Color.Transparent,
                    contentColor = WidgetPickerTheme.colors.searchBarClearButtonIcon,
                ),
        onClick = onClick,
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = stringResource(R.string.widget_search_bar_clear_button_label),
        )
    }
}

private object WidgetsSearchBarDimens {
    val paddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    val minHeight = 52.dp
}
