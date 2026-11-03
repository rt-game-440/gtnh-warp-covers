# 开发记忆

- 2026-11-03 14:20 GTNH 的 Hodgepodge（MixinChunkProviderServer_FastUnload 覆写 `unloadQueuedChunks`）会把加载时尚未填充的新区块及其 8 邻保护 600 tick 不卸载；自检里在同一 tick 卸载区块前要反射调 `ChunkGenScheduler.forDimension(dim).onChunkUnload(chunkXZ2Int)` 解除保护（见 `selfcheck/ScenarioWorld.unloadChunk`）。只在区块首次生成时复现，磁盘上已有区块不触发。
- 2026-11-03 14:20 GT 机器读档后 `mTickTimer` 同样从 0 开始，重启后的自检校验也要先 `warmUp` 再 fill/drain。
- 2026-11-03 14:20 覆盖板配置随物品走：`Cover.asItemStack()` 被 `detachCover` 与掉落使用（`GTOreDictUnificator.get` 保留 NBT），放置时构造函数里 `CoverContext.getCoverItem()` 就是手持的带 NBT 物品；但随机器物品重放（`buildCoverFromNbt`）走 `readFromNbt`，没有 `onPlayerAttach`，连接者用机器归属者兜底。`allowsCopyPasteTool` 要关掉，否则会复制连接端 ID。
- 2026-11-03 14:20 项目里有的文件是 CRLF、有的是 LF，PowerShell 多行 `.Replace` 容易静默不匹配；改文件优先用 Edit 或整体 Write。

- 2026-11-03 12:14 GT 机器（BaseMetaTileEntity）刚放置时 `mTickTimer` 过小，`fill/drain` 一律拒绝；自检里需先对 TileEntity 手动调用约 8 次 `updateEntity()` 再读写流体。
- 2026-11-03 12:14 非主世界（如下界）用 `world.getChunkFromBlockCoords` 在无玩家时可能返回 EmptyChunk，`setBlock` 返回 true 却放不上；自检里改用 `((WorldServer) world).theChunkProviderServer.loadChunk(cx, cz)` 显式加载。
- 2026-11-03 12:14 GT 储罐会拒绝高温流体（岩浆）；自检里用 `ic2distilledwater` 当「另一种流体」。
- 2026-11-03 12:14 流体覆盖板抽/灌的面规则与 GT 泵覆盖板一致：`drain/fill(coverSide, …)`；频道核心在 `core` 包与 GT 无关，GT 适配在 `gt` 包（`MachinePort` 适配 `GtFluidPort`）。
- 2026-11-03 12:14 #3 暂未实现身份端口（归属/权限归 #9），核心只有机器端口与配置端口。
