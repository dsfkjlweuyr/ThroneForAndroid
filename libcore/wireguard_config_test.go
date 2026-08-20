package libcore

import (
	"context"
	"strings"
	"testing"

	"github.com/sagernet/sing-box/adapter/service"
	"github.com/sagernet/sing-box/box"
	"github.com/sagernet/sing-box/option"
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
