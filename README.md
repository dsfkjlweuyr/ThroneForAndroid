# Throne for Android (Formerly NekoBox for Android)

[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=21)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-orange.svg)](https://www.gnu.org/licenses/gpl-3.0)

## 使用前须知

> 免责声明：本项目仅用于技术研究与代码学习之目的，不提供任何形式的网络代理服务。请勿将本项目用于违反当地法律法规的任何活动。请勿在生产环境中使用本项目，使用者应自行承担使用本项目可能带来的全部风险。若您下载或引用本项目，请在 24 小时内自行删除相关内容，并避免长期存储、分享或传播本项目的任何部分。**作者保留随时修改、更新或移除本项目及其内容的权利，恕不另行通知。**
> 
> Disclaimer: This project is intended solely for technical research and code learning purposes and does not provide any form of network proxy service. Please do not use this project for any activities that violate local laws and regulations. Do not use this project in production environments. Users are fully responsible for any risks that may arise from using this project. If you download or reference this project, please delete all related content within 24 hours and avoid long-term storage, distribution, or dissemination of any part of this project. **The author reserves the right to modify, update, or remove any part of this project or its contents at any time without prior notice.**

## 下载 / Downloads

[![GitHub All Releases](https://img.shields.io/github/downloads/dsfkjlweuyr/ThroneForAndroid/total?label=downloads-total&logo=github&style=flat-square)](https://github.com/dsfkjlweuyr/ThroneForAndroid/releases)

[GitHub Releases 下载](https://github.com/dsfkjlweuyr/ThroneForAndroid/releases)

**Google Play 版本自 2024 年 5 月起已被第三方控制，为非开源版本，请不要下载。**

**The Google Play version has been controlled by a third party since May 2024 and is a non-open
source version. Please do not download it.**

## 交流反馈 / Feedback

https://t.me/Matsuridayo

## 项目主页 & 文档 / Homepage & Documents

https://matsuridayo.github.io

## 连接与速度测试 / Connection and Speed Tests

当前组菜单提供“URL 测试本组”和“速度测试本组”两个入口，不再提供批量直连 TCP/ICMP Ping。URL 延迟测试的新安装默认地址为 `http://cp.cloudflare.com/`，默认并发为 10。

速度测试支持下载+上传、仅下载、仅上传和简单下载；节点会逐个测试，流量通过各自代理发送。默认模式为下载+上传，默认超时为 5000 ms，简单下载默认地址为 `http://cachefly.cachefly.net/1mb.test`。速度测试可能消耗大量流量，请按需选择模式或及时取消。升级不会覆盖已经保存的自定义测试设置。

The current-group menu provides **URL test this group** and **Speed test this group**; direct batch TCP/ICMP Ping actions are no longer exposed. New installations use `http://cp.cloudflare.com/` with 10 URL-latency workers by default.

Speed testing supports download + upload, download only, upload only, and simple download. Profiles are tested one at a time through their own proxy. The default mode is download + upload, the default timeout is 5000 ms, and the default simple-download URL is `http://cachefly.cachefly.net/1mb.test`. Speed tests may consume significant data. Existing custom test settings are preserved during upgrades.

## 支持的代理协议 / Supported Proxy Protocols

* SOCKS (4/4a/5)
* HTTP(S)
* SSH
* Shadowsocks
* ShadowsocksR
* VMess
* Trojan
* VLESS
* AnyTLS/AnyReality
* Snell 1/2/3/4/5/6
* ShadowTLS
* TUIC
* Juicity
* Hysteria 1/2
* WireGuard
* Trojan-Go (trojan-go-plugin)
* NaïveProxy (naive-plugin)
* Mieru (mieru-plugin)

<details>
<summary>XHTTP Extra TLS配置示例</summary>

<pre><code class="language-json">
{
    "no_grpc_header": false,  // stream-up/one
	"x_padding_bytes": "100-10000",
	"sc_max_each_post_bytes": 1000000, // packet-up only
	"sc_min_posts_interval_ms": 30, // packet-up only
	"xmux": {
		"max_concurrency": "16-32",
		"max_connections": "0-0",
		"c_max_reuse_times": "0-0",
		"h_max_request_times": "600-900",
		"h_max_reusable_secs": "1800-3000",
		"h_keep_alive_period": 0
	},
    "x_padding_obfs_mode": false,
    "x_padding_key": "",
    "x_padding_header": "",
    "x_padding_placement": "",
    "x_padding_method": "",
    "uplink_http_method": "",
    "session_placement": "",
    "session_key": "",
    "seq_placement": "",
    "seq_key": "",
    "uplink_data_placement": "",
    "uplink_data_key": "",
    "uplink_chunk_size": 0,
	"download": {
		"mode": "auto",
		"host": "b.yourdomain.com",
		"path": "/xhttp",
        "no_grpc_header": false,  // stream-up/one
	    "x_padding_bytes": "100-10000",
	    "sc_max_each_post_bytes": 1000000, // packet-up only
	    "sc_min_posts_interval_ms": 30, // packet-up only
		"xmux": {
			"max_concurrency": "16-32",
			"max_connections": "0-0",
			"c_max_reuse_times": "0-0",
			"h_max_request_times": "600-900",
			"h_max_reusable_secs": "1800-3000",
			"h_keep_alive_period": 0
		},
        "x_padding_obfs_mode": false,
        "x_padding_key": "",
        "x_padding_header": "",
        "x_padding_placement": "",
        "x_padding_method": "",
        "uplink_http_method": "",
        "session_placement": "",
        "session_key": "",
        "seq_placement": "",
        "seq_key": "",
        "uplink_data_placement": "",
        "uplink_data_key": "",
        "uplink_chunk_size": 0,
		"server": "$(ip_or_domain_of_your_cdn)",
		"server_port": 443,
		"tls": {
			"enabled": true,
			"server_name": "b.yourdomain.com",
			"alpn": "h2",
			"utls": {
				"enabled": true,
				"fingerprint": "chrome"
			}
		}
	}
}
</code></pre>
</details>

<details>
<summary>XHTTP Extra Reality配置示例</summary>

<pre><code class="language-json">
{
    "no_grpc_header": false,  // stream-up/one
	"x_padding_bytes": "100-10000",
	"sc_max_each_post_bytes": 1000000, // packet-up only
	"sc_min_posts_interval_ms": 30, // packet-up only
	"xmux": {
		"max_concurrency": "16-32",
		"max_connections": "0-0",
		"c_max_reuse_times": "0-0",
		"h_max_request_times": "600-900",
		"h_max_reusable_secs": "1800-3000",
		"h_keep_alive_period": 0
	},
    "x_padding_obfs_mode": false,
    "x_padding_key": "",
    "x_padding_header": "",
    "x_padding_placement": "",
    "x_padding_method": "",
    "uplink_http_method": "",
    "session_placement": "",
    "session_key": "",
    "seq_placement": "",
    "seq_key": "",
    "uplink_data_placement": "",
    "uplink_data_key": "",
    "uplink_chunk_size": 0,
	"download": {
		"mode": "auto",
		"host": "example.com",
		"path": "/xhttp",
        "no_grpc_header": false,  // stream-up/one
	    "x_padding_bytes": "100-10000",
	    "sc_max_each_post_bytes": 1000000, // packet-up only
	    "sc_min_posts_interval_ms": 30, // packet-up only
		"xmux": {
			"max_concurrency": "16-32",
			"max_connections": "0-0",
			"c_max_reuse_times": "0-0",
			"h_max_request_times": "600-900",
			"h_max_reusable_secs": "1800-3000",
			"h_keep_alive_period": 0
		},
        "x_padding_obfs_mode": false,
        "x_padding_key": "",
        "x_padding_header": "",
        "x_padding_placement": "",
        "x_padding_method": "",
        "uplink_http_method": "",
        "session_placement": "",
        "session_key": "",
        "seq_placement": "",
        "seq_key": "",
        "uplink_data_placement": "",
        "uplink_data_key": "",
        "uplink_chunk_size": 0,
		"server": "$(ip_or_domain_of_your_cdn)",
		"server_port": 443,
		"tls": {
			"enabled": true,
			"server_name": "example.com",
			"reality": {
				"enabled": true,
				"public_key": "$(your_publicKey)",
				"short_id": "$(your_shortId)"
			},
			"utls": {
				"enabled": true,
				"fingerprint": "chrome"
			}
		}
	}
}
</code></pre>
</details>

请到[这里](https://matsuridayo.github.io/nb4a-plugin/)下载插件以获得完整的代理支持.

Please visit [here](https://matsuridayo.github.io/nb4a-plugin/) to download plugins for full proxy
supports.

## 支持的订阅格式 / Supported Subscription Format

* 一些广泛使用的格式 (如 Shadowsocks, ClashMeta 和 v2rayN)
* sing-box 订阅格式

仅支持解析出站，即节点。分流规则等信息会被忽略。

* Some widely used formats (like Shadowsocks, ClashMeta and v2rayN)
* sing-box outbound

Only resolving outbound, i.e. nodes, is supported. Information such as diversion rules are ignored.

## Credits

Core:

- [SagerNet/sing-box](https://github.com/SagerNet/sing-box)
- [Mahdi-zarei/speedtest-go](https://github.com/Mahdi-zarei/speedtest-go)（版本与来源见 [`libcore/DEPENDENCIES.md`](libcore/DEPENDENCIES.md)）

Android GUI:

- [shadowsocks/shadowsocks-android](https://github.com/shadowsocks/shadowsocks-android)
- [SagerNet/SagerNet](https://github.com/SagerNet/SagerNet)

Web Dashboard:

- [Yacd-meta](https://github.com/MetaCubeX/Yacd-meta)

## Star History

<a href="https://www.star-history.com/?repos=dsfkjlweuyr%2FThroneForAndroid&type=date&legend=bottom-right">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=dsfkjlweuyr/ThroneForAndroid&type=date&theme=dark&legend=bottom-right&sealed_token=vVh7Hn3UTnDoalet423u1x-LDNiIZQ2VfWY7GGmbMR8V-4feGR0yTT_IpVxBrxSTOwF7xFnsTcZjyHqNufLeTmdL5f-lw36iYWXcJSlXuJwapM1s8wChkg" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=dsfkjlweuyr/ThroneForAndroid&type=date&legend=bottom-right&sealed_token=vVh7Hn3UTnDoalet423u1x-LDNiIZQ2VfWY7GGmbMR8V-4feGR0yTT_IpVxBrxSTOwF7xFnsTcZjyHqNufLeTmdL5f-lw36iYWXcJSlXuJwapM1s8wChkg" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=dsfkjlweuyr/ThroneForAndroid&type=date&legend=bottom-right&sealed_token=vVh7Hn3UTnDoalet423u1x-LDNiIZQ2VfWY7GGmbMR8V-4feGR0yTT_IpVxBrxSTOwF7xFnsTcZjyHqNufLeTmdL5f-lw36iYWXcJSlXuJwapM1s8wChkg" />
 </picture>
</a>
