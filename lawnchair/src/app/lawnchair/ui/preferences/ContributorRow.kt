package app.lawnchair.ui.preferences

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.accompanist.glide.GlideImage

@Composable
fun ContributorRow(name: String, description: String, photoUrl: String, url: String, showDivider: Boolean = true) {
    val context = LocalContext.current

    PreferenceTemplate(height = 72.dp, showDivider = showDivider) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    val webpage = Uri.parse(url)
                    val intent = Intent(Intent.ACTION_VIEW, webpage)
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                    }
                }
                .padding(start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlideImage(
                data = photoUrl,
                contentDescription = null,
                fadeIn = true,
                modifier = Modifier
                    .clip(CircleShape)
                    .width(32.dp)
                    .height(32.dp),
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colors.onBackground.copy(alpha = 0.08F))
                    )
                }
            )
            Spacer(modifier = Modifier.requiredWidth(16.dp))
            Column {
                Text(text = name, style = MaterialTheme.typography.subtitle1, color = MaterialTheme.colors.onBackground)
                CompositionLocalProvider(
                    LocalContentAlpha provides ContentAlpha.medium,
                    LocalContentColor provides MaterialTheme.colors.onBackground
                ) {
                    Text(text = description, style = MaterialTheme.typography.body2)
                }
            }
        }
    }
}