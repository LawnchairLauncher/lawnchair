package ch.deletescape.lawnchair.settings.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.support.design.widget.Snackbar
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.android.launcher3.BuildConfig
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.compat.LauncherAppsCompat

class AboutActivity : SettingsBaseActivity(), View.OnClickListener {

    private var tapCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        supportActionBar?.elevation = 0f
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<View>(R.id.lawnchairCard).apply {
            setOnClickListener {
                context.startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/deletescape-media/lawnchair")))
            }
            setOnLongClickListener {
                val componentName = ComponentName(context, Launcher::class.java)
                LauncherAppsCompat.getInstance(context)
                        .showAppDetailsForProfile(componentName, Process.myUserHandle())
                true
            }
        }
        findViewById<TextView>(R.id.versionText).text = BuildConfig.VERSION_NAME
        findViewById<RecyclerView>(R.id.recyclerView).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = ContributorAdapter(context)
        }
        findViewById<View>(R.id.contributors_card).setOnClickListener(this)

        tapCount = if (Utilities.getLawnchairPrefs(this).developerOptionsEnabled) 7 else 0
    }

    override fun onClick(v: View) {
        if (tapCount == 6 && tapCount < 7) {
            Utilities.getLawnchairPrefs(this).developerOptionsEnabled = true
            Snackbar.make(
                    findViewById(R.id.content),
                    R.string.developer_options_enabled,
                    Snackbar.LENGTH_LONG).show()
            tapCount++
        } else {
            tapCount++
        }
    }

    class ContributorAdapter(val context: Context) : RecyclerView.Adapter<ContributorAdapter.Holder>() {

        val contributors = arrayOf("paphonb", "fonix232", "divadsn")

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(LayoutInflater.from(parent.context)
                    .inflate(R.layout.contributor_item, parent, false))
        }

        override fun getItemCount() = contributors.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(contributors[position])
        }

        inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

            init {
                itemView.setOnClickListener(this)
            }

            fun bind(contributor: String) {
                (itemView as TextView).text = contributor
            }

            override fun onClick(v: View?) {
                context.startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/${contributors[adapterPosition]}")))
            }
        }
    }
}
