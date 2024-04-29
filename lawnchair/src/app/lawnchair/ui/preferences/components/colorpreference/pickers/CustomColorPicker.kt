package app.lawnchair.ui.preferences.components.colorpreference.pickers

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color.argb
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.ui.preferences.components.colorpreference.HsbColorSlider
import app.lawnchair.ui.preferences.components.colorpreference.HsbSliderType
import app.lawnchair.ui.preferences.components.colorpreference.RgbColorSlider
import app.lawnchair.ui.preferences.components.colorpreference.colorStringToIntColor
import app.lawnchair.ui.preferences.components.colorpreference.hsvValuesToIntColor
import app.lawnchair.ui.preferences.components.colorpreference.intColorToColorString
import app.lawnchair.ui.preferences.components.colorpreference.intColorToHsvColorArray
import app.lawnchair.ui.preferences.components.layout.Chip
import app.lawnchair.ui.preferences.components.layout.ClickableIcon
import app.lawnchair.ui.preferences.components.layout.DividerColumn
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.util.requireSystemService
import com.android.launcher3.R
import kotlinx.coroutines.launch

/**
 * Unlike [PresetsList] & [SwatchGrid], This Composable allows the user to select a fully custom [ColorOption] using HEX, HSB & RGB values.
 *
 * @see HexColorPicker
 * @see HsvColorPicker
 * @see RgbColorPicker
 */
@Composable
fun CustomColorPicker(
    selectedColor: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    val focusManager = LocalFocusManager.current

    val selectedColorCompose = Color(selectedColor)

    val textFieldValue = remember {
        mutableStateOf(
            TextFieldValue(
                text = intColorToColorString(color = selectedColor),
            ),
        )
    }

    Column(
        modifier = modifier,
    ) {
        PreferenceGroup(
            heading = stringResource(id = R.string.hex),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .requiredSize(48.dp)
                        .clip(CircleShape)
                        .background(selectedColorCompose),
                )

                Spacer(modifier = Modifier.requiredWidth(16.dp))

                HexColorPicker(
                    textFieldValue = textFieldValue.value,
                    onTextFieldValueChange = { newValue ->
                        val newText = newValue.text.removePrefix("#").take(6).uppercase()
                        textFieldValue.value = newValue.copy(text = newText)
                        val newColor = colorStringToIntColor(colorString = newText)
                        if (newColor != null) {
                            onSelect(newColor)
                        }
                    },
                )
            }
        }

        val pagerState = rememberPagerState(
            initialPage = 0,
            pageCount = { 2 },
        )
        val scope = rememberCoroutineScope()
        val scrollToPage =
            { page: Int -> scope.launch { pagerState.animateScrollToPage(page) } }

        PreferenceGroup(
            heading = stringResource(id = R.string.color_sliders),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Column {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 16.dp),
                ) {
                    Chip(
                        label = stringResource(id = R.string.hsb),
                        onClick = { scrollToPage(0) },
                        currentOffset = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                        page = 0,
                    )
                    Chip(
                        label = stringResource(id = R.string.rgb),
                        onClick = { scrollToPage(1) },
                        currentOffset = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                        page = 1,
                    )
                }

                HorizontalPager(
                    modifier = Modifier.animateContentSize(),
                    state = pagerState,
                    verticalAlignment = Alignment.Top,
                ) { page ->
                    when (page) {
                        0 -> {
                            HsvColorPicker(
                                selectedColor = selectedColor,
                                onSelectedColorChange = {
                                    textFieldValue.value =
                                        textFieldValue.value.copy(
                                            text = intColorToColorString(
                                                selectedColor,
                                            ),
                                        )
                                },
                                onSliderValuesChange = { newColor ->
                                    focusManager.clearFocus()
                                    onSelect(newColor)
                                },
                            )
                        }
                        1 -> {
                            RgbColorPicker(
                                selectedColor = selectedColor,
                                onSelectedColorChange = {
                                    textFieldValue.value =
                                        textFieldValue.value.copy(
                                            text = intColorToColorString(selectedColor),
                                        )
                                },
                                onSliderValuesChange = { newColor ->
                                    focusManager.clearFocus()
                                    onSelect(newColor)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HexColorPicker(
    textFieldValue: TextFieldValue,
    modifier: Modifier = Modifier,
    onTextFieldValueChange: (TextFieldValue) -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val clipboardManager: ClipboardManager = context.requireSystemService()

    val invalidString = colorStringToIntColor(textFieldValue.text) == null

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        OutlinedTextField(
            modifier = Modifier.weight(1f),
            textStyle = LocalTextStyle.current.copy(
                fontSize = 18.sp,
                textAlign = TextAlign.Start,
            ),
            isError = invalidString,
            value = textFieldValue,
            onValueChange = onTextFieldValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                },
            ),
            trailingIcon = {
                Crossfade(targetState = invalidString, label = "") {
                    if (it) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_warning),
                            contentDescription = stringResource(id = R.string.invalid_color),
                        )
                    }
                }
            },
        )

        Spacer(modifier = Modifier.requiredWidth(16.dp))

        ClickableIcon(
            imageVector = Icons.Rounded.ContentCopy,
            onClick = {
                val clip =
                    ClipData.newPlainText(context.getString(R.string.hex), textFieldValue.text)
                clipboardManager.setPrimaryClip(clip)
                Toast.makeText(
                    context,
                    context.getString(R.string.copied_toast),
                    Toast.LENGTH_SHORT,
                ).show()
            },
        )

        ClickableIcon(
            imageVector = Icons.Rounded.ContentPaste,
            onClick = {
                clipboardManager.primaryClip?.getItemAt(0)?.text?.let {
                    onTextFieldValueChange(textFieldValue.copy(text = it.toString()))
                    focusManager.clearFocus()
                }
            },
        )
    }
}

