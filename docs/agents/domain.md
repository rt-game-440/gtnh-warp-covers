# Domain Docs（领域文档）

规定各工程技能在探索代码库时如何使用本仓库的领域文档。本仓库为**单上下文**布局。

## 探索前先读

- 根目录的 **`GLOSSARY.md`**；若根目录存在 **`GLOSSARY-MAP.md`**，它会指向各上下文的 `GLOSSARY.md`，读取与当前主题相关的那些。
- **`docs/adr/`**：读取与即将改动区域相关的 ADR。多上下文仓库还要检查 `src/<context>/docs/adr/` 下的上下文级决策。

上述文件不存在时**直接继续，不作提示**：不要指出它们缺失，也不要预先建议创建。`/domain-modeling` 技能（经由 `/grill-with-docs` 和 `/improve-codebase-architecture` 触发）会在术语或决策真正确定时按需创建。

## 文件结构

单上下文仓库（本仓库）：

```
/
├── GLOSSARY.md
├── docs/adr/
│   ├── 0001-event-sourced-orders.md
│   └── 0002-postgres-for-write-model.md
└── src/
```

多上下文仓库（根目录存在 `GLOSSARY-MAP.md`）：

```
/
├── GLOSSARY-MAP.md
├── docs/adr/                          ← 系统级决策
└── src/
    ├── ordering/
    │   ├── GLOSSARY.md
    │   └── docs/adr/                  ← 上下文级决策
    └── billing/
        ├── GLOSSARY.md
        └── docs/adr/
```

## 使用术语表中的词汇

输出中提到领域概念时（issue 标题、重构提案、假设、测试名），使用 `GLOSSARY.md` 中定义的术语，不要换成术语表明确避免的同义词。

如果需要的概念还不在术语表中，说明：要么你在发明项目不用的说法（请重新考虑），要么确实存在缺口（记下来交给 `/domain-modeling`）。

## 标出与 ADR 的冲突

如果输出与现有 ADR 相矛盾，要明确指出，而不是悄悄推翻：

> _与 ADR-0007（event-sourced orders）相矛盾，但值得重新讨论，因为……_
