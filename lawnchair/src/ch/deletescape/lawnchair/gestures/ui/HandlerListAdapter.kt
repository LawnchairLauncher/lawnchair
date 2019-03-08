/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.deletescape.lawnchair.gestures.ui

import android.content.Context
import android.content.res.ColorStateList
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import ch.deletescape.lawnchair.applyAccent
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.gestures.GestureController
import ch.deletescape.lawnchair.gestures.GestureHandler
import com.android.launcher3.R

class HandlerListAdapter(private val context: Context, isSwipeUp: Boolean, private val currentClass: String, private val onSelectHandler: (handler: GestureHandler) -> Unit, showBlank: Boolean = true) : RecyclerView.Adapter<HandlerListAdapter.Holder>() {

    val handlers = GestureController.getGestureHandlers(context, isSwipeUp, showBlank)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(LayoutInflater.from(context).inflate(R.layout.gesture_item, parent, false))
    }

    override fun getItemCount() = handlers.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.text.text = handlers[position].displayName
        holder.text.isChecked = handlers[position]::class.java.name == currentClass
    }

    inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

        val text = itemView.findViewById<CheckedTextView>(android.R.id.text1)!!.apply {
            setOnClickListener(this@Holder)
            applyAccent()
        }

        override fun onClick(v: View) {
            onSelectHandler(handlers[adapterPosition])
        }
    }
}