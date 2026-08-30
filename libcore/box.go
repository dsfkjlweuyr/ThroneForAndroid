package libcore

import (
	"context"
	"errors"
	"fmt"
	"io"
	"libcore/device"
	"log"
	"net"
	"net/http"
	"net/url"
	"runtime"
	"runtime/debug"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/experimental/v2rayapi"
	"github.com/sagernet/sing-box/protocol/group"

	box "github.com/sagernet/sing-box"
	"github.com/sagernet/sing-box/constant"
	sblog "github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing-box/option"
	E "github.com/sagernet/sing/common/exceptions"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"
	"github.com/sagernet/sing/service"
	"github.com/sagernet/sing/service/pause"
)

var mainInstance *BoxInstance
var boxInstanceSequence atomic.Uint64

func VersionBox() string {
	version := []string{
		"sing-box: " + constant.Version,
		runtime.Version() + "@" + runtime.GOOS + "/" + runtime.GOARCH,
	}

	var tags string
	debugInfo, loaded := debug.ReadBuildInfo()
	if loaded {
		for _, setting := range debugInfo.Settings {
			switch setting.Key {
			case "-tags":
				tags = setting.Value
			}
		}
	}

	if tags != "" {
		version = append(version, tags)
	}

	return strings.Join(version, "\n")
}

func ResetAllConnections(system bool) {
	// 官方无 conntrack；等价能力是 NetworkManager.ResetNetwork()：
	// CloseAll 连接 + 通知 endpoint/inbound/outbound.InterfaceUpdated()
	// （hy2/quic 等会丢弃死路径上的会话，下次拨号重建）。
	// 正常切网由 interfaceMonitor → notifyInterfaceUpdate 自动 ResetNetwork
	// （对齐官方 libbox，app 侧不应再叠一层）。本函数仅供手动
	// Action.RESET_UPSTREAM_CONNECTIONS / wakeResetConnections 等显式入口。
	b := mainInstance
	if b == nil || b.Box == nil {
		log.Println("ResetAllConnections: no main instance, skip system=", system)
		return
	}
	b.Network().ResetNetwork()
	log.Println("ResetAllConnections: Network.ResetNetwork() done system=", system)
}

type BoxInstance struct {
	access sync.Mutex

	*box.Box
	cancel context.CancelFunc
	state  int

	v2api        *v2rayapi.StatsService
	selector     *group.Selector
	pauseManager pause.Manager

	diagnosticID  uint64
	diagnosticTag string
	isURLTest     bool
}

func (b *BoxInstance) urlTestTrace(stage string, format string, args ...any) {
	if b == nil || !b.isURLTest {
		return
	}
	prefix := fmt.Sprintf("URLTestTrace goId=%d tag=%q stage=%s ", b.diagnosticID, b.diagnosticTag, stage)
	log.Printf(prefix+format, args...)
}

func (b *BoxInstance) lifecycleTrace(stage string, format string, args ...any) {
	if b == nil || b.isURLTest {
		return
	}
	prefix := fmt.Sprintf("BoxLifecycleTrace goId=%d stage=%s ", b.diagnosticID, stage)
	log.Printf(prefix+format, args...)
}

func NewSingBoxInstance(config string, localTransport LocalDNSTransport) (b *BoxInstance, err error) {
	return newSingBoxInstance(config, localTransport, true)
}

// NewTestSingBoxInstance 供 URL 测速等一次性实例使用：不注册 PlatformLogWriter。
// 官方内核在 PlatformLogWriter != nil 时无条件创建 CacheFile 与 ClashServer
// （官方 box.go 的 needCacheFile/needClashAPI 分支）：主进程批量测速并发创建的
// 大量实例曾共享默认 cache.db（bbolt）把 freelist 写坏，并在 bbolt 定时器
// goroutine 里 panic 导致主进程闪退；即便退而求其次做文件隔离也是纯浪费——
// 测速实例根本不需要 cache 与 Clash API。置 nil 后两者均不再创建，
// box 日志回落到 stderr（logcat 仍可见）。
func NewTestSingBoxInstance(config string, localTransport LocalDNSTransport) (b *BoxInstance, err error) {
	return newSingBoxInstance(config, localTransport, false)
}

