# 测试验收报告

测试环境使用专用 `campus_card_test`；HTTP 集成测试在 Spring context 创建前要求 `CC_DB_*` 与 `CC_TEST_DB_*` 一致，并拒绝数据库名不以 `_test` 结尾的连接。浏览器测试只访问独立的 `127.0.0.1:8089`，服务进程也映射到相同测试库。正式运行库 `campus_card` 与正式服务 `8088` 未被测试写入。

## 接口覆盖

| # | 方法与路径 | 实际覆盖断言 | 测试方法 |
|---:|---|---|---|
| 1 | `GET /api/auth/csrf` | 登录 session cookie 与 CSRF token 的关联、刷新及登录后的 token 更新 | `csrfCookieLoginSessionRotationAndStringMoneyAreEnforced`、`failedLoginSessionReplacementAndCurrentLogoutAreMappedAtHttpBoundary` |
| 2 | `POST /api/auth/login` | 成功/失败登录、角色加载、登录轮换、停用账户拒绝；浏览器错误提示与学生/管理员切换 | `csrfCookieLoginSessionRotationAndStringMoneyAreEnforced`、`failedLoginSessionReplacementAndCurrentLogoutAreMappedAtHttpBoundary`、`roleOwnershipIdempotenceAndDisabledCoreSessionsAreEnforced`；E2E 学生登录与角色切换、退出后重登录 |
| 3 | `GET /api/auth/me` | 匿名/有效/退出后/失效 session 的状态，用户资料无密码哈希；错误旧密码后仍有效 | `csrfCookieLoginSessionRotationAndStringMoneyAreEnforced`、`failedLoginSessionReplacementAndCurrentLogoutAreMappedAtHttpBoundary`、`passwordChangeKeepsCurrentSessionAndRevokesOtherSessionsButAllowsLogout` |
| 4 | `POST /api/auth/password` | 错误旧密码为 401 `LOGIN_FAILED` 且当前 session 保持有效；改密保留当前 session 并撤销其它 session | `passwordChangeKeepsCurrentSessionAndRevokesOtherSessionsButAllowsLogout`；E2E 设置页错误旧密码、成功改密、旧密码失败/新密码成功及旧 session 过期 |
| 5 | `POST /api/auth/logout` | 正常退出、失效 session 仍可退出；退出网络不确定时刷新后保持待确认，重试成功才允许新账号登录 | `failedLoginSessionReplacementAndCurrentLogoutAreMappedAtHttpBoundary`、`passwordChangeKeepsCurrentSessionAndRevokesOtherSessionsButAllowsLogout`；E2E `logout stays pending across reload until the server confirms it` |
| 6 | `GET /api/card` | 有卡/余额查询、无权限及跨用户隔离 | `cardHttpValidatesMoneyKeepsBalanceAtomicAndScopesOperationLookup`、`roleOwnershipIdempotenceAndDisabledCoreSessionsAreEnforced`；学生 dashboard/wallet E2E |
| 7 | `POST /api/card/recharge` | 合法金额、精度/零/负数/上限/格式校验、操作号幂等/冲突；提交后 502 通过同操作号查询恢复 | `cardHttpValidatesMoneyKeepsBalanceAtomicAndScopesOperationLookup`、`transactionMaximumPageAndFullExportAreSortedEscapedAndNotTruncated`；Vitest API session/不确定响应；真实 DB E2E 代理 502 后刷新、查询恢复且只产生一笔流水 |
| 8 | `POST /api/card/consume` | 余额足够与不足、金额校验、并发防透支、操作号幂等/冲突 | `cardHttpValidatesMoneyKeepsBalanceAtomicAndScopesOperationLookup`、`roleOwnershipIdempotenceAndDisabledCoreSessionsAreEnforced`；E2E 余额不足与金额精度错误 |
| 9 | `GET /api/card/operations/{operationId}` | 存在/不存在/他人操作号查询及冲突保护 | `cardHttpValidatesMoneyKeepsBalanceAtomicAndScopesOperationLookup` |
| 10 | `GET /api/transactions` | 角色与用户隔离、类型/用户/日期筛选、结束日包含、排序、分页上限及错误参数 | `adminCanReadAllUsersButUserDataNeverLeaksAcrossQueries`、`transactionPagingFiltersCsvBomAndMalformedParametersAreEnforced`、`transactionMaximumPageAndFullExportAreSortedEscapedAndNotTruncated`；学生/管理员页面按类型/用户/日期筛选 |
| 11 | `GET /api/transactions/export` | BOM、完整分页导出、筛选条件、CSV 字段转义及公式注入防护 | `transactionPagingFiltersCsvBomAndMalformedParametersAreEnforced`、`transactionMaximumPageAndFullExportAreSortedEscapedAndNotTruncated`；学生与管理员 E2E 保存文件并核对 BOM、筛选内容 |
| 12 | `GET /api/admin/users` | 管理员分页、关键字/状态筛选；学生禁止读取管理数据 | `adminCanReadAllUsersButUserDataNeverLeaksAcrossQueries`、`adminHttpLifecycleEnforcesUniquenessAndPreservesDisabledCardHistory`；管理员页面搜索用户 E2E |
| 13 | `POST /api/admin/users` | 创建学生和卡、重复用户名/学号拒绝、开户事务回滚 | `adminHttpLifecycleEnforcesUniquenessAndPreservesDisabledCardHistory`；核心测试 `adminCreatesAtomicallyAndEnforcesUniqueUsernameAndStudentNumber`、`failedCardInsertRollsBackTheNewUser`；浏览器创建新用户后登录并交易 |
| 14 | `PUT /api/admin/users/{id}` | 修改资料；重复学号返回 `409 DUPLICATE` 且姓名/学号回滚不变；管理员账户不可被普通资料接口修改 | `adminHttpLifecycleEnforcesUniquenessAndPreservesDisabledCardHistory`；核心测试 `adminCanEditUsersFilterByEnabledStateButCannotEditOrDisableAdmin`；管理员编辑资料 E2E |
| 15 | `PATCH /api/admin/users/{id}/enabled` | 停用/启用、停用后旧 session 交易拒绝、交易历史保留、管理员不能停用 | `adminHttpLifecycleEnforcesUniquenessAndPreservesDisabledCardHistory`、`roleOwnershipIdempotenceAndDisabledCoreSessionsAreEnforced`；管理员状态筛选和切换 E2E |
| 16 | `GET /api/admin/statistics` | 用户数、启用数、含停用卡的总余额、日期区间交易统计、日期反转错误和非管理员拒绝 | `adminStatisticsHttpUsesAllStudentBalancesAndInclusiveFilteredFlows`；核心测试 `statisticsExcludeAdminsAndDateRangeOnlyChangesTransactionTotals`；管理员统计页与同日真实 HTTP 返回逐项比较、未来日期归零 |
| 17 | `GET /api/dashboard` | 学生自有数据/管理员汇总、趋势补零与含端点日期、默认 7 天及 1/90/91/非法天数边界 | `dashboardFiltersByRoleAndReturnsAnInclusiveZeroFilledTrend` |
| 18 | `GET /api/health` | 数据库正常返回 UP；独立离线测试进程连接不可达数据库返回 503 `DATABASE_ERROR` | `dashboardFiltersByRoleAndReturnsAnInclusiveZeroFilledTrend`、`healthEndpointReportsDatabaseErrorFromAnIsolatedOfflineTestServer` |