@Composable
private fun HsvColorPicker(
    selectedColor: Int,
    onSelectedColorChange: () -> Unit,
    onSliderValuesChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hsv = remember { intColorToHsvColorArray(selectedColor) }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var brightness by remember { mutableFloatStateOf(hsv[2]) }
    val coroutineScope = rememberCoroutineScope()

    fun updateColor(
        newHue: Float? = null,
        newSaturation: Float? = null,
        newBrightness: Float? = null,
    ) {
        coroutineScope.launch {
            if (newHue != null) hue = newHue
            if (newSaturation != null) saturation = newSaturation
            if (newBrightness != null) brightness = newBrightness

            onSliderValuesChange(
                hsvValuesToIntColor(
                    hue = newHue ?: hue,
                    saturation = newSaturation ?: saturation,
                    brightness = newBrightness ?: brightness,
                ),
            )
        }
    }

    DividerColumn(
        modifier = modifier,
    ) {
        HsbColorSlider(
            type = HsbSliderType.HUE,
            value = hue,
            onValueChange = { newValue -> updateColor(newHue = newValue) },
        )
        HsbColorSlider(
            type = HsbSliderType.SATURATION,
            value = saturation,
            onValueChange = { newValue -> updateColor(newSaturation = newValue) },
        )
        HsbColorSlider(
            type = HsbSliderType.BRIGHTNESS,
            value = brightness,
            onValueChange = { newValue -> updateColor(newBrightness = newValue) },
        )

        val latestOnSelectedColorChange by rememberUpdatedState(onSelectedColorChange)
        LaunchedEffect(key1 = selectedColor) {
            if (selectedColor ==
                hsvValuesToIntColor(hue, saturation, brightness)
            ) {
                return@LaunchedEffect
            }

            intColorToHsvColorArray(selectedColor).also {
                hue = it[0]
                saturation = it[1]
                brightness = it[2]
            }

            latestOnSelectedColorChange()
        }
    }
}

@Composable
private fun RgbColorPicker(
    selectedColor: Int,
    onSelectedColorChange: () -> Unit,
    onSliderValuesChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selectedColorCompose: Color = Color(selectedColor),
) {
    var red by remember { mutableIntStateOf(selectedColor.red) }
    var green by remember { mutableIntStateOf(selectedColor.green) }
    var blue by remember { mutableIntStateOf(selectedColor.blue) }
    val coroutineScope = rememberCoroutineScope()

    fun updateColor(
        newRed: Int? = null,
        newGreen: Int? = null,
        newBlue: Int? = null,
    ) {
        coroutineScope.launch {
            if (newRed != null) red = newRed
            if (newGreen != null) green = newGreen
            if (newBlue != null) blue = newBlue

            onSliderValuesChange(
                argb(
                    255,
                    newRed ?: red,
                    newGreen ?: green,
                    newBlue ?: blue,
                ),
            )
        }
    }

    DividerColumn(
        modifier = modifier,
    ) {
        RgbColorSlider(
            label = stringResource(id = R.string.rgb_red),
            value = red,
            colorStart = selectedColorCompose.copy(red = 0f),
            colorEnd = selectedColorCompose.copy(red = 1f),
            onValueChange = { newValue -> updateColor(newRed = newValue.toInt()) },
        )
        RgbColorSlider(
            label = stringResource(id = R.string.rgb_green),
            value = green,
            colorStart = selectedColorCompose.copy(green = 0f),
            colorEnd = selectedColorCompose.copy(green = 1f),
            onValueChange = { newValue -> updateColor(newGreen = newValue.toInt()) },
        )
        RgbColorSlider(
            label = stringResource(id = R.string.rgb_blue),
            value = blue,
            colorStart = selectedColorCompose.copy(blue = 0f),
            colorEnd = selectedColorCompose.copy(blue = 1f),
            onValueChange = { newValue -> updateColor(newBlue = newValue.toInt()) },
        )

        val latestOnSelectedColorChange by rememberUpdatedState(onSelectedColorChange)
        LaunchedEffect(key1 = selectedColor) {
            red = selectedColor.red
            green = selectedColor.green
            blue = selectedColor.blue
            latestOnSelectedColorChange()
        }
    }
}
