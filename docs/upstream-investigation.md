# Tailscale 二进制 / Magisk 模块 / Fork 上游调查

调查时间：2026-05-01
调查人：Claude (Opus 4.7)
调查触发：设备上 `tailscale version` 显示 1.90.6，但 tailscale 官方 stable 已是 1.96.4。

---

## TL;DR

1. 设备上的 tailscale 版本由**三层独立组件**决定：GUI ⟶ Magisk 模块 ⟶ 上游 fork。
2. 你停在 1.90.6 是因为上游构建仓库 `anasfanani/tailscale-android-cli` 的最新 release 就是 v1.90.6-android (2025-11-03)；该 fork 的 `main` 分支 VERSION.txt = 1.91.0，**落后官方 5 个 minor**。
3. 模块脚本 `customize.sh` 仅在 `tailscaled` 二进制不存在时才下载，**已装好的不会自动升级**。
4. 1.90.6 已经**完全删除 `tailscale file` 子命令** —— 原 GUI 的 Drop daemon + 文件分享整套流程对当前 tailscale 失效（已加 disabled banner）。
5. 升级到 1.96 必须等上游构建仓库出 1.96 build，或自己 cross-compile。

---

## 版本管理链路

```
┌─────────────────────────────┐
│  TailControl GUI            │  ← 本仓库 (Magisk-Tailscaled-GUI)
│  通过 libsu su -c 调用 CLI   │
└──────────────┬──────────────┘
               │ shell-out
               ▼
┌─────────────────────────────┐
│  tailscaled / tailscale 二进制
│  /data/adb/tailscale/bin/   │
│  当前版本：1.90.6 arm64     │
└──────────────┬──────────────┘
               │ 由 customize.sh 安装
               ▼
┌─────────────────────────────┐
│  Magisk Tailscaled v2.0.0.1 │  ← anasfanani/Magisk-Tailscaled
│  (2025 年发布的安装/启动脚本)│
│                              │
│  customize.sh 行为：         │
│    1) 优先解压模块 zip 自带的 │
│       tailscale/bin/*-{ARCH} │
│    2) 找不到则 gh_download   │
│       anasfanani/tailscale-  │
│       android-cli            │
│       latest release         │
│  ⚠ 仅在 tailscaled 不存在时   │
│    下载，已装的不会自动升级   │
└──────────────┬──────────────┘
               │ 拉 release tarball
               ▼
┌─────────────────────────────┐
│  anasfanani/                 │
│  tailscale-android-cli       │
│  (tailscale fork)            │
│                              │
│  Releases:                   │
│    v1.90.6-android   2025-11-03  ← stable
│    v1.90.61-android-pre 2025-11-10  ← pre
│  main VERSION.txt: 1.91.0    │
│  ⚠ 落后上游 5 个 minor       │
│                              │
│  build_android.sh +          │
│  GitHub Actions              │
│  NDK r27c + clang 21         │
│  cross-compile arm/arm64/    │
│  amd64                        │
└──────────────┬──────────────┘
               │ fork
               ▼
┌─────────────────────────────┐
│  tailscale/tailscale         │
│  上游 OSS 仓库               │
│  当前 stable: 1.96.4         │
└─────────────────────────────┘
```

### 各层版本一览（截至 2026-05-01）

| 组件 | 版本 | 发布时间 |
|---|---|---|
| TailControl GUI (本仓库) | 2.0.0 | — |
| Magisk-Tailscaled 模块 | v2.0.0.1 | 2025-11 |
| 设备上的 tailscale 二进制 | 1.90.6 | 2025-11-03 |
| `anasfanani/tailscale-android-cli` 最新 release | v1.90.6-android | 2025-11-03 |
| `anasfanani/tailscale-android-cli` 最新 pre | v1.90.61-android-pre | 2025-11-10 |
| `anasfanani/tailscale-android-cli` main 分支 | 1.91.0 (VERSION.txt) | 持续 |
| `tailscale/tailscale` 上游 stable | 1.96.4 | — |

---

## anasfanani fork 相对 tailscale 上游做了什么

通过 `git log --author=anas` 列出的 17 个独立 commit 可分为 6 类：

### 1. CI / 构建系统（最核心）
| 文件 | 作用 |
|---|---|
| `build_android.sh` | bash 脚本，下载 NDK r27c → 设 `CC=aarch64-linux-android21-clang` → 通过 `./tool/go` (tailscale 自带 Go) cross-compile → 输出 `tailscale_<ver>_<arch>.tgz` |
| `.github/workflows/build_android.yml` | GitHub Actions 工作流，每个 release tag 触发构建，支持 stable/pre 两种 release track |

