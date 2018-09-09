package com.google.android.apps.nexuslauncher.qsb;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.text.TextUtils;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;

public class b implements ActionMode.Callback {
    private final String Dp;
    private final Intent Dq;
    final /* synthetic */ AbstractQsbLayout Dr;

    public b(AbstractQsbLayout aVar, String str, Intent intent) {
        this.Dr = aVar;
        this.Dp = str;
        this.Dq = intent;
    }

    @SuppressLint("ResourceType")
    public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
        actionMode.setTitle(null);
        actionMode.setSubtitle(null);
        actionMode.setTitleOptionalHint(true);
        actionMode.setTag(Launcher.AUTO_CANCEL_ACTION_MODE);
        if (Dp != null) {
            menu.add(0, 16908322, 0, 17039371).setShowAsAction(1);
        }
        if (Dq != null) {
            menu.add(0, R.id.hotseat_qsb_menu_item, 0, R.string.hotseat_qsb_preferences).setShowAsAction(8);
        }
        return Dp != null || Dq != null;
    }

    public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
        return true;
    }

    public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
        if (menuItem.getItemId() == 16908322 && !TextUtils.isEmpty(this.Dp)) {
            this.Dr.startSearch(this.Dp, 3);
            actionMode.finish();
            return true;
        } else if (menuItem.getItemId() != R.id.hotseat_qsb_menu_item || this.Dq == null) {
            return false;
        } else {
            this.Dr.getContext().sendBroadcast(this.Dq);
            actionMode.finish();
            return true;
        }
    }

    public void onDestroyActionMode(ActionMode actionMode) {
    }
}
