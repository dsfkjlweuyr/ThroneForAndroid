package libcore

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"
)

type speedTestRequestLog struct {
	access  sync.Mutex
	methods map[string]map[string]int
}

func newSpeedTestRequestLog() *speedTestRequestLog {
	return &speedTestRequestLog{methods: make(map[string]map[string]int)}
}

func (l *speedTestRequestLog) record(request *http.Request) {
	l.access.Lock()
	defer l.access.Unlock()
	if l.methods[request.URL.Path] == nil {
		l.methods[request.URL.Path] = make(map[string]int)
	}
	l.methods[request.URL.Path][request.Method]++
}

func (l *speedTestRequestLog) count(path string, method string) int {
	l.access.Lock()
	defer l.access.Unlock()
	return l.methods[path][method]
}

type speedTestFixture struct {
	server   *httptest.Server
	requests *speedTestRequestLog
	dials    atomic.Int64
}

func newSpeedTestFixture(t *testing.T) *speedTestFixture {
	t.Helper()
	requestLog := newSpeedTestRequestLog()
	var serverURL string
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		requestLog.record(request)
		switch request.URL.Path {
		case "/servers-primary":
			writer.WriteHeader(http.StatusOK)
		case "/servers-fallback":
			writer.Header().Set("Content-Type", "application/xml")
			_, _ = fmt.Fprintf(writer, `<?xml version="1.0"?><settings><servers><server url="%s/speedtest/upload.php" lat="0" lon="0" name="Fixture" country="Testland" sponsor="Throne" id="42" host="fixture.invalid" /></servers></settings>`, serverURL)
		case "/speedtest/latency.txt":
			_, _ = writer.Write([]byte("test=test"))
		case "/speedtest/random1000x1000.jpg", "/simple.bin":
			_, _ = writer.Write([]byte(strings.Repeat("d", 4096)))
		case "/speedtest/upload.php":
			_, _ = io.Copy(io.Discard, request.Body)
			writer.WriteHeader(http.StatusOK)
			_, _ = writer.Write([]byte("ok"))
		default:
			http.NotFound(writer, request)
		}
	}))
	serverURL = server.URL
	t.Cleanup(server.Close)
	return &speedTestFixture{
		server:   server,
		requests: requestLog,
	}
}

func (f *speedTestFixture) dialer(ctx context.Context, network string, address string) (net.Conn, error) {
	f.dials.Add(1)
	return (&net.Dialer{}).DialContext(ctx, network, address)
}

func (f *speedTestFixture) config(t *testing.T, mode string) speedTestConfig {
	t.Helper()
	config, err := newSpeedTestConfig(
		mode,
		150,
		f.server.URL+"/servers-primary",
		f.server.URL+"/servers-fallback",
		f.server.URL+"/simple.bin",
	)
	if err != nil {
		t.Fatal(err)
	}
	config.connections = 2
	config.uploadPayloadBytes = 1024
	return config
}

func TestDeriveSpeedTestURLs(t *testing.T) {
	latencyURL, err := deriveSpeedTestLatencyURL("https://example.com/speedtest/upload.php?ignored=false")
	if err != nil {
		t.Fatal(err)
	}
	if latencyURL != "https://example.com/speedtest/latency.txt?ignored=false" {
		t.Fatalf("unexpected latency URL: %s", latencyURL)
	}

	downloadURL, err := deriveSpeedTestDownloadURL("https://example.com/speedtest/upload.php", 1000)
	if err != nil {
		t.Fatal(err)
	}
	if downloadURL != "https://example.com/speedtest/random1000x1000.jpg" {
		t.Fatalf("unexpected download URL: %s", downloadURL)
	}
}