func newSingBoxInstance(config string, localTransport LocalDNSTransport, platformLog bool) (b *BoxInstance, err error) {
	defer device.DeferPanicToError("NewSingBoxInstance", func(err_ error) { err = err_ })
	diagnosticID := boxInstanceSequence.Add(1)
	createStarted := time.Now()

	// create box context
	ctx, cancel := context.WithCancel(context.Background())
	ctx = box.Context(ctx,
		nekoboxAndroidInboundRegistry(), nekoboxAndroidOutboundRegistry(), nekoboxAndroidEndpointRegistry(),
		nekoboxAndroidDNSTransportRegistry(localTransport), nekoboxAndroidServiceRegistry(),
	)
	ctx = service.ContextWithDefaultRegistry(ctx)
	// 每 box 注册独立的 PlatformInterface 实例（对齐官方 libbox 结构）。
	// 若用进程级单例，并发测速时各 box 的 Initialize 会互相覆盖 wrapper.networkManager，
	// 导致 interfaceMonitor.UpdateDefaultInterface 里 UpdateInterfaces() 刷的是"最新 box"
	// 的 NetworkManager 缓存，落选 box 自己的接口缓存永远为空 → 所有拨号秒报
	// "no available network interface"（见 platform_box.go 批注）。
	platformWrapper := &boxPlatformInterfaceWrapper{
		diagnosticID: diagnosticID,
		isURLTest:    !platformLog,
	}
	service.MustRegister[adapter.PlatformInterface](ctx, platformWrapper)

	// parse options
	var options option.Options
	err = options.UnmarshalJSONContext(ctx, []byte(config))
	if err != nil {
		if !platformLog {
			log.Printf("URLTestTrace goId=%d stage=parse-config failed elapsed=%s error=%v", diagnosticID, time.Since(createStarted), err)
		}
		return nil, fmt.Errorf("decode config: %v", err)
	}
	if !platformLog {
		log.Printf("URLTestTrace goId=%d stage=parse-config ok elapsed=%s", diagnosticID, time.Since(createStarted))
	}

	// 官方内核不支持 fork 私有的 "geoip:xxx"/"geosite:xxx" 伪路径 local rule-set，
	// 这里预处理：从 geoip.db/geosite.db 生成 .srs 缓存并改写为真实路径。
	if options.Route != nil {
		err = prepareLocalGeoRuleSets(options.Route.RuleSet)
		if err != nil {
			cancel()
			if !platformLog {
				log.Printf("URLTestTrace goId=%d stage=prepare-rulesets failed elapsed=%s error=%v", diagnosticID, time.Since(createStarted), err)
			}
			return nil, fmt.Errorf("prepare geo rule-sets: %v", err)
		}
	}

	// create box
	// 测速实例（platformLog=false）传 nil：见 NewTestSingBoxInstance 批注。
	var logWriter sblog.PlatformWriter
	if platformLog {
		logWriter = boxPlatformLogWriter
		// 官方内核的 PlatformWriter 通道不做级别过滤（observable.go 无条件
		// 转发所有级别），在此记录配置级别供 WriteMessage 侧过滤；
		// 空级别对齐官方默认 trace。级别非法时 box.New 会报同样的错，此处忽略。
		if options.Log != nil && options.Log.Level != "" {
			if parsedLevel, parseErr := sblog.ParseLevel(options.Log.Level); parseErr == nil {
				setPlatformLogLevel(parsedLevel)
			}
		} else {
			setPlatformLogLevel(sblog.LevelTrace)
		}
	}
	instance, err := box.New(box.Options{
		Options:           options,
		Context:           ctx,
		PlatformLogWriter: logWriter,
	})
	if err != nil {
		cancel()
		if !platformLog {
			log.Printf("URLTestTrace goId=%d stage=create-box failed elapsed=%s error=%v", diagnosticID, time.Since(createStarted), err)
		}
		return nil, fmt.Errorf("create service: %v", err)
	}
	diagnosticTag := ""
	if defaultOutbound := instance.Outbound().Default(); defaultOutbound != nil {
		diagnosticTag = defaultOutbound.Tag()
	}
	platformWrapper.diagnosticTag = diagnosticTag

	b = &BoxInstance{
		Box:           instance,
		cancel:        cancel,
		pauseManager:  service.FromContext[pause.Manager](ctx),
		diagnosticID:  diagnosticID,
		diagnosticTag: diagnosticTag,
		isURLTest:     !platformLog,
	}
	b.urlTestTrace("create-box", "ok elapsed=%s", time.Since(createStarted))

	// selector
	if proxy, ok := b.Outbound().Outbound("proxy"); ok {
		if selector, ok := proxy.(*group.Selector); ok {
			b.selector = selector
		}
	}

	return b, nil
}

