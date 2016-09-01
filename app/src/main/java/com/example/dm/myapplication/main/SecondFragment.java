package com.example.dm.myapplication.main;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.example.dm.myapplication.R;
import com.example.dm.myapplication.beans.AppUser;
import com.example.dm.myapplication.beans.ComUserPostInfo;
import com.example.dm.myapplication.com.ComPostTopicActivity;
import com.example.dm.myapplication.customviews.xlistview.XListView;
import com.example.dm.myapplication.customviews.xlistview.adapter.ComAppAdapter;
import com.example.dm.myapplication.utiltools.DateUtil;
import com.example.dm.myapplication.utiltools.SystemUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import cn.bmob.v3.BmobQuery;
import cn.bmob.v3.BmobUser;
import cn.bmob.v3.datatype.BmobDate;
import cn.bmob.v3.exception.BmobException;
import cn.bmob.v3.listener.FindListener;


/**
 * Created by dm on 16-3-29.
 * 第二个页面
 */
public class SecondFragment extends Fragment implements XListView.IXListViewListener {
    private final static int QUERY_ITEM_LIMITS = 5;     // 查询结果条目限制个数

    private RelativeLayout mTitleRout;

    private ImageView mPostNewImv;
    private XListView mListView = null;
    private ProgressBar mProgressBar;

    private Handler handler = null;
    private ComAppAdapter mComAppAdapter;
    private List<ComUserPostInfo> mList = new ArrayList<>();

    private String currentTimeStr = DateUtil.getCurrentTimeStr();
    private View view;
    private Date mDate;
    private String lastItemPostTimeStr;

