package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.ui.adapter.SeriesAdapter;
import com.github.tvbox.osc.ui.widget.GridSpacingItemDecoration;
import com.github.tvbox.osc.util.Utils;
import com.lxj.xpopup.core.BottomPopupView;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量下载弹窗。
 * - 打开即开始嗅探全部集，列表项默认不可点击；
 * - 嗅探成功的项变为高亮可点击，点击后通过 OnDownloadItemClickListener 回调，由外部调起 1DM+ 下载；
 * - 提供"重新嗅探"按钮，外部回调后清空缓存并对当前列表重新嗅探一轮；
 * - 弹窗关闭回调由外部用于销毁 WebView 池释放内存。
 */
public class BatchDownloadDialog extends BottomPopupView {

    private final List<VodInfo.VodSeries> mList;
    private final OnDownloadItemClickListener mDownloadListener;
    private final OnResniffListener mResniffListener;
    private final OnDialogDismissListener mDismissListener;
    private SeriesAdapter mSeriesAdapter;
    private TextView mTvStatus;
    private ProgressBar mPb;
    private int mTotal;

    public interface OnDownloadItemClickListener {
        void onDownloadItemClick(int position, VodInfo.VodSeries item);
    }

    public interface OnResniffListener {
        /**
         * 用户点击"重新嗅探"按钮，由外部清空缓存并对当前列表重新发起嗅探
         */
        void onResniff();
    }

    public interface OnDialogDismissListener {
        /**
         * 弹窗被关闭（用户点关闭或外部 dismiss），由外部清理 WebView 等资源
         */
        void onDialogDismiss();
    }

    public BatchDownloadDialog(@NonNull @NotNull Context context,
                              List<VodInfo.VodSeries> list,
                              OnDownloadItemClickListener downloadListener,
                              OnResniffListener resniffListener,
                              OnDialogDismissListener dismissListener) {
        super(context);
        mList = new ArrayList<>();
        for (VodInfo.VodSeries vs : list) {
            VodInfo.VodSeries copy = new VodInfo.VodSeries(vs.name, vs.url);
            copy.selected = false;
            mList.add(copy);
        }
        mDownloadListener = downloadListener;
        mResniffListener = resniffListener;
        mDismissListener = dismissListener;
        mTotal = mList.size();
    }

    @Override
    protected void onDismiss() {
        super.onDismiss();
        if (mDismissListener != null) {
            mDismissListener.onDialogDismiss();
        }
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.dialog_batch_download;
    }

    @Override
    protected int getMaxHeight() {
        return (int) (com.blankj.utilcode.util.ScreenUtils.getScreenHeight() * 0.75);
    }

    @Override
    protected void onCreate() {
        super.onCreate();

        RecyclerView rv = findViewById(R.id.rv);
        rv.setLayoutManager(new GridLayoutManager(getContext(), Utils.getSeriesSpanCount(mList)));
        rv.addItemDecoration(new GridSpacingItemDecoration(Utils.getSeriesSpanCount(mList), 20, true));

        mSeriesAdapter = new SeriesAdapter(true);
        mSeriesAdapter.setDownloadMode(true);
        mSeriesAdapter.setNewData(mList);
        rv.setAdapter(mSeriesAdapter);

        mTvStatus = findViewById(R.id.tvSniffStatus);
        mPb = findViewById(R.id.pbSniff);
        if (mPb != null) {
            mPb.setMax(mTotal > 0 ? mTotal : 1);
        }

        // 点击事件：仅当 selected=true 时回调（未成功的项 SeriesAdapter 中已禁用点击）
        mSeriesAdapter.setOnItemClickListener((adapter, view, position) -> {
            VodInfo.VodSeries item = mList.get(position);
            if (!item.selected) return;
            if (mDownloadListener != null) {
                mDownloadListener.onDownloadItemClick(position, item);
            }
        });

        // 重新嗅探
        View tvResniff = findViewById(R.id.tvResniff);
        if (tvResniff != null) {
            tvResniff.setOnClickListener(v -> {
                if (mResniffListener != null) {
                    mResniffListener.onResniff();
                }
            });
        }

        findViewById(R.id.tvClose).setOnClickListener(v -> dismiss());
    }

    /**
     * 标记某一项嗅探成功，更新为可点击高亮。
     */
    @UiThread
    public void markItemSuccess(int position) {
        if (position < 0 || position >= mList.size()) return;
        mList.get(position).selected = true;
        if (mSeriesAdapter != null) {
            mSeriesAdapter.notifyItemChanged(position);
        }
    }

    /**
     * 标记某一项嗅探失败（保持不可点击）。
     */
    @UiThread
    public void markItemFailed(int position) {
        if (position < 0 || position >= mList.size()) return;
        mList.get(position).selected = false;
        if (mSeriesAdapter != null) {
            mSeriesAdapter.notifyItemChanged(position);
        }
    }

    /**
     * 重置所有项为未嗅探状态（不可点击、不高亮），用于重新嗅探前的清理。
     */
    @UiThread
    public void resetAll() {
        for (int i = 0; i < mList.size(); i++) {
            mList.get(i).selected = false;
        }
        if (mSeriesAdapter != null) {
            mSeriesAdapter.notifyDataSetChanged();
        }
        if (mPb != null) {
            mPb.setMax(mTotal > 0 ? mTotal : 1);
            mPb.setProgress(0);
        }
        if (mTvStatus != null) {
            mTvStatus.setText("0/" + mTotal + "  正在重新嗅探…");
        }
    }

    /**
     * 更新嗅探进度文案与进度条。
     */
    @UiThread
    public void updateProgress(int done, int total, String detail) {
        if (mTvStatus != null) {
            mTvStatus.setText(done + "/" + total + (detail == null || detail.isEmpty() ? "" : "  " + detail));
        }
        if (mPb != null) {
            mPb.setMax(total > 0 ? total : 1);
            mPb.setProgress(done);
        }
    }
}