func (b *BoxInstance) Start() (err error) {
	b.access.Lock()
	defer b.access.Unlock()
	started := time.Now()
	b.urlTestTrace("box-start", "begin")
	b.lifecycleTrace("start", "begin state=%d", b.state)

	defer device.DeferPanicToError("box.Start", func(err_ error) { err = err_ })

	if b.state == 0 {
		b.state = 1
		err = b.Box.Start()
		if err != nil {
			b.urlTestTrace("box-start", "failed elapsed=%s error=%v", time.Since(started), err)
			b.lifecycleTrace("start", "failed state=%d elapsed=%s error=%v", b.state, time.Since(started), err)
		} else {
			b.urlTestTrace("box-start", "ok elapsed=%s", time.Since(started))
			b.lifecycleTrace("start", "success state=%d elapsed=%s", b.state, time.Since(started))
		}
		return err
	}
	b.lifecycleTrace("start", "rejected state=%d elapsed=%s", b.state, time.Since(started))
	return errors.New("already started")
}

func (b *BoxInstance) Close() (err error) {
	b.access.Lock()
	defer b.access.Unlock()
	started := time.Now()
	b.urlTestTrace("box-close", "begin state=%d", b.state)
	b.lifecycleTrace("close", "begin state=%d", b.state)

	defer device.DeferPanicToError("box.Close", func(err_ error) { err = err_ })

	// no double close
	if b.state == 2 {
		b.urlTestTrace("box-close", "skip already-closed elapsed=%s", time.Since(started))
		b.lifecycleTrace("close", "skip already-closed elapsed=%s", time.Since(started))
		return nil
	}
	b.state = 2

	// clear main instance
	if mainInstance == b {
		mainInstance = nil
		goServeProtect(false)
	}

	// close box
	if b.cancel != nil {
		b.cancel()
	}
	if b.Box != nil {
		err = b.Box.Close()
	}
	b.urlTestTrace("box-close", "done elapsed=%s error=%v", time.Since(started), err)
	b.lifecycleTrace("close", "done state=%d elapsed=%s error=%v", b.state, time.Since(started), err)
	return err
}

func (b *BoxInstance) Sleep() {
	if b.pauseManager != nil {
		b.pauseManager.DevicePause()
	}
	// _ = b.Box.Router().ResetNetwork()
}

func (b *BoxInstance) Wake() {
	if b.pauseManager != nil {
		b.pauseManager.DeviceWake()
	}
}

func (b *BoxInstance) SetAsMain() {
	mainInstance = b
	goServeProtect(true)
}

func (b *BoxInstance) SetV2rayStats(outbounds string) {
	b.access.Lock()
	defer b.access.Unlock()
	if b.v2api != nil {
		log.Println("duplicate call of SetV2rayStats")
		return
	}
	// 官方 experimental/v2rayapi 的 StatsService 即 adapter.ConnectionTracker
	b.v2api = v2rayapi.NewStatsService(option.V2RayStatsServiceOptions{
		Enabled:   true,
		Outbounds: strings.Split(outbounds, "\n"),
	})
	b.Box.Router().AppendTracker(b.v2api)
}

