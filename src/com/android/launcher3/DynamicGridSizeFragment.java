package com.android.launcher3;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.app.Dialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.NumberPicker;
import android.widget.TextView;
import com.android.launcher3.settings.SettingsProvider;

public class DynamicGridSizeFragment extends Fragment implements NumberPicker.OnValueChangeListener, Dialog.OnDismissListener{
    public static final String DYNAMIC_GRID_SIZE_FRAGMENT = "dynamicGridSizeFragment";
    public static final int MIN_DYNAMIC_GRID_ROWS = 2;
    public static final int MIN_DYNAMIC_GRID_COLUMNS = 3;
    ImageView mDynamicGridImage;
    ListView mListView;
    View mCurrentSelection;
    GridSizeArrayAdapter mAdapter;
    DeviceProfile.GridSize mCurrentSize;

    Dialog mDialog;

    int mCustomGridRows = 0;
    int mCustomGridColumns = 0;

    View.OnClickListener mSettingsItemListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            mCurrentSize = DeviceProfile.GridSize
                    .getModeForValue((Integer) v.getTag());

            setCleared(mCurrentSelection);
            setSelected(v);
            mCurrentSelection = v;

            if (mCurrentSize == DeviceProfile.GridSize.Custom) {
                showNumberPicker();
            }

            ((GridSizeArrayAdapter) mListView.getAdapter()).notifyDataSetChanged();

