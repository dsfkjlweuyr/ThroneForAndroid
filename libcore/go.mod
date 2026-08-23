module libcore

go 1.24.7

require (
	// v0.1.6签名与 v1.13.16 锁定版一致。
	github.com/exclavenetwork/sing-juicity v0.1.6
	github.com/gofrs/uuid/v5 v5.4.0
	// 与 Throne 2e7182b9ea99947a409fee30f74df83752ab763c 的测速基线一致。
	github.com/Mahdi-zarei/speedtest-go v1.7.13-0.20260107171856-79c565dfd83a
	github.com/miekg/dns v1.1.72
	github.com/oschwald/maxminddb-golang v1.13.1
	github.com/sagernet/quic-go v0.59.0-sing-box-mod.4
	github.com/sagernet/sing v0.8.12-0.20260726145744-ef2df370afca
	// 版本唯一来源是 ../nb4a.properties 的 SINGBOX_VERSION；此处仅为 Go
	// module graph 所需占位值，实际源码始终由下方 replace 指向 CI 检出的官方 tag。
	github.com/sagernet/sing-box v0.0.0
	github.com/sagernet/sing-tun v0.8.12-0.20260727151122-3a09076491df
	github.com/ulikunitz/xz v0.5.15
	golang.org/x/mobile v0.0.0-20231108233038-35478a0c49da
	golang.org/x/net v0.50.0
	golang.org/x/sys v0.41.0
)

// 官方内核：构建时由 buildScript/lib/core/get_source.sh 按 nb4a.properties 的
// SINGBOX_VERSION 克隆并校验 SagerNet/sing-box 到仓库同级目录（../../sing-box）。
replace github.com/sagernet/sing-box => ../../sing-box