func (b *BoxInstance) QueryStats(tag, direct string) int64 {
	if b.v2api == nil {
		return 0
	}
	resp, err := b.v2api.GetStats(context.Background(), &v2rayapi.GetStatsRequest{
		Name:   fmt.Sprintf("outbound>>>%s>>>traffic>>>%s", tag, direct),
		Reset_: true,
	})
	if err != nil || resp.Stat == nil {
		return 0
	}
	return resp.Stat.Value
}

func (b *BoxInstance) SelectOutbound(tag string) bool {
	if b.selector != nil {
		if b.selector.SelectOutbound(tag) {
			// 替代 fork 的 nekoutils.Selector_OnProxySelected 钩子。
			// 注意：仅覆盖 app 内的切换路径；通过 Clash API（yacd 面板）
			// 切换不会触发该回调（官方内核无此钩子，待有具体案例再修）。
			if intfNB4A != nil {
				intfNB4A.Selector_OnProxySelected(b.selector.Tag(), tag)
			}
			return true
		}
	}
	return false
}

func UrlTest(i *BoxInstance, link string, timeout int32) (latency int32, err error) {
	defer device.DeferPanicToError("box.UrlTest", func(err_ error) { err = err_ })
	if i == nil {
		i = mainInstance
	}
	boxPlatformLogWriter.WriteMessage(sblog.LevelDebug, fmt.Sprintf("box.UrlTest link=%s timeout=%dms instance=%v", link, timeout, i != nil))
	if i == nil {
		// 无实例：直连测试（单 GET，计时含拨号）
		client := &http.Client{Timeout: time.Duration(timeout) * time.Millisecond}
		latency, err = urlTestDirect(client, link)
	} else {
		var connectionTracker adapter.ConnectionTracker
		if i.v2api != nil {
			connectionTracker = i.v2api
		}
		latency, err = urlTest(i, connectionTracker, link, timeout)
	}
	boxPlatformLogWriter.WriteMessage(sblog.LevelDebug, fmt.Sprintf("box.UrlTest result latency=%dms err=%v", latency, err))
	return
}