前端接口边界 Vitest 还验证 session epoch 变化时取消待写/CSRF 重试、旧请求不能结束新会话、成功响应 JSON 截断标记为网络不确定、提交后模拟非 JSON 502 后按原 operationId 查询且不再写入，以及 `LOGIN_FAILED` 不触发 session-expired 事件而 `AUTH_REQUIRED` 会触发。

核心服务测试的数据库断言覆盖 `100 + 50 - 30 = 120`、金额溢出/精度/零/负数、消费余额不足、流水插入失败回滚余额、开户卡写入失败回滚新用户、两个并发 80 元消费只允许一个成功、同一 UUID 幂等及冲突 UUID 不改余额、停用旧 session 交易拒绝、用户/管理员隔离、空筛选分页/日期结束日、CSV 公式转义、统计和数据库 provider 不存在时异常翻译。

## 页面与核心业务验收

| 页面/流程 | 实际覆盖 |
|---|---|
| 登录与会话 | 错误密码提示；学生/管理员菜单切换；密码修改导致的旧 session 过期；退出 POST 被故障代理拦截、刷新后仍显示待确认并禁用登录、重试获得服务端确认后再允许登录 |
| 学生总览 | 学生欢迎信息、余额卡、390px 宽度卡片占比、移动抽屉初始关闭/打开导航/关闭，以及 1280px 桌面布局和横向溢出检查 |
| 校园卡 | 实际充值/消费；0.001 精度错误和余额不足；真实充值先提交到 MySQL 后模拟 502，刷新后仍保留同一用户、操作号、金额/说明且待查询，不自动再次提交，查询恢复后只显示一笔该操作流水 |
| 学生交易记录 | 类型筛选、消费记录与充值隔离、CSV 下载文件 BOM 及筛选内容 |
| 账户设置 | 错误旧密码提示且不退出（随后 `/me` 仍 200）；新建用户成功改密、当前 session 保留、其它 session 失效、旧密码拒绝/新密码登录成功 |
| 管理员用户管理 | 创建、搜索、编辑、停用/启用与状态筛选；用户创建弹窗初始焦点、Shift+Tab/Tab 环回、Escape 关闭及焦点恢复；390px 无横向溢出 |
| 管理员交易记录 | 对新建学生按 ID、类型和含结束日期范围筛选真实交易，下载 CSV 并核对 BOM 和筛选结果 |
| 管理员运营统计 | 今日统计数字及充值/消费金额逐项对比同日期真实 API 响应；未来日期零交易；反向日期显示校验错误 |
| SPA 路由 | Java HTTP 验证 `/`、登录、dashboard、wallet、transactions、users、statistics、settings 所有静态入口可刷新，未知静态资源和未知 API 为 404 |

