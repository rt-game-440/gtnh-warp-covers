# 开发记忆

- 2026-11-03 12:14 GT 机器（BaseMetaTileEntity）刚放置时 `mTickTimer` 过小，`fill/drain` 一律拒绝；自检里需先对 TileEntity 手动调用约 8 次 `updateEntity()` 再读写流体。
- 2026-11-03 12:14 非主世界（如下界）用 `world.getChunkFromBlockCoords` 在无玩家时可能返回 EmptyChunk，`setBlock` 返回 true 却放不上；自检里改用 `((WorldServer) world).theChunkProviderServer.loadChunk(cx, cz)` 显式加载。
- 2026-11-03 12:14 GT 储罐会拒绝高温流体（岩浆）；自检里用 `ic2distilledwater` 当「另一种流体」。
- 2026-11-03 12:14 流体覆盖板抽/灌的面规则与 GT 泵覆盖板一致：`drain/fill(coverSide, …)`；频道核心在 `core` 包与 GT 无关，GT 适配在 `gt` 包（`MachinePort` 适配 `GtFluidPort`）。
- 2026-11-03 12:14 #3 暂未实现身份端口（归属/权限归 #9），核心只有机器端口与配置端口。