func TestExecuteSpeedTestModesAndOutboundDialer(t *testing.T) {
	testCases := []struct {
		name             string
		mode             string
		expectDiscovery  bool
		expectDownload   bool
		expectUpload     bool
		expectSimple     bool
	}{
		{name: "download and upload", mode: SpeedTestModeDownloadUpload, expectDiscovery: true, expectDownload: true, expectUpload: true},
		{name: "download only", mode: SpeedTestModeDownload, expectDiscovery: true, expectDownload: true},
		{name: "upload only", mode: SpeedTestModeUpload, expectDiscovery: true, expectUpload: true},
		{name: "simple download", mode: SpeedTestModeSimpleDownload, expectSimple: true},
	}

	for _, testCase := range testCases {
		t.Run(testCase.name, func(t *testing.T) {
			fixture := newSpeedTestFixture(t)
			var samples atomic.Int64
			outcome, err := executeSpeedTest(
				context.Background(),
				fixture.config(t, testCase.mode),
				fixture.dialer,
				func(speedTestProgress) { samples.Add(1) },
			)
			if err != nil {
				t.Fatal(err)
			}
			if fixture.dials.Load() == 0 {
				t.Fatal("expected every HTTP connection to use the injected outbound dialer")
			}
			if samples.Load() == 0 {
				t.Fatal("expected at least one progress sample")
			}

			assertSpeedTestRequest(t, fixture.requests, "/servers-primary", http.MethodGet, testCase.expectDiscovery)
			assertSpeedTestRequest(t, fixture.requests, "/servers-fallback", http.MethodGet, testCase.expectDiscovery)
			assertSpeedTestRequest(t, fixture.requests, "/speedtest/random1000x1000.jpg", http.MethodGet, testCase.expectDownload)
			assertSpeedTestRequest(t, fixture.requests, "/speedtest/upload.php", http.MethodPost, testCase.expectUpload)
			assertSpeedTestRequest(t, fixture.requests, "/simple.bin", http.MethodGet, testCase.expectSimple)

			if testCase.expectDownload || testCase.expectSimple {
				if outcome.downloadBytes == 0 || outcome.downloadBitsPerSecond == 0 {
					t.Fatalf("expected download measurements, got bytes=%d rate=%d", outcome.downloadBytes, outcome.downloadBitsPerSecond)
				}
			} else if outcome.downloadBytes != 0 || outcome.downloadBitsPerSecond != 0 {
				t.Fatalf("download phase was not trimmed: %+v", outcome)
			}
			if testCase.expectUpload {
				if outcome.uploadBytes == 0 || outcome.uploadBitsPerSecond == 0 {
					t.Fatalf("expected upload measurements, got bytes=%d rate=%d", outcome.uploadBytes, outcome.uploadBitsPerSecond)
				}
			} else if outcome.uploadBytes != 0 || outcome.uploadBitsPerSecond != 0 {
				t.Fatalf("upload phase was not trimmed: %+v", outcome)
			}
			if testCase.expectDiscovery {
				if outcome.server == nil || outcome.server.ID != "42" || outcome.latencyMs < 0 {
					t.Fatalf("unexpected selected server: %+v", outcome)
				}
			} else if outcome.server != nil {
				t.Fatalf("simple download must not discover a server: %+v", outcome.server)
			}
		})
	}
}

func assertSpeedTestRequest(t *testing.T, log *speedTestRequestLog, path string, method string, expected bool) {
	t.Helper()
	count := log.count(path, method)
	if expected && count == 0 {
		t.Fatalf("expected %s %s", method, path)
	}
	if !expected && count != 0 {
		t.Fatalf("did not expect %s %s, got %d request(s)", method, path, count)
	}
}

func TestSimpleDownloadCountsActualBytes(t *testing.T) {
	const payload = "actual response bytes"
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		_, _ = writer.Write([]byte(payload))
	}))
	defer server.Close()

	client := server.Client()
	var final speedTestProgress
	rate, transferred, err := runSimpleDownload(
		context.Background(),
		client,
		server.URL,
		time.Second,
		func(progress speedTestProgress) { final = progress },
	)
	if err != nil {
		t.Fatal(err)
	}
	if transferred != int64(len(payload)) || final.downloadBytes != int64(len(payload)) {
		t.Fatalf("expected %d actual bytes, got result=%d sample=%d", len(payload), transferred, final.downloadBytes)
	}
	if rate <= 0 || final.downloadBitsPerSecond <= 0 {
		t.Fatalf("expected positive rate, got result=%d sample=%d", rate, final.downloadBitsPerSecond)
	}
}

func TestSimpleDownloadTimeoutAndCancellation(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		writer.WriteHeader(http.StatusOK)
		if flusher, ok := writer.(http.Flusher); ok {
			flusher.Flush()
		}
		<-request.Context().Done()
	}))
	defer server.Close()

	t.Run("timeout", func(t *testing.T) {
		_, _, err := runSimpleDownload(context.Background(), server.Client(), server.URL, 20*time.Millisecond, func(speedTestProgress) {})
		if !errors.Is(err, context.DeadlineExceeded) {
			t.Fatalf("expected deadline exceeded, got %v", err)
		}
	})

	t.Run("cancel", func(t *testing.T) {
		ctx, cancel := context.WithCancel(context.Background())
		time.AfterFunc(20*time.Millisecond, cancel)
		_, _, err := runSimpleDownload(ctx, server.Client(), server.URL, time.Second, func(speedTestProgress) {})
		if !errors.Is(err, context.Canceled) {
			t.Fatalf("expected cancellation, got %v", err)
		}
	})
}

