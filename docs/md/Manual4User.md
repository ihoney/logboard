# User Manual 日志系统使用手册

### 使用概述  
必配置的选项：
- beating.host 心跳接收服务器ip

可配置的选项：
- beating.appid 日志工具使用者id
- beating.msg 自定义消息，如日志使用方的集群名、父模块名、别名、自定义标签
- log.interval 设置心跳发送时间间隔
- log.level 设置统一的最低日志级别

### 日志接入

### 日志可视化

### 接口汇总   
  
|   services    |       host     |    port    |      desc      |
|:-------------:|:--------------:|:----------:|:---------------|
|log-boot       |                |     8701   | 日志首页，socket服务端           |
|logbase        |                |     8702   | 日志系统Mysql统计结果后台服务     |
|msg-router     |                |     8703   | redis消息转发、短信邮件消息      |
|es-query       |                |     8704   | es查询专用接口                  |
|task-scheduler |                |     18701  | 心跳检测端口                    |
|logbase  db    |                |     3306   | root/111111                   |


- 日志接入系统操作类 
  - 查询  
   id
  - 所有 
  

###Requirements 前提

- 打日志前要选择合适的工具类,BasicLogger快速打日志，AdanvancedLogger设置日志详细参数；
- BasicLogger 可设置3项：AppCode,  LogType, LogMsg
- AdvancedLogger 可设置：
      AppCode,  LogType, LogMsg, 
      UserIp, UserId, UserPlatform, LogTag,
      AppModule, AppThread, LogExp, LogLocation,
      Inputs, Outputs, CoderComments, LogTopic
- steraming日志必有8项
      logLevel, logTime, codeClass, codeFile, lineNumber, appCode, logType, logMsg, logOptions

- 修改task—scheduler的统计时间为5s