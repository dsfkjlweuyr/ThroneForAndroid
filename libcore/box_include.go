package libcore

import (
	"context"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/adapter/endpoint"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/adapter/inbound"
	"github.com/sagernet/sing-box/adapter/outbound"
	"github.com/sagernet/sing-box/adapter/service"
	"github.com/sagernet/sing-box/dns"
	"github.com/sagernet/sing-box/dns/transport"
	"github.com/sagernet/sing-box/dns/transport/fakeip"
	"github.com/sagernet/sing-box/dns/transport/hosts"
	"github.com/sagernet/sing-box/dns/transport/local"
	"github.com/sagernet/sing-box/dns/transport/quic"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing-box/protocol/anytls"
	"github.com/sagernet/sing-box/protocol/block"
	"github.com/sagernet/sing-box/protocol/direct"
	protocolDns "github.com/sagernet/sing-box/protocol/dns"
	"github.com/sagernet/sing-box/protocol/group"
	"github.com/sagernet/sing-box/protocol/http"
	"github.com/sagernet/sing-box/protocol/hysteria"
	"github.com/sagernet/sing-box/protocol/hysteria2"
	"github.com/sagernet/sing-box/protocol/mixed"
	"github.com/sagernet/sing-box/protocol/redirect"
	"github.com/sagernet/sing-box/protocol/shadowsocks"
	"github.com/sagernet/sing-box/protocol/shadowtls"
	"github.com/sagernet/sing-box/protocol/socks"
	"github.com/sagernet/sing-box/protocol/ssh"
	"github.com/sagernet/sing-box/protocol/tor"
	"github.com/sagernet/sing-box/protocol/trojan"
	"github.com/sagernet/sing-box/protocol/tuic"
	"github.com/sagernet/sing-box/protocol/tun"
	"github.com/sagernet/sing-box/protocol/vless"
	"github.com/sagernet/sing-box/protocol/vmess"
	"github.com/sagernet/sing-box/protocol/wireguard"

	h2http "libcore/protocol/http"
	"libcore/protocol/juicity"

	_ "github.com/sagernet/sing-box/experimental/clashapi"
	_ "github.com/sagernet/sing-box/transport/v2rayquic"

	E "github.com/sagernet/sing/common/exceptions"
)

func nekoboxAndroidInboundRegistry() *inbound.Registry {
	registry := inbound.NewRegistry()

	tun.RegisterInbound(registry)
	redirect.RegisterRedirect(registry)
	redirect.RegisterTProxy(registry)
	direct.RegisterInbound(registry)

	socks.RegisterInbound(registry)
	http.RegisterInbound(registry)
	mixed.RegisterInbound(registry)

	return registry
}

func nekoboxAndroidOutboundRegistry() *outbound.Registry {
	registry := outbound.NewRegistry()

	direct.RegisterOutbound(registry)

	block.RegisterOutbound(registry)
	protocolDns.RegisterOutbound(registry)

	group.RegisterSelector(registry)
	group.RegisterURLTest(registry)

	socks.RegisterOutbound(registry)
	http.RegisterOutbound(registry)
	// 覆盖 sing-box 的 http outbound：TLS 下默认 ALPN ["h2","http/1.1"]，
	// 协商到 h2 时走 HTTP/2 CONNECT（兼容 h2-only HTTPS 代理节点）。
	h2http.RegisterOutbound(registry)
	shadowsocks.RegisterOutbound(registry)
	// 官方内核无 shadowsocksr / snell（starifly fork 私有），按迁移方针先摘除；
	// 配置中含这两类 outbound 时 box.New 会报 "unknown outbound type" 直接失败，
	// 待有具体用户案例再评估替代实现。
	vmess.RegisterOutbound(registry)
	trojan.RegisterOutbound(registry)
	tor.RegisterOutbound(registry)
	ssh.RegisterOutbound(registry)
	shadowtls.RegisterOutbound(registry)
	vless.RegisterOutbound(registry)
	anytls.RegisterOutbound(registry)

	hysteria.RegisterOutbound(registry)
	tuic.RegisterOutbound(registry)
	hysteria2.RegisterOutbound(registry)
	juicity.RegisterOutbound(registry)

	// 官方 sing-box 1.11 起废弃、1.13.0 移除 wireguard outbound（仅保留 endpoint，
	// 见下方 nekoboxAndroidEndpointRegistry）；镜像官方 include/registry.go 的 stub，
	// 让旧式 wireguard outbound 配置得到明确报错而非 "unknown outbound type"。
	outbound.Register[option.StubOptions](registry, C.TypeWireGuard, func(ctx context.Context, router adapter.Router, logger log.ContextLogger, tag string, options option.StubOptions) (adapter.Outbound, error) {
		return nil, E.New("WireGuard outbound is deprecated in sing-box 1.11.0 and removed in sing-box 1.13.0, use WireGuard endpoint instead")
	})

	return registry
}

func nekoboxAndroidEndpointRegistry() *endpoint.Registry {
	registry := endpoint.NewRegistry()

	wireguard.RegisterEndpoint(registry)

	return registry
}

func nekoboxAndroidDNSTransportRegistry(localTransport LocalDNSTransport) *dns.TransportRegistry {
	registry := dns.NewTransportRegistry()

	transport.RegisterTCP(registry)
	transport.RegisterUDP(registry)
	transport.RegisterTLS(registry)
	transport.RegisterHTTPS(registry)
	hosts.RegisterTransport(registry)
	// local.RegisterTransport(registry)
	fakeip.RegisterTransport(registry)

	quic.RegisterTransport(registry)
	quic.RegisterHTTP3Transport(registry)

	if localTransport == nil {
		local.RegisterTransport(registry)
	} else {
		dns.RegisterTransport(registry, "local", func(ctx context.Context, logger log.ContextLogger, tag string, options option.LocalDNSServerOptions) (adapter.DNSTransport, error) {
			return newPlatformTransport(localTransport, tag, options), nil
		})
	}

	return registry
}

func nekoboxAndroidServiceRegistry() *service.Registry {
	registry := service.NewRegistry()

	return registry
}
