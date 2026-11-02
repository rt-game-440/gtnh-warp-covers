# Issue tracker：GitHub

本仓库的 issue 和 spec 存放在 GitHub Issues（`rt-game-440/gtnh-warp-covers`），所有操作都使用 `gh` CLI。

## 常用操作

- **创建 issue**：`gh issue create --title "..." --body "..."`。多行正文用 heredoc（PowerShell 下用单引号 here-string `@'...'@`）。
- **读取 issue**：`gh issue view <number> --json number,title,body,labels,comments`。
- **列出 issue**：`gh issue list --state open --json number,title,body,labels,comments --jq '[.[] | {number, title, body, labels: [.labels[].name], comments: [.comments[].body]}]'`，按需加 `--label`、`--state` 过滤。
- **设为父 issue 的子 issue**：`gh issue create --parent <parent> ...`，或事后执行 `gh issue edit <parent> --add-sub-issue <child>`（`gh` 2.94+）。旧版 `gh`：`gh api --method POST repos/<owner>/<repo>/issues/<parent>/sub_issues -F sub_issue_id=<child-db-id>`（使用数据库 id，见下方 **阻塞关系**）。不支持子 issue 时，在子 issue 正文顶部写 `Part of #<parent>`。
- **评论**：`gh issue comment <number> --body "..."`
- **添加 / 移除标签**：`gh issue edit <number> --add-label "..."` / `--remove-label "..."`
- **关闭**：`gh issue close <number> --comment "..."`

仓库从 `git remote -v` 推断；在本地克隆内运行时 `gh` 会自动识别。

## 把 PR 当作需求入口

**PRs as a request surface: no.** _（如果本仓库把外部 PR 视为功能请求，改为 `yes`；`/triage` 会读取这个开关。）_

设为 `yes` 时，PR 与 issue 走同一套标签和状态，使用对应的 `gh pr` 命令：

- **读取 PR**：`gh pr view <number> --comments`，diff 用 `gh pr diff <number>`。
- **列出待分诊的外部 PR**：`gh api --paginate 'repos/{owner}/{repo}/pulls?state=open' --jq '.[] | select(.author_association | IN("OWNER","MEMBER","COLLABORATOR") | not) | {number, title, author: .user.login, author_association, labels: [.labels[].name]}'`。
- **评论 / 打标签 / 关闭**：`gh pr comment`、`gh pr edit --add-label`/`--remove-label`、`gh pr close`。

GitHub 的 issue 和 PR 共用一套编号，单独的 `#42` 可能是两者之一：先试 `gh pr view 42`，失败再用 `gh issue view 42`。

## 当技能要求 "publish to the issue tracker"（发布到 issue 跟踪）

创建一个 GitHub issue。

## 当技能要求 "fetch the relevant ticket"（获取相关工单）

按上方 **读取 issue** 的方式读取。

## Wayfinding 操作

供 `/wayfinder` 使用。**map** 是一个 issue，各个 **child**（子工单）是它的子 issue。

- **Map**：一个带 `wayfinder:map` 标签的 issue，正文包含 Notes / Decisions-so-far / Fog 三部分。`gh issue create --label wayfinder:map`。
- **子工单**：作为 GitHub 子 issue 关联到 map（见 **设为父 issue 的子 issue**）。未启用子 issue 时，把子工单加入 map 正文的任务列表，并在子工单正文顶部写 `Part of #<map>`。标签：`wayfinder:<type>`（`research`/`prototype`/`grilling`/`task`）。认领后指派给当前开发者。
- **阻塞关系**：使用 GitHub **原生 issue 依赖**，这是界面可见的标准表示。添加依赖：`gh api --method POST repos/<owner>/<repo>/issues/<child>/dependencies/blocked_by -F issue_id=<blocker-db-id>`，其中 `<blocker-db-id>` 是阻塞方的数字**数据库 id**（`gh api repos/<owner>/<repo>/issues/<n> --jq .id`，_不是_ `#number` 或 `node_id`）。GitHub 用 `issue_dependencies_summary.blocked_by` 报告阻塞数（只统计未关闭的阻塞方，即实时门槛）。不支持依赖时，退回到在子工单正文顶部写 `Blocked by: #<n>, #<n>`。所有阻塞方关闭后，工单即解除阻塞。
- **前沿查询**：列出 map 下未关闭的子工单（`gh issue list --state open`，限定在 map 的子 issue / 任务列表范围内），去掉有未关闭阻塞方（`issue_dependencies_summary.blocked_by > 0`，或 `Blocked by` 行里有未关闭 issue）或已有指派人的，按 map 顺序取第一个。
- **认领**：`gh issue edit <n> --add-assignee @me`，作为本次会话的第一个写操作。
- **解决**：`gh issue comment <n> --body "<answer>"`，然后 `gh issue close <n>`，再把上下文指针（要点 + 链接）追加到 map 的 Decisions-so-far。