## 运行结果

| 套件 | 实际结果 | 证据 |
|---|---:|---|
| Maven Java 测试 | 32 通过，0 失败，0 错误，0 跳过；含 14 backend、5 Swing frontend、13 server HTTP 测试；最新 `mvn test` 含 PUT 重复学号回滚断言 | `local/final-clean-verify.log`、`local/final-maven-test.log` |
| Vitest | 10 通过，0 失败，0 跳过 | `local/final-vitest.log` |
| Playwright E2E | 4 通过，0 失败，0 跳过 | `local/web-e2e-final-expanded.log` |

三组最终验收合计 46 项：32 Java + 10 Vitest + 4 Playwright，全部通过，失败/错误/跳过均为 0。Java 离线 health 测试从 Surefire 实际 runtime classpath 启动 Spring 子进程，因此 `clean verify` 无需已有 server JAR。Playwright 使用 `JAVA_HOME` 下的 Java 启动独立 8089 server。最终运行均配置隔离测试库，未跳过。

本轮覆盖四项前端回归：错误旧密码 401 被误当作 session 过期；非 JSON 5xx 交易响应会丢弃待确认操作号；退出网络失败后 UI 可能误显示已退出并允许新账号登录；提交成功但响应不确定后刷新丢失操作号。分别由 API/Vitest、真实数据库代理 502 后刷新恢复、退出失败跨刷新重试 E2E 和 store 存储单测验证。
