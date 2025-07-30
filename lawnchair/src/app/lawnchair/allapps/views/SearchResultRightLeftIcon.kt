package app.lawnchair.allapps.views

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import app.lawnchair.font.FontManager
import app.lawnchair.launcher
import app.lawnchair.search.adapter.SearchTargetCompat
import app.lawnchair.util.AppInfo
import app.lawnchair.util.AppInfoHelper
import app.lawnchair.util.ImageViewWrapper
import com.android.app.search.LayoutType
import com.android.launcher3.DeviceProfile
import com.android.launcher3.R

class SearchResultRightLeftIcon(context: Context, attrs: AttributeSet?) :
    LinearLayout(context, attrs),
    SearchResultView {

    private val launcher = context.launcher
    private var grid: DeviceProfile = launcher.deviceProfile
    private lateinit var title: TextView
    private lateinit var avatar: SearchResultIcon
    private lateinit var call: ImageView
    private lateinit var message: ImageView
    private lateinit var preview: ImageViewWrapper
    private val appInfoHelper = AppInfoHelper(context)
    private var defPhoneAppInfo: AppInfo? = null
    private var defSmsAppInfo: AppInfo? = null
    private var isSmall = false

    private var flags = 0

    override fun onFinishInflate() {
        super.onFinishInflate()
        isSmall = id == R.id.search_result_small_icon_row_left_right
        defPhoneAppInfo = appInfoHelper.getDefaultPhoneAppInfo()
        defSmsAppInfo = appInfoHelper.getDefaultMessageAppInfo()
        onFocusChangeListener = launcher.focusHandler
        title = ViewCompat.requireViewById(this, R.id.title)
        avatar = ViewCompat.requireViewById(this, R.id.avatar)
        call = ViewCompat.requireViewById(this, R.id.icon2)
        message = ViewCompat.requireViewById(this, R.id.icon1)
        preview = ViewCompat.requireViewById(this, R.id.files_preview)
        FontManager.INSTANCE.get(context).setCustomFont(title, R.id.font_body)
        setUpdateResources()
    }

    private fun setUpdateResources() {
        if (isSmall) {
            message.setImageDrawable(defSmsAppInfo?.appIcon)
            call.setImageDrawable(defPhoneAppInfo?.appIcon)
            call.visibility = VISIBLE
            message.visibility = VISIBLE
            avatar.visibility = VISIBLE
            preview.visibility = GONE
        } else {
            call.visibility = GONE
            message.visibility = GONE
            avatar.visibility = GONE
            preview.visibility = VISIBLE
        }
        val heightRes = if (isSmall) {
            resources.getDimensionPixelSize(R.dimen.search_result_small_row_height)
        } else {
            resources.getDimensionPixelSize(
                R.dimen.search_result_files_row_height,
            )
        }
        val layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            heightRes,
        )
        layoutParams.leftMargin = grid.allAppsPadding.left
        layoutParams.rightMargin = grid.allAppsPadding.right
        this.layoutParams = layoutParams
    }

    override val isQuickLaunch: Boolean get() = hasFlag(flags, SearchResultView.FLAG_QUICK_LAUNCH)

    override val titleText: CharSequence? get() = title.text

    override fun launch(): Boolean {
        val logTag = "Contact or files"
        Log.d(logTag, "in launch")
        Log.d(logTag, performClick().toString())
        return true
    }

    override fun bind(
        target: SearchTargetCompat,
        shortcuts: List<SearchTargetCompat>,
    ) {
        title.text = target.searchAction?.title
        val isNewFile = target.resultType == SearchTargetCompat.RESULT_TYPE_FILE_TILE &&
            target.layoutType == LayoutType.THUMBNAIL
        val isFile = !isSmall && isNewFile

        if (!isFile) {
            avatar.bind(target) {
                title.text = it.title
                tag = it
            }
            val number = target.searchAction?.subtitle.toString()
            message.setOnClickListener {
                defSmsAppInfo?.let { appInfo ->
                    launchApp(appInfo.packageName, number)
                }
            }
            call.setOnClickListener {
                defPhoneAppInfo?.let { appInfo ->
                    launchApp(appInfo.packageName, number)
                }
            }
        }

        if (!isFile) {
            isSmall = true
            setUpdateResources()
        }

        if (isFile) {
            preview.setImageIcon(target.searchAction?.icon)
            title.isSingleLine = false
        }

        if (shouldHandleClick(target)) {
            setOnClickListener {
                target.searchAction?.intent?.let { intent -> handleSearchTargetClick(context, intent) }
            }
        }
    }

    private fun launchApp(packageName: String, phoneNumber: String? = null) {
        val packageManager = context.packageManager
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)

        launchIntent ?: return
        if (packageName == defSmsAppInfo?.packageName) {
            val smsIntent = Intent(Intent.ACTION_VIEW)
            smsIntent.data = Uri.parse("smsto:$phoneNumber")
            smsIntent.putExtra("address", phoneNumber)
            handleSearchTargetClick(context, smsIntent)
        } else if (packageName == defPhoneAppInfo?.packageName && phoneNumber != null) {
            val phoneIntent = Intent(Intent.ACTION_DIAL)
            phoneIntent.data = Uri.parse("tel:$phoneNumber")
            handleSearchTargetClick(context, phoneIntent)
        }
    }
}
