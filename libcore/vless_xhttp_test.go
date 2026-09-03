package libcore

import (
	"context"
	"testing"

	box "github.com/sagernet/sing-box"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing/service"
)

const vlessXHTTPAutoConfig = `{
  "log": { "disabled": true },
  "outbounds": [
    {
      "type": "vless",
      "tag": "vless-xhttp-auto",
      "server": "127.0.0.1",
      "server_port": 443,
      "uuid": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
      "transport": {
        "type": "xhttp",
        "mode": "auto",
        "host": "example.com",
        "path": "/xhttp"
      }
    }
  ],
  "route": { "final": "vless-xhttp-auto" }
}`

const vlessXHTTPStreamUpConfig = `{
  "log": { "disabled": true },
  "outbounds": [
    {
      "type": "vless",
      "tag": "vless-xhttp-stream-up",
      "server": "127.0.0.1",
      "server_port": 443,
      "uuid": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
      "transport": {
        "type": "xhttp",
        "mode": "stream-up",
        "host": "example.com",
        "path": "/xhttp",
        "x_padding_bytes": "100-500",
        "no_grpc_header": true,
        "sc_max_each_post_bytes": "500000-1000000",
        "sc_min_posts_interval_ms": "30-50",
        "xmux": {
          "max_concurrency": "1-4",
          "c_max_reuse_times": "10-20",
          "h_keep_alive_period": 30
        }
      }
    }
  ],
  "route": { "final": "vless-xhttp-stream-up" }
}`

const vlessXHTTPStreamOneConfig = `{
  "log": { "disabled": true },
  "outbounds": [
    {
      "type": "vless",
      "tag": "vless-xhttp-stream-one",
      "server": "127.0.0.1",
      "server_port": 443,
      "uuid": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
      "transport": {
        "type": "xhttp",
        "mode": "stream-one",
        "host": "example.com",
        "path": "/xhttp"
      }
    }
  ],
  "route": { "final": "vless-xhttp-stream-one" }
}`

const vlessStandardWsConfig = `{
  "log": { "disabled": true },
  "outbounds": [
    {
      "type": "vless",
      "tag": "vless-ws",
      "server": "127.0.0.1",
      "server_port": 443,
      "uuid": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
      "transport": {
        "type": "ws",
        "path": "/ws"
      }
    }
  ],
  "route": { "final": "vless-ws" }
}`

func checkVlessConfig(config string) (*box.Box, error) {
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

func TestVLESSXHTTPConfigs(t *testing.T) {
	testCases := []struct {
		name   string
		config string
	}{
		{name: "xhttp-auto", config: vlessXHTTPAutoConfig},
		{name: "xhttp-stream-up", config: vlessXHTTPStreamUpConfig},
		{name: "xhttp-stream-one", config: vlessXHTTPStreamOneConfig},
		{name: "standard-ws-compatibility", config: vlessStandardWsConfig},
	}

	for _, tc := range testCases {
		t.Run(tc.name, func(t *testing.T) {
			instance, err := checkVlessConfig(tc.config)
			if err != nil {
				t.Fatalf("VLESS config check failed for %s: %v", tc.name, err)
			}
			if err := instance.Close(); err != nil {
				t.Fatalf("close checked instance for %s: %v", tc.name, err)
			}
		})
	}
}