### 2. Android 自更新（262 行新代码）
| 文件 | 作用 |
|---|---|
| `clientupdate/clientupdate_android.go` | 实现 `tailscale update` 在 Android 上的逻辑 —— 不去 tailscale 官方 release，而是去 anasfanani 自家 fork 的 release 拉新 tarball；支持 `--pre` 切到 pre-release track |
| `clientupdate/clientupdate.go` | 把 update 子命令路由到 Android 实现 |

### 3. Android DNS 适配
| 文件 | 作用 |
|---|---|
| `net/dns/manager_android.go` | 重写 105 行：让 `--accept-dns=true` 在 Android 系统 DNS 子系统下能正确工作 |
| `net/dns/resolvconfpath_android.go` | 改写 resolvconf 路径解析，读 `/system/etc/resolv.conf` 系列 |

### 4. iptables / 路由
| 文件 | 改动 |
|---|---|
| `wgengine/router/osrouter/router_linux.go` | +76 行 / -24 行：让 advertise-routes 和 advertise-exit-node 在 Android iptables/netd 上正常 |
| `util/linuxfw/iptables_runner.go` | +22 行：iptables 命令兼容 Android |

### 5. 解开上游 stub 的功能
上游 tailscale 在 Android build tag 下默认禁掉了一些功能，anasfanani 重新启用：

| 文件 | 启用的功能 |
|---|---|
| `ipn/ipnlocal/ssh.go`, `ssh/tailssh/*.go` | Tailscale SSH server（不依赖系统 sshd） |
| `ipn/localapi/cert.go`, `ipn/localapi/disabled_stubs.go` | `tailscale cert` 子命令 |

### 6. 路径与小修
| 文件 | 改动 |
|---|---|
| `paths/paths.go`, `paths_unix.go`, `logpolicy/logpolicy.go`, `tsweb/tsweb.go` | tailscaled socket/state/log 默认路径改到 `/data/adb/tailscale/` |
| `cmd/tailscaled/tailscaled.go` | 启动时自动建 `tailscale → tailscaled` symlink，省去模块脚本手动建 |
| `hostinfo/hostinfo_android.go` | 新增 79 行：上报 Android 系统信息 |
| `ssh/tailssh/incubator.go` | SSH 会话里过滤 `TS_*` 环境变量 |
| `ipn/ipnlocal/local.go`, `peerapi.go`, `net/netmon/state.go`, `net/netns/netns_linux.go` | PeerAPI / connectivity 边界小修 |

---

## tailscale 1.90.6 功能矩阵（在 Magisk-Tailscaled 上实测）

通过 `adb shell su -c '...'` 测试，见会话探测记录。

| 命令 | 结果 | GUI 集成 |
|---|---|---|
| `tailscale version` | 1.90.6 | — |
| `tailscale status --json` | ✅ | 已用 (Home + 各处) |
| `tailscale up/down/set` | ✅ | 已用 |
| `tailscale switch / login / logout` | ✅ | 已用 (Accounts) |
| `tailscale ping` | ✅ 默认直连后自停，`-c N` 可设上限 | 已用 (PeerDetail) |
| `tailscale netcheck` | ✅ 一次性命令 | 已用 (Netcheck) |
| **`tailscale file *`** | ❌ **整个 file 子命令删除** | **Drop banner 提示不可用** |
| `tailscale ssh <host>` | ✅ wrapper，需要本机 `/system/bin/ssh`（Android 默认无） | 提供命令复制（PeerDetail） |
| `tailscale set --ssh=true` | ✅ Tailscale SSH server，不依赖系统 sshd | 已用 (Settings 开关) |
| `tailscale serve / funnel` | ✅ 命令在；`status` 输出 "No serve config" | 暂未集成（需要 HTTPS 证书 + 端口管理） |
| `tailscale exit-node list/suggest` | ✅ 表格输出，suggest 给一个推荐 hostname | 已用 (ExitNodePicker 顶部建议) |
| `tailscale dns status` | ✅ 输出 MagicDNS suffix + 本机 DNS name | 已用 (Settings DNS 卡) |
| `tailscale metrics` | ✅ Prometheus 文本，44 行；区分 direct/DERP/peer_relay 入出字节 | 暂未集成；现有 `/proc/net/dev` 方案可用 |
| `tailscale whois <ip>` | ✅ 返回 Machine name/ID + User email | 已用 (PeerDetail Owner 卡) |
| `tailscale ip` | ✅ 自身 IPv4+IPv6 | status JSON 已有，重复 |
| `tailscale bugreport` | ❌ "404 Not Found" — 控制端不支持 | 跳过 |
| `tailscale appc-routes` | — "not a connector" | 跳过 |
| `tailscale nc / web / cert / configure / update` | 命令在 | GUI 集成成本不划算（除 update，见下） |

