package ch.deletescape.lawnchair.shortcuts;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.content.Context;
import android.graphics.Point;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewParent;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ch.deletescape.lawnchair.AbstractFloatingView;
import ch.deletescape.lawnchair.BubbleTextView;
import ch.deletescape.lawnchair.ItemInfo;
import ch.deletescape.lawnchair.Launcher;
import ch.deletescape.lawnchair.LauncherAnimUtils;
import ch.deletescape.lawnchair.LauncherAppState;
import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.anim.PropertyListBuilder;
import ch.deletescape.lawnchair.dragndrop.DragOptions;
import ch.deletescape.lawnchair.dragndrop.DragView;
import ch.deletescape.lawnchair.popup.PopupContainerWithArrow;
import ch.deletescape.lawnchair.popup.PopupItemView;
import ch.deletescape.lawnchair.popup.PopupPopulator;
import ch.deletescape.lawnchair.popup.SystemShortcut;

public class ShortcutsItemView extends PopupItemView implements OnLongClickListener, OnTouchListener {
    private final List<DeepShortcutView> mDeepShortcutViews;
    private final Point mIconLastTouchPos;
    private final Point mIconShift;
    private Launcher mLauncher;
    private LinearLayout mShortcutsLayout;
    private LinearLayout mSystemShortcutIcons;
    private final List<View> mSystemShortcutViews;

    public ShortcutsItemView(Context context) {
        this(context, null, 0);
    }

    public ShortcutsItemView(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public ShortcutsItemView(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
        mIconShift = new Point();
        mIconLastTouchPos = new Point();
        mDeepShortcutViews = new ArrayList<>();
        mSystemShortcutViews = new ArrayList<>();
        mLauncher = Launcher.getLauncher(context);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mShortcutsLayout = findViewById(R.id.deep_shortcuts);
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        switch (motionEvent.getAction()) {
            case 0:
            case 2:
                mIconLastTouchPos.set((int) motionEvent.getX(), (int) motionEvent.getY());
                break;
        }
        return false;
    }

    @Override
    public boolean onLongClick(View v) {
        boolean r0 = v.isInTouchMode();
        if (!r0) {
            return false;
        }
        ViewParent parent = v.getParent();
        r0 = parent instanceof DeepShortcutView;
        r0 = !r0;
        if (r0) {
            return false;
        } else {
            r0 = mLauncher.isDraggingEnabled();
            if (r0) {
                r0 = mLauncher.getDragController().isDragging();
                if (!r0) {
                    DeepShortcutView r5 = (DeepShortcutView) v.getParent();
                    r5.setWillDrawIcon(false);
                    mIconShift.x = mIconLastTouchPos.x - r5.getIconCenter().x;
                    mIconShift.y = mIconLastTouchPos.y - mLauncher.getDeviceProfile().iconSizePx;
                    PopupContainerWithArrow r2 = (PopupContainerWithArrow) getParent();
                    DragView dv = mLauncher.getWorkspace().beginDragShared(r5.getIconView(), r2, r5.getFinalInfo(), new ShortcutDragPreviewProvider(r5.getIconView(), mIconShift), new DragOptions());
                    dv.animateShift(-mIconShift.x, -mIconShift.y);
                    AbstractFloatingView.closeOpenContainer(mLauncher, 1);
                    LauncherAppState.getInstance().getLauncher().closeFolder();
                    return false;
                }
            } else {
                return false;
            }
        }
        return false;
    }

    public void addShortcutView(View view, PopupPopulator.Item item) {
        addShortcutView(view, item, -1);
    }

    private void addShortcutView(View view, PopupPopulator.Item item, int i) {
        if (item == PopupPopulator.Item.SHORTCUT) {
            mDeepShortcutViews.add((DeepShortcutView) view);
        } else {
            mSystemShortcutViews.add(view);
        }
        if (item == PopupPopulator.Item.SYSTEM_SHORTCUT_ICON) {
            if (mSystemShortcutIcons == null) {
                mSystemShortcutIcons = (LinearLayout) mLauncher.getLayoutInflater().inflate(R.layout.system_shortcut_icons, mShortcutsLayout, false);
                mShortcutsLayout.addView(mSystemShortcutIcons, 0);
            }
            mSystemShortcutIcons.addView(view, i);
            return;
        }
        if (mShortcutsLayout.getChildCount() > 0) {
            View childAt = mShortcutsLayout.getChildAt(mShortcutsLayout.getChildCount() - 1);
            if (childAt instanceof DeepShortcutView) {
                childAt.findViewById(R.id.divider).setVisibility(View.VISIBLE);
            }
        }
        mShortcutsLayout.addView(view, i);
    }

    public List<DeepShortcutView> getDeepShortcutViews(boolean z) {
        if (z) {
            Collections.reverse(mDeepShortcutViews);
        }
        return mDeepShortcutViews;
    }

    public List<View> getSystemShortcutViews(boolean z) {
        if (z || mSystemShortcutIcons != null) {
            Collections.reverse(mSystemShortcutViews);
        }
        return mSystemShortcutViews;
    }

    public void enableWidgetsIfExist(BubbleTextView bubbleTextView) {
        PopupPopulator.Item item;
        ItemInfo itemInfo = (ItemInfo) bubbleTextView.getTag();
        SystemShortcut widgets = new SystemShortcut.Widgets();
        OnClickListener onClickListener = widgets.getOnClickListener(mLauncher, itemInfo);
        View view2 = null;
        for (View view : mSystemShortcutViews) {
            if (view.getTag() instanceof SystemShortcut.Widgets) {
                view2 = view;
                break;
            }
        }
        if (mSystemShortcutIcons == null) {
            item = PopupPopulator.Item.SYSTEM_SHORTCUT;
        } else {
            item = PopupPopulator.Item.SYSTEM_SHORTCUT_ICON;
        }
        if (onClickListener != null && view2 == null) {
            view2 = mLauncher.getLayoutInflater().inflate(item.layoutId, this, false);
            PopupPopulator.initializeSystemShortcut(getContext(), view2, widgets);
            view2.setOnClickListener(onClickListener);
            if (item == PopupPopulator.Item.SYSTEM_SHORTCUT_ICON) {
                addShortcutView(view2, item, 0);
                return;
            }
            ((PopupContainerWithArrow) getParent()).close(false);
            PopupContainerWithArrow.showForIcon(bubbleTextView);
        } else if (onClickListener == null && view2 != null) {
            if (item == PopupPopulator.Item.SYSTEM_SHORTCUT_ICON) {
                mSystemShortcutViews.remove(view2);
                mSystemShortcutIcons.removeView(view2);
                return;
            }
            ((PopupContainerWithArrow) getParent()).close(false);
            PopupContainerWithArrow.showForIcon(bubbleTextView);
        }
    }

    @Override
    public Animator createOpenAnimation(boolean z, boolean z2) {
        AnimatorSet createAnimatorSet = LauncherAnimUtils.createAnimatorSet();
        createAnimatorSet.play(super.createOpenAnimation(z, z2));
        for (int i = 0; i < mShortcutsLayout.getChildCount(); i++) {
            if (mShortcutsLayout.getChildAt(i) instanceof DeepShortcutView) {
                View iconView = ((DeepShortcutView) mShortcutsLayout.getChildAt(i)).getIconView();
                iconView.setScaleX(0.0f);
                iconView.setScaleY(0.0f);
                createAnimatorSet.play(LauncherAnimUtils.ofPropertyValuesHolder(iconView, new PropertyListBuilder().scale(1.0f).build()));
            }
        }
        return createAnimatorSet;
    }

    @Override
    public Animator createCloseAnimation(boolean z, boolean z2, long j) {
        AnimatorSet createAnimatorSet = LauncherAnimUtils.createAnimatorSet();
        createAnimatorSet.play(super.createCloseAnimation(z, z2, j));
        for (int i = 0; i < mShortcutsLayout.getChildCount(); i++) {
            if (mShortcutsLayout.getChildAt(i) instanceof DeepShortcutView) {
                View iconView = ((DeepShortcutView) mShortcutsLayout.getChildAt(i)).getIconView();
                iconView.setScaleX(1.0f);
                iconView.setScaleY(1.0f);
                createAnimatorSet.play(LauncherAnimUtils.ofPropertyValuesHolder(iconView, new PropertyListBuilder().scale(0.0f).build()));
            }
        }
        return createAnimatorSet;
    }

    @Override
    public int getArrowColor(boolean z) {
        Context context = getContext();
        if (z || mDeepShortcutViews.isEmpty()) {
            return Utilities.resolveAttributeData(context, R.attr.appPopupBgColor);
        } else {
            return Utilities.resolveAttributeData(context, R.attr.appPopupHeaderBgColor);
        }
    }
}