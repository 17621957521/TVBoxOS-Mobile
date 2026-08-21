package com.github.tvbox.osc.ui.activity;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Rational;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.blankj.utilcode.util.AppUtils;
import com.blankj.utilcode.util.LogUtils;
import com.blankj.utilcode.util.NotificationUtils;
import com.blankj.utilcode.util.ScreenUtils;
import com.blankj.utilcode.util.ServiceUtils;
import com.blankj.utilcode.util.ToastUtils;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.catvod.crawler.Spider;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.base.BaseVbActivity;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.ParseBean;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.constant.IntentKey;
import com.github.tvbox.osc.databinding.ActivityDetailBinding;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.receiver.BatteryReceiver;
import com.github.tvbox.osc.service.PlayService;
import com.github.tvbox.osc.ui.adapter.ParseAdapter;
import com.github.tvbox.osc.ui.adapter.SeriesAdapter;
import com.github.tvbox.osc.ui.adapter.SeriesFlagAdapter;
import com.github.tvbox.osc.ui.dialog.AllVodSeriesBottomDialog;
import com.github.tvbox.osc.ui.dialog.AllVodSeriesRightDialog;
import com.github.tvbox.osc.ui.dialog.BatchDownloadDialog;
import com.github.tvbox.osc.ui.dialog.QuickSearchDialog;
import com.github.tvbox.osc.ui.dialog.VideoDetailDialog;
import com.github.tvbox.osc.ui.fragment.PlayFragment;
import com.github.tvbox.osc.ui.widget.LinearSpacingItemDecoration;
import com.github.tvbox.osc.util.AdBlocker;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.ScreenShotListenManager;
import com.github.tvbox.osc.util.SearchHelper;
import com.github.tvbox.osc.util.SubtitleHelper;
import com.github.tvbox.osc.util.Utils;
import com.github.tvbox.osc.util.VideoParseRuler;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.gyf.immersionbar.ImmersionBar;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.core.BasePopupView;
import com.lxj.xpopup.enums.PopupPosition;
import com.lxj.xpopup.interfaces.OnSelectListener;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author pj567
 * @date :2020/12/22
 * @description:
 */

public class DetailActivity extends BaseVbActivity<ActivityDetailBinding> {
    private PlayFragment playFragment = null;
    private SourceViewModel sourceViewModel;
    private Movie.Video mVideo;
    private VodInfo vodInfo;
    public SeriesFlagAdapter seriesFlagAdapter;
    public SeriesAdapter seriesAdapter;
    public String vodId;
    public String sourceKey;
    private View seriesFlagFocus = null;
    private boolean isReverse;
    private String preFlag = "";
    private HashMap<String, String> mCheckSources = null;
    BatteryReceiver mBatteryReceiver = new BatteryReceiver();
    //改为view模式无法自动响应返回键操作,onBackPress时手动dismiss
    private BasePopupView mAllSeriesRightDialog;
    private BasePopupView mAllSeriesBottomDialog;
    /**
     * Home键广播,用于触发后台服务
     */
    private BroadcastReceiver mHomeKeyReceiver;
    /**
     * 是否开启后台播放标记,不在广播开启,onPause根据标记开启
     */
    boolean openBackgroundPlay;
    private BroadcastReceiver mRemoteActionReceiver;

    /**
     * 截屏监听
     */
    ScreenShotListenManager screenShotListenManager;

    @Override
    protected void init() {
        initReceiver();
        initView();
        initViewModel();
        initData();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mBatteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mBatteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        }
        ImmersionBar.with(this)
                .statusBarColor(R.color.black)
                .navigationBarColor(R.color.white)
                .fitsSystemWindows(true)
                .statusBarDarkFont(false)
                .init();
        toggleScreenShotListen(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        openBackgroundPlay = false;
        playServerSwitch(false);
        mBinding.ivPrivateBrowsing.postDelayed(NotificationUtils::cancelAll, 800);
    }

    private void initView() {
        mBinding.ivPrivateBrowsing.setVisibility(Hawk.get(HawkConfig.PRIVATE_BROWSING, false) ? View.VISIBLE : View.GONE);
        mBinding.ivPrivateBrowsing.setOnClickListener(view -> ToastUtils.showShort("当前为无痕浏览"));
        mBinding.previewPlayerPlace.setVisibility(showPreview ? View.VISIBLE : View.GONE);

        mBinding.mGridView.setHasFixedSize(true);
        mBinding.mGridView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 0, false));
        mBinding.mGridView.addItemDecoration(new LinearSpacingItemDecoration(20, false));

        seriesAdapter = new SeriesAdapter(false);
        mBinding.mGridView.setAdapter(seriesAdapter);
        mBinding.mGridViewFlag.setHasFixedSize(true);
        seriesFlagAdapter = new SeriesFlagAdapter();
        mBinding.mGridViewFlag.setAdapter(seriesFlagAdapter);
        isReverse = false;
        preFlag = "";
        if (showPreview) {
            playFragment = new PlayFragment();
            getSupportFragmentManager().beginTransaction().add(R.id.previewPlayer, playFragment).commit();
            getSupportFragmentManager().beginTransaction().show(playFragment).commitAllowingStateLoss();
        }

        findViewById(R.id.ll_title).setOnClickListener(view -> {
            new XPopup.Builder(this)
                    .isViewMode(true)
                    .hasNavigationBar(false)
                    .asCustom(new VideoDetailDialog(this, vodInfo))
                    .show();
        });
        findViewById(R.id.tvDownload).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                use1DMDownload();
            }
        });
        findViewById(R.id.tvDownloadAll).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startBatchDownload();
            }
        });
        mBinding.tvSort.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void onClick(View v) {
                sortSeries();
            }
        });
        mBinding.tvCast.setVisibility(View.GONE);
        mBinding.tvCollect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = mBinding.tvCollect.getText().toString();
                if ("加入收藏".equals(text)) {
                    RoomDataManger.insertVodCollect(sourceKey, vodInfo);
                    Toast.makeText(DetailActivity.this, "已加入收藏夹", Toast.LENGTH_SHORT).show();
                    mBinding.tvCollect.setText("取消收藏");
                } else {
                    RoomDataManger.deleteVodCollect(sourceKey, vodInfo);
                    Toast.makeText(DetailActivity.this, "已移除收藏夹", Toast.LENGTH_SHORT).show();
                    mBinding.tvCollect.setText("加入收藏");
                }
            }
        });

        seriesFlagAdapter.setOnItemClickListener((adapter, view, position) -> {
            chooseFlag(position);
        });

        seriesAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                chooseSeries(position, false);
            }
        });

        mBinding.tvAllSeries.setOnClickListener(view -> {
            showAllSeriesDialog();
        });

        mBinding.tvSite.setOnClickListener(view -> {
            startQuickSearch();
            QuickSearchDialog quickSearchDialog = new QuickSearchDialog(DetailActivity.this);
            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH, quickSearchData));
            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_WORD, quickSearchWord));
            quickSearchDialog.show();
            if (pauseRunnable != null && pauseRunnable.size() > 0) {
                searchExecutorService = Executors.newFixedThreadPool(5);
                for (Runnable runnable : pauseRunnable) {
                    searchExecutorService.execute(runnable);
                }
                pauseRunnable.clear();
                pauseRunnable = null;
            }
            quickSearchDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    try {
                        if (searchExecutorService != null) {
                            pauseRunnable = searchExecutorService.shutdownNow();
                            searchExecutorService = null;
                        }
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                }
            });
        });
        mBinding.tvChangeLine.setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            quickLineChange();
        });
        setLoadSir(mBinding.llLayout);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (openBackgroundPlay) {
            playServerSwitch(true);
        }
    }

    private void initReceiver() {
        // 注册广播接收器
        if (mHomeKeyReceiver == null) {
            mHomeKeyReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action != null && action.equals(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)) {
                        openBackgroundPlay = Hawk.get(HawkConfig.BACKGROUND_PLAY_TYPE, 0) == 1 && playFragment.getPlayer() != null && playFragment.getPlayer().isPlaying();
                    }
                }
            };
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(mHomeKeyReceiver, new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS), Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(mHomeKeyReceiver, new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
            }
        }
    }

    /**
     * 排序(倒序)
     */
    public void sortSeries() {
        if (vodInfo != null && vodInfo.seriesMap.size() > 0) {
            vodInfo.reverseSort = !vodInfo.reverseSort;
            isReverse = !isReverse;
            vodInfo.reverse();
            vodInfo.playIndex = (vodInfo.seriesMap.get(vodInfo.playFlag).size() - 1) - vodInfo.playIndex;
//                    insertVod(sourceKey, vodInfo);

            seriesAdapter.notifyDataSetChanged();
        }
    }

    public void showAllSeriesDialog() {
        if (fullWindows) {
            mAllSeriesRightDialog = new XPopup.Builder(this)
                    .isViewMode(true)//隐藏导航栏(手势条)在dialog模式下会闪一下,改为view模式,但需处理onBackPress的隐藏,下方同理
                    .hasNavigationBar(false)
                    .popupHeight(ScreenUtils.getScreenHeight())
                    .popupPosition(PopupPosition.Right)
                    .enableDrag(false)//禁用拖拽,内部有横向rv
                    .asCustom(new AllVodSeriesRightDialog(this));
            mAllSeriesRightDialog.show();
        } else {
            mAllSeriesBottomDialog = new XPopup.Builder(this)
                    .isViewMode(true)
                    .hasNavigationBar(false)
                    .maxHeight(ScreenUtils.getScreenHeight() - (ScreenUtils.getScreenHeight() / 4))
                    .asCustom(new AllVodSeriesBottomDialog(this, seriesAdapter.getData(), (position, text) -> {
                        chooseSeries(position, false);
                    }));
            mAllSeriesBottomDialog.show();
        }
    }

    private void chooseFlag(int position) {
        //新选中的flag
        String newFlag = seriesFlagAdapter.getData().get(position).name;
        if (vodInfo != null && !vodInfo.playFlag.equals(newFlag)) {
            for (int i = 0; i < vodInfo.seriesFlags.size(); i++) {//遍历flag集合
                VodInfo.VodSeriesFlag flag = vodInfo.seriesFlags.get(i);
                if (flag.name.equals(vodInfo.playFlag)) {//取消当前播放的选中状态
                    flag.selected = false;
                    seriesFlagAdapter.notifyItemChanged(i);
                    break;
                }
            }
            //新选中的flag
            VodInfo.VodSeriesFlag flag = vodInfo.seriesFlags.get(position);
            flag.selected = true;
            //清除上一个线路集数的选中状态
            List<VodInfo.VodSeries> currentSeriesList = vodInfo.seriesMap.get(vodInfo.playFlag);
            if (currentSeriesList.size() > vodInfo.playIndex) {//有效集数
                currentSeriesList.get(vodInfo.playIndex).selected = false;
            }
            vodInfo.playFlag = newFlag;
            seriesFlagAdapter.notifyItemChanged(position);
            refreshList();
        }
    }

    private void chooseSeries(int position, boolean reloadWithChangeLine) {
        if (vodInfo != null && vodInfo.seriesMap.get(vodInfo.playFlag).size() > 0) {
            boolean reload = false;
            for (int j = 0; j < vodInfo.seriesMap.get(vodInfo.playFlag).size(); j++) {
                seriesAdapter.getData().get(j).selected = false;
                seriesAdapter.notifyItemChanged(j);
            }
            //解决倒叙不刷新
            if (vodInfo.playIndex != position) {
                seriesAdapter.getData().get(position).selected = true;
                seriesAdapter.notifyItemChanged(position);
                vodInfo.playIndex = position;

                reload = true;
            }
            //解决当前集不刷新的BUG
            if (!preFlag.isEmpty() && !vodInfo.playFlag.equals(preFlag)) {
                reload = true;
            }

            seriesAdapter.getData().get(vodInfo.playIndex).selected = true;
            seriesAdapter.notifyItemChanged(vodInfo.playIndex);

            //选集全屏 想选集不全屏的注释下面一行
            if (!showPreview || reload || reloadWithChangeLine) {
                jumpToPlay();
            }
        }
    }

    private void initCheckedSourcesForSearch() {
        mCheckSources = SearchHelper.getSourcesForSearch();
    }

    private List<Runnable> pauseRunnable = null;

    private void jumpToPlay() {
        if (vodInfo != null && vodInfo.seriesMap.get(vodInfo.playFlag).size() > 0) {
            preFlag = vodInfo.playFlag;
            //更新播放地址
            Bundle bundle = new Bundle();
            //保存历史
            insertVod(sourceKey, vodInfo);
            bundle.putString("sourceKey", sourceKey);
//            bundle.putSerializable("VodInfo", vodInfo);
            App.getInstance().setVodInfo(vodInfo);
            if (previewVodInfo == null) {
                try {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    ObjectOutputStream oos = new ObjectOutputStream(bos);
                    oos.writeObject(vodInfo);
                    oos.flush();
                    oos.close();
                    ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()));
                    previewVodInfo = (VodInfo) ois.readObject();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (previewVodInfo != null) {
                previewVodInfo.playerCfg = vodInfo.playerCfg;
                previewVodInfo.playFlag = vodInfo.playFlag;
                previewVodInfo.playIndex = vodInfo.playIndex;
                previewVodInfo.seriesMap = vodInfo.seriesMap;
//                    bundle.putSerializable("VodInfo", previewVodInfo);
                App.getInstance().setVodInfo(previewVodInfo);
            }
            playFragment.setData(bundle);

            //定位选集
            mBinding.mGridView.scrollToPosition(vodInfo.playIndex);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    void refreshList() {
        int seriesSize = vodInfo.seriesMap.get(vodInfo.playFlag).size();
        if (seriesSize > 0 && seriesSize <= vodInfo.playIndex) {//当前集数大于新选线路的总集数,设置为最后一集
            vodInfo.playIndex = seriesSize - 1;
        }

        if (vodInfo.seriesMap.get(vodInfo.playFlag) != null) {
            boolean canSelect = true;
            for (int j = 0; j < vodInfo.seriesMap.get(vodInfo.playFlag).size(); j++) {
                if (vodInfo.seriesMap.get(vodInfo.playFlag).get(j).selected) {
                    canSelect = false;
                    break;
                }
            }
            if (canSelect)
                vodInfo.seriesMap.get(vodInfo.playFlag).get(vodInfo.playIndex).selected = true;
        }
        seriesAdapter.setNewData(vodInfo.seriesMap.get(vodInfo.playFlag));

    }

    private void initViewModel() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        sourceViewModel.detailResult.observe(this, new Observer<AbsXml>() {
            @Override
            public void onChanged(AbsXml absXml) {
                if (absXml != null && absXml.movie != null && absXml.movie.videoList != null && absXml.movie.videoList.size() > 0) {
                    showSuccess();
                    mVideo = absXml.movie.videoList.get(0);
                    vodInfo = new VodInfo();
                    vodInfo.setVideo(mVideo);
                    vodInfo.sourceKey = mVideo.sourceKey;

                    mBinding.tvName.setText(TextUtils.isEmpty(mVideo.name) ? "暂无信息" : mVideo.name);
                    String srcName = ApiConfig.get().getSource(mVideo.sourceKey).getName();
                    mBinding.tvSite.setText("来源：" + (TextUtils.isEmpty(srcName) ? "未知" : srcName));

                    if (vodInfo.seriesMap != null && vodInfo.seriesMap.size() > 0) {//线路
                        mBinding.mGridViewFlag.setVisibility(View.VISIBLE);
                        mBinding.mGridView.setVisibility(View.VISIBLE);
                        mBinding.mEmptyPlaylist.setVisibility(View.GONE);

                        VodInfo vodInfoRecord = RoomDataManger.getVodInfo(sourceKey, vodId);
                        // 读取历史记录
                        if (vodInfoRecord != null) {
                            vodInfo.playIndex = Math.max(vodInfoRecord.playIndex, 0);
                            vodInfo.playFlag = vodInfoRecord.playFlag;
                            vodInfo.playerCfg = vodInfoRecord.playerCfg;
                            vodInfo.reverseSort = vodInfoRecord.reverseSort;
                        } else {
                            vodInfo.playIndex = 0;
                            vodInfo.playFlag = null;
                            vodInfo.playerCfg = "";
                            vodInfo.reverseSort = false;
                        }

                        if (vodInfo.reverseSort) {
                            vodInfo.reverse();
                        }

                        if (vodInfo.playFlag == null || !vodInfo.seriesMap.containsKey(vodInfo.playFlag))
                            vodInfo.playFlag = (String) vodInfo.seriesMap.keySet().toArray()[0];

                        int flagScrollTo = 0;
                        for (int j = 0; j < vodInfo.seriesFlags.size(); j++) {
                            VodInfo.VodSeriesFlag flag = vodInfo.seriesFlags.get(j);
                            if (flag.name.equals(vodInfo.playFlag)) {
                                flagScrollTo = j;
                                flag.selected = true;
                            } else
                                flag.selected = false;
                        }
//                        setTextShow(tvPlayUrl, "播放地址：", vodInfo.seriesMap.get(vodInfo.playFlag).get(0).url);
                        //设置线路数据
                        seriesFlagAdapter.setNewData(vodInfo.seriesFlags);
                        mBinding.mGridViewFlag.scrollToPosition(flagScrollTo);

                        refreshList();
                        if (showPreview) {
                            jumpToPlay();
                            mBinding.previewPlayer.setVisibility(View.VISIBLE);
                            toggleSubtitleTextSize();
                        }
                        // startQuickSearch();
                    } else {//空布局
                        mBinding.mGridViewFlag.setVisibility(View.GONE);
                        mBinding.mGridView.setVisibility(View.GONE);
                        mBinding.mEmptyPlaylist.setVisibility(View.VISIBLE);
                    }
                } else {
                    showEmpty();
                    mBinding.previewPlayer.setVisibility(View.GONE);
                }
            }
        });
    }

    private String getHtml(String label, String content) {
        if (content == null) {
            content = "";
        }
        return label + "<font color=\"#FFFFFF\">" + content + "</font>";
    }

    private void initData() {
        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            Bundle bundle = intent.getExtras();
            loadDetail(bundle.getString("id", null), bundle.getString("sourceKey", ""));
        }
    }

    private void loadDetail(String vid, String key) {
        if (vid != null) {
            vodId = vid;
            sourceKey = key;
            showLoading();
            sourceViewModel.getDetail(sourceKey, vodId);
            boolean isVodCollect = RoomDataManger.isVodCollect(sourceKey, vodId);
            if (isVodCollect) {
                mBinding.tvCollect.setText("取消收藏");
            } else {
                mBinding.tvCollect.setText("加入收藏");
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_REFRESH) {
            if (event.obj != null) {
                if (event.obj instanceof Integer) {
                    int index = (int) event.obj;
                    for (int j = 0; j < vodInfo.seriesMap.get(vodInfo.playFlag).size(); j++) {
                        seriesAdapter.getData().get(j).selected = false;
                        seriesAdapter.notifyItemChanged(j);
                    }
                    seriesAdapter.getData().get(index).selected = true;
                    seriesAdapter.notifyItemChanged(index);
                    //mBinding.mGridView.setSelection(index);
                    vodInfo.playIndex = index;
                    //保存历史
                    insertVod(sourceKey, vodInfo);
                } else if (event.obj instanceof JSONObject) {
                    vodInfo.playerCfg = ((JSONObject) event.obj).toString();
                    //保存历史
                    insertVod(sourceKey, vodInfo);
                }

            }
        } else if (event.type == RefreshEvent.TYPE_QUICK_SEARCH_SELECT) {
            if (event.obj != null) {
                Movie.Video video = (Movie.Video) event.obj;
                loadDetail(video.id, video.sourceKey);
            }
        } else if (event.type == RefreshEvent.TYPE_QUICK_SEARCH_WORD_CHANGE) {
            if (event.obj != null) {
                String word = (String) event.obj;
                switchSearchWord(word);
            }
        } else if (event.type == RefreshEvent.TYPE_QUICK_SEARCH_RESULT) {
            try {
                searchData(event.obj == null ? null : (AbsXml) event.obj);
            } catch (Exception e) {
                searchData(null);
            }
        }
    }

    private String searchTitle = "";
    private boolean hadQuickStart = false;
    private final List<Movie.Video> quickSearchData = new ArrayList<>();
    private final List<String> quickSearchWord = new ArrayList<>();
    private ExecutorService searchExecutorService = null;

    private void switchSearchWord(String word) {
        OkGo.getInstance().cancelTag("quick_search");
        quickSearchData.clear();
        searchTitle = word;
        searchResult();
    }

    private void startQuickSearch() {
        initCheckedSourcesForSearch();
        if (hadQuickStart)
            return;
        hadQuickStart = true;
        OkGo.getInstance().cancelTag("quick_search");
        quickSearchWord.clear();
        searchTitle = mVideo.name;
        quickSearchData.clear();
        quickSearchWord.addAll(SearchHelper.splitWords(searchTitle));
        // 分词
        OkGo.<String>get("http://api.pullword.com/get.php?source=" + URLEncoder.encode(searchTitle) + "&param1=0&param2=0&json=1")
                .tag("fenci")
                .execute(new AbsCallback<String>() {
                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        if (response.body() != null) {
                            return response.body().string();
                        } else {
                            throw new IllegalStateException("网络请求错误");
                        }
                    }

                    @Override
                    public void onSuccess(Response<String> response) {
                        String json = response.body();
                        try {
                            for (JsonElement je : new Gson().fromJson(json, JsonArray.class)) {
                                quickSearchWord.add(je.getAsJsonObject().get("t").getAsString());
                            }
                        } catch (Throwable th) {
                            th.printStackTrace();
                        }
                        List<String> words = new ArrayList<>(new HashSet<>(quickSearchWord));
                        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_WORD, words));
                    }

                    @Override
                    public void onError(Response<String> response) {
                        super.onError(response);
                    }
                });

        searchResult();
    }

    private void searchResult() {
        try {
            if (searchExecutorService != null) {
                searchExecutorService.shutdownNow();
                searchExecutorService = null;
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        searchExecutorService = Executors.newFixedThreadPool(5);
        List<SourceBean> searchRequestList = new ArrayList<>();
        searchRequestList.addAll(ApiConfig.get().getSourceBeanList());
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        searchRequestList.remove(home);
        searchRequestList.add(0, home);

        ArrayList<String> siteKey = new ArrayList<>();
        for (SourceBean bean : searchRequestList) {
            if (!bean.isSearchable() || !bean.isQuickSearch()) {
                continue;
            }
            if (mCheckSources != null && !mCheckSources.containsKey(bean.getKey())) {
                continue;
            }
            siteKey.add(bean.getKey());
        }
        for (String key : siteKey) {
            searchExecutorService.execute(new Runnable() {
                @Override
                public void run() {
                    sourceViewModel.getQuickSearch(key, searchTitle);
                }
            });
        }
    }

    private void searchData(AbsXml absXml) {
        if (absXml != null && absXml.movie != null && absXml.movie.videoList != null && absXml.movie.videoList.size() > 0) {
            List<Movie.Video> data = new ArrayList<>();
            for (Movie.Video video : absXml.movie.videoList) {
                // 去除当前相同的影片
                if (video.sourceKey.equals(sourceKey) && video.id.equals(vodId))
                    continue;
                data.add(video);
            }
            quickSearchData.addAll(data);
            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH, data));
        }
    }

    private void insertVod(String sourceKey, VodInfo vodInfo) {
        if (Hawk.get(HawkConfig.PRIVATE_BROWSING, false)) {//无痕浏览
            return;
        }
        try {
            vodInfo.playNote = vodInfo.seriesMap.get(vodInfo.playFlag).get(vodInfo.playIndex).name;
        } catch (Throwable th) {
            vodInfo.playNote = "";
        }
        RoomDataManger.insertVodRecord(sourceKey, vodInfo);
        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_HISTORY_REFRESH));
    }

    @Override
    protected void onDestroy() {
        registerActionReceiver(false);
        cleanupBatch();
        super.onDestroy();
        unregisterReceiver(mBatteryReceiver);
        // 注销广播接收器
        if (mHomeKeyReceiver != null) {
            unregisterReceiver(mHomeKeyReceiver);
            mHomeKeyReceiver = null;
        }

        try {
            if (searchExecutorService != null) {
                searchExecutorService.shutdownNow();
                searchExecutorService = null;
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        OkGo.getInstance().cancelTag("fenci");
        OkGo.getInstance().cancelTag("detail");
        OkGo.getInstance().cancelTag("quick_search");
        toggleScreenShotListen(false);
    }

    @Override
    public void onBackPressed() {
        if (mAllSeriesRightDialog != null && mAllSeriesRightDialog.isShow()) {
            mAllSeriesRightDialog.dismiss();
            return;
        }
        if (mAllSeriesBottomDialog != null && mAllSeriesBottomDialog.isShow()) {
            mAllSeriesBottomDialog.dismiss();
            return;
        }
        if (playFragment.hideAllDialogSuccess()) {//fragment有弹窗隐藏并拦截返回
            return;
        }
        if (fullWindows) {
            toggleFullPreview();
            mBinding.mGridView.requestFocus();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event != null && playFragment != null && fullWindows) {
            if (playFragment.dispatchKeyEvent(event)) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    // preview
    VodInfo previewVodInfo = null;
    boolean showPreview = Hawk.get(HawkConfig.SHOW_PREVIEW, true);
    ; // true 开启 false 关闭
    boolean fullWindows = false;
    ViewGroup.LayoutParams windowsPreview = null;
    ViewGroup.LayoutParams windowsFull = null;

    public void toggleFullPreview() {
        if (windowsPreview == null) {
            windowsPreview = mBinding.previewPlayer.getLayoutParams();
        }
        if (windowsFull == null) {//全屏尺寸
            windowsFull = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        fullWindows = !fullWindows;

        //交由fragment处理播放器全屏逻辑
        playFragment.changedLandscape(fullWindows);
        //activity处理预览尺寸(全屏/非全屏预览)
        mBinding.previewPlayer.setLayoutParams(fullWindows ? windowsFull : windowsPreview);
        mBinding.mGridView.setVisibility(fullWindows ? View.GONE : View.VISIBLE);
        mBinding.mGridViewFlag.setVisibility(fullWindows ? View.GONE : View.VISIBLE);

        //全屏下禁用详情页几个按键的焦点 防止上键跑过来
        mBinding.tvSort.setFocusable(!fullWindows);
        mBinding.tvCollect.setFocusable(!fullWindows);
        toggleSubtitleTextSize();
    }

    void toggleSubtitleTextSize() {
        int subtitleTextSize = SubtitleHelper.getTextSize(this);
        if (!fullWindows) {
            subtitleTextSize *= 0.6;
        }
        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SUBTITLE_SIZE_CHANGE, subtitleTextSize));
    }

    public void use1DMDownload() {
        if (vodInfo != null && vodInfo.seriesMap.get(vodInfo.playFlag).size() > 0) {
            VodInfo.VodSeries vod = vodInfo.seriesMap.get(vodInfo.playFlag).get(vodInfo.playIndex);
            String url = TextUtils.isEmpty(playFragment.getFinalUrl()) ? vod.url : playFragment.getFinalUrl();
            // 创建Intent对象，启动1DM App
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.setDataAndType(Uri.parse(url), "video/mp4");
            intent.putExtra("title", vodInfo.name + " " + vod.name); // 传入文件保存名
//            intent.setClassName("idm.internet.download.manager.plus", "idm.internet.download.manager.MainActivity");
            intent.setClassName("idm.internet.download.manager.plus", "idm.internet.download.manager.Downloader");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            // 检查1DM App是否已安装
            PackageManager pm = getPackageManager();
            List<ResolveInfo> activities = pm.queryIntentActivities(intent, 0);
            boolean isIntentSafe = activities.size() > 0;

            if (isIntentSafe) {
                startActivity(intent); // 启动1DM App
            } else {
                // 如果1DM App未安装，提示用户安装1DM App
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("请先安装1DM+下载管理器");
                builder.setMessage("为了下载视频，请先安装1DM+下载管理器。是否现在安装？");
                builder.setPositiveButton("立即下载", new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        // 跳转到下载链接
                        Intent downloadIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://od.lk/d/MzRfMTg0NTcxMDdf/1DM _v15.6.apk"));
                        startActivity(downloadIntent);
                    }
                });
                builder.setNegativeButton("取消", null);
                builder.show();
            }
        } else {
            ToastUtils.showShort("资源异常,请稍后重试");
        }
    }

    /**
     * 画中画模式
     */
    public void enterPip() {
        if (Utils.supportsPiPMode()) {
            // 创建一个Intent对象，模拟按下Home键
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            startActivity(intent);

            // Calculate Video Resolution
            int vWidth = playFragment.getPlayer().getVideoSize()[0];
            int vHeight = playFragment.getPlayer().getVideoSize()[1];
            Rational ratio;
            if (vWidth != 0) {
                if ((((double) vWidth) / ((double) vHeight)) > 2.39) {
                    vHeight = (int) (((double) vWidth) / 2.35);
                }
                ratio = new Rational(vWidth, vHeight);
            } else {
                ratio = new Rational(16, 9);
            }
            List<RemoteAction> actions = new ArrayList<>();
            actions.add(generateRemoteAction(android.R.drawable.ic_media_previous, IntentKey.BROADCAST_ACTION_PREV, "Prev", "Play Previous"));
            actions.add(generateRemoteAction(android.R.drawable.ic_media_play, IntentKey.BROADCAST_ACTION_PLAYPAUSE, "Play", "Play/Pause"));
            actions.add(generateRemoteAction(android.R.drawable.ic_media_next, IntentKey.BROADCAST_ACTION_NEXT, "Next", "Play Next"));
            PictureInPictureParams params = new PictureInPictureParams.Builder()
                    .setAspectRatio(ratio)
                    .setActions(actions).build();
            playFragment.getPlayer().postDelayed(() -> {//代码模拟home键时会立即执行,toggleFullPreview中竖屏有切换横屏操作,
                if (!fullWindows) {
                    toggleFullPreview();
                }
            }, 300);
            enterPictureInPictureMode(params);
            playFragment.getController().hideBottom();

            playFragment.getPlayer().postDelayed(() -> {
                if (!playFragment.getPlayer().isPlaying()) {
                    playFragment.getController().togglePlay();
                }
            }, 400);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private RemoteAction generateRemoteAction(int iconResId, int actionCode, String title, String desc) {
        final PendingIntent intent =
                PendingIntent.getBroadcast(
                        DetailActivity.this,
                        actionCode,
                        new Intent(IntentKey.BROADCAST_ACTION).putExtra("action", actionCode),
                        0);
        final Icon icon = Icon.createWithResource(DetailActivity.this, iconResId);
        return (new RemoteAction(icon, title, desc, intent));
    }

    /**
     * 事件接收广播(画中画/后台播放点击事件)
     * @param isRegister 注册/注销
     */
    private void registerActionReceiver(boolean isRegister) {
        if (isRegister) {
            mRemoteActionReceiver = new BroadcastReceiver() {

                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent == null || !intent.getAction().equals(IntentKey.BROADCAST_ACTION) || playFragment.getController() == null) {
                        return;
                    }

                    int currentStatus = intent.getIntExtra("action", 1);
                    if (currentStatus == IntentKey.BROADCAST_ACTION_PREV) {
                        playFragment.playPrevious();
                    } else if (currentStatus == IntentKey.BROADCAST_ACTION_PLAYPAUSE) {
                        playFragment.getController().togglePlay();
                    } else if (currentStatus == IntentKey.BROADCAST_ACTION_NEXT) {
                        playFragment.playNext(false);
                    } else if (currentStatus == IntentKey.BROADCAST_ACTION_CLOSE) {
                        playServerSwitch(false);
                        finish();
                        NotificationUtils.cancelAll();
                    }
                }
            };
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(mRemoteActionReceiver, new IntentFilter(IntentKey.BROADCAST_ACTION), Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(mRemoteActionReceiver, new IntentFilter(IntentKey.BROADCAST_ACTION));
            }
        } else {
            if (mRemoteActionReceiver != null) {
                unregisterReceiver(mRemoteActionReceiver);
                mRemoteActionReceiver = null;
            }
            if (playFragment.getPlayer().isPlaying()) {// 退出画中画时,暂停播放(画中画的全屏也会触发,但全屏后会自动播放)
                playFragment.getController().togglePlay();
            }
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);
        registerActionReceiver(Utils.supportsPiPMode() && isInPictureInPictureMode);
    }

    /**
     * 后台播放服务开关,开启时注册操作广播,关闭时注销
     */
    private void playServerSwitch(boolean open) {
        if (open) {
            VodInfo.VodSeries vod = vodInfo.seriesMap.get(vodInfo.playFlag).get(vodInfo.playIndex);
            PlayService.start(playFragment.getPlayer(), vodInfo.name + "&&" + vod.name);
            registerActionReceiver(true);
        } else {
            if (ServiceUtils.isServiceRunning(PlayService.class)) {
                PlayService.stop();
                registerActionReceiver(false);
            }
        }
    }

    public String getCurrentVodUrl() {
        return playFragment == null ? "" : playFragment.getFinalUrl();
    }

    public void quickLineChange() {
        List<VodInfo.VodSeriesFlag> flags = seriesFlagAdapter.getData();
        if (flags.size() > 1) {
            int currentIndex = 0;
            for (int i = 0; i < flags.size(); i++) {
                if (flags.get(i).selected) {
                    currentIndex = i;
                }
            }
            currentIndex += 1;
            if (currentIndex >= flags.size()) {
                currentIndex = 0;
            }
            mBinding.mGridViewFlag.smoothScrollToPosition(currentIndex);
            chooseFlag(currentIndex);
            mBinding.mGridView.postDelayed(() -> chooseSeries(vodInfo.playIndex, true), 300);
        }
    }

    public void showParseRoot(boolean show, ParseAdapter adapter) {
        mBinding.rvParse.setAdapter(adapter);
        int defaultIndex = 0;
        for (int i = 0; i < adapter.getData().size(); i++) {
            if (adapter.getData().get(i).isDefault()) {
                defaultIndex = i;
                break;
            }
        }
        if (defaultIndex != 0) {
            mBinding.rvParse.scrollToPosition(defaultIndex);
        }
        mBinding.parseRoot.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void toggleScreenShotListen(boolean open) {
        if (open){
            if (screenShotListenManager == null){
                screenShotListenManager = ScreenShotListenManager.newInstance(this);
            }
            screenShotListenManager.setListener(imagePath -> {

                if (playFragment.getPlayer().isInPlaybackState())return;

                new XPopup.Builder(this)
                        .isDarkTheme(Utils.isDarkTheme())
                        .asCenterList("",new String[]{"跳转阿狸","跳转优汐","跳转夸父","关闭"}, null, (position, text) -> {
                            String pkg = "";
                            String cls = "";
                            switch (position){
                                case 0:
                                    pkg = "com.alicloud.databox";
                                    cls = "com.alicloud.databox.launcher.splash.SplashActivity";
                                    break;
                                case 1:
                                    pkg = "com.UCMobile";
                                    cls = "com.uc.browser.InnerUCMobile";
                                    break;
                                case 2:
                                    pkg = "com.quark.browser";
                                    cls = "com.ucpro.MainActivity";
                                    break;
                                case 3:
                                    return;
                            }
                            try {
                                startActivity(new Intent().setComponent(new ComponentName(pkg, cls)));
                            }catch (Exception e){
                                ToastUtils.showShort("未找到应用");
                            }
                        })
                        .show();
            });
            screenShotListenManager.startListen();
        }else {
            if (screenShotListenManager != null) {
                screenShotListenManager.stopListen();
            }
        }
    }

    // ==================== 批量下载功能 ====================

    /**
     * 批量嗅探任务条目
     */
    private static class SniffTask {
        final int position;       // 在 series 中的原始位置，用于回写缓存与刷新列表
        final String name;
        final String url;
        String resolvedUrl;
        volatile boolean done;       // 是否已完成（成功或失败均置 true，避免重复回调）
        volatile boolean inFlight;  // 是否已被分发到某个槽位

        SniffTask(int position, String name, String url) {
            this.position = position;
            this.name = name;
            this.url = url;
        }
    }

    /**
     * 嗅探槽位：每个槽位持有独立的 WebView / 标志 / 超时 Runnable，支持串行（并发度=1）嗅探，跨任务复用 WebView。
     */
    private static class SniffSlot {
        final int id;
        WebView webView;
        final boolean[] found = {false};
        Runnable timeoutRunnable;
        SniffTask currentTask;
        // LiveData observer 引用，便于嗅探完成后反注册
        Observer<JSONObject> parseObserver;

        SniffSlot(int id) {
            this.id = id;
        }
    }

    private List<SniffTask> mBatchTasks;
    private final AtomicInteger mBatchDoneCount = new AtomicInteger(0);
    private final AtomicInteger mBatchDispatchedCount = new AtomicInteger(0);
    private volatile boolean mBatchCancelled;
    private final Handler mBatchHandler = new Handler(Looper.getMainLooper());
    private static final int SNIFF_TIMEOUT = 8000;
    // 依次（串行）嗅探：并发度=1，单 WebView 槽位跨任务复用，避免并发带来的资源争抢
    private static final int SNIFF_CONCURRENCY = 1;
    private final SniffSlot[] mSniffSlots = new SniffSlot[SNIFF_CONCURRENCY];
    // 弹窗引用，用于实时刷新单项状态
    private BatchDownloadDialog mBatchDialog;

    /**
     * 启动批量下载流程：展示选集弹窗，弹窗一出现就读缓存刷新列表并启动依次嗅探
     */
    private void startBatchDownload() {
        if (vodInfo == null || vodInfo.seriesMap == null || vodInfo.seriesMap.get(vodInfo.playFlag) == null) {
            ToastUtils.showShort("暂无播放数据");
            return;
        }
        List<VodInfo.VodSeries> series = vodInfo.seriesMap.get(vodInfo.playFlag);
        if (series.isEmpty()) {
            ToastUtils.showShort("暂无集数数据");
            return;
        }
        // 构建任务列表
        mBatchTasks = new ArrayList<>();
        for (int i = 0; i < series.size(); i++) {
            VodInfo.VodSeries vs = series.get(i);
            mBatchTasks.add(new SniffTask(i, vodInfo.name + " " + vs.name, vs.url));
        }
        mBatchDoneCount.set(0);
        mBatchDispatchedCount.set(0);
        mBatchCancelled = false;
        for (int i = 0; i < SNIFF_CONCURRENCY; i++) {
            if (mSniffSlots[i] == null) {
                mSniffSlots[i] = new SniffSlot(i);
            } else {
                mSniffSlots[i].currentTask = null;
                // 保留 webView 实例（若存在）以跨次复用，避免每次重新创建
                mSniffSlots[i].found[0] = false;
                mSniffSlots[i].timeoutRunnable = null;
                mSniffSlots[i].parseObserver = null;
                if (mSniffSlots[i].webView != null) {
                    try {
                        mSniffSlots[i].webView.stopLoading();
                        mSniffSlots[i].webView.setWebViewClient(null);
                        mSniffSlots[i].webView.loadUrl("about:blank");
                        mSniffSlots[i].webView.clearHistory();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }

        // 显示弹窗（列表默认全不可点击）
        mBatchDialog = new BatchDownloadDialog(this, series, this::onBatchDownloadItemClick, this::onResniff,
                () -> {
                    // 弹窗关闭时彻底销毁 WebView 池，释放内存
                    mBatchCancelled = true;
                    cleanupBatch();
                });
        new XPopup.Builder(this)
                .isViewMode(true)
                .hasNavigationBar(false)
                .maxHeight(ScreenUtils.getScreenHeight() - (ScreenUtils.getScreenHeight() / 4))
                .asCustom(mBatchDialog)
                .show();

        // 预创建 WebView 池（首批任务可直接复用，避免串行现场初始化）
        runOnUiThread(this::ensureWebViewPool);

        // 先按已嗅探过的缓存刷新弹窗（持久化缓存，应用被杀后下次打开仍可读取）。
        // 注意：只有成功项（非空串）命中跳过；失败项（空串缓存）不标记 done，会被依次嗅探重试。
        for (SniffTask task : mBatchTasks) {
            String key = App.buildSniffKey(sourceKey, vodInfo.playFlag, task.url);
            String cached = App.getSniffCache(key);
            if (cached != null && !cached.isEmpty()) {
                task.resolvedUrl = cached;
                task.done = true;
                int pos = task.position;
                int done = mBatchDoneCount.incrementAndGet();
                runOnUiThread(() -> {
                    if (mBatchDialog != null) {
                        mBatchDialog.markItemSuccess(pos);
                        mBatchDialog.updateProgress(done, mBatchTasks.size(), "已嗅探");
                    }
                });
            }
        }

        // 启动依次嗅探（dispatchNextTask 会自动跳过 done 的项，失败项会被重新嗅探）
        int initial = Math.min(SNIFF_CONCURRENCY, mBatchTasks.size());
        for (int i = 0; i < initial; i++) {
            dispatchNextTask(i);
        }
    }

    /**
     * 重新嗅探回调：清空当前列表对应的缓存、重置任务与弹窗、重启依次嗅探。
     */
    private void onResniff() {
        if (mBatchTasks == null || mBatchTasks.isEmpty()) return;

        // 1. 先停掉当前在飞的嗅探（软清理：保留 WebView 池以便复用，避免重复创建）
        mBatchCancelled = true;
        cleanupAllSlots(false);
        mBatchCancelled = false;

        // 2. 清空当前列表对应的缓存（成功和失败的都清掉，保证全部重新嗅探）
        for (SniffTask task : mBatchTasks) {
            String key = App.buildSniffKey(sourceKey, vodInfo.playFlag, task.url);
            App.removeSniffCache(key);
        }

        // 3. 重置任务状态
        for (SniffTask task : mBatchTasks) {
            task.resolvedUrl = null;
            task.done = false;
            task.inFlight = false;
        }
        mBatchDoneCount.set(0);
        mBatchDispatchedCount.set(0);

        // 4. 重置弹窗 UI（全部项变灰、进度归零）
        if (mBatchDialog != null) {
            mBatchDialog.resetAll();
        }

        // 5. 确保 WebView 池就绪（首次/重建后预创建，否则复用已有）
        ensureWebViewPool();

        // 6. 重启依次嗅探
        int initial = Math.min(SNIFF_CONCURRENCY, mBatchTasks.size());
        for (int i = 0; i < initial; i++) {
            dispatchNextTask(i);
        }
    }

    /**
     * 弹窗列表项点击回调：仅 selected=true（嗅探成功）才会回调。
     * 直接调用 1DM+ 启动下载，使用嗅探到的 URL，文件名 = "剧名_集名.mp4"
     */
    private void onBatchDownloadItemClick(int position, VodInfo.VodSeries item) {
        if (mBatchTasks == null || position < 0 || position >= mBatchTasks.size()) return;
        SniffTask task = mBatchTasks.get(position);
        if (TextUtils.isEmpty(task.resolvedUrl)) return;
        String safeName = task.name == null ? "" : task.name.replace(" ", "_") + ".mp4";
        sendUrlTo1DM(task.resolvedUrl, safeName);
    }

    /**
     * 发送单个 URL 到 1DM+ 启动下载。
     */
    private void sendUrlTo1DM(String url, String fileName) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.setDataAndType(Uri.parse(url), "video/mp4");
        intent.putExtra("title", fileName);
        intent.setClassName("idm.internet.download.manager.plus", "idm.internet.download.manager.Downloader");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PackageManager pm = getPackageManager();
        List<ResolveInfo> activities = pm.queryIntentActivities(intent, 0);
        if (activities.isEmpty()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("未安装 1DM+");
            builder.setMessage("已嗅探到下载地址。\n如需直接下载，请安装 1DM+ 下载管理器。是否现在安装？");
            builder.setPositiveButton("立即下载", (dialog, which) -> {
                Intent downloadIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://od.lk/d/MzRfMTg0NTcxMDdf/1DM _v15.6.apk"));
                downloadIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(downloadIntent);
            });
            builder.setNegativeButton("取消", null);
            builder.show();
            return;
        }
        startActivity(intent);
        ToastUtils.showShort("已发送到 1DM+");
    }

    /**
     * 调度下一个待嗅探任务到指定槽位（并发度=1 时只有 slot 0，实现依次串行）。
     * 调度规则：从尚未分发的任务里取下一个；如已无任务，则不调度（仅等待未完成槽位收尾）。
     */
    private void dispatchNextTask(int slotId) {
        if (mBatchCancelled || mBatchTasks == null) {
            return;
        }
        SniffTask next = null;
        synchronized (mBatchTasks) {
            for (int i = 0; i < mBatchTasks.size(); i++) {
                SniffTask t = mBatchTasks.get(i);
                if (!t.inFlight && !t.done) {
                    t.inFlight = true;
                    next = t;
                    break;
                }
            }
        }
        if (next == null) {
            return;
        }
        int dispatched = mBatchDispatchedCount.incrementAndGet();
        updateBatchDialogProgress(dispatched - 1, mBatchTasks.size(), "正在嗅探: " + next.name);
        processBatchTask(slotId, next);
    }

    /**
     * 处理单个批量任务（在指定槽位上）
     */
    private void processBatchTask(int slotId, SniffTask task) {
        SniffSlot slot = mSniffSlots[slotId];
        slot.currentTask = task;
        slot.found[0] = false;

        // 使用 sourceViewModel.getPlay() 获取播放信息。注意：playResult 是共享 LiveData，
        // 多路并发嗅探时所有 observer 都会被任一请求 postValue 触发。
        // 修复：必须用 info.key（== 调用 getPlay 时传入的 url）识别是不是本 task 的请求结果，
        // 不匹配的直接跳过；null 也跳过，让本槽位超时兜底，避免误标其他 task 失败。
        Observer<JSONObject> observer = new Observer<JSONObject>() {
            @Override
            public void onChanged(JSONObject info) {
                if (task.done) {
                    sourceViewModel.playResult.removeObserver(this);
                    return;
                }
                // 只处理属于本 task 的请求结果（key == task.url）
                if (info == null) {
                    return; // 其他请求失败的 null，不处理，等本 task 超时或自己的结果
                }
                String key = info.optString("key", "");
                if (!task.url.equals(key)) {
                    return; // 不是我发起的请求结果，忽略
                }
                sourceViewModel.playResult.removeObserver(this);
                if (mBatchCancelled) {
                    onBatchTaskDone(slot, task);
                    return;
                }
                try {
                    boolean parse = info.optString("parse", "1").equals("1");
                    boolean jx = info.optString("jx", "0").equals("1");
                    String playUrl = info.optString("playUrl", "");
                    String url = info.getString("url");
                    String flag = info.optString("flag");
                    if (!parse && !jx) {
                        task.resolvedUrl = playUrl + url;
                        onBatchTaskDone(slot, task);
                    } else {
                        boolean userJxList = (playUrl.isEmpty() && ApiConfig.get().getVipParseFlags().contains(flag)) || jx;
                        resolveWithParse(userJxList, playUrl, url, slot, task);
                    }
                } catch (Throwable th) {
                    task.resolvedUrl = "";
                    onBatchTaskDone(slot, task);
                }
            }
        };
        slot.parseObserver = observer;
        sourceViewModel.playResult.observe(this, observer);
        sourceViewModel.getPlay(sourceKey, vodInfo.playFlag, "", task.url, "");
    }

    /**
     * 解析需要嗅探的URL（在指定槽位上）
     */
    private void resolveWithParse(boolean useParse, String playUrl, String url, SniffSlot slot, SniffTask task) {
        ParseBean parseBean = null;
        if (useParse) {
            parseBean = ApiConfig.get().getDefaultParse();
        } else {
            if (playUrl.startsWith("json:")) {
                parseBean = new ParseBean();
                parseBean.setType(1);
                parseBean.setUrl(playUrl.substring(5));
            } else if (playUrl.startsWith("parse:")) {
                String parseRedirect = playUrl.substring(6);
                for (ParseBean pb : ApiConfig.get().getParseBeanList()) {
                    if (pb.getName().equals(parseRedirect)) {
                        parseBean = pb;
                        break;
                    }
                }
            }
            if (parseBean == null) {
                parseBean = new ParseBean();
                parseBean.setType(0);
                parseBean.setUrl(playUrl);
            }
        }

        if (parseBean.getType() == 1) {
            resolveWithJsonParse(parseBean, url, slot, task);
        } else if (parseBean.getType() == 0) {
            sniffWithWebView(parseBean, url, slot, task);
        } else {
            task.resolvedUrl = url;
            onBatchTaskDone(slot, task);
        }
    }

    /**
     * JSON 方式解析（在指定槽位上）
     */
    private void resolveWithJsonParse(ParseBean pb, String url, SniffSlot slot, SniffTask task) {
        OkGo.<String>get(pb.getUrl() + encodeUrl(url))
                .tag("batch_json_jx")
                .execute(new AbsCallback<String>() {
                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        return response.body() != null ? response.body().string() : "";
                    }

                    @Override
                    public void onSuccess(Response<String> response) {
                        try {
                            JSONObject rs = jsonParseSimple(url, response.body());
                            if (rs != null) {
                                task.resolvedUrl = rs.optString("url", "");
                            }
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                        onBatchTaskDone(slot, task);
                    }

                    @Override
                    public void onError(Response<String> response) {
                        super.onError(response);
                        onBatchTaskDone(slot, task);
                    }
                });
    }

    /**
     * 简化版 JSON 解析
     */
    private JSONObject jsonParseSimple(String input, String json) throws org.json.JSONException {
        JSONObject jsonPlayData = new JSONObject(json);
        String url;
        if (jsonPlayData.has("data")) {
            url = jsonPlayData.getJSONObject("data").getString("url");
        } else {
            url = jsonPlayData.getString("url");
        }
        if (url.startsWith("//")) {
            url = "http:" + url;
        }
        if (!url.startsWith("http")) {
            return null;
        }
        JSONObject taskResult = new JSONObject();
        taskResult.put("url", url);
        return taskResult;
    }

    /**
     * WebView 嗅探（每个槽位独立 WebView，跨任务复用避免反复创建开销）
     */
    private void sniffWithWebView(ParseBean pb, String videoUrl, SniffSlot slot, SniffTask task) {
        runOnUiThread(() -> {
            if (mBatchCancelled) {
                onBatchTaskDone(slot, task);
                return;
            }
            // 复用 WebView：池已预创建则直接复用；否则现场初始化（仅首次/被销毁后）。
            // 关键优化点：跨任务保留 WebView 实例，避免 JS 引擎、内核反复初始化带来的耗时。
            if (slot.webView == null) {
                ensureSlotWebView(slot);
            } else {
                // 复用前清理上次任务残留（不清磁盘缓存，避免下次重新加载资源变慢）
                try {
                    slot.webView.stopLoading();
                    slot.webView.setWebViewClient(null);
                    slot.webView.loadUrl("about:blank");
                    slot.webView.clearHistory();
                    slot.webView.clearFormData();
                } catch (Throwable ignored) {
                }
            }
            slot.found[0] = false;

            String webUserAgent = null;
            HashMap<String, String> webHeaders = null;
            if (pb.getExt() != null) {
                try {
                    JSONObject jsonObject = new JSONObject(pb.getExt());
                    if (jsonObject.has("header")) {
                        JSONObject headerJson = jsonObject.optJSONObject("header");
                        Iterator<String> keys = headerJson.keys();
                        while (keys.hasNext()) {
                            String key = keys.next();
                            if (key.equalsIgnoreCase("user-agent")) {
                                webUserAgent = headerJson.getString(key).trim();
                            } else if (webHeaders == null) {
                                webHeaders = new HashMap<>();
                                webHeaders.put(key, headerJson.optString(key, ""));
                            }
                        }
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }

            final String finalWebUserAgent = webUserAgent;
            final HashMap<String, String> finalWebHeaders = webHeaders;
            final Map<String, Boolean> loadedUrls = new HashMap<>();

            // 每次任务都要重设 WebViewClient（闭包捕获了本次 task，避免上次任务的回调误触发）
            slot.webView.setWebViewClient(new WebViewClient() {
                @Override
                public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                    String reqUrl = request.getUrl().toString();
                    if (slot.found[0]) return null;
                    boolean ad;
                    if (!loadedUrls.containsKey(reqUrl)) {
                        ad = AdBlocker.isAd(reqUrl);
                        loadedUrls.put(reqUrl, ad);
                    } else {
                        ad = Boolean.TRUE.equals(loadedUrls.get(reqUrl));
                    }
                    if (!ad) {
                        if (checkBatchVideoFormat(reqUrl)) {
                            slot.found[0] = true;
                            if (slot.timeoutRunnable != null) {
                                mBatchHandler.removeCallbacks(slot.timeoutRunnable);
                            }
                            task.resolvedUrl = reqUrl;
                            // 复用模式下不销毁 WebView，只停止加载，下个任务会复用
                            runOnUiThread(() -> {
                                try {
                                    if (slot.webView != null) {
                                        slot.webView.stopLoading();
                                        slot.webView.loadUrl("about:blank");
                                    }
                                } catch (Throwable ignored) {
                                }
                                onBatchTaskDone(slot, task);
                            });
                        }
                    }
                    return null;
                }
            });

            if (finalWebUserAgent != null) {
                slot.webView.getSettings().setUserAgentString(finalWebUserAgent);
            }

            slot.timeoutRunnable = () -> {
                if (!slot.found[0]) {
                    slot.found[0] = true;
                    // 超时仅停止加载，不销毁 WebView（下个任务复用）
                    try {
                        if (slot.webView != null) {
                            slot.webView.stopLoading();
                            slot.webView.loadUrl("about:blank");
                        }
                    } catch (Throwable ignored) {
                    }
                    onBatchTaskDone(slot, task);
                }
            };
            mBatchHandler.postDelayed(slot.timeoutRunnable, SNIFF_TIMEOUT);

            String fullUrl = pb.getUrl() + videoUrl;
            if (finalWebHeaders != null) {
                slot.webView.loadUrl(fullUrl, finalWebHeaders);
            } else {
                slot.webView.loadUrl(fullUrl);
            }
        });
    }

    /**
     * 检查是否为视频URL
     */
    private boolean checkBatchVideoFormat(String url) {
        try {
            if (url.contains("url=http") || url.contains(".html")) {
                return false;
            }
            SourceBean sourceBean = ApiConfig.get().getSource(sourceKey);
            if (sourceBean.getType() == 3) {
                Spider sp = ApiConfig.get().getCSP(sourceBean);
                if (sp != null && sp.manualVideoCheck()) {
                    return sp.isVideoFormat(url);
                }
            }
            return VideoParseRuler.checkIsVideoForParse("", url);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 单个任务完成：CAS 防重复回调、写缓存、刷新弹窗、调度本槽位下一个任务。
     */
    private void onBatchTaskDone(SniffSlot slot, SniffTask task) {
        if (task.done) {
            return;
        }
        synchronized (mBatchTasks) {
            if (task.done) return;
            task.done = true;
        }
        if (slot.parseObserver != null) {
            sourceViewModel.playResult.removeObserver(slot.parseObserver);
            slot.parseObserver = null;
        }
        // 软清理：保留 WebView 实例供下一个任务复用（避免反复创建 WebView / JS 引擎初始化）
        resetSlotState(slot);
        slot.currentTask = null;

        // 写入持久化缓存（失败也写入空串，下次若显式开启弹窗仍会重嗅探失败项，因为成功缓存判断是 !cached.isEmpty()）
        String key = App.buildSniffKey(sourceKey, vodInfo.playFlag, task.url);
        App.putSniffCache(key, task.resolvedUrl == null ? "" : task.resolvedUrl);

        int done = mBatchDoneCount.incrementAndGet();
        int total = mBatchTasks == null ? done : mBatchTasks.size();
        // 刷新弹窗单项状态与进度
        final int pos = task.position;
        final boolean success = !TextUtils.isEmpty(task.resolvedUrl);
        final int finalDone = done;
        runOnUiThread(() -> {
            if (mBatchDialog != null) {
                if (success) {
                    mBatchDialog.markItemSuccess(pos);
                } else {
                    mBatchDialog.markItemFailed(pos);
                }
                mBatchDialog.updateProgress(finalDone, total, finalDone >= total ? "完成" : "嗅探中…");
            }
        });

        if (mBatchCancelled) {
            if (mBatchDoneCount.get() >= total || allSlotsIdle()) {
                cleanupBatch();
            }
            return;
        }

        if (done >= total) {
            // 全部完成
            runOnUiThread(() -> ToastUtils.showShort("批量嗅探完成"));
            return;
        }
        // 本槽位调度下一个任务（并发度=1 时就是依次串行）
        dispatchNextTask(slot.id);
    }

    /**
     * 是否所有槽位都已空闲（无 currentTask）
     */
    private boolean allSlotsIdle() {
        for (int i = 0; i < SNIFF_CONCURRENCY; i++) {
            if (mSniffSlots[i] != null && mSniffSlots[i].currentTask != null) {
                return false;
            }
        }
        return true;
    }

    /**
     * 更新弹窗进度（如果弹窗存在）
     */
    private void updateBatchDialogProgress(int done, int total, String detail) {
        runOnUiThread(() -> {
            if (mBatchDialog != null) {
                mBatchDialog.updateProgress(done, total, detail);
            }
        });
    }

    /**
     * 软清理槽位：仅取消超时 Runnable、解绑 WebViewClient、停掉当前加载。
     * 保留 WebView 实例供下一个任务复用（关键性能优化点：WebView/JS 引擎初始化开销大）。
     */
    private void resetSlotState(SniffSlot slot) {
        if (slot == null) return;
        if (slot.timeoutRunnable != null) {
            mBatchHandler.removeCallbacks(slot.timeoutRunnable);
            slot.timeoutRunnable = null;
        }
        slot.found[0] = false;
        if (slot.webView != null) {
            try {
                slot.webView.stopLoading();
                // 解绑上次的 WebViewClient，避免下个任务 loadUrl 前的旧回调误触发
                slot.webView.setWebViewClient(null);
                slot.webView.loadUrl("about:blank");
                slot.webView.clearHistory();
                slot.webView.clearFormData();
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * 硬销毁槽位 WebView：仅在弹窗真正关闭 / Activity 销毁时调用，释放内存。
     */
    private void destroySlotWebView(SniffSlot slot) {
        if (slot == null) return;
        if (slot.timeoutRunnable != null) {
            mBatchHandler.removeCallbacks(slot.timeoutRunnable);
            slot.timeoutRunnable = null;
        }
        slot.found[0] = false;
        if (slot.webView != null) {
            try {
                slot.webView.stopLoading();
                slot.webView.setWebViewClient(null);
                slot.webView.loadUrl("about:blank");
                slot.webView.removeAllViews();
                slot.webView.destroy();
            } catch (Throwable ignored) {
            }
            slot.webView = null;
        }
    }

    /**
     * 预创建全部槽位的 WebView（仅初始化尚未创建的槽位）。
     * 在主线程同步创建：弹窗 show 之后调用，让首批嗅探任务直接复用而非现场初始化。
     */
    private void ensureWebViewPool() {
        if (mBatchCancelled) return;
        for (int i = 0; i < SNIFF_CONCURRENCY; i++) {
            SniffSlot slot = mSniffSlots[i];
            if (slot == null) {
                slot = new SniffSlot(i);
                mSniffSlots[i] = slot;
            }
            if (slot.webView == null && !mBatchCancelled) {
                ensureSlotWebView(slot);
            }
        }
    }

    /**
     * 创建并配置单个槽位的 WebView（仅初始化一次），后续任务复用此实例。
     */
    private void ensureSlotWebView(SniffSlot slot) {
        if (slot.webView != null) return;
        WebView webView = new WebView(this);
        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(1, 1);
        webView.setLayoutParams(params);
        addContentView(webView, params);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setBlockNetworkImage(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);

        slot.webView = webView;
    }

    /**
     * 清理全部槽位：destroy=true 销毁 WebView（弹窗关闭/Activity 销毁），destroy=false 仅软清理（重新嗅探时复用池）。
     */
    private void cleanupAllSlots(boolean destroy) {
        for (int i = 0; i < SNIFF_CONCURRENCY; i++) {
            SniffSlot s = mSniffSlots[i];
            if (s == null) continue;
            if (destroy) {
                destroySlotWebView(s);
            } else {
                resetSlotState(s);
            }
            s.currentTask = null;
            s.parseObserver = null;
            s.found[0] = false;
        }
        mBatchHandler.removeCallbacksAndMessages(null);
    }

    /**
     * 清理批量下载资源（不删缓存，缓存持久化保留）
     */
    private void cleanupBatch() {
        cleanupAllSlots(true);
        mBatchDialog = null;
        mBatchTasks = null;
    }
}
