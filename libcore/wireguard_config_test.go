package libcore

import (
	"context"
	"strings"
	"testing"

	box "github.com/sagernet/sing-box"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing/service"
)

const wireGuardEndpointConfig = `{
  "log": { "disabled": true },
  "endpoints": [
    {
      "type": "wireguard",
      "tag": "wireguard-main",
      "address": ["10.0.0.2/32", "fd00::2/128"],
      "private_key": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
      "peers": [
        {
          "address": "198.51.100.10",
          "port": 51820,
          "public_key": "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=",
          "allowed_ips": ["0.0.0.0/0", "::/0"]
        }
      ]
    }
  ],
  "outbounds": [
    { "type": "direct", "tag": "direct" }
  ],
  "route": { "final": "wireguard-main" }
}`

const wireGuardSelectorConfig = `{
  "log": { "disabled": true },
  "endpoints": [
    {
      "type": "wireguard",
      "tag": "wireguard-main",
      "address": ["10.0.0.2/32", "fd00::2/128"],
      "private_key": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
      "peers": [
        {
          "address": "198.51.100.10",
          "port": 51820,
          "public_key": "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=",
          "allowed_ips": ["0.0.0.0/0", "::/0"]
        }
      ]
    }
  ],
  "outbounds": [
    {
      "type": "selector",
      "tag": "proxy",
      "default": "wireguard-main",
      "outbounds": ["wireguard-main", "next-hop"]
    },
    { "type": "socks", "tag": "next-hop", "server": "192.0.2.20", "server_port": 1080 },
    { "type": "direct", "tag": "direct" }
  ],
  "route": { "final": "proxy" }
}`

const wireGuardApplicationFacingChainConfig = `{
  "log": { "disabled": true },
  "endpoints": [
    {
      "type": "wireguard",
      "tag": "wireguard-main",
      "detour": "next-hop",
      "address": ["10.0.0.2/32", "fd00::2/128"],
      "private_key": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
      "peers": [
        {
          "address": "198.51.100.10",
          "port": 51820,
          "public_key": "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=",
          "allowed_ips": ["0.0.0.0/0", "::/0"]
        }
      ]
    }
  ],
  "outbounds": [
    { "type": "socks", "tag": "next-hop", "server": "192.0.2.20", "server_port": 1080 },
    { "type": "direct", "tag": "direct" }
  ],
  "route": { "final": "wireguard-main" }
}`

const wireGuardEgressFacingChainConfig = `{
  "log": { "disabled": true },
  "endpoints": [
    {
      "type": "wireguard",
      "tag": "wireguard-main",
      "address": ["10.0.0.2/32", "fd00::2/128"],
      "private_key": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
      "peers": [
        {
          "address": "198.51.100.10",
          "port": 51820,
          "public_key": "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=",
          "allowed_ips": ["0.0.0.0/0", "::/0"]
        }
      ]
    }
  ],
  "outbounds": [
    {
      "type": "socks",
      "tag": "next-hop",
      "server": "192.0.2.20",
      "server_port": 1080,
      "detour": "wireguard-main"
    },
    { "type": "direct", "tag": "direct" }
  ],
  "route": { "final": "next-hop" }
}`

func checkConfig(config string) (*box.Box, error) {
	ctx := box.Context(
		context.Background(),
		nekoboxAndroidInboundRegistry(),
		nekoboxAndroidOutboundRegistry(),
		nekoboxAndroidEndpointRegistry(),
		nekoboxAndroidDNSTransportRegistry(nil),
		nekoboxAndroidServiceRegistry(),
	)
	ctx = service.ContextWithDefaultRegistry(ctx)
	var options option.Options
	if err := options.UnmarshalJSONContext(ctx, []byte(config)); err != nil {
		return nil, err
	}
	return box.New(box.Options{Options: options, Context: ctx})
}

func TestWireGuardSingleEndpointConfig(t *testing.T) {
	instance, err := checkConfig(wireGuardEndpointConfig)
	if err != nil {
		t.Fatalf("WireGuard endpoint config check failed: %v", err)
	}
	if err := instance.Close(); err != nil {
		t.Fatalf("close checked instance: %v", err)
	}
}

func TestWireGuardTopologyConfigs(t *testing.T) {
	testCases := []struct {
		name   string
		config string
	}{
		{name: "selector-member", config: wireGuardSelectorConfig},
		// T4A URL tests build an isolated box whose final target is the tested profile.
		{name: "urltest-main-target", config: wireGuardEndpointConfig},
		{name: "application-facing-chain", config: wireGuardApplicationFacingChainConfig},
		{name: "egress-facing-chain", config: wireGuardEgressFacingChainConfig},
	}

	for _, testCase := range testCases {
		t.Run(testCase.name, func(t *testing.T) {
			instance, err := checkConfig(testCase.config)
			if err != nil {
				t.Fatalf("WireGuard topology config check failed: %v", err)
			}
			if err := instance.Close(); err != nil {
				t.Fatalf("close checked instance: %v", err)
			}
		})
	}
}

func TestWireGuardListenPortDetourConflictRejected(t *testing.T) {
	const invalidConfig = `{
  "log": { "disabled": true },
  "endpoints": [
    {
      "type": "wireguard",
      "tag": "wireguard-main",
      "detour": "next-hop",
      "listen_port": 51820,
      "address": ["10.0.0.2/32"],
      "private_key": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
      "peers": [
        {
          "address": "198.51.100.10",
          "port": 51820,
          "public_key": "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=",
          "allowed_ips": ["0.0.0.0/0", "::/0"]
        }
      ]
    }
  ],
  "outbounds": [
    { "type": "direct", "tag": "next-hop" }
  ],
  "route": { "final": "wireguard-main" }
}`

	instance, err := checkConfig(invalidConfig)
	if instance != nil {
		_ = instance.Close()
	}
	if err == nil || !strings.Contains(err.Error(), "`listen_port` is conflict with `detour`") {
		t.Fatalf("expected the WireGuard listen_port/detour diagnostic, got: %v", err)
	}
}

func TestWireGuardLegacyOutboundRejected(t *testing.T) {
	const legacyConfig = `{
  "outbounds": [
    { "type": "wireguard", "tag": "wireguard-main" }
  ],
  "route": { "final": "wireguard-main" }
}`
	instance, err := checkConfig(legacyConfig)
	if instance != nil {
		_ = instance.Close()
	}
	if err == nil || !strings.Contains(err.Error(), "WireGuard outbound is deprecated") {
		t.Fatalf("expected the legacy WireGuard outbound removal diagnostic, got: %v", err)
	}
}
