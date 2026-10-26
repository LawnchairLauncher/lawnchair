package app.lawnchair.curated

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.res.Configuration
import android.net.Uri
import android.os.Process
import android.widget.Toast
import com.android.launcher3.R
import com.android.launcher3.model.data.WorkspaceItemInfo

/**
 * Where a curated app comes from, and the prompt shown when an uninstalled one is tapped. The
 * user always confirms; the store app or Obtainium does the actual installing.
 */
object CuratedSource {
    private const val EXTRA_SOURCE = "app.lawnchair.curated.SOURCE"
    private const val EXTRA_NAME = "app.lawnchair.curated.NAME"

    private val FDROID_CLIENTS = listOf(
        "org.fdroid.fdroid", "org.fdroid.basic", "com.looker.droidify", "com.machiav3lli.fdroid",
    )
    private val OBTAINIUM = listOf("dev.imranr.obtainium", "dev.imranr.obtainium.fdroid")
    private const val FDROID_SITE = "https://f-droid.org/"
    private const val OBTAINIUM_RELEASES = "https://github.com/ImranR98/Obtainium/releases/latest"

    fun promiseIntent(app: CuratedApp): Intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setPackage(app.packageName)
        .putExtra(EXTRA_SOURCE, app.source ?: "")
        .putExtra(EXTRA_NAME, app.name)

    /** Handles a tap on a not yet installed curated app; false if the item is not one. */
    @JvmStatic
    fun onPromiseClicked(context: Context, item: WorkspaceItemInfo): Boolean {
        val intent = item.intent ?: return false
        val source = intent.getStringExtra(EXTRA_SOURCE) ?: return false
        val pkg = intent.`package` ?: return false
        val name = intent.getStringExtra(EXTRA_NAME) ?: item.title?.toString() ?: pkg
        showInstallPrompt(context, pkg, name, source)
        return true
    }

    private class Option(val label: String, val action: () -> Unit)

    private fun showInstallPrompt(context: Context, pkg: String, name: String, source: String) {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val fdroidClient = FDROID_CLIENTS.firstOrNull { isInstalled(launcherApps, it) }
        val obtainium = OBTAINIUM.firstOrNull { isInstalled(launcherApps, it) }
        val fdroidPage = "https://f-droid.org/packages/$pkg/"
        val projectUrl = source.takeIf { it.startsWith("http") }
        val options = mutableListOf<Option>()

        if (source == CuratedApp.SOURCE_FDROID) {
            if (fdroidClient != null) {
                options += Option(context.getString(R.string.curated_install_fdroid, appLabel(context, fdroidClient))) {
                    open(context, Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).setPackage(fdroidClient))
                }
            }
            if (obtainium != null) {
                options += Option(context.getString(R.string.curated_install_obtainium)) {
                    open(context, obtainiumIntent(fdroidPage, obtainium))
                }
            }
        } else if (projectUrl != null) {
            if (obtainium != null) {
                options += Option(context.getString(R.string.curated_install_obtainium)) {
                    open(context, obtainiumIntent(projectUrl, obtainium))
                }
            }
            options += Option(context.getString(R.string.curated_open_project)) { open(context, view(projectUrl)) }
        }
        // Without a client, offer to get one first
        if (source == CuratedApp.SOURCE_FDROID && fdroidClient == null) {
            options += Option(context.getString(R.string.curated_get_fdroid)) { open(context, view(FDROID_SITE)) }
        }
        if (obtainium == null && (projectUrl != null || fdroidClient == null)) {
            options += Option(context.getString(R.string.curated_get_obtainium)) { open(context, view(OBTAINIUM_RELEASES)) }
        }
        if (source == CuratedApp.SOURCE_FDROID) {
            options += Option(context.getString(R.string.curated_open_fdroid_page)) { open(context, view(fdroidPage)) }
        }

        val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val theme = if (dark) {
            android.R.style.Theme_DeviceDefault_Dialog_Alert
        } else {
            android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
        }
        val message = if (fdroidClient == null && obtainium == null) {
            context.getString(R.string.curated_install_no_client, name)
        } else {
            context.getString(R.string.curated_install_message, name)
        }
        AlertDialog.Builder(context, theme)
            .setTitle(context.getString(R.string.curated_install_title, name))
            .setMessage(message)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            .apply {
                // A message and a list cannot share a plain AlertDialog, so options are buttons in a list view
                setView(optionList(context, options) { dismiss() })
            }
            .show()
    }

    private fun optionList(context: Context, options: List<Option>, onPicked: () -> Unit) =
        android.widget.ListView(context).apply {
            adapter = android.widget.ArrayAdapter(context, android.R.layout.simple_list_item_1, options.map { it.label })
            setOnItemClickListener { _, _, position, _ ->
                onPicked()
                options[position].action()
            }
        }

    private fun obtainiumIntent(url: String, obtainium: String) =
        Intent(Intent.ACTION_VIEW, Uri.parse("obtainium://add/$url")).setPackage(obtainium)

    private fun view(url: String) = Intent(Intent.ACTION_VIEW, Uri.parse(url))

    private fun open(context: Context, intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.curated_open_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun isInstalled(launcherApps: LauncherApps, pkg: String) = try {
        launcherApps.isPackageEnabled(pkg, Process.myUserHandle())
    } catch (e: Exception) {
        false
    }

    private fun appLabel(context: Context, pkg: String): String = try {
        context.packageManager.run { getApplicationLabel(getApplicationInfo(pkg, 0)).toString() }
    } catch (e: Exception) {
        "F-Droid"
    }
}
