package app.lawnchair.allapps.views

import android.content.Context
import android.provider.SearchRecentSuggestions
import android.util.AttributeSet
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import app.lawnchair.font.FontManager
import app.lawnchair.launcher
import app.lawnchair.search.adapter.HEADER_JUSTIFY
import app.lawnchair.search.LawnchairRecentSuggestionProvider
import app.lawnchair.search.adapter.SPACE
import app.lawnchair.search.adapter.SPACE_MINI
import app.lawnchair.search.adapter.SearchTargetCompat
import app.lawnchair.search.adapter.SearchResultActionCallBack
import app.lawnchair.theme.color.ColorTokens
import com.android.launcher3.R

class SearchResultText(context: Context, attrs: AttributeSet?) :
    LinearLayout(context, attrs), SearchResultView {

    private val launcher = context.launcher
    private lateinit var title: TextView
    private lateinit var clearHistory: ImageButton

    override fun onFinishInflate() {
        super.onFinishInflate()
        onFocusChangeListener = launcher.focusHandler
        title = ViewCompat.requireViewById(this, R.id.title)
        title.setTextColor(ColorTokens.ColorAccent.resolveColor(context))
        clearHistory = ViewCompat.requireViewById(this, R.id.clear_history)
        clearHistory.setColorFilter(ColorTokens.ColorAccent.resolveColor(context))
        clearHistory.visibility = GONE
        FontManager.INSTANCE.get(context).setCustomFont(title, R.id.font_heading)
    }

    override val isQuickLaunch: Boolean get() = false
    override val titleText: CharSequence? get() = title.text
    override fun launch(): Boolean = false

    override fun bind(target: SearchTargetCompat, shortcuts: List<SearchTargetCompat>, callBack: SearchResultActionCallBack?) {
        title.text = target.searchAction?.title
        val res = when (title.text) {
            SPACE -> resources.getDimensionPixelSize(R.dimen.space_layout_height)
            SPACE_MINI -> resources.getDimensionPixelSize(R.dimen.space_layout_mini_height)
            else -> resources.getDimensionPixelSize(R.dimen.search_result_text_height)
        }
        clearHistory.visibility = if (target.packageName == HEADER_JUSTIFY) VISIBLE else INVISIBLE
        if (target.packageName == HEADER_JUSTIFY) {
            clearHistory.setOnClickListener {
                val suggestionsRecent = SearchRecentSuggestions(
                    launcher,
                    LawnchairRecentSuggestionProvider.AUTHORITY,
                    LawnchairRecentSuggestionProvider.MODE
                )
                suggestionsRecent.clearHistory()
                callBack?.action()
            }
        }
        val layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, res)
        this.layoutParams = layoutParams
    }
}
