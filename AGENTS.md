基于GTNH 2.9.0 正式版
测试实例：D:\mc\Prism-GTNH\instances\GTNH-2.9.0-RC-2\.minecraft
可从gtnh中英文wiki查询资料 gtnh.huijiwiki.com

详见`docs/需求文档.md`

## Agent skills

### Issue tracker

Issue 和 spec 记录在 GitHub Issues（`rt-game-440/gtnh-warp-covers`），使用 `gh` CLI 操作。见 `docs/agents/issue-tracker.md`。

### Triage labels

使用默认的 5 个分诊标签：`needs-triage`、`needs-info`、`ready-for-agent`、`ready-for-human`、`wontfix`。见 `docs/agents/triage-labels.md`。

### Domain docs

单上下文布局：根目录 `GLOSSARY.md` + `docs/adr/`。见 `docs/agents/domain.md`。

## 构建、测试与自检入口

- 构建：`./gradlew build`（产物在 `build/libs`）；开发客户端 `./gradlew runClient`，开发服务端 `./gradlew runServer`（工作目录 `run/server`）。
- 单元测试（JUnit 5）：`./scripts/test.ps1`，可追加 gradle 参数，如 `--tests "*SelfCheck*"`。
- 服务器自检：`./scripts/selfcheck.ps1 [-Scenario gt_hull_smoke]`。自动启动开发服务器 → 布置 → 停服 → 重启 → 校验，退出码 0 为通过。
  - 自检命令 `/warpcovers_selfcheck <setup|verify> <场景>` 仅开发环境注册，发布版不含；结果写入项目 `logs/yyyy-MM-dd HH-mm-ss.log` 的 `[RESULT]` 行。
  - 截图辅助：开发客户端里 `/warpcovers_selfcheck gui <x> <y> <z> <面>` 直接打开该面覆盖板的界面（等同蹲下右键）。
  - 新场景实现 `SelfCheckScenario` 并在 `CommonProxy.serverStarting` 登记。
- 主代码与测试代码只能使用 Java 8 API（Jabel 仅提供新语法），例如不能用 `List.of`、`Files.writeString`。
- 模组 ID `warpcovers`，包名 `com.rtgame.warpcovers`；GT5-Unofficial 为硬依赖，ServerUtilities 为可选依赖。