            mAdapter.notifyDataSetInvalidated();
            setCurrentImage();
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.dynamic_grid_size_screen, container, false);
        mDynamicGridImage = (ImageView) v.findViewById(R.id.dynamic_grid_size_image);
        mDynamicGridImage.setBackground(getResources().getDrawable(R.drawable.grid));

        LinearLayout titleLayout = (LinearLayout) v.findViewById(R.id.dynamic_grid_title);
        titleLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setSize();
            }
        });

        mCurrentSize = DeviceProfile.GridSize.getModeForValue(
                SettingsProvider.getIntCustomDefault(getActivity(),
                SettingsProvider.SETTINGS_UI_DYNAMIC_GRID_SIZE, 0));

        setCurrentImage();

        mListView = (ListView) v.findViewById(R.id.dynamic_grid_list);
        Resources res = getResources();
        String [] values = {
                res.getString(R.string.grid_size_comfortable),
                res.getString(R.string.grid_size_cozy),
                res.getString(R.string.grid_size_condensed),
                res.getString(R.string.grid_size_custom)};
        mAdapter = new GridSizeArrayAdapter(getActivity(),
                R.layout.settings_pane_list_item, values);
        mListView.setAdapter(mAdapter);

        return v;
    }

    private void setCurrentImage() {
        Drawable d = null;
        boolean custom = false;

        switch (mCurrentSize) {
            case Comfortable:
                d = getResources().getDrawable(R.drawable.grid_comfortable);
                break;
            case Cozy:
                d = getResources().getDrawable(R.drawable.grid_cozy);
                break;
            case Condensed:
                d = getResources().getDrawable(R.drawable.grid_condensed);
                break;
            default:

                custom = true;
                break;
        }

        if (d != null && !custom) {
            mDynamicGridImage.setImageBitmap(null);
            mDynamicGridImage.setBackground(d);
        } else if (custom) {
            mDynamicGridImage.setBackground(null);
            mDynamicGridImage.setImageBitmap(writeOnDrawable(R.drawable.grid));
        }
    }

    public Bitmap writeOnDrawable(int drawableId){
        LauncherAppState app = LauncherAppState.getInstance();
        DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();

        int rows = mCustomGridRows == 0 ? (int) grid.numRows : mCustomGridRows;
        int columns = mCustomGridColumns == 0 ? (int) grid.numColumns : mCustomGridColumns;

        String text = rows + " " + "\u00d7" + " " + columns;

        Bitmap bm = BitmapFactory.decodeResource(getResources(),
                drawableId).copy(Bitmap.Config.ARGB_8888, true);

        Paint paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.BLACK);
        int px = getResources().getDimensionPixelOffset(R.dimen.grid_custom_text);
        paint.setTextSize(px);

        Canvas canvas = new Canvas(bm);

        float canvasWidth = canvas.getWidth();
        float sentenceWidth = paint.measureText(text);
        float startPositionX = (canvasWidth - sentenceWidth) / 2;

        canvas.drawText(text, startPositionX, bm.getHeight()/2, paint);

        return bm;
    }

    @Override
    public Animator onCreateAnimator(int transit, boolean enter, int nextAnim) {
        if (enter) {
            DisplayMetrics displaymetrics = new DisplayMetrics();
            getActivity().getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
            int width = displaymetrics.widthPixels;
            final ObjectAnimator anim = ObjectAnimator.ofFloat(this, "translationX", width, 0);

            final View darkPanel = ((Launcher) getActivity()).getDarkPanel();
            darkPanel.setVisibility(View.VISIBLE);
            ObjectAnimator anim2 = ObjectAnimator.ofFloat(
                    darkPanel , "alpha", 0.0f, 0.3f);
            anim2.start();

            anim.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator arg0) {}
                @Override
                public void onAnimationRepeat(Animator arg0) {}
                @Override
                public void onAnimationEnd(Animator arg0) {
                    darkPanel.setVisibility(View.GONE);
                }
                @Override
                public void onAnimationCancel(Animator arg0) {}
            });

            return anim;
        } else {
            return super.onCreateAnimator(transit, enter, nextAnim);
        }
    }

    public void setSize() {
        ((Launcher) getActivity()).setDynamicGridSize(mCurrentSize);
    }

    private void setSelected(View v) {
        v.setBackgroundColor(Color.WHITE);
        TextView t = (TextView) v.findViewById(R.id.item_name);
        t.setTextColor(getResources().getColor(R.color.settings_bg_color));
    }

    private void setCleared(View v) {
        v.setBackgroundColor(getResources().getColor(R.color.settings_bg_color));
        TextView t = (TextView) v.findViewById(R.id.item_name);
        t.setTextColor(Color.WHITE);
    }

    private void showNumberPicker() {
        mDialog = new Dialog(getActivity());
        mDialog.setTitle(getResources().getString(R.string.preferences_interface_homescreen_custom));
        mDialog.setContentView(R.layout.custom_grid_size_dialog);

        NumberPicker nPRows= (NumberPicker) mDialog.findViewById(R.id.custom_rows);
        NumberPicker nPColumns = (NumberPicker) mDialog.findViewById(R.id.custom_columns);

        LauncherAppState app = LauncherAppState.getInstance();
        DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();

        int rows = grid.numRowsBase;
        int columns = grid.numColumnsBase;
        if (mCustomGridColumns == 0) {
            mCustomGridColumns = (int) grid.numColumns;
        }
        if (mCustomGridRows == 0) {
            mCustomGridRows = (int) grid.numRows;
        }

        nPRows.setMinValue(Math.max(MIN_DYNAMIC_GRID_ROWS, rows - DeviceProfile.GRID_SIZE_MIN));
        nPRows.setMaxValue(rows + DeviceProfile.GRID_SIZE_MAX);
        nPRows.setValue(mCustomGridRows);
        nPRows.setWrapSelectorWheel(false);
        nPRows.setOnValueChangedListener(this);
        nPRows.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);

        nPColumns.setMinValue(Math.max(MIN_DYNAMIC_GRID_COLUMNS, columns - DeviceProfile.GRID_SIZE_MIN));
        nPColumns.setMaxValue(columns + DeviceProfile.GRID_SIZE_MAX);
        nPColumns.setValue(mCustomGridColumns);
        nPColumns.setWrapSelectorWheel(false);
        nPColumns.setOnValueChangedListener(this);
        nPColumns.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);

        Button b = (Button) mDialog.findViewById(R.id.dialog_confirm_button);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mDialog != null) {
                    mDialog.dismiss();
                }
            }
        });
        mDialog.setOnDismissListener(this);
        mDialog.show();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mDialog != null) {
            mDialog.dismiss();
        }
    }

    @Override
    public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
        if (picker.getId() == R.id.custom_rows) {
            mCustomGridRows = newVal;
        } else if (picker.getId() == R.id.custom_columns) {
            mCustomGridColumns = newVal;
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        SettingsProvider.putInt(getActivity(),
                SettingsProvider.SETTINGS_UI_HOMESCREEN_ROWS, mCustomGridRows);
        SettingsProvider.putInt(getActivity(),
                SettingsProvider.SETTINGS_UI_HOMESCREEN_COLUMNS, mCustomGridColumns);

        mAdapter.notifyDataSetInvalidated();

        setCurrentImage();
    }

    private class GridSizeArrayAdapter extends ArrayAdapter<String> {
        Context mContext;
        String[] mTitles;

        public GridSizeArrayAdapter(Context context, int textViewResourceId,
                                       String[] objects) {
            super(context, textViewResourceId, objects);

            mContext = context;
            mTitles = objects;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater) mContext
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.settings_pane_list_item,
                    parent, false);
            TextView textView = (TextView) convertView
                    .findViewById(R.id.item_name);
            textView.setText(mTitles[position]);
            // Set Selected State
            if (position == mCurrentSize.getValue()) {
                mCurrentSelection = convertView;
                setSelected(mCurrentSelection);
            }

            if (position == DeviceProfile.GridSize.Custom.getValue()) {
                LauncherAppState app = LauncherAppState.getInstance();
                DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();

                String state = mTitles[position];
                int rows = SettingsProvider.getIntCustomDefault(getActivity(),
                        SettingsProvider.SETTINGS_UI_HOMESCREEN_ROWS, grid.numRowsBase);
                int columns = SettingsProvider.getIntCustomDefault(getActivity(),
                        SettingsProvider.SETTINGS_UI_HOMESCREEN_COLUMNS, grid.numColumnsBase);
                state += " " + "(" + rows + " " + "\u00d7" + " " + columns + ")";

                textView.setText(state);
            }

            convertView.setOnClickListener(mSettingsItemListener);
            convertView.setTag(position);
            return convertView;
        }
    }
}
