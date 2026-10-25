# Rando

## 本地编译

- 云端会话启动时 `.claude/hooks/session-start.sh` 会在后台装好 Android SDK，推送前先在本地跑 `LANG=C.UTF-8 ./gradlew assembleDebug testDebugUnitTest`（测试名有中文，需要 UTF-8 locale）。
- 刚开会话时如果报找不到 SDK 或 `local.properties`，说明后台安装还没跑完，等一会再试。
- 对讲降噪用的 RNNoise 是原生代码（`app/src/main/cpp`，v0.1.1 源码直接放在仓库里），第一次编译时 Gradle 会自己下载 NDK 和 CMake。

## 用到的在线服务（都不需要 key）

- 地图：OpenFreeMap（`tiles.openfreemap.org`），MapLibre 渲染
- 算路：BRouter 公共服务器（`brouter.de/brouter`），车型对应的配置见 `BikeProfile`
  - 「公园连道」「不上马路」用的是自己的配置 `RandoProfiles`，算路前 POST 到 `/brouter/profile` 换一个 `custom_xxx` 名字；服务器会清掉旧的（再用时返回 500），`BRouterClient` 会重新上传
  - 新加坡的 PCN 在 OSM 里是本地自行车路线，BRouter 的 WayTags 里是 `route_bicycle_lcn=yes`；路的分类见 `RoadKind`
  - 改配置后先用 curl 上传试一下：语法错了服务器会在返回的 `error` 里指出行号；条件要加括号，否则会被悄悄忽略（每段 CostPerKm 都是 1000）
- 地点搜索：Photon（`photon.komoot.io`），限定在新加坡范围
- 对讲直连：WebRTC（`io.github.webrtc-sdk:android`），只用数据通道传自己压好的 AAC，见 `RtcLink`；信令走一起骑服务器的房间（`{"t":"rtc"}`），STUN / TURN 地址从服务器的 `/ice` 拿
  - TURN 要在 Cloudflare 后台建一个 TURN key，Worker 配 Secrets `TURN_KEY_ID`、`TURN_KEY_API_TOKEN`；没配只有 STUN，两边都在运营商内网时打不通，退回走服务器

## Pull request 流程

- 推送分支后创建 PR，CI（`build`）通过后自动合并，不需要再问。
- 优先开 GitHub 自动合并（squash）；PR 已经是 clean 状态、开不了自动合并时，直接 squash 合并。
- CI 失败先修，修好再合；不要跳过、禁用测试来让 CI 变绿。
- 合并后如果还有后续改动，从最新的 `main` 重新拉分支，不要在已合并的分支上继续提交。