    private long[] mHits = new long[2];     //存储时间的数组

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fg2, container, false);

        initView();
        generateData();
        dealEvents();   // 事件处理: gridview item的点击事件

        return view;
    }


    private void initView() {
        mTitleRout = (RelativeLayout) view.findViewById(R.id.title_rout);
        mPostNewImv = (ImageView) view.findViewById(R.id.com_post_new_rout);
        mListView = (XListView) view.findViewById(R.id.lv_main);
        mProgressBar = (ProgressBar) view.findViewById(R.id.com_loading_prbar);

        mListView.setPullLoadEnable(true);
        mListView.setXListViewListener(SecondFragment.this);
        handler = new Handler();

        mProgressBar.setVisibility(ProgressBar.VISIBLE);
    }

    private void dealEvents() {
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Toast.makeText(getActivity(), "点击了第" + position + "个list.", Toast.LENGTH_SHORT).show();
            }
        });

        mPostNewImv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AppUser appUser = BmobUser.getCurrentUser(AppUser.class);
                if (appUser == null) {
                    startActivity(new Intent(getActivity(), LoginActivity.class));
                } else {
                    startActivity(new Intent(getActivity(), ComPostTopicActivity.class));
                }
            }
        });

        // 双击事件，回顶部栏
        mTitleRout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //实现数组的移位操作，点击一次，左移一位，
                // 末尾补上当前开机时间（cpu的时间）
                System.arraycopy(mHits, 1, mHits, 0, mHits.length - 1);
                mHits[mHits.length - 1] = SystemClock.uptimeMillis();

                //双击事件的时间间隔500ms
                if (mHits[0] >= (SystemClock.uptimeMillis() - 500)) {
                    Log.i("LOG", "I am here!");
                    SystemUtils.scrollToListviewTop(mListView);
                }
            }
        });
    }

    /**
     * 获取数据：获取云端最近时间内的5条数据
     */
    private void generateData() {
        mDate = new Date();
        Log.i("LOG", "mDate in generateData >>> " + mDate);
        BmobQuery<ComUserPostInfo> postInfoBmobQuery = new BmobQuery<>();
        postInfoBmobQuery.addWhereLessThanOrEqualTo("createdAt", new BmobDate(mDate));
        postInfoBmobQuery.order("-createdAt");  // 按时间降序排列
        postInfoBmobQuery.setLimit(QUERY_ITEM_LIMITS);  // 设定返回的查询条数
        // 设定查询缓存策略-CACHE_ELSE_NETWORK: 先从缓存读取数据, 如果没有, 再从网络获取.
        postInfoBmobQuery.setCachePolicy(BmobQuery.CachePolicy.CACHE_ELSE_NETWORK);
        postInfoBmobQuery.setMaxCacheAge(TimeUnit.DAYS.toMillis(1));    //此表示缓存一天
        postInfoBmobQuery.findObjects(new FindListener<ComUserPostInfo>() {
            @Override
            public void done(List<ComUserPostInfo> list, BmobException e) {
                if (e == null) {
                    for (ComUserPostInfo comUserPostInfo : list) {
                        mList.add(comUserPostInfo);
                    }

                    mComAppAdapter = new ComAppAdapter(getActivity());
                    mComAppAdapter.setData(mList);
                    mListView.setAdapter(mComAppAdapter);
                    mProgressBar.setVisibility(ProgressBar.GONE);

                    // get the last item post time
                    if (!mList.isEmpty()) {
                        ComUserPostInfo lastPostInfo = mList.get(mList.size() - 1);
                        Log.i("LOG", "lastPostInfo.getUserTimeStr() in generateData " +
                                lastPostInfo.getUserTimeStr());
                        lastItemPostTimeStr = lastPostInfo.getUserTimeStr();
                    }

                } else {
                    mProgressBar.setVisibility(ProgressBar.GONE);
                }
            }
        });
    }

    /**
     * 下拉刷新数据：获取云端最近时间内的5条数据
     * 思路：记录第一次加载应用或下拉刷新的时间，
     * 取第二次或之后下拉刷新的时间这一段时间内的数据；
     * 从Bmob云端进行复合查询
     */
    private void generateRefleshData() {
        // 使用复合查询
        Log.i("LOG", "mDate in generateRefleshData query1 >>> " + mDate);
        BmobQuery<ComUserPostInfo> query1 = new BmobQuery<>();
        query1.addWhereGreaterThanOrEqualTo("createdAt", new BmobDate(mDate));

        mDate = new Date();     // 获取当前最新时间
        Log.i("LOG", "mDate in generateRefleshData query2 >>> " + mDate);
        BmobQuery<ComUserPostInfo> query2 = new BmobQuery<>();
        query2.addWhereLessThanOrEqualTo("createdAt", new BmobDate(mDate));

        List<BmobQuery<ComUserPostInfo>> andQuerys = new ArrayList<>();
        andQuerys.add(query1);
        andQuerys.add(query2);

        BmobQuery<ComUserPostInfo> postInfoBmobQuery = new BmobQuery<>();
        postInfoBmobQuery.and(andQuerys);
        postInfoBmobQuery.addWhereLessThanOrEqualTo("createdAt", new BmobDate(mDate));
        postInfoBmobQuery.order("-createdAt");  // 按时间降序排列
        postInfoBmobQuery.setLimit(QUERY_ITEM_LIMITS);  // 设定返回的查询条数
        // 设定查询缓存策略-NETWORK_ELSE_CACHE: 先从网络读取数据, 如果没有, 再从缓存获取.
        postInfoBmobQuery.setCachePolicy(BmobQuery.CachePolicy.NETWORK_ELSE_CACHE);
        postInfoBmobQuery.setMaxCacheAge(TimeUnit.DAYS.toMillis(1));    //此表示缓存一天
        postInfoBmobQuery.findObjects(new FindListener<ComUserPostInfo>() {
            @Override
            public void done(List<ComUserPostInfo> list, BmobException e) {
                if (e == null) {
                    if (list.size() == 0) {
                        SystemUtils.showHandlerToast(getActivity(), "没有更多内容了...");
                        Log.i("LOG", "list.size() in generateRefleshData >>> " + list.size());
                    } else {
                        for (ComUserPostInfo comUserPostInfo : list) {
                            mList.add(comUserPostInfo);
                        }

                        mComAppAdapter = new ComAppAdapter(getActivity());
                        mComAppAdapter.setData(mList);
                        mListView.setAdapter(mComAppAdapter);
                    }

                    mProgressBar.setVisibility(ProgressBar.GONE);
                } else {
                    mProgressBar.setVisibility(ProgressBar.GONE);
                }
            }
        });
    }

    /**
     * 上拉加载更多数据：获取云端最近时间内的5条数据
     * 思路：获取应用第一次打开时加载的数据的最后一个时间,
     * 上滑加载更多时, 数据取该日期之前的数据, 之后更新时间
     */
    private void generateLoadMoreData() {
        // get last item post time
        Date newdate = DateUtil.string2Date(lastItemPostTimeStr);
        Log.i("LOG", "newdate in generateLoadMoreData >>> " + newdate);

        BmobQuery<ComUserPostInfo> postInfoBmobQuery = new BmobQuery<>();
        postInfoBmobQuery.addWhereLessThanOrEqualTo("createdAt", new BmobDate(newdate));
        postInfoBmobQuery.order("-createdAt");  // 按时间降序排列
        postInfoBmobQuery.setLimit(QUERY_ITEM_LIMITS);  // 设定返回的查询条数
        // 设定查询缓存策略-CACHE_ELSE_NETWORK: 先从网络读取数据, 如果没有, 再从缓存获取.
        postInfoBmobQuery.setCachePolicy(BmobQuery.CachePolicy.NETWORK_ELSE_CACHE);
        postInfoBmobQuery.setMaxCacheAge(TimeUnit.DAYS.toMillis(1));    //此表示缓存一天
        postInfoBmobQuery.findObjects(new FindListener<ComUserPostInfo>() {
            @Override
            public void done(List<ComUserPostInfo> list, BmobException e) {
                if (e == null) {
                    if (list.size() == 0) {
                        SystemUtils.showHandlerToast(getActivity(), "没有更多内容了...");
                        Log.i("LOG", "list.size() in generateLoadMoreData >>> " + list.size());
                    } else {
                        for (ComUserPostInfo comUserPostInfo : list) {
                            mList.add(comUserPostInfo);
                        }

                        // 监听数据的变化, 上拉加载更多后处于当前可视的最后一个item位置
                        mComAppAdapter.notifyDataSetChanged();

                        // get the last item post time
                        if (!mList.isEmpty()) {
                            ComUserPostInfo lastPostInfo = mList.get(mList.size() - 1);
                            Log.i("LOG", "lastPostInfo.getUserTimeStr() in generateLoadMoreData" +
                                    lastPostInfo.getUserTimeStr());
                            lastItemPostTimeStr = lastPostInfo.getUserTimeStr();
                        }
                    }

                    mProgressBar.setVisibility(ProgressBar.GONE);
                } else {
                    mProgressBar.setVisibility(ProgressBar.GONE);
                }
            }
        });
    }

    @Override
    public void onRefresh() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                generateRefleshData();

                mComAppAdapter = new ComAppAdapter(getActivity());
                mComAppAdapter.setData(mList);
                mListView.setAdapter(mComAppAdapter);
                onLoad();
            }
        }, 500);
    }

    @Override
    public void onLoadMore() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                generateLoadMoreData();
                onLoad();
            }
        }, 500);
    }

    /**
     * 监听数据的改变，加载数据
     */
    private void onLoad() {
        mListView.stopRefresh();
        mListView.stopLoadMore();
        mListView.setRefreshTime(currentTimeStr);
    }
}