func TestTransferPhaseStopsAtConfiguredTimeout(t *testing.T) {
	started := time.Now()
	rate, transferred, err := runTransferPhase(
		context.Background(),
		&http.Client{},
		SpeedTestStageDownload,
		30*time.Millisecond,
		2,
		func(ctx context.Context, total *atomic.Int64) error {
			total.Add(128)
			select {
			case <-ctx.Done():
				return ctx.Err()
			case <-time.After(time.Millisecond):
				return nil
			}
		},
		func(int64, int64) {},
	)
	if err != nil {
		t.Fatal(err)
	}
	if elapsed := time.Since(started); elapsed < 20*time.Millisecond || elapsed > time.Second {
		t.Fatalf("transfer phase ignored configured timeout: %s", elapsed)
	}
	if rate <= 0 || transferred <= 0 {
		t.Fatalf("expected measured transfer, got rate=%d bytes=%d", rate, transferred)
	}
}

func TestSpeedTestSessionCancellationResult(t *testing.T) {
	session := &SpeedTestSession{
		result: SpeedTestResult{Stage: SpeedTestStageDownload},
		done:   make(chan struct{}),
	}
	session.finish(&speedTestOutcome{downloadBytes: 128}, context.Canceled)

	result := session.GetResult()
	if !result.Done || !result.Cancelled || result.Stage != SpeedTestStageCancelled {
		t.Fatalf("unexpected cancellation result: %+v", result)
	}
	if result.DownloadBytes != 128 || result.Error != "" {
		t.Fatalf("cancellation must preserve measurements without reporting success/error: %+v", result)
	}
}

func TestSpeedTestHTTPErrorPropagation(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		http.Error(writer, "unavailable", http.StatusServiceUnavailable)
	}))
	defer server.Close()

	config, err := newSpeedTestConfig(
		SpeedTestModeSimpleDownload,
		100,
		"",
		"",
		server.URL,
	)
	if err != nil {
		t.Fatal(err)
	}
	_, err = executeSpeedTest(context.Background(), config, (&net.Dialer{}).DialContext, func(speedTestProgress) {})
	if err == nil || !strings.Contains(err.Error(), "503 Service Unavailable") {
		t.Fatalf("expected HTTP status error, got %v", err)
	}
}

func TestDownloadPhaseHTTPErrorPropagation(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		http.Error(writer, "unavailable", http.StatusServiceUnavailable)
	}))
	defer server.Close()

	var transferred atomic.Int64
	err := downloadSpeedTestPayload(context.Background(), server.Client(), server.URL, &transferred)
	if err == nil || !strings.Contains(err.Error(), "503 Service Unavailable") {
		t.Fatalf("expected download HTTP status error, got %v", err)
	}
	if transferred.Load() != 0 {
		t.Fatalf("failed response must not count as downloaded payload, got %d bytes", transferred.Load())
	}
}

func TestNewSpeedTestConfigDefaultsAndValidation(t *testing.T) {
	config, err := newSpeedTestConfig("", 0, "", "", "")
	if err != nil {
		t.Fatal(err)
	}
	if config.mode != SpeedTestModeDownloadUpload ||
		config.timeout != time.Duration(DefaultSpeedTestTimeoutMs)*time.Millisecond ||
		config.connections != DefaultSpeedTestConnections ||
		config.serverListURL != DefaultSpeedTestServerListURL ||
		config.fallbackServerListURL != FallbackSpeedTestServerListURL ||
		config.simpleDownloadURL != DefaultSimpleDownloadURL {
		t.Fatalf("unexpected defaults: %+v", config)
	}

	if _, err := newSpeedTestConfig("invalid", 100, "", "", ""); err == nil {
		t.Fatal("expected invalid mode to be rejected")
	}
	if _, err := newSpeedTestConfig(SpeedTestModeSimpleDownload, 100, "", "", "file:///tmp/test"); err == nil {
		t.Fatal("expected non-HTTP simple download URL to be rejected")
	}
}
