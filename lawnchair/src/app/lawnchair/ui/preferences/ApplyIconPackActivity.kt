package app.lawnchair.ui.preferences

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Surface
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.util.navigationBarsOrDisplayCutoutPadding
import coil.annotation.ExperimentalCoilApi
import coil.compose.rememberImagePainter
import com.android.launcher3.R
import com.google.accompanist.insets.ProvideWindowInsets
import java.util.*

class ApplyIconPackActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val packPackageName = intent.getStringExtra("packageName")
        val (packName, packIcon) =
            try {
                packageManager.getApplicationInfo(packPackageName ?: "INVALID_PKG", 0)
                    .let {
                        packageManager.getApplicationLabel(it)
                            .toString() to packageManager.getApplicationIcon(it)
                            .toBitmap()
                    }
            } catch (nameNotFoundException: PackageManager.NameNotFoundException) {
                "\"%s\"".format(Locale.getDefault(), packPackageName) to null
            }

        setContent {
            LawnchairTheme {
                ProvideWindowInsets {
                    CompositionLocalProvider(LocalPreferenceInteractor provides viewModel<PreferenceViewModel>()) {
                        val iconPacks by LocalPreferenceInteractor.current.iconPacks.collectAsState()
                        val isIconPackValid = iconPacks.any { it.packageName == packPackageName }
                        Box(modifier = Modifier.fillMaxSize()) {
                            Surface(
                                shape = RoundedCornerShape(topStart = 25.dp, topEnd = 25.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .navigationBarsOrDisplayCutoutPadding()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(25.dp)
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        if (isIconPackValid && packPackageName != null) {
                                            ApplyIconPackShowcase(
                                                iconPackPackageName = packPackageName,
                                                iconPackName = packName,
                                                iconPackIcon = packIcon
                                            )
                                        } else {
                                            InvalidIconPackPlaceholder(
                                                iconPackName = packName,
                                                iconPackIcon = packIcon
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    @Composable
    private fun ColumnScope.CircularIcon(bitmap: Bitmap?) {
        if (bitmap != null)
            Surface(
                shape = CircleShape,
                modifier = Modifier
                    .height(80.dp)
                    .aspectRatio(1f)
                    .align(Alignment.CenterHorizontally),
                color = MaterialTheme.colorScheme.surface
            ) {
                Image(
                    painter = rememberImagePainter(bitmap),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
    }

    @Composable
    private fun ColumnScope.InvalidIconPackPlaceholder(
        iconPackName: String?,
        iconPackIcon: Bitmap?
    ) {
        CircularIcon(bitmap = iconPackIcon)
        HorizontalSpacer()
        Text(
            text = stringResource(R.string.invalid_icon_pack_description).format(
                Locale.getDefault(),
                iconPackName
            ),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        HorizontalSpacer()
        FullWidthButton(
            muted = true,
            text = stringResource(id = android.R.string.cancel)
        ) {
            finish()
        }
    }

    @Composable
    private fun ColumnScope.ApplyIconPackShowcase(
        iconPackPackageName: String,
        iconPackName: String,
        iconPackIcon: Bitmap?
    ) {
        val hapticFeedback = LocalHapticFeedback.current
        var iconPacksPreference by preferenceManager().iconPackPackage.getAdapter()
        CircularIcon(bitmap = iconPackIcon)
        HorizontalSpacer(height = 8.dp)
        Text(
            text = iconPackName,
            modifier = Modifier.fillMaxWidth(),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        HorizontalSpacer(height = 8.dp)
        Text(
            text = formattedIconPackApplyMessage(iconPackName = iconPackName),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        HorizontalSpacer()
        FullWidthButton(text = stringResource(id = R.string.apply_grid)) {
            if (iconPacksPreference == iconPackPackageName) {
                iconPacksPreference = ""
            }
            iconPacksPreference = iconPackPackageName
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            finish()
        }
        HorizontalSpacer()
        FullWidthButton(
            muted = true,
            text = stringResource(id = android.R.string.cancel)
        ) {
            finish()
        }
    }

    @Composable
    private fun FullWidthButton(
        text: String,
        muted: Boolean = false,
        onClick: () -> Unit
    ) {
        Button(
            colors = ButtonDefaults.buttonColors(containerColor = if (muted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick
        ) {
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(16.dp),
                color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
            )
        }
    }


    @Composable
    private fun formattedIconPackApplyMessage(iconPackName: String): String {
        return stringResource(R.string.apply_icon_pack_description).format(
            iconPackName, kotlin.run {
                "%s > %s > %s".format(
                    Locale.getDefault(),
                    stringResource(id = R.string.smartspace_preferences),
                    stringResource(id = R.string.general_label),
                    stringResource(id = R.string.icon_pack)
                )
            }
        )
    }

    @Composable
    private fun HorizontalSpacer(height: Dp = 16.dp) {
        Spacer(
            modifier = Modifier
                .requiredHeight(height)
                .fillMaxWidth()
        )
    }
}