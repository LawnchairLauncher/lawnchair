package ch.deletescape.lawnchair.allapps;

import android.support.v7.widget.RecyclerView;

import java.util.HashSet;
import java.util.List;

public class AllAppsFastScrollHelper implements AllAppsGridAdapter.BindViewCallback {
    private AlphabeticalAppsList mApps;
    String mCurrentFastScrollSection;
    int mFastScrollFrameIndex;
    final int[] mFastScrollFrames = new int[10];
    Runnable mFastScrollToTargetSectionRunnable = new Runnable() {
        public void run() {
            AllAppsFastScrollHelper.this.mCurrentFastScrollSection = AllAppsFastScrollHelper.this.mTargetFastScrollSection;
            AllAppsFastScrollHelper.this.mHasFastScrollTouchSettled = true;
            AllAppsFastScrollHelper.this.mHasFastScrollTouchSettledAtLeastOnce = true;
            AllAppsFastScrollHelper.this.updateTrackedViewsFastScrollFocusState();
        }
    };
    private boolean mHasFastScrollTouchSettled;
    private boolean mHasFastScrollTouchSettledAtLeastOnce;
    private AllAppsRecyclerView mRv;
    Runnable mSmoothSnapNextFrameRunnable = new Runnable() {
        public void run() {
            if (AllAppsFastScrollHelper.this.mFastScrollFrameIndex < AllAppsFastScrollHelper.this.mFastScrollFrames.length) {
                AllAppsFastScrollHelper.this.mRv.scrollBy(0, AllAppsFastScrollHelper.this.mFastScrollFrames[AllAppsFastScrollHelper.this.mFastScrollFrameIndex]);
                AllAppsFastScrollHelper allAppsFastScrollHelper = AllAppsFastScrollHelper.this;
                allAppsFastScrollHelper.mFastScrollFrameIndex++;
                AllAppsFastScrollHelper.this.mRv.postOnAnimation(AllAppsFastScrollHelper.this.mSmoothSnapNextFrameRunnable);
            }
        }
    };
    int mTargetFastScrollPosition = -1;
    String mTargetFastScrollSection;
    private HashSet<RecyclerView.ViewHolder> mTrackedFastScrollViews = new HashSet<>();

    public AllAppsFastScrollHelper(AllAppsRecyclerView allAppsRecyclerView, AlphabeticalAppsList alphabeticalAppsList) {
        this.mRv = allAppsRecyclerView;
        this.mApps = alphabeticalAppsList;
    }

    public void onSetAdapter(AllAppsGridAdapter allAppsGridAdapter) {
        allAppsGridAdapter.setBindViewCallback(this);
    }

    public boolean smoothScrollToSection(int i, int i2, AlphabeticalAppsList.FastScrollSectionInfo fastScrollSectionInfo) {
        if (this.mTargetFastScrollPosition == fastScrollSectionInfo.fastScrollToItem.position) {
            return false;
        }
        this.mTargetFastScrollPosition = fastScrollSectionInfo.fastScrollToItem.position;
        smoothSnapToPosition(i, i2, fastScrollSectionInfo);
        return true;
    }

    private void smoothSnapToPosition(int i, int i2, AlphabeticalAppsList.FastScrollSectionInfo fastScrollSectionInfo) {
        int i3;
        this.mRv.removeCallbacks(this.mSmoothSnapNextFrameRunnable);
        this.mRv.removeCallbacks(this.mFastScrollToTargetSectionRunnable);
        trackAllChildViews();
        if (this.mHasFastScrollTouchSettled) {
            this.mCurrentFastScrollSection = fastScrollSectionInfo.sectionName;
            this.mTargetFastScrollSection = null;
            updateTrackedViewsFastScrollFocusState();
        } else {
            this.mCurrentFastScrollSection = null;
            this.mTargetFastScrollSection = fastScrollSectionInfo.sectionName;
            this.mHasFastScrollTouchSettled = false;
            updateTrackedViewsFastScrollFocusState();
            AllAppsRecyclerView allAppsRecyclerView = this.mRv;
            Runnable runnable = this.mFastScrollToTargetSectionRunnable;
            if (this.mHasFastScrollTouchSettledAtLeastOnce) {
                i3 = 200;
            } else {
                i3 = 100;
            }
            allAppsRecyclerView.postDelayed(runnable, (long) i3);
        }
        List<AlphabeticalAppsList.FastScrollSectionInfo> fastScrollerSections = this.mApps.getFastScrollerSections();
        int i4 = fastScrollSectionInfo.fastScrollToItem.position;
        if (fastScrollerSections.size() <= 0 || fastScrollerSections.get(0) != fastScrollSectionInfo) {
            i3 = Math.min(i2, this.mRv.getCurrentScrollY(i4, 0));
        } else {
            i3 = 0;
        }
        int length = this.mFastScrollFrames.length;
        i3 -= i;
        float signum = Math.signum((float) i3);
        int ceil = (int) (((double) signum) * Math.ceil((double) (((float) Math.abs(i3)) / ((float) length))));
        i4 = i3;
        for (i3 = 0; i3 < length; i3++) {
            this.mFastScrollFrames[i3] = (int) (((float) Math.min(Math.abs(ceil), Math.abs(i4))) * signum);
            i4 -= ceil;
        }
        this.mFastScrollFrameIndex = 0;
        this.mRv.postOnAnimation(this.mSmoothSnapNextFrameRunnable);
    }

    public void onFastScrollCompleted() {
        this.mRv.removeCallbacks(this.mSmoothSnapNextFrameRunnable);
        this.mRv.removeCallbacks(this.mFastScrollToTargetSectionRunnable);
        this.mHasFastScrollTouchSettled = false;
        this.mHasFastScrollTouchSettledAtLeastOnce = false;
        this.mCurrentFastScrollSection = null;
        this.mTargetFastScrollSection = null;
        this.mTargetFastScrollPosition = -1;
        updateTrackedViewsFastScrollFocusState();
        this.mTrackedFastScrollViews.clear();
    }

    public void onBindView(AllAppsGridAdapter.ViewHolder viewHolder) {
        if (this.mCurrentFastScrollSection != null || this.mTargetFastScrollSection != null) {
            this.mTrackedFastScrollViews.add(viewHolder);
        }
    }

    private void trackAllChildViews() {
        int childCount = this.mRv.getChildCount();
        for (int i = 0; i < childCount; i++) {
            RecyclerView.ViewHolder childViewHolder = this.mRv.getChildViewHolder(this.mRv.getChildAt(i));
            if (childViewHolder != null) {
                this.mTrackedFastScrollViews.add(childViewHolder);
            }
        }
    }

    private void updateTrackedViewsFastScrollFocusState() {
        for (RecyclerView.ViewHolder c0091j : this.mTrackedFastScrollViews) {
            boolean z;
            int adapterPosition = c0091j.getAdapterPosition();
            if (this.mCurrentFastScrollSection == null || adapterPosition <= -1 || adapterPosition >= this.mApps.getAdapterItems().size()) {
                z = false;
            } else {
                AlphabeticalAppsList.AdapterItem adapterItem = this.mApps.getAdapterItems().get(adapterPosition);
                z = !(adapterItem == null || !this.mCurrentFastScrollSection.equals(adapterItem.sectionName)) && adapterItem.position == this.mTargetFastScrollPosition;
            }
            c0091j.itemView.setActivated(z);
        }
    }
}