package com.hgt.stats;

import com.hgt.domain.AppsCodeCounts;
import com.hgt.domain.SimpleStringBean;
import com.hgt.entity.StatsByApp5s;
import com.hgt.entity.StatsByApp5sTotal;
import com.hgt.mapper.StatsByApp5sMapper;
import com.hgt.mapper.StatsByApp5sTotalMapper;
import com.hgt.mapper.StatsByAppMapper;
import com.hgt.tools.StatsIdBuilder;
import com.hgt.utils.DateHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * README: 按应用统计日志的定时任务
 * Created by Yao on 12/7/16.
 * =============================================================================
 * CHANGELOG:
 */
@RestController
public class ByAppsStats {

    private final String BASE_URL = "logb/stats/app";

    @Autowired
    StatsByAppMapper statsByAppMapper;

    @Autowired
    StatsByApp5sMapper statsByApp5sMapper;

    @Autowired
    StatsByApp5sTotalMapper statsByApp5sTotalMapper;

    @RequestMapping(value = BASE_URL + "/counts/5s", method = RequestMethod.GET)
    public void statsAppLogCountsBy5s() {

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        HashMap<String, Object> dateMap = new HashMap<String, Object>();

        Date insertTime = new Date();
        String strInsertTime = simpleDateFormat.format(insertTime);

//        String dateNow = simpleDateFormat.format(new Date());
        String startTime = "";
        dateMap.put("endTime", strInsertTime);
        //设置查询时间为最新5s
//        startTime = DateHelper.operateDatetimeByHour(strInsertTime, -72);
        startTime = DateHelper.operateDatetimeBySecond(strInsertTime, -5);
        dateMap.put("startTime", startTime);


        //region 按应用统计5s内的日志数，并入库
        List<AppsCodeCounts> firstResult = statsByAppMapper.selectAppCountsByTimePeriod(dateMap);
//        System.out.println("成功得到 " + firstResult.size() + " 条结果 ");

        for (AppsCodeCounts item : firstResult) {
            StatsByApp5s newItem = new StatsByApp5s();
            newItem.setStatsRid(StatsIdBuilder.build(item.getAppCode(), strInsertTime));
            newItem.setStartTime(insertTime);
            newItem.setAppCode(item.getAppCode());
            newItem.setLogCounts(Integer.parseInt(item.getCounts()));
            int result4Insert = statsByApp5sMapper.insert(newItem);
            newItem = null;
        }
        //endregion 按应用统计5s内的日志数，并入库

        //region 统计5s内的日志总数，并入库
        SimpleStringBean thisTotalCount = statsByAppMapper.selectTotalCountsByTimePeriod(dateMap);
        StatsByApp5sTotal statsByApp5sTotal = new StatsByApp5sTotal();
        statsByApp5sTotal.setStatsRid(StatsIdBuilder.build("TOTAL", strInsertTime));
        statsByApp5sTotal.setStartTime(insertTime);
        //5s内的日志总数
        int count;
        if (thisTotalCount == null) {
            count = 0;
        } else {
            count = Integer.parseInt(thisTotalCount.getContents());
        }
        statsByApp5sTotal.setLogCounts(count);
        int result4InsertTotal = statsByApp5sTotalMapper.insert(statsByApp5sTotal);
        //endregion 统计5s内的日志总数，并入库

    }

}
