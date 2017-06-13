package ch.deletescape.lawnchair.preferences;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import ch.deletescape.lawnchair.R;

public class SeekbarPreference extends Preference implements SeekBar.OnSeekBarChangeListener {

    private SeekBar mSeekbar;
    private TextView mValueText;
    private float min;
    private float max;
    private float current;
    private float defaultValue;
    private int multiplier;
    private String format;

    public SeekbarPreference(Context context) {
        this(context, null);
    }

    public SeekbarPreference(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SeekbarPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setLayoutResource(R.layout.preference_seekbar);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.SeekbarPreference);
        min = ta.getFloat(R.styleable.SeekbarPreference_minValue, 0);
        max = ta.getFloat(R.styleable.SeekbarPreference_maxValue, 100);
        multiplier = ta.getInt(R.styleable.SeekbarPreference_summaryMultiplier, 1);
        format = ta.getString(R.styleable.SeekbarPreference_summaryFormat);
        defaultValue = ta.getFloat(R.styleable.SeekbarPreference_defaultValue, min);
        if (format == null) {
            format = "%.2f";
        }
        ta.recycle();
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        mSeekbar = (SeekBar) view.findViewById(R.id.seekbar);
        mValueText = (TextView) view.findViewById(R.id.txtValue);
        mSeekbar.setOnSeekBarChangeListener(this);

        current = getPersistedFloat(defaultValue);
        int progress = (int) ((current - min) / ((max - min) / 100));
        mSeekbar.setProgress(progress);
        updateSummary();
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        return super.onCreateView(parent);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        current = min + (((max - min) / 100) * progress);
        updateSummary();

        persistFloat(current);

    }

    private void updateSummary() {
        mValueText.setText(String.format(format, current * multiplier));
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    }
}
