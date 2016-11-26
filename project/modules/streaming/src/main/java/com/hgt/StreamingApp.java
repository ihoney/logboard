package com.hgt;

import com.google.common.collect.Lists;
import com.hgt.converter.MapJsonConverter;
import com.hgt.es.common.ESAdminOperations;
import com.hgt.es.config.ESConfig;
import com.hgt.filter.LogKeyChecker;
import com.hgt.filter.LogOptionsChecker;
import com.hgt.hbase.common.HBaseOperations;
import com.hgt.hbase.keys.RowkeyFactory;
import com.hgt.obj.CloneUtils;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.function.*;
import org.apache.spark.streaming.Duration;
import org.apache.spark.streaming.Time;
import org.apache.spark.streaming.api.java.JavaDStream;
import org.apache.spark.streaming.api.java.JavaPairDStream;
import org.apache.spark.streaming.api.java.JavaPairReceiverInputDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import org.apache.spark.streaming.kafka.KafkaUtils;
import scala.Tuple2;

import java.io.IOException;
import java.util.*;

/**
 * 日志实时处理类
 */
public class StreamingApp {

    public static void main(String[] args) {

        //region 读取配置文件
        Properties prop = new Properties();
        try {
            prop.load(Thread.currentThread().getContextClassLoader().getResourceAsStream("config.properties"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        String zkQuorum = prop.getProperty("kafka.zookeeper");
        //话题所在的组
        String group = prop.getProperty("group.name");
        //话题名称以","分隔
        String topics = prop.getProperty("topic.list");
        //endregion

        //每个话题的分片数
        int numThreads = 2;
        SparkConf sparkConf = new SparkConf().setAppName("LogStreaming").setMaster("local[3]");
        //每5s进行批处理
        JavaStreamingContext jssc = new JavaStreamingContext(sparkConf, new Duration(5000));

        //存放话题跟分片的映射关系
        Map<String, Integer> topicmap = new HashMap<>();
        String[] topicsArr = topics.split(",");
        int n = topicsArr.length;
        for (int i = 0; i < n; i++) {
            topicmap.put(topicsArr[i], numThreads);
        }

        //从Kafka中获取数据转换成RDD
        JavaPairReceiverInputDStream<String, String> lines = KafkaUtils.createStream(jssc, zkQuorum, group, topicmap);

        //从topic中获取所需数据
        JavaDStream<String> logItems = lines.flatMap(new FlatMapFunction<Tuple2<String, String>, String>() {
            @Override
            public Iterable<String> call(Tuple2<String, String> arg0)
                    throws Exception {
                return Lists.newArrayList(arg0._2);
            }
        });

        //region 过滤次要日志，存储有效日志
        JavaDStream<String> validLogs = logItems.filter(new Function<String, Boolean>() {
            @Override
            public Boolean call(String s) throws Exception {

                System.out.println(s);

                HashMap<String, String> logMap = new LinkedHashMap<>();
                logMap.put("logLevel", s.substring(1, 6).trim());
                logMap.put("logTime", s.substring(7, 30).trim());
                logMap.put("codeClass", s.substring(31, 71).trim());
                logMap.put("codeFile", s.substring(72, 92).trim());
                logMap.put("lineNumber", s.substring(93, 96).trim());

                ///!!!!!BUG 日志内容不能含有逗号等
                String logsRight = s.substring(100, s.length() - 2);
                String[] manualLogs = logsRight.split(",");
                int ml = manualLogs.length;

                String[][] m = new String[ml][2];
                //将3个固定的appCode, logType, logMsg加入map
                for (int i = 0; i < 3; i++) {

                    m[i] = manualLogs[i].split(":");
                    String k = m[i][0].replace("\"", "");
                    String v = m[i][1].replace("\"", "");

                    logMap.put(k, v);

                }

                //region 将日志索引进es集群
                HashMap<String, String> esLogMap = new LinkedHashMap<>();
                esLogMap = CloneUtils.clone(logMap);
                String thisTime = esLogMap.get("logTime");
                String formattedTime = thisTime.replace(" ", "T").replace(",", ".") + "Z";
                esLogMap.put("logTime", formattedTime);
                //将剩余部分strLogOptions加入esLogMap
                String strLogOptions = "";
                if (manualLogs[3].substring(13).contains(":")) {
                    for (int k = 3; k < ml; k++) {
                        strLogOptions = strLogOptions + manualLogs[k] + ",";
                    }
                } else {
                    strLogOptions = manualLogs[3];
                }

                strLogOptions = strLogOptions.substring(0, strLogOptions.length() - 1);
                String ko = strLogOptions.split(":")[0].replace("\"", "");
                String vo = strLogOptions.substring(strLogOptions.split(":")[0].length() + 1).replace("\"", "");
                esLogMap.put(ko, vo);

                String eslogString = MapJsonConverter.simpleMapToJsonStr(esLogMap);

                //region ES配置
                ESConfig esConfig = new ESConfig("es-yao", "192.168.99.140:9300,192.168.99.141:9300");
                ESAdminOperations esAdminOperations = new ESAdminOperations(esConfig);
                esAdminOperations.indexingDataByMap("test-log", "log9type", esLogMap);
                esAdminOperations.close();
                //endregion


                //将不固定的logOptions加入map
                if (manualLogs[3].substring(13).contains(":")) {

                    for (int j = 3; j < ml; j++) {

                        m[j] = manualLogs[j].split(":");

                        if (j == 3) {
                            //去掉第4个键值对的"logOptions:"
                            String k1 = m[j][1].substring(1).replace("\"", "");
                            String v1 = m[j][2].replace("\"", "");
                            logMap.put(k1, v1);

                        } else if (j == ml - 1) {
                            //去掉最后一个键值对后面的"}"
                            String k1 = m[j][0].replace("\"", "");
                            String v1 = m[j][1].substring(0, m[j][1].length() - 1).replace("\"", "");
                            logMap.put(k1, v1);

                        } else {
                            //普通键值对直接加入map
                            String k1 = m[j][0].replace("\"", "");
                            String v1 = m[j][1].replace("\"", "");
                            logMap.put(k1, v1);

                        }
                    }
                }


                //过滤日志
                if (new LogKeyChecker(logMap).isLogValid() && new LogOptionsChecker(logMap).isLogValid()) {
                    List<String> keys = new ArrayList<String>(logMap.keySet());
                    List<String> vals = new ArrayList<String>(logMap.values());
                    String tableName = "one-log";
                    HBaseOperations hBaseOperations = new HBaseOperations();
                    String rk = RowkeyFactory.build16(logMap.get("logType"), logMap.get("logTime"));
                    //TO-DO：分成2个列族存储
                    hBaseOperations.insertRow(tableName, rk, "loginfo", keys, vals);
                    return true;
                } else {
                    return false;
                }
            }
        });
        //endregion


        //统计该时段所有日志
        JavaPairDStream<String, Integer> wordCounts = validLogs.mapToPair(

                new PairFunction<String, String, Integer>() {
                    @Override
                    public Tuple2<String, Integer> call(String s) {
                        return new Tuple2<>(s, 1);
                    }
                })
                .reduceByKey(new Function2<Integer, Integer, Integer>() {
                    @Override
                    public Integer call(Integer i1, Integer i2) {
                        return i1 + i2;
                    }
                });

        HashMap<String, String> countMap = new HashMap<>();

        wordCounts.foreach(new Function2<JavaPairRDD<String, Integer>, Time, Void>() {

            @Override
            public Void call(JavaPairRDD<String, Integer> values, Time time)
                    throws Exception {

                values.foreach(new VoidFunction<Tuple2<String, Integer>>() {

                    @Override
                    public void call(Tuple2<String, Integer> tuple) throws Exception {

                        System.out.println("Tuple1: " + tuple._1() + ", Tuple2: " + tuple._2());

                        countMap.put(tuple._1(), String.valueOf(tuple._2()));


                    }
                });

                return null;
            }
        });

        jssc.start();
        jssc.awaitTermination();

    }

}