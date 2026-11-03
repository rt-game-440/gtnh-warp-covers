# 开发记忆

- 2026-11-03 18:47 服务端不能 `new UISettings()`：构造时会加载仅客户端的类，抛 `NoClassDefFoundError`。自检构建物品界面时传 null（`buildUI` 用不到）。流体图标铺满整格，选中态只改底色看不出来，要在图标上面画框。
- 2026-11-03 17:55 ModularUI2 陷阱：`addUIWidgets` 构建时 `PanelSyncManager.getPlayer()` 会 NPE（同步管理器还没绑定容器），要用 `CoverGuiData.getPlayer()`。这个问题让 #8 起所有覆盖板界面一打开就崩，而 JUnit 和自检都发现不了，因为之前从未构建过界面；现在 `aggregate_cover` 场景会在服务端构建一次。按钮文字默认颜色在 GT 中灰底上几乎看不清，要 `.color(0xFFFFFFFF)`。
- 2026-11-03 17:55 操作开发客户端：LWJGL 按扫描码读键盘，windows-mcp 的 Shortcut、Type 发的键收不到，要用 `scripts/client-input.ps1`；中文输入法开启时按键也进不去，要先切到英文（按一次 shift）。不要用带 loc 的 Type：聊天框没开时那一下点击会挖掉准星对着的方块。MUI 按钮要先移动鼠标、等一帧再点。自检方块在 Y=200 悬空，玩家要站在 `/setblock` 放的平台上，离太远界面会被立刻关掉。点窗口的关闭按钮关不掉客户端，要结束进程（先 `/save-all`）；注意别碰标题是 `GT: New Horizons` 的用户实例。
- 2026-11-03 16:44 ServerUtilities 身份适配（`gt/ServerUtilitiesIdentity`）：`ForgeTeam.setStatus`（管理员、盟友变化）不发事件，靠 `WarpEvents` 每 20 tick 复查；退队事件在状态改完之前发出，所以事件里只设标记，到 tick END 再复查。`Universe.loaded()` 在 `FMLServerAboutToStart` 就为 true，队伍数据到 `onServerStarted` 才加载，所以不能在 tick 之前做失权判定。自检造假玩家要自己设 `player.team`，入队用 `forceAddMember`（`addMember` 要求已被邀请）。归属者为空串的频道（自检直接建的）所有人都算管理者，所以旧场景不受权限影响。
- 2026-11-03 16:28 传输时钟统一用服务器 tick（`MinecraftServer.getTickCounter()`）：GT 传给 `doCoverThings` 的 aTickTimer 是每台机器各自的计时器，不能用于输出端接收窗口和状态栏。自检要控制时间就调 `WarpCover.tickAt(now)`，不要调 `doCoverThings`。
- 2026-11-03 16:28 ModularUI2 `TextFieldWidget` 的数字模式（`setNumbers*`）经过 double 计算且可能带格式化，大数丢精度；`LongSyncValue.setStringValue` 又直接 `Long.parseLong`。所以数字框用 `setValidator` + `core/NumberInput` 规范成纯整数。
- 2026-11-03 16:07 能量端口（`gt/GtEnergyPort`）：充能用 `IBasicEnergyContainer.increaseStoredEnergyUnits(n, true)` 直接加内部储能，不走 `injectEnergyUnits`，所以不判电压、不会爆炸；抽取 `decreaseStoredEnergyUnits(n, false)`，只抽 `getOutputVoltage() > 0` 的机器。贴放限制用 `CoverRegistry.registerCover(stack, tex, factory, CoverPlacer.builder().onlyPlaceIf(...).build())`，GT 右键贴覆盖板前会调 `isCoverPlaceable`。能量类型 `ChannelType.hasFilter()` 为 false，核心与面板跳过名单。自检里露天 GT 电器淋雨、下界挨岩浆会爆炸，要清天气、用石头围住。
- 2026-11-03 15:50 GTNHLib 物品迭代器陷阱：`InventoryItemSink.simulatedSinkIterator()` 的 `rewind()` 恒返回 false、不支持 previous，模拟只能单遍逐槽 `insert(..., false)` 累计；`AbstractInventoryIterator.previousIndex()/nextIndex()` 返回的是相邻**槽号** `slots[last∓1]` 而不是迭代位置（第一个元素调 previousIndex 越界 -1），要自己数位置（见 `gt/GtItemPort`）。`InventoryIterator.extract(n, forced)` 第二参数是 forced 不是 simulate。
- 2026-11-03 15:50 物品键 `注册名@损伤值[#Base64(按键名排序重建的 NBT)]`（`gt/ItemKeys`）；`FilterList` 在匹配 NBT 关时按 `ObjectKeys.withoutNbt` 比较。核心退回余量走 `MachinePort.restore`：物品端口先强制放回原槽、再正常放入、最后在方块旁掉落，保证不丢物品。
- 2026-11-03 15:50 覆盖板通用逻辑在 `gt/WarpCover` 抽象基类，流体/物品子类只给 `channelType()`、`createPort()` 和 GT 的 letsXxxIn/Out；新类型覆盖板照此加。自检失败消息由 `SelfCheckRunner.describe` 带前 8 层栈。
- 2026-11-03 15:00 GT 2.9.0 覆盖板界面走 ModularUI2：覆盖板重写 `getCoverGui()` 返回 `CoverBaseGui` 子类（见 `gt/WarpCoverGui`）；`Cover.onCoverShiftRightClick(player)` 在 `GTGuis.GLOBAL_SWITCH_MUI2` 下调 `CoverUIFactory.INSTANCE.open(...)` 打开界面。开发环境可用 `/warpcovers_selfcheck gui <x> <y> <z> <面>` 直接打开，截图不必模拟蹲下右键。
- 2026-11-03 15:00 界面快照与动作：服务器端 `CoverPanel` 产出快照（`gt/PanelSnapshot` 转 NBT 经 `GenericSyncValue` 下发），所有操作走 `registerSyncedAction(key, false, true, …)` 只在服务器执行；客户端只做预检（重名、空名、超长）。核心测试替身是 `core` 包私有类，`gt` 包测试要内联最小实现。
- 2026-11-03 15:00 用户常在同一台电脑上玩 GTNH 实例；windows-mcp 返回 `USER_CONTROL`（系统空闲时间≈0）时不要启动开发客户端或抢桌面，把 3 级截图推迟到桌面空闲时统一采集。

- 2026-11-03 14:20 GTNH 的 Hodgepodge（MixinChunkProviderServer_FastUnload 覆写 `unloadQueuedChunks`）会把加载时尚未填充的新区块及其 8 邻保护 600 tick 不卸载；自检里在同一 tick 卸载区块前要反射调 `ChunkGenScheduler.forDimension(dim).onChunkUnload(chunkXZ2Int)` 解除保护（见 `selfcheck/ScenarioWorld.unloadChunk`）。只在区块首次生成时复现，磁盘上已有区块不触发。
- 2026-11-03 14:20 GT 机器读档后 `mTickTimer` 同样从 0 开始，重启后的自检校验也要先 `warmUp` 再 fill/drain。
- 2026-11-03 14:20 覆盖板配置随物品走：`Cover.asItemStack()` 被 `detachCover` 与掉落使用（`GTOreDictUnificator.get` 保留 NBT），放置时构造函数里 `CoverContext.getCoverItem()` 就是手持的带 NBT 物品；但随机器物品重放（`buildCoverFromNbt`）走 `readFromNbt`，没有 `onPlayerAttach`，连接者用机器归属者兜底。`allowsCopyPasteTool` 要关掉，否则会复制连接端 ID。
- 2026-11-03 14:20 项目里有的文件是 CRLF、有的是 LF，PowerShell 多行 `.Replace` 容易静默不匹配；改文件优先用 Edit 或整体 Write。

- 2026-11-03 12:14 GT 机器（BaseMetaTileEntity）刚放置时 `mTickTimer` 过小，`fill/drain` 一律拒绝；自检里需先对 TileEntity 手动调用约 8 次 `updateEntity()` 再读写流体。
- 2026-11-03 12:14 非主世界（如下界）用 `world.getChunkFromBlockCoords` 在无玩家时可能返回 EmptyChunk，`setBlock` 返回 true 却放不上；自检里改用 `((WorldServer) world).theChunkProviderServer.loadChunk(cx, cz)` 显式加载。
- 2026-11-03 12:14 GT 储罐会拒绝高温流体（岩浆）；自检里用 `ic2distilledwater` 当「另一种流体」。
- 2026-11-03 12:14 流体覆盖板抽/灌的面规则与 GT 泵覆盖板一致：`drain/fill(coverSide, …)`；频道核心在 `core` 包与 GT 无关，GT 适配在 `gt` 包（`MachinePort` 适配 `GtFluidPort`）。
- 2026-11-03 12:14 #3 暂未实现身份端口（归属/权限归 #9），核心只有机器端口与配置端口。
