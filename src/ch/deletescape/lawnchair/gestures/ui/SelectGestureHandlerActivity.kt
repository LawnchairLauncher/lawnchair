package ch.deletescape.lawnchair.gestures.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import ch.deletescape.lawnchair.gestures.GestureController
import ch.deletescape.lawnchair.gestures.GestureHandler
import ch.deletescape.lawnchair.settings.ui.SettingsBaseActivity
import com.android.launcher3.R
import com.android.launcher3.Utilities

class SelectGestureHandlerActivity : SettingsBaseActivity() {

    val key by lazy { intent.getStringExtra("key") }
    val value by lazy { intent.getStringExtra("value") }
    val currentClass by lazy { GestureController.getClassName(value) }

    var selectedHandler: GestureHandler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.preference_spring_recyclerview)

        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        title = intent.getStringExtra("title")

        val recyclerView = findViewById<RecyclerView>(R.id.list)
        recyclerView.adapter = HandlerListAdapter(this)
        recyclerView.layoutManager = LinearLayoutManager(this)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    fun onSelectHandler(handler: GestureHandler) {
        selectedHandler = handler
        if (handler.configIntent != null) {
            startActivityForResult(handler.configIntent, 0)
        } else {
            saveChanges()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 0 && resultCode == Activity.RESULT_OK) {
            selectedHandler?.onConfigResult(data)
            saveChanges()
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun saveChanges() {
        Utilities.getLawnchairPrefs(this).sharedPrefs.edit().putString(key, selectedHandler.toString()).apply()
        finish()
    }

    inner class HandlerListAdapter(private val context: Context) : RecyclerView.Adapter<HandlerListAdapter.Holder>() {

        val handlers = GestureController.getGestureHandlers(context)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(LayoutInflater.from(context).inflate(R.layout.gesture_item, parent, false))
        }

        override fun getItemCount() = handlers.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.text.text = handlers[position].displayName
            holder.text.isChecked = handlers[position]::class.java.name == currentClass
        }

        inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

            val text = itemView.findViewById<CheckedTextView>(android.R.id.text1)!!.apply { setOnClickListener(this@Holder) }

            override fun onClick(v: View) {
                onSelectHandler(handlers[adapterPosition])
            }
        }
    }
}