---

## 升级到上游 1.96 的可行路径

**根本阻塞**：`anasfanani/tailscale-android-cli` 还没 build 1.96。这跟模块/GUI 无关。

按可行度排序：

### 1. `tailscale update` — anasfanani 已实现（最简单）
设备上跑 `su -c 'tailscale update'` 或 `tailscale update --pre`，由 `clientupdate_android.go` 拉 anasfanani 自家 release 替换 binary。**前提是 anasfanani 出了新 release**；现在跑只会确认已是最新。

GUI 集成可选：Settings 加"版本 + 检查更新"卡，按钮跑 `tailscale update --check`。

### 2. 等 anasfanani 出新 release
fork 维护节奏不稳：v1.86 (上一稳定) → v1.90.6 (2025-11-03) 间隔几个月。1.91 → 1.96 需要 5 次 minor 同步。

### 3. 自编译
在 Linux/macOS 主机：
```bash
git clone https://github.com/anasfanani/tailscale-android-cli
cd tailscale-android-cli
./build_android.sh arm64       # 输出 tailscale_<ver>_arm64.tgz
adb push tailscale_*_arm64.tgz /data/local/tmp/
adb shell su -c 'tar -xzf /data/local/tmp/tailscale_*_arm64.tgz -C /data/adb/tailscale/bin/ \
  && tailscaled.service restart'
```
需要：bash + curl + Go (tailscale 自带 `tool/go`) + 自动下载的 NDK r27c。

如果 fork main 落后官方版本，还得先 rebase 到官方对应 tag，可能引入 conflict（fork 改了 router_linux/iptables/dns 等核心文件）。

### 4. 推维护者
该 fork 没启用 issue tracker，主要靠 anasfanani 个人推进。提 issue 也许可见，但响应不可控。

---

## 已强制处理的兼容性问题

| 问题 | 处理 |
|---|---|
| `tailscale file *` 删除导致 Drop daemon 不可用 | DropViewModel.init 中 probe `tailscale file --help`，UI 顶部红色 errorContainer banner，开关被拒绝 |
| toybox 的 `tail -n 0` 不像 GNU tail（仍输出历史） | `LogRepository.tail(lines=0)` 改用 `wc -l + tail -n +X -F` 跳过现有行 |
| libsu callbackFlow 命令完成后不主动 close → netcheck 按钮卡在 loading | `RootShell.stream()` 用 `submit(executor, callback) { close() }` 让 Flow 在命令退出时主动结束 |
| OPPO/OnePlus `LOG_FLOWCTRL` 限速吃日志 | 待修：debug 包关闭 libsu verbose 日志；TrafficScreen 只在前台采样（`Lifecycle.repeatOnLifecycle`） |

---

## 参考路径

| 文件 | 作用 |
|---|---|
| `/home/mt/magisk-tailscaled/customize.sh` | 模块安装脚本，含 `gh_download` 逻辑 |
| `/home/mt/magisk-tailscaled/tailscale/scripts/start.sh` | tailscaled 启动脚本 |
| `/home/mt/magisk-tailscaled/tailscale/settings.sh` | 模块路径与日志变量 |
| `/home/mt/tailscale-android-cli/build_android.sh` | anasfanani 的 cross-compile 脚本 |
| `/home/mt/tailscale-android-cli/clientupdate/clientupdate_android.go` | Android 自更新实现 |
| `/data/adb/tailscale/bin/tailscaled` | 设备上的二进制（5.6 MB，2025-11-04 安装） |

## 相关链接

- Magisk-Tailscaled 模块：https://github.com/anasfanani/Magisk-Tailscaled
- Tailscale Android CLI fork：https://github.com/anasfanani/tailscale-android-cli
- Tailscale 上游：https://github.com/tailscale/tailscale
- Tailscale CLI 文档：https://tailscale.com/docs/reference/tailscale-cli
