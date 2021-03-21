package com.lawnchair.ui.preferences

import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun ContributorLink(@DrawableRes iconResId: Int, url: String) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .width(48.dp)
            .height(48.dp)
            .clip(CircleShape)
            .clickable {
                val webpage = Uri.parse(url)
                val intent = Intent(Intent.ACTION_VIEW, webpage)
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                }
            }
    ) {
        Image(
            painterResource(id = iconResId), contentDescription = null, modifier = Modifier
                .height(24.dp)
                .width(24.dp)
                .align(Alignment.Center), colorFilter = ColorFilter.tint(color = MaterialTheme.colors.onBackground)
        )
    }
}