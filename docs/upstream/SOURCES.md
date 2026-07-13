# MMDSkinSync 未提交上游快照来源

采集时间（UTC）：2026-07-13T04:41:23.216Z

## 冻结策略

本目录冻结两个具有相同 Git HEAD、但工作树内容不同的 Sync 输入快照。Git HEAD 不能单独代表任一快照；身份由来源路径、Git 元数据、完整 porcelain 状态、binary patch 和逐文件 SHA-256 manifest 共同确定。未跟踪文件未执行 git add，由 manifest 与 versions 目录的字节级复制冻结。

排除项仅限生成/工具目录：.git、.gradle、build、run、runs、run-data、out、bin、.idea、.settings、.metadata、eclipse、repo、.architectury-transformer、target，以及工具状态文件 .ai_progress.md。JAR、Windows/Linux native、wrapper、源码和未跟踪实现均属于输入并保留。

## Sync 1.21.1 / MC-MMD-rust 1.0.5 / 90-JNI

- 绝对路径：C:\Users\Administrator\Desktop\MMDsKin1\MMDSkinSync
- 工作区相对路径：../MMDsKin1/MMDSkinSync
- 目标仓相对路径：../../MMDsKin1/MMDSkinSync
- Git origin：https://github.com/XUANHLGG/MMDSkinSync.git
- Git branch / upstream：1.21.1 / origin/1.21.1
- Git HEAD：613f2cb81c1741920ae2b779fe4afa80f88d24f6
- 状态：36 项（tracked 修改/删除 30，untracked 6）；正文见 snapshot-1.21.1.status.txt
- 状态 SHA-256（UTF-8、LF、无末尾换行）：358df41fad6c61b8cf0b10b2374d2c582ec115c50cbcbc0da6230a6eb0b30cdf
- MC-MMD JAR：libs/mmdskin-common-1.0.5-1.21.1-2.jar，SHA-256 0e23791e1c989ed3f22c7919dc1dca5bf34d14bde7dcfdb331b5065ce51c3576
- Linux native：common/src/main/resources/natives/linux-x64/libmmdsync_bridge.so，SHA-256 9e69cde319230ca79a416ad844050c5bc756267d626947ecee4596fb412aa4c7
- Windows native：common/src/main/resources/natives/windows-x64/mmdsync_bridge.dll，SHA-256 b872e6e12b970724fa049678174fc60999f732199cb84d8b64101f4779571eb3

## Sync 1.21.4 / MC-MMD-rust 1.0.5 / 79-JNI

- 绝对路径：C:\Users\Administrator\Desktop\MMDSkin\MMDSkinSync
- 工作区相对路径：MMDSkinSync
- 目标仓相对路径：.
- Git origin：https://github.com/XUANHLGG/MMDSkinSync.git
- Git branch / upstream：1.21.1 / origin/1.21.1
- Git HEAD：613f2cb81c1741920ae2b779fe4afa80f88d24f6
- 状态：36 项（tracked 修改/删除 28，untracked 8）；正文见 snapshot-1.21.4.status.txt
- 状态 SHA-256（UTF-8、LF、无末尾换行）：9b912b225d5212681dccd159f4882d77f7e6da9fdaab0d41e1eb21381da0d968
- 说明：分支名仍为 1.21.1，但当前未提交工作树是本次冻结的 Minecraft 1.21.4 / 79-JNI 适配；必须以完整快照而非分支名判定版本。
- MC-MMD JAR：libs/mmdskin-common-1.0.5-1.21.4-1.jar，SHA-256 6677d4544c2502e55590a96743f350b88dae64c45c0ac8325bc2d48fdbe7bdc5
- Linux native：common/src/main/resources/natives/linux-x64/libmmdsync_bridge.so，SHA-256 9007fbb8d4a201a3cb21d37677ded5ba231bea46e89f83fc618ed1f89e90d511
- Windows native：common/src/main/resources/natives/windows-x64/mmdsync_bridge.dll，SHA-256 62e6ae9b37a2deafb8036ea29eb163ffa8455d77d50728d917892d3713245093

两版 JAR/native 具有不同哈希与 JNI 契约，严禁跨版本替换或混用。

## 1.21.1 来源依赖（只记录，不导入目标 Git）

这些仓库是 1.21.1 适配的来源依赖。本步骤不复制其仓库内容、不修改来源，也不将其导入目标 Git。

### MC-MMD-rust

- 绝对路径：C:\Users\Administrator\Desktop\MMDsKin1\MC-MMD-rust
- 工作区相对路径：../MMDsKin1/MC-MMD-rust
- Git origin：https://github.com/shiroha-233/MC-MMD-rust.git
- Git branch / upstream：1.21.1 / origin/1.21.1
- Git HEAD：48c31b6acf3ece211e7bdbe0482ceb1be1a41205
- 状态：1 项（tracked 1，untracked 0）；完整摘要： M settings.gradle
- 状态 SHA-256（UTF-8、LF、无末尾换行）：6936c31d0ed3194a4f2ae3f1d221315c0b585befe6d7db053e05995a6033c371

### mmdsync-bridge

- 绝对路径：C:\Users\Administrator\Desktop\MMDsKin1\mmdsync-bridge
- 工作区相对路径：../MMDsKin1/mmdsync-bridge
- Git origin：https://github.com/XUANHLGG/mmdsync-bridge.git
- Git branch / upstream：main / origin/main
- Git HEAD：df2d8b41b09ff244c05020e47aa4c748ce3bf50f
- 状态：84 项（tracked 54，untracked 30）；完整 porcelain 状态由该来源仓保持，本次不导入。
- 状态 SHA-256（UTF-8、LF、无末尾换行）：ae43ce9773cc3c5655458c3849aca933f6ec89f8640758266d00a2407673c13b

## 来源适配报告

- 源文件绝对路径：C:\Users\Administrator\Desktop\MMDsKin1\MMDSKIN_SYNC_1.0.5_ADAPTATION.md
- 工作区相对路径：../MMDsKin1/MMDSKIN_SYNC_1.0.5_ADAPTATION.md
- 冻结副本：docs/upstream/MMDSKIN_SYNC_1.0.5_ADAPTATION-1.21.1.md
- 源文件 SHA-256：8c1d3277a839d123af4a34e3590d8bbdb863885ee34cd2f42b214336b1f5892e
- 字节数：27704
