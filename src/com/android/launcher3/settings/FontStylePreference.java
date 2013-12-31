package com.android.launcher3.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.preference.ListPreference;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.android.launcher3.R;

import java.util.Arrays;
import java.util.List;

public class FontStylePreference extends ListPreference {
    private ListView mListView;
    private String mValue;
    public int mClickedDialogEntryIndex;
    private boolean mValueSet;

    public FontStylePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        return;
    }

    @Override
    public View onCreateDialogView() {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.preference_font_style, null);
        mListView = (ListView) view.findViewById(android.R.id.list);
        mListView.setAdapter(new FontStyleAdapter(getContext()));
        mListView.setOnItemClickListener(mOnClickListener);
        mListView.setItemChecked(getValueIndex(), true);
        return view;
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult && mClickedDialogEntryIndex >= 0 && getEntryValues() != null) {
            String value = getEntryValues()[mClickedDialogEntryIndex].toString();
            if (callChangeListener(value)) {
                setValue(value);
            }
        }
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        setValue(restoreValue ? getPersistedString(mValue) : (String) defaultValue);
    }

    /**
     * Sets the value of the key. This should be one of the entries in
     * {@link #getEntryValues()}.
     *
     * @param value The value to set for the key.
     */
    @Override
    public void setValue(String value) {
        // Always persist/notify the first time.
        final boolean changed = !TextUtils.equals(mValue, value);
        if (changed || !mValueSet) {
            mValue = value;
            mValueSet = true;
            persistString(value);
            if (changed) {
                notifyChanged();
            }
        }
    }

    /**
     * Returns the value of the key. This should be one of the entries in
     * {@link #getEntryValues()}.
     *
     * @return The value of the key.
     */
    @Override
    public String getValue() {
        return mValue;
    }

    private int getValueIndex() {
        return findIndexOfValue(mValue);
    }

    private AdapterView.OnItemClickListener mOnClickListener = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
            mClickedDialogEntryIndex = position;
        }
    };

    private class FontStyleAdapter extends ArrayAdapter<CharSequence> {
        private LayoutInflater mInflater;
        private List<CharSequence> mEntries;
        private List<CharSequence> mValues;

        public FontStyleAdapter(Context context) {
            super(context, -1, getEntryValues());

            mInflater = LayoutInflater.from(context);
            mEntries = Arrays.asList(getEntries());
            mValues = Arrays.asList(getEntryValues());
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            CheckedTextView textView;

            if (convertView == null) {
                textView = (CheckedTextView) mInflater.inflate(R.layout.list_item_checkable, parent, false);
            } else {
                textView = (CheckedTextView) convertView;
            }

            if (textView != null) {
                textView.setText(mEntries.get(position));
                textView.setTag(mValues.get(position));
                textView.setTypeface(Typeface.create((String) mValues.get(position), Typeface.NORMAL));
            }

            return textView;
        }
    }
}
