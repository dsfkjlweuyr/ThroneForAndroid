package xhttp

import (
	"net/http"
	"net/url"
	"strings"

	"github.com/sagernet/sing-box/option"
	E "github.com/sagernet/sing/common/exceptions"
	Xbadoption "libcore/protocol/vless/internal/xray/badoption"
)

const V2RayTransportTypeXHTTP = "xhttp"

func NormalizeXHTTPMode(mode string) (string, error) {
	mode = strings.TrimSpace(mode)
	if mode == "" {
		return "auto", nil
	}
	switch mode {
	case "auto", "packet-up", "stream-up", "stream-one":
		return mode, nil
	default:
		return "", E.New("unsupported mode: ", mode)
	}
}

type V2RayXHTTPBaseOptions struct {
	Mode                 string                 `json:"mode"`
	Host                 string                 `json:"host,omitempty"`
	Path                 string                 `json:"path,omitempty"`
	Headers              map[string]string      `json:"headers,omitempty"`
	DomainStrategy       option.DomainStrategy  `json:"domain_strategy,omitempty"`
	XPaddingBytes        Xbadoption.Range       `json:"x_padding_bytes"`
	NoGRPCHeader         bool                   `json:"no_grpc_header,omitempty"`
	NoSSEHeader          bool                   `json:"no_sse_header,omitempty"`
	ScMaxEachPostBytes   Xbadoption.Range       `json:"sc_max_each_post_bytes"`
	ScMinPostsIntervalMs Xbadoption.Range       `json:"sc_min_posts_interval_ms"`
	ScMaxBufferedPosts   int64                  `json:"sc_max_buffered_posts,omitempty"`
	ScStreamUpServerSecs Xbadoption.Range       `json:"sc_stream_up_server_secs"`
	Xmux                 *V2RayXHTTPXmuxOptions `json:"xmux"`
}

type V2RayXHTTPOptions struct {
	V2RayXHTTPBaseOptions
	Download *V2RayXHTTPDownloadOptions `json:"download"`
}

type V2RayXHTTPDownloadOptions struct {
	V2RayXHTTPBaseOptions
	option.ServerOptions
	option.OutboundTLSOptionsContainer
	Detour string `json:"detour,omitempty"`
}

func (c *V2RayXHTTPBaseOptions) GetNormalizedPath() string {
	pathAndQuery := strings.SplitN(c.Path, "?", 2)
	path := pathAndQuery[0]
	if path == "" || path[0] != '/' {
		path = "/" + path
	}
	if path[len(path)-1] != '/' {
		path = path + "/"
	}
	return path
}

func (c *V2RayXHTTPBaseOptions) GetNormalizedQuery() string {
	pathAndQuery := strings.SplitN(c.Path, "?", 2)
	query := ""
	if len(pathAndQuery) > 1 {
		query = pathAndQuery[1]
	}
	return query
}

func (c *V2RayXHTTPBaseOptions) GetRequestHeader(rawURL string) http.Header {
	header := http.Header{}
	for k, v := range c.Headers {
		header.Add(k, v)
	}
	u, _ := url.Parse(rawURL)
	// https://www.rfc-editor.org/rfc/rfc7541.html#appendix-B
	// h2's HPACK Header Compression feature employs a huffman encoding using a static table.
	// 'X' is assigned an 8 bit code, so HPACK compression won't change actual padding length on the wire.
	// https://www.rfc-editor.org/rfc/rfc9204.html#section-4.1.2-2
	// h3's similar QPACK feature uses the same huffman table.
	u.RawQuery = "x_padding=" + strings.Repeat("X", int(c.GetNormalizedXPaddingBytes().Rand()))
	header.Set("Referer", u.String())
	return header
}

func (c *V2RayXHTTPBaseOptions) GetNormalizedXPaddingBytes() Xbadoption.Range {
	if c.XPaddingBytes.To == 0 {
		return Xbadoption.Range{
			From: 100,
			To:   1000,
		}
	}
	return c.XPaddingBytes
}

func (c *V2RayXHTTPBaseOptions) GetNormalizedScMaxEachPostBytes() Xbadoption.Range {
	if c.ScMaxEachPostBytes.To == 0 {
		return Xbadoption.Range{
			From: 1000000,
			To:   1000000,
		}
	}
	return c.ScMaxEachPostBytes
}

func (c *V2RayXHTTPBaseOptions) GetNormalizedScMinPostsIntervalMs() Xbadoption.Range {
	if c.ScMinPostsIntervalMs.To == 0 {
		return Xbadoption.Range{
			From: 30,
			To:   30,
		}
	}
	return c.ScMinPostsIntervalMs
}

func (c *V2RayXHTTPBaseOptions) GetNormalizedScMaxBufferedPosts() int {
	if c.ScMaxBufferedPosts == 0 {
		return 30
	}
	return int(c.ScMaxBufferedPosts)
}

func (c *V2RayXHTTPBaseOptions) GetNormalizedScStreamUpServerSecs() Xbadoption.Range {
	if c.ScStreamUpServerSecs.To == 0 {
		return Xbadoption.Range{
			From: 20,
			To:   80,
		}
	}
	return c.ScStreamUpServerSecs
}

type V2RayXHTTPXmuxOptions struct {
	MaxConcurrency   Xbadoption.Range `json:"max_concurrency"`
	MaxConnections   Xbadoption.Range `json:"max_connections"`
	CMaxReuseTimes   Xbadoption.Range `json:"c_max_reuse_times"`
	HMaxRequestTimes Xbadoption.Range `json:"h_max_request_times"`
	HMaxReusableSecs Xbadoption.Range `json:"h_max_reusable_secs"`
	HKeepAlivePeriod int64            `json:"h_keep_alive_period"`
}

func (m V2RayXHTTPXmuxOptions) isZero() bool {
	return m == (V2RayXHTTPXmuxOptions{})
}

func (m *V2RayXHTTPXmuxOptions) Validate() error {
	if m.MaxConnections.To > 0 && m.MaxConcurrency.To > 0 {
		return E.New("maxConnections cannot be specified together with maxConcurrency")
	}
	return nil
}

func (m *V2RayXHTTPXmuxOptions) GetNormalizedMaxConcurrency() Xbadoption.Range {
	if m.isZero() {
		return Xbadoption.Range{From: 1, To: 1}
	}
	return m.MaxConcurrency
}

func (m *V2RayXHTTPXmuxOptions) GetNormalizedMaxConnections() Xbadoption.Range {
	return m.MaxConnections
}

func (m *V2RayXHTTPXmuxOptions) GetNormalizedCMaxReuseTimes() Xbadoption.Range {
	return m.CMaxReuseTimes
}

func (m *V2RayXHTTPXmuxOptions) GetNormalizedHMaxRequestTimes() Xbadoption.Range {
	if m.isZero() && m.HMaxRequestTimes.From == 0 && m.HMaxRequestTimes.To == 0 {
		return Xbadoption.Range{From: 600, To: 900}
	}
	return m.HMaxRequestTimes
}

func (m *V2RayXHTTPXmuxOptions) GetNormalizedHMaxReusableSecs() Xbadoption.Range {
	if m.isZero() && m.HMaxReusableSecs.From == 0 && m.HMaxReusableSecs.To == 0 {
		return Xbadoption.Range{From: 1800, To: 3000}
	}
	return m.HMaxReusableSecs
}
