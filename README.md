# 校园卡管理系统

Java 17 + Swing + JDBC DAO + MySQL 8.0 的桌面应用，包含用户与管理员两个入口。

## 功能

- 用户：登录、余额查询、模拟充值与消费、记录筛选分页、CSV 导出、修改密码。
- 管理员：开户、用户资料修改与启停、全站交易查询、余额及期间充值消费统计。
- 同一事务更新余额和流水，行锁避免并发超额消费，操作编号避免重复提交。

## 运行

需要 JDK 17、Maven 3.9 和 MySQL 8.0。Windows 脚本优先读取 PATH 及 JAVA_HOME/MAVEN_HOME/MYSQL_HOME，同时支持本机 E 盘已有安装位置。

1. 初始化独立数据库：`powershell -ExecutionPolicy Bypass -File scripts/init-db.ps1 -Seed`。有密码的数据库账号加 `-PromptPassword`；可指定 `-HostName`、`-Port`、`-User`、`-Database`。
2. 复制 `config/application.example.properties` 为 `config/application.properties`，填写应用数据库账号、密码及 URL。也可使用环境变量 `CC_DB_URL`、`CC_DB_USER`、`CC_DB_PASSWORD`，其优先级更高。
3. 构建：`powershell -ExecutionPolicy Bypass -File scripts/build.ps1`。
4. 连接检查：`powershell -ExecutionPolicy Bypass -File scripts/start.ps1 -CheckDatabase`。
5. 启动：`powershell -ExecutionPolicy Bypass -File scripts/start.ps1`。

本机网络若无法访问 Maven Central，可使用 `scripts/build.ps1 -UseMirror`，或 Maven 加 `-s config/maven-settings-mirror.xml`，显式选择阿里云镜像。

跨平台可执行 `mvn -B package`，再从项目根目录执行 `java -Dfile.encoding=UTF-8 -jar frontend/target/campus-card-system.jar`。配置文件支持 `-Dcampus.config=/absolute/path/application.properties`。

显式导入种子数据后，示例管理员为 `admin / admin123`，用户为 `student / student123`，学生初始余额为 100 元并存在对应充值流水。种子仅用于本地功能体验；充值消费不接入真实支付。管理员通过用户管理创建更多普通用户。

## 结构

`common` 定义模型和公共接口；`backend` 实现业务、安全及 JDBC DAO；`frontend` 提供 Swing 界面；`database` 提供幂等建表与可选种子脚本。管理员账号不持有校园卡，普通用户一人一卡，停用保留流水。

金额使用 BigDecimal/DECIMAL(12,2)，最多两位小数。日期按上海时区处理，结束日期包含当天。统计用户数仅包含普通用户，总余额包含停用用户，日期范围只限制交易统计。

提交失败且结果未确认时请使用原操作的恢复/重试入口，避免重复发起新交易。界面重新登录后不会展示旧会话异步结果。

## 测试

`mvn -B verify` 执行单元测试与 Swing 会话测试。真实 MySQL 集成测试需设置：

```powershell
$env:CC_TEST_DB_URL = 'jdbc:mysql://127.0.0.1:3306/campus_card_test?connectionTimeZone=Asia/Shanghai&allowPublicKeyRetrieval=true'
$env:CC_TEST_DB_USER = 'your-test-user'
$env:CC_TEST_DB_PASSWORD = 'your-test-password'
mvn -B verify
```

先使用 `scripts/init-db.ps1 -Database campus_card_test` 创建测试库，并给予测试账号建表、修改与清理测试数据的权限。测试会自动应用 schema 和 seed，并重建测试夹具。数据库名必须为 `campus_card_test` 或以 `_test` 结尾，禁止指向正常运行数据库。未设置测试 URL 时数据库集成测试跳过；配置后连接失败必须使测试失败。

Linux 无桌面环境时使用 `xvfb-run -a mvn -B verify`。GitHub Actions 提供独立 MySQL 8.0 服务，并上传可运行 JAR，进入仓库 Actions 的成功任务后可下载 `campus-card-system` artifact。测试报告位于各模块 `target/surefire-reports/`。

本机已配置独立运行库和测试库；测试凭据保存在被忽略的 `local/test-env.ps1`，可执行 `. ./local/test-env.ps1` 后运行验证。

## 协作

四个编码子智能体分别只负责数据持久化、业务逻辑、Swing 界面和测试代码；统筹负责公共契约、装配与审查。目录所有权及接口约定见 CONTRACTS.md。

本地数据库密码、配置文件、日志和构建缓存均被 Git 忽略。可运行 JAR 位于 `frontend/target/campus-card-system.jar`。
