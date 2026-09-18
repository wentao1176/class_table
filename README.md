# 大学课表 Android App

一款贴合国内大学作息的安卓课表应用，UI 参照微信小程序课表页面设计（周日~周六七列、一天 11 节课、马卡龙色课程卡片、非本周课程置灰），并针对**中国法定节假日与调休**做了完整适配。

- 应用包名：`com.xwt.schedule`
- 最低系统：Android 7.0（API 24）；目标系统：Android 13（API 33）
- 技术栈：Java + 原生 AndroidX + Material Components，无后端，数据仅保存在本机
- 当前版本：**v1.4**（versionCode 5）
- 安装包：`大学课表_v1.4.apk`（debug 签名，可直接侧载安装）
- 仓库：<https://github.com/wentao1176/class_table>（同时也是应用内自动更新的版本源）

## 功能一览

### 节假日与调休适配（v1.1 新增）

数据取自国务院办公厅《关于2025年部分节假日安排的通知》《关于2026年部分节假日安排的通知》，
内置 2025 / 2026 全部放假区间与调休上班日（未收录年份回退为「法定节日当天放假」）。

- **法定节假日自动停课**：放假当天不排课、不推送提醒，仅在表头以红色日期 +「休」角标体现
- **调休上班日按课表上课**：本来是周末的调休日（如 2026-09-20 周日、2026-10-10 周六）标注「补」角标，
  并改为显示被调休星期的课表（默认按官方假期中「被占用的工作日」推导，可在「我的」页逐日改成
  按周几上课或直接设为不上课）

> **为什么没有灰底**：周六、周日以及法定节假日没有课是**常态**，不是异常状态，
> 所以不给这些列加任何特殊底色，避免把"本来就没课"渲染成"出了问题"。
> 节假日 / 调休信息统一收敛到表头的「休 / 补」角标与日期颜色上。
- **今日页**：放假当天显示节日大卡片与「假期后第一节课」预告；调休日标注「调休补课」
- **课程页**：顶部提示最近一次放假 / 调休安排
- **我的页**：新增「节假日与调休」分组，可开关上述两项行为、查看本学期全部放假与调休清单
- **上课提醒**：按「日期」而非「课程星期」排定，放假当天不提醒，调休日照常提醒并在通知里注明

### 应用内自动更新（v1.2 新增）

版本源就是这个 GitHub 仓库本身，不需要额外的服务器或 GitHub Release：

- 应用读取仓库根目录的 **`update.json`**，与自身 `versionCode` 比较，有新版就在应用内下载安装
- 启动时自动检查（**24 小时内最多一次**，可在「我的」页关闭）；「我的」页也有「检查更新」可手动触发
- 下载走应用内进度条，完成后经 `FileProvider` 拉起系统安装器；Android 8+ 若未授权「安装未知应用」
  会先引导去授权
- 可「忽略此版本」，忽略后不再重复弹同一个版本

`update.json` 格式：

```json
{
  "versionCode": 5,
  "versionName": "1.4",
  "apkUrls": [
    "https://cdn.jsdelivr.net/gh/wentao1176/class_table@main/apk/class_table_v1.4.apk",
    "https://raw.githubusercontent.com/wentao1176/class_table/main/apk/class_table_v1.4.apk"
  ],
  "notes": "本次更新说明，会显示在更新对话框里",
  "forceUpdate": false
}
```

- `apkUrls` 按顺序尝试（也兼容单个 `"apkUrl": "..."` 字符串写法）
- **首选 jsDelivr CDN**：国内可直连，实测无需代理即可完整下载；
  `raw.githubusercontent.com` 作为兜底（国内常被阻断）
- 文件名带版本号，避免 CDN 缓存到旧包

#### 清单缓存：并行拉取 + 取最大 versionCode

`apk/` 里的文件名带版本号所以永远不会过期，但 `update.json` 路径是固定的，
而 **jsDelivr 对分支引用有缓存（最长约 12 小时）**，实测：

```
推送 v1.3 后立刻请求
  cdn.jsdelivr.net/.../update.json            -> versionCode=3  (旧缓存)
  raw.githubusercontent.com/.../update.json   -> versionCode=4  (已更新)
```

而且 **加 `?t=<时间戳>` 查询参数并不能绕过**（实测仍返回旧内容）。所以做了两件事：

1. **客户端**：两个镜像**并行**拉取（整体耗时约等于单个超时，不翻倍），
   解析后**取 `versionCode` 最大的那份**。这样 jsDelivr 命中旧缓存时，
   raw 的新内容能纠正它；反之 raw 被墙时，jsDelivr 也能兜住。
   只认"第一个成功的镜像"会在命中旧缓存时误报"已是最新"，这正是要避免的。
2. **发布流程**：推完代码后调一次 jsDelivr 的刷新接口，把缓存立刻清掉：

   ```bash
   curl "https://purge.jsdelivr.net/gh/wentao1176/class_table@main/update.json"
   # 返回 {"status":"finished", ...} 即为刷新成功
   ```

> **发布新版本的流程**：改 `app/build.gradle` 的 `versionCode` / `versionName` →
> 构建出 apk → 放到 `apk/class_table_vX.Y.apk`（同时删掉旧版本包）→ 更新 `update.json`
> 的 `versionCode` 与 `apkUrls` → 提交并推送 → **调一次 purge 接口**。
> 用户下次启动就会收到更新提示。

### 课表页（主界面）

- 周日~周六 7 列网格，左侧标注 1~11 节节次与上课/下课时间
- 课程卡片显示课程名与教室（@楼室号），10 种马卡龙配色可选
- 课程可连排多节；同一时段重叠的课程自动左右分栏
- **非本周课程自动置灰**：单周/双周/自定义周次不上课的周次仍显示位置但变灰，与参考小程序一致
- 顶部「‹ 第X周 ›」切换周次，点标题弹出整学期周次选择器，可一键回到本周；副标题显示本周放假/调休天数
- 表头用「休 / 补」角标与日期颜色（放假红、调休蓝）标注节假日与调休，**列本身不加灰底**
- 当天所在列高亮，上课时段内显示红色当前时间线
- 右下角「+」添加课程；点课程卡片查看详情、编辑或删除（调休补课日会注明日期与原因）

### 今日页

- 「下一节课」大卡片：课程名、节次时间、教室、教师，以及倒计时（还有 X 小时 Y 分钟 / 正在上课）
- 今天没课时自动向后预告最近一次课（明天/周X），并自动跳过法定节假日
- 今日课程时间线，标注「待上课 / 进行中 / 已结束」，每 30 秒自动刷新

### 课程页

- 全部课程按星期、节次排序，彩色条标识课程颜色
- 显示周次规则（每周 / 单周 / 双周 / 自定义周）、教室、教师
- 顶部横幅提示最近一次放假 / 调休安排
- 点击编辑，长按删除

### 添加 / 编辑课程

- 课程名、教师、教室
- 星期七选一、开始节次（1~11）、连排节数（1~4），实时显示上课起止时间
- 周次规则：每周 / 单周 / 双周 / 自定义（20 周网格多选），并可设置起止周区间
- 10 种卡片颜色自选

### 上课提醒通知

- 每节课上课前按设定时间（5/10/15/20/30/60 分钟可选，默认 15 分钟）推送系统通知
- 通知点击直接打开 App；使用 AlarmManager 精准闹钟（Android 12+ 自动申请/降级）
- 重启手机、更新应用、修改系统时间后自动重建提醒
- Android 13+ 首次启动申请通知权限；「我的」页可随时授权、跳转设置、发送测试通知
- 整学期闹钟重排在后台线程执行，不再阻塞主线程

### 我的（设置）

- 学期名称、开学日期（第 1 周周日，决定当前周次自动计算）、学期总周数（**默认 16 周**，可选 16/18/20/24）、学期结束日期
- 节假日与调休：停课开关、调休上课开关、本学期放假与调休清单（可修改每个调休日按周几上课）
- 提醒开关与提前量、通知权限状态、测试通知
- 关于与更新：当前版本号、手动「检查更新」、启动时自动检查开关
- 一键恢复示例课表 / 清空全部课程

## 默认作息时间（可在代码 `util/TimeTable.java` 修改）

| 节次 | 起 | 止 | 节次 | 起 | 止 |
|---|---|---|---|---|---|
| 1 | 08:00 | 08:45 | 7 | 16:10 | 16:55 |
| 2 | 08:55 | 09:40 | 8 | 16:55 | 17:40 |
| 3 | 10:00 | 10:45 | 9 | 19:00 | 19:45 |
| 4 | 10:55 | 11:40 | 10 | 19:55 | 20:40 |
| 5 | 14:30 | 15:15 | 11 | 20:50 | 21:35 |
| 6 | 15:15 | 16:00 | | | |

首次启动会载入一套与参考截图一致的示例课表（开学日期默认 2026-09-13，第 1 周，共 15 门课），其中双周课在第 1 周置灰；可在「我的」里清空后录入自己的课表。

> **示例课表里没有周六 / 周日的课**，这是刻意的：周末本来就没课。
> 参考截图里周末出现的「人工智能导论 / 模式识别 / 企业法律风险管理」是**调休当周被误录的重复项**
> （课程名、教室、节次与周一 / 周三的课完全一一对应），现在调休改由节假日引擎自动推导，不需要也不应该写进课表。
> 对应地，示例课程的周次区间也统一为 `1-16`，与默认学期周数一致。

## 安装方法

1. 把 `大学课表_v1.4.apk` 传到安卓手机（或直接从 GitHub 仓库 `apk/` 目录下载）
2. 文件管理器点击安装，首次需允许「安装未知来源应用」
3. 打开后允许通知权限；国产 ROM（小米/华为/OPPO/vivo 等）建议在系统设置中允许本应用「自启动」与「通知」，以保证课前提醒在后台准时触发
4. 想用应用内自动更新，还需在系统设置里为本应用打开「安装未知应用」——首次点更新时应用会引导你去授权

## 源码构建

环境：JDK 11 / 17、Android SDK Platform 33、Build-Tools 33.0.2、Gradle 7.6.2、AGP 7.4.2

```bash
# 写入 local.properties 指向 SDK
echo "sdk.dir=/path/to/sdk" > local.properties
# Debug 构建
gradle assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
# 运行测试
gradle testDebugUnitTest
```

> 工程目录含中文时，`gradle.properties` 已加入 `android.overridePathCheck=true`，
> `app/build.gradle` 的 `testOptions` 已强制测试进程使用 UTF-8，否则单元测试会因
> 类路径编码问题报 `ClassNotFoundException`。
> 另外该编码问题会让 Gradle 测试 Worker 完全找不到测试类，**更可靠的做法是把工程复制到
> 纯 ASCII 路径再跑测试**。

### 推送代码到 GitHub

到 `github.com:22` 的 SSH 连接在本机常被干扰（握手成功后数据通道被 RST，
表现为 `send-pack: unexpected disconnect`）。仓库已配置 `core.sshCommand`，
让 SSH 走本机 HTTP 代理 + `ssh.github.com:443`：

```bash
git config core.sshCommand \
  'ssh -o HostName=ssh.github.com -o Port=443 -o "ProxyCommand=python C:/Users/<你>/.workbuddy-ai/tools/ssh_proxy.py %h %p"'
```

`ssh_proxy.py` 是一个把 stdio 接到 HTTP `CONNECT` 隧道的小脚本。
换机器 / 换代理端口时改这里即可（代理地址也可用 `SSH_PROXY_HOST` / `SSH_PROXY_PORT` 环境变量覆盖）。
推送大文件（如 apk）偶尔仍会中断，**重试一次即可成功**。

## 目录结构

```
app/src/main/java/com/xwt/schedule/
├─ App.java                     # Application，建通知渠道
├─ MainActivity.java            # 底部导航 + 四个页面（hide/show + commitNow）
├─ model/Course.java            # 课程模型与周次规则、JSON 序列化
├─ data/CourseStore.java        # SharedPreferences 存储 + 示例课表 + 节假日/调休设置
├─ util/TimeTable.java          # 11 节作息时间
├─ util/WeekUtil.java           # 学期/周次/日期换算
├─ util/ChinaHoliday.java       # 中国法定节假日与调休数据（官方通知）
├─ util/DayPlan.java            # 结合用户设置算出「某天上不上课、按周几上课」
├─ util/Palette.java            # 课程卡片配色
├─ ui/ScheduleGridView.java     # 课表网格自定义 View（休/补角标、不放假列不加灰底）
├─ ui/ScheduleFragment.java     # 课表页
├─ ui/TodayFragment.java        # 今日页
├─ ui/CourseListFragment.java   # 课程列表页
├─ ui/SettingsFragment.java     # 我的/设置页（含节假日与调休、检查更新）
├─ ui/UpdateDialogs.java        # 更新提示 / 下载进度 / 授权引导对话框
├─ ui/CourseEditActivity.java   # 添加/编辑课程
├─ ui/WeekPickerDialog.java     # 周次选择器
├─ update/UpdateInfo.java       # update.json 解析与版本比较（可单测）
├─ update/UpdateChecker.java    # 读取远端清单，多镜像依次尝试
├─ update/UpdateInstaller.java  # 下载 apk（多镜像重试）并拉起安装器
└─ notify/                      # 通知渠道、AlarmManager 调度、开机重建

app/src/test/java/com/xwt/schedule/
├─ CourseLogicTest.java         # 周次规则、作息时间纯 JVM 单测
├─ HolidayLogicTest.java        # 节假日/调休数据纯 JVM 单测
├─ UpdateLogicTest.java         # 更新清单解析、坏数据容错、镜像地址
└─ AppRobolectricTest.java      # 界面与业务集成测试（Robolectric）

update.json                     # 自动更新的版本清单（发布新版本时改这里）
apk/                            # 各版本安装包，供应用内下载
```

## v1.1 变更

**问题修复**

1. **「我的」Tab 点击无反应（根因）**：`fragment_settings.xml` 里的
   `MaterialSwitch` 没有声明 `android:textOn` / `android:textOff`，在
   `Theme.MaterialComponents` 下这两个属性解析为 `null`，
   `SwitchCompat.makeLayout()` 会拿 null 去构造 `StaticLayout` 抛 `NullPointerException`。
   由于被 `hide()` 的 Fragment 视图是 `GONE`、`ViewGroup` 不会测量 `GONE` 子视图，
   这个崩溃只在「我的」页第一次可见（也就是点下 Tab 的那一刻）触发，页面因此画不出来，
   表现为点击无反应。已为全部开关显式补上空字符串文本，并在测试里加了
   「设置页必须能完成 measure/layout」的回归防护。
2. `MainActivity` 改为一次性 `add` + `hide/show` + `commitNow()` 同步切换；
   页面按 tag 复用，配置变更 / 进程恢复后不再重复 `add` 出多份页面；非当前页限制为
   `STARTED` 状态，避免隐藏页仍在后台跑刷新；补上重复点击已选中 Tab 的处理。
3. 整学期闹钟重排从主线程移到后台线程，避免启动 / 返回前台时卡顿。
4. 课表卡片在列宽真正确定后（含旋转、分屏）会重新计算布局，不再沿用首次估算宽度。
5. 工程目录含中文时无法构建、单元测试报 `ClassNotFoundException` 的问题
   （`android.overridePathCheck` + 测试进程强制 UTF-8）。

**新功能**

6. 中国法定节假日与调休适配（见上文「节假日与调休适配」）。
7. 「我的」页显示学期结束日期、本学期放假 / 调休天数统计，并可逐日调整调休补课安排。

## v1.2 变更

**修正**

1. **去掉放假列的灰底**：周六、周日以及法定节假日没有课是常态，原先给放假列整列加淡灰底
   会把"本来就没课"渲染成"异常状态"。现已移除该底色，节假日 / 调休信息只保留表头的
   「休 / 补」角标与日期颜色（放假红、调休蓝）。
2. **学期总周数默认 20 周改为 16 周**（`CourseStore.DEFAULT_TOTAL_WEEKS`）。
   注意：如果之前手动改过周数，本机已保存的设置优先，需要在「我的」页里重新选一次。

**新功能**

3. **基于 GitHub 仓库的应用内自动更新**（见上文「应用内自动更新」）：
   读取仓库根目录 `update.json`，比较 `versionCode`，应用内下载 apk 并经 `FileProvider`
   拉起系统安装器；支持多镜像按顺序重试、忽略指定版本、关闭自动检查。
   新增 `INTERNET` / `ACCESS_NETWORK_STATE` / `REQUEST_INSTALL_PACKAGES` 权限。

**测试**

4. 新增 `UpdateLogicTest`（9 项）：覆盖清单解析、缺字段 / 非法 JSON 的容错、
   版本比较、多镜像地址顺序。合计 **36 项测试全部通过**。

## v1.4 变更

**修正**

1. **示例课表删除 5 门被误录的周末课**。原来的示例数据里，周六 / 周日各有几门双周课，
   经比对它们与工作日课程完全同课同教室（见下表），是**调休当周照抄截图产生的重复项**；
   调休现在已由节假日引擎按国家规范自动推导，这些课留着会在 16 周里除调休外的
   普通周末显示并不存在的课。已全部删除，并在测试里加了「周末两列不得出现任何课程卡片」
   的回归防护。

   | 被删掉的周末课 | 对应的正常课 |
   | --- | --- |
   | 周日 3-4 人工智能导论 @复302（双周） | 周一 3-4 人工智能导论 @复302（每周） |
   | 周日 5-6 模式识别 @复302（双周） | 周一 5-6 模式识别 @复302（每周） |
   | 周六 3-4 模式识别 @复302（双周） | 周三 3-4 模式识别 @复302（每周） |
   | 周六 5-6 人工智能导论 @复302（双周） | 周三 5-6 人工智能导论 @复302（每周） |
   | 周六 7-8 企业法律风险管理 @中119（双周） | 周三 7-8 企业法律风险管理 @中119（每周） |

   示例课表由 20 门变为 **15 门**（全部落在周一~周五）。

2. **课程周次区间与学期周数统一为 16 周**。原先 `Course.weekEnd` 的默认值、JSON 反序列化
   兜底值、示例数据都还写死 `20`，与 v1.2 定下的 16 周学期不一致。现统一引用单一常量
   `Course.DEFAULT_WEEK_END = 16`（`CourseStore.DEFAULT_TOTAL_WEEKS` 也由它派生）。

3. 保留放假列的「节日名 / 放假」水印。它只画在**法定节假日**那一列，作用是区分
   「放假」和「本来就没课」这两种不同状态，与 v1.2 移除的整列灰底不是一回事；
   周六 / 周日永远不会出现该水印。

4. **更新检查不再被镜像的旧缓存骗到**。原来 `UpdateChecker` 是"取第一个成功的镜像"，
   而 jsDelivr 对分支引用最长缓存约 12 小时；实测推送后立刻请求，
   jsDelivr 返回 `versionCode=3`（旧）而 raw 返回 `4`（新）。现在改为
   **并行拉取所有镜像 → 取 `versionCode` 最大的那份**，两个镜像互相纠正。
   发布流程里另外补了一步 jsDelivr purge（见上文「清单缓存」）。

**测试**

5. 新增 `weekendsNeverShowCourses`：断言第 1 周（无节假日、无调休）渲染出的卡片
   覆盖的星期集合恰好是 {周一…周五}，且总数为 15。
6. `UpdateLogicTest` 新增 4 项覆盖多镜像选优：镜像互相矛盾时取新、与顺序无关、
   部分镜像挂掉（返回 `null`）时仍能用活着的那份、缓存旧版的镜像不影响判定。
   合计 **41 项测试全部通过**。

> 单元测试注意：`org.json` 属于 Android 框架，普通本地单测里只有会抛 "not mocked" 的空壳，
> 所以 `UpdateLogicTest` 用 Robolectric 运行（与 `AppRobolectricTest` 一致）。