// urlTest 替代 libneko/speedtest.UrlTest 与 fork 的 boxapi.CreateProxyHttpClient，
// 对齐 husi libcore/ping.go 的"显式拨号 + 连接复用 + 双 HEAD 请求"模式：
// 第一次 HEAD 预热（含握手，不计时），第二次 HEAD 复用同一连接计时。
// 收益：① 延迟为纯 RTT，跨协议可比、贴近实际使用时连接复用的体感；
// ② 第二次请求验证连接持续性——"首包能通但随即断开"的节点不再假成功。
// 超时由 ctx 全程控制（拨号 + 两次请求共用 timeout 预算）。
func urlTest(instance *BoxInstance, tracker adapter.ConnectionTracker, link string, timeout int32) (int32, error) {
	totalStarted := time.Now()
	instance.urlTestTrace("urltest", "begin link=%s timeout=%dms", link, timeout)
	linkURL, err := url.Parse(link)
	if err != nil {
		instance.urlTestTrace("parse-link", "failed elapsed=%s error=%v", time.Since(totalStarted), err)
		return 0, E.Cause(err, "parse test link")
	}
	hostname := linkURL.Hostname()
	port := linkURL.Port()
	if port == "" {
		switch linkURL.Scheme {
		case "http":
			port = "80"
		case "https":
			port = "443"
		default:
			instance.urlTestTrace("parse-link", "failed elapsed=%s error=unsupported-scheme scheme=%q", time.Since(totalStarted), linkURL.Scheme)
			return 0, E.New("unsupported test link scheme: ", linkURL.Scheme)
		}
	}

	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(timeout)*time.Millisecond)
	defer cancel()

	outbound := instance.Outbound().Default()
	if outbound == nil {
		instance.urlTestTrace("select-outbound", "failed elapsed=%s error=no-default-outbound", time.Since(totalStarted))
		return 0, E.New("no default outbound")
	}
	destination := M.ParseSocksaddrHostPortStr(hostname, port)
	dialStarted := time.Now()
	instance.urlTestTrace("dial", "begin outbound=%q destination=%s", outbound.Tag(), destination)
	conn, err := outbound.DialContext(ctx, N.NetworkTCP, destination)
	if err != nil {
		instance.urlTestTrace("dial", "failed elapsed=%s totalElapsed=%s error=%v", time.Since(dialStarted), time.Since(totalStarted), err)
		return 0, err
	}
	instance.urlTestTrace("dial", "ok elapsed=%s", time.Since(dialStarted))
	if tracker != nil {
		conn = tracker.RoutedConnection(ctx, conn, adapter.InboundContext{
			Outbound:    outbound.Tag(),
			Destination: destination,
		}, nil, outbound)
	}
	defer conn.Close()

	// client 恒复用上面建立的连接（keep-alive）；重定向不跟随（generate_204 类直返）。
	client := &http.Client{
		Transport: &http.Transport{
			DialContext: func(context.Context, string, string) (net.Conn, error) {
				return conn, nil
			},
		},
		CheckRedirect: func(req *http.Request, via []*http.Request) error {
			return http.ErrUseLastResponse
		},
	}
	defer client.CloseIdleConnections()

	doHead := func() error {
		req, err := http.NewRequestWithContext(ctx, http.MethodHead, link, nil)
		if err != nil {
			return err
		}
		resp, err := client.Do(req)
		if err != nil {
			return err
		}
		_ = resp.Body.Close()
		if resp.StatusCode >= 400 {
			return E.New("unexpected status: ", resp.Status)
		}
		return nil
	}

	// 第一次：预热（建立 TLS 会话等），不计时
	warmupStarted := time.Now()
	instance.urlTestTrace("warmup-head", "begin")
	if err = doHead(); err != nil {
		boxPlatformLogWriter.WriteMessage(sblog.LevelDebug, fmt.Sprintf("box.urlTest warmup request failed: %v", err))
		instance.urlTestTrace("warmup-head", "failed elapsed=%s totalElapsed=%s error=%v", time.Since(warmupStarted), time.Since(totalStarted), err)
		return 0, err
	}
	instance.urlTestTrace("warmup-head", "ok elapsed=%s", time.Since(warmupStarted))
	// 第二次：复用连接，纯 RTT 计时
	start := time.Now()
	instance.urlTestTrace("measure-head", "begin reusedConnection=true")
	if err = doHead(); err != nil {
		boxPlatformLogWriter.WriteMessage(sblog.LevelDebug, fmt.Sprintf("box.urlTest measure request failed after %dms: %v", time.Since(start).Milliseconds(), err))
		instance.urlTestTrace("measure-head", "failed elapsed=%s totalElapsed=%s error=%v", time.Since(start), time.Since(totalStarted), err)
		return 0, err
	}
	latency := int32(time.Since(start).Milliseconds())
	boxPlatformLogWriter.WriteMessage(sblog.LevelDebug, fmt.Sprintf("box.urlTest ok latency=%dms", latency))
	instance.urlTestTrace("measure-head", "ok latency=%dms totalElapsed=%s", latency, time.Since(totalStarted))
	return latency, nil
}

// urlTestDirect 为无 box 实例时的直连测速：单 GET，计时含拨号。
func urlTestDirect(client *http.Client, link string) (int32, error) {
	req, err := http.NewRequest(http.MethodGet, link, nil)
	if err != nil {
		return 0, err
	}
	start := time.Now()
	resp, err := client.Do(req)
	if err != nil {
		return 0, err
	}
	_ = resp.Body.Close()
	if resp.StatusCode >= 400 {
		return 0, E.New("unexpected status: ", resp.Status)
	}
	return int32(time.Since(start).Milliseconds()), nil
}

var protectCloser io.Closer

func goServeProtect(start bool) {
	if protectCloser != nil {
		protectCloser.Close()
		protectCloser = nil
	}
	if start {
		protectCloser = serveProtect("protect_path", func(fd int) {
			intfBox.AutoDetectInterfaceControl(int32(fd))
		})
	}
}
