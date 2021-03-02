package ch.deletescape.lawnchair.settings.views

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import ch.deletescape.lawnchair.extensions.getThemeDrawable
import com.android.launcher3.R

class IconPackListItem(context: Context) : ConstraintLayout(context), Checkable {

    private val radioButtonView: RadioButton
    private val iconPackNameView: TextView
    private val iconPackIconView: ImageView

    init {
        LayoutInflater.from(context).inflate(R.layout.icon_pack_list_item, this)
        radioButtonView = findViewById(R.id.radio_button)
        iconPackNameView = findViewById(R.id.icon_pack_name)
        iconPackIconView = findViewById(R.id.icon_pack_icon)
        background = context.getThemeDrawable(R.attr.selectableItemBackground)
    }

    var iconPackPackageName: String = ""

    var iconPackName: String? = null
        set(value) {
            field = value
            iconPackNameView.text = value
        }

    var iconPackIcon: Drawable? = null
        set(value) {
            field = value
            iconPackIconView.setImageDrawable(value)
        }

    override fun setChecked(checked: Boolean) {
        radioButtonView.isChecked = checked
    }

    override fun isChecked(): Boolean = radioButtonView.isChecked

    override fun toggle() {
        isChecked = !isChecked
    }

}