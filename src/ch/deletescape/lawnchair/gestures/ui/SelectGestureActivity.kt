package ch.deletescape.lawnchair.gestures.ui

import android.content.Context
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import ch.deletescape.lawnchair.gestures.GestureController
import ch.deletescape.lawnchair.settings.ui.SettingsBaseActivity
import com.android.launcher3.R
import com.android.launcher3.Utilities

class SelectGestureActivity : SettingsBaseActivity() {

    val key by lazy { intent.getStringExtra("key") }
    val value by lazy { intent.getStringExtra("value") }
    val currentClass by lazy { GestureController.getClassName(value) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.preference_spring_recyclerview)

        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        title = intent.getStringExtra("title")

        val recyclerView = findViewById<RecyclerView>(R.id.list)
        recyclerView.adapter = GestureListAdapter(this)
        recyclerView.layoutManager = LinearLayoutManager(this)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    inner class GestureListAdapter(private val context: Context) : RecyclerView.Adapter<GestureListAdapter.Holder>() {

        val gestures = GestureController.getGestureHandlers(context)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(LayoutInflater.from(context).inflate(R.layout.gesture_item, parent, false))
        }

        override fun getItemCount() = gestures.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.text.text = gestures[position].displayName
            holder.text.isChecked = gestures[position]::class.java.name == currentClass
        }

        inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

            val text = itemView.findViewById<CheckedTextView>(android.R.id.text1)!!.apply { setOnClickListener(this@Holder) }

            override fun onClick(v: View) {
                Utilities.getLawnchairPrefs(context).sharedPrefs.edit()
                        .putString(key, gestures[adapterPosition].toString()).apply()
                finish()
            }
        }
    }
}
