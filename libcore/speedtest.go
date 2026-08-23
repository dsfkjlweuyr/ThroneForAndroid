package libcore

import (
	"context"
	"encoding/json"
	"encoding/xml"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"path"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/Mahdi-zarei/speedtest-go/speedtest"
	M "github.com/sagernet/sing/common/metadata"
)

const (
	SpeedTestModeDownloadUpload = "download_upload"
	SpeedTestModeDownload       = "download"
	SpeedTestModeUpload         = "upload"
	SpeedTestModeSimpleDownload = "simple_download"

	SpeedTestStagePending    = "pending"
	SpeedTestStageDiscovery  = "discovery"
	SpeedTestStageLatency    = "latency"
	SpeedTestStageDownload   = "download"
	SpeedTestStageUpload     = "upload"
	SpeedTestStageComplete   = "complete"
	SpeedTestStageCancelled  = "cancelled"
	SpeedTestStageError      = "error"

	DefaultSpeedTestTimeoutMs       = int32(5000)
	DefaultSpeedTestConnections     = 8
	DefaultSpeedTestServerListURL   = "https://www.speedtest.net/api/js/servers"
	FallbackSpeedTestServerListURL  = "https://www.speedtest.net/speedtest-servers-static.php"
	DefaultSimpleDownloadURL        = "http://cachefly.cachefly.net/1mb.test"
	speedTestDownloadImageSize      = 1000
	speedTestUploadPayloadBytes     = int64(999490)
	speedTestSampleInterval         = 100 * time.Millisecond
	speedTestMaximumServerListBytes = 4 * 1024 * 1024
)

// SpeedTestResult is an immutable snapshot returned to Android. All rates use
// bits per second and all byte counters contain bytes actually transferred.
type SpeedTestResult struct {
	ProfileKey            string
	Mode                  string
	Stage                 string
	DownloadBitsPerSecond int64
	UploadBitsPerSecond   int64
	DownloadBytes         int64
	UploadBytes           int64
	LatencyMs             int64
	ServerID              string
	ServerName            string
	ServerCountry         string
	ServerSponsor         string
	Error                 string
	Cancelled             bool
	Done                  bool
}

type speedTestConfig struct {
	mode                  string
	timeout               time.Duration
	connections           int
	serverListURL         string
	fallbackServerListURL string
	simpleDownloadURL     string
	downloadImageSize     int
	uploadPayloadBytes    int64
}

type speedTestProgress struct {
	stage                 string
	downloadBitsPerSecond int64
	uploadBitsPerSecond   int64
	downloadBytes         int64
	uploadBytes           int64
	latencyMs             int64
	server                *speedtest.Server
}

type speedTestOutcome struct {
	downloadBitsPerSecond int64
	uploadBitsPerSecond   int64
	downloadBytes         int64
	uploadBytes           int64
	latencyMs             int64
	server                *speedtest.Server
}

type speedTestDialer func(ctx context.Context, network string, address string) (net.Conn, error)
type speedTestProgressFunc func(progress speedTestProgress)

// SpeedTestSession owns one isolated test box and one cancellable speed test.
// Start is asynchronous; Android polls GetResult until Done becomes true.
type SpeedTestSession struct {
	access sync.RWMutex

	box    *BoxInstance
	config speedTestConfig
	result SpeedTestResult

	ctx    context.Context
	cancel context.CancelFunc
	done   chan struct{}

	started bool
	closed  bool
}

// NewSpeedTestSession creates a speed-test box without a PlatformLogWriter,
// preserving the existing per-box platform wrapper, interface monitor and fd
// protect behavior. Empty URLs select the Throne-compatible defaults.
func NewSpeedTestSession(
	profileKey string,
	boxConfig string,
	localTransport LocalDNSTransport,
	mode string,
	timeoutMs int32,
	serverListURL string,
	fallbackServerListURL string,
	simpleDownloadURL string,
) (*SpeedTestSession, error) {
	config, err := newSpeedTestConfig(mode, timeoutMs, serverListURL, fallbackServerListURL, simpleDownloadURL)
	if err != nil {
		return nil, err
	}

	testBox, err := NewTestSingBoxInstance(boxConfig, localTransport)
	if err != nil {
		return nil, fmt.Errorf("create speed-test box: %w", err)
	}

	ctx, cancel := context.WithCancel(context.Background())
	return &SpeedTestSession{
		box:    testBox,
		config: config,
		result: SpeedTestResult{
			ProfileKey: profileKey,
			Mode:       config.mode,
			Stage:      SpeedTestStagePending,
		},
		ctx:    ctx,
		cancel: cancel,
		done:   make(chan struct{}),
	}, nil
}

func newSpeedTestConfig(
	mode string,
	timeoutMs int32,
	serverListURL string,
	fallbackServerListURL string,
	simpleDownloadURL string,
) (speedTestConfig, error) {
	if mode == "" {
		mode = SpeedTestModeDownloadUpload
	}
	switch mode {
	case SpeedTestModeDownloadUpload, SpeedTestModeDownload, SpeedTestModeUpload, SpeedTestModeSimpleDownload:
	default:
		return speedTestConfig{}, fmt.Errorf("unsupported speed-test mode %q", mode)
	}
	if timeoutMs <= 0 {
		timeoutMs = DefaultSpeedTestTimeoutMs
	}
	if serverListURL == "" {
		serverListURL = DefaultSpeedTestServerListURL
	}
	if fallbackServerListURL == "" {
		fallbackServerListURL = FallbackSpeedTestServerListURL
	}
	if simpleDownloadURL == "" {
		simpleDownloadURL = DefaultSimpleDownloadURL
	}

	if mode == SpeedTestModeSimpleDownload {
		if err := validateSpeedTestURL(simpleDownloadURL); err != nil {
			return speedTestConfig{}, fmt.Errorf("invalid simple download URL: %w", err)
		}
	} else {
		if err := validateSpeedTestURL(serverListURL); err != nil {
			return speedTestConfig{}, fmt.Errorf("invalid server-list URL: %w", err)
		}
		if err := validateSpeedTestURL(fallbackServerListURL); err != nil {
			return speedTestConfig{}, fmt.Errorf("invalid fallback server-list URL: %w", err)
		}
	}

	return speedTestConfig{
		mode:                  mode,
		timeout:               time.Duration(timeoutMs) * time.Millisecond,
		connections:           DefaultSpeedTestConnections,
		serverListURL:         serverListURL,
		fallbackServerListURL: fallbackServerListURL,
		simpleDownloadURL:     simpleDownloadURL,
		downloadImageSize:     speedTestDownloadImageSize,
		uploadPayloadBytes:    speedTestUploadPayloadBytes,
	}, nil
}

func validateSpeedTestURL(value string) error {
	parsed, err := url.Parse(value)
	if err != nil {
		return err
	}
	if parsed.Host == "" || (parsed.Scheme != "http" && parsed.Scheme != "https") {
		return errors.New("URL must use http or https and include a host")
	}
	return nil
}

// Start starts the box synchronously, then runs network work asynchronously.
func (s *SpeedTestSession) Start() error {
	s.access.Lock()
	if s.closed {
		s.access.Unlock()
		return errors.New("speed-test session is closed")
	}
	if s.started {
		s.access.Unlock()
		return errors.New("speed-test session is already started")
	}
	s.started = true
	s.access.Unlock()

	if err := s.box.Start(); err != nil {
		s.finish(nil, fmt.Errorf("start speed-test box: %w", err))
		_ = s.box.Close()
		return err
	}

	go s.run()
	return nil
}

func (s *SpeedTestSession) run() {
	defer func() {
		_ = s.box.Close()
	}()

	outbound := s.box.Outbound().Default()
	if outbound == nil {
		s.finish(nil, errors.New("speed-test box has no default outbound"))
		return
	}

	dialer := func(ctx context.Context, network string, address string) (net.Conn, error) {
		return outbound.DialContext(ctx, network, M.ParseSocksaddr(address))
	}
	outcome, err := executeSpeedTest(s.ctx, s.config, dialer, s.updateProgress)
	s.finish(outcome, err)
}

func (s *SpeedTestSession) updateProgress(progress speedTestProgress) {
	s.access.Lock()
	defer s.access.Unlock()
	if s.result.Done {
		return
	}
	s.result.Stage = progress.stage
	s.result.DownloadBitsPerSecond = progress.downloadBitsPerSecond
	s.result.UploadBitsPerSecond = progress.uploadBitsPerSecond
	s.result.DownloadBytes = progress.downloadBytes
	s.result.UploadBytes = progress.uploadBytes
	if progress.server != nil {
		s.result.LatencyMs = progress.latencyMs
		s.result.ServerID = progress.server.ID
		s.result.ServerName = progress.server.Name
		s.result.ServerCountry = progress.server.Country
		s.result.ServerSponsor = progress.server.Sponsor
	}
}

func (s *SpeedTestSession) finish(outcome *speedTestOutcome, runErr error) {
	s.access.Lock()
	if s.result.Done {
		s.access.Unlock()
		return
	}
	if outcome != nil {
		s.result.DownloadBitsPerSecond = outcome.downloadBitsPerSecond
		s.result.UploadBitsPerSecond = outcome.uploadBitsPerSecond
		s.result.DownloadBytes = outcome.downloadBytes
		s.result.UploadBytes = outcome.uploadBytes
		s.result.LatencyMs = outcome.latencyMs
		if outcome.server != nil {
			s.result.ServerID = outcome.server.ID
			s.result.ServerName = outcome.server.Name
			s.result.ServerCountry = outcome.server.Country
			s.result.ServerSponsor = outcome.server.Sponsor
		}
	}
	if runErr == nil {
		s.result.Stage = SpeedTestStageComplete
	} else if errors.Is(runErr, context.Canceled) {
		s.result.Stage = SpeedTestStageCancelled
		s.result.Cancelled = true
	} else {
		s.result.Stage = SpeedTestStageError
		s.result.Error = runErr.Error()
	}
	s.result.Done = true
	close(s.done)
	s.access.Unlock()
}

// GetResult returns a copy so callers cannot mutate live session state.
func (s *SpeedTestSession) GetResult() *SpeedTestResult {
	s.access.RLock()
	defer s.access.RUnlock()
	result := s.result
	return &result
}

// Cancel stops the current request and all remaining phase work.
func (s *SpeedTestSession) Cancel() {
	s.cancel()
}

// Close cancels the session, waits for an active run and releases its box.
func (s *SpeedTestSession) Close() error {
	s.access.Lock()
	if s.closed {
		s.access.Unlock()
		return nil
	}
	s.closed = true
	started := s.started
	s.access.Unlock()

	s.cancel()
	if started {
		<-s.done
	}
	return s.box.Close()
}

func executeSpeedTest(
	ctx context.Context,
	config speedTestConfig,
	dialer speedTestDialer,
	progress speedTestProgressFunc,
) (*speedTestOutcome, error) {
	transport := &http.Transport{
		DialContext:           dialer,
		ForceAttemptHTTP2:     true,
		MaxIdleConns:          config.connections * 4,
		MaxIdleConnsPerHost:   config.connections,
		IdleConnTimeout:       30 * time.Second,
		TLSHandshakeTimeout:   config.timeout,
		ExpectContinueTimeout: time.Second,
	}
	defer transport.CloseIdleConnections()
	client := &http.Client{Transport: transport}
	outcome := &speedTestOutcome{}

	if config.mode == SpeedTestModeSimpleDownload {
		progress(speedTestProgress{stage: SpeedTestStageDownload})
		rate, transferred, err := runSimpleDownload(ctx, client, config.simpleDownloadURL, config.timeout, progress)
		outcome.downloadBitsPerSecond = rate
		outcome.downloadBytes = transferred
		return outcome, err
	}

	discoveryCtx, cancelDiscovery := context.WithTimeout(ctx, config.timeout)
	progress(speedTestProgress{stage: SpeedTestStageDiscovery})
	servers, err := fetchSpeedTestServers(discoveryCtx, client, config.serverListURL, config.fallbackServerListURL)
	if err != nil {
		cancelDiscovery()
		return outcome, fmt.Errorf("discover speed-test servers: %w", err)
	}
	progress(speedTestProgress{stage: SpeedTestStageLatency})
	selected, latency, err := selectLowestLatencyServer(discoveryCtx, client, servers)
	cancelDiscovery()
	if err != nil {
		return outcome, fmt.Errorf("select speed-test server: %w", err)
	}
	outcome.server = selected
	outcome.latencyMs = latency.Milliseconds()
	progress(speedTestProgress{
		stage:     SpeedTestStageLatency,
		latencyMs: outcome.latencyMs,
		server:    selected,
	})

	if config.mode == SpeedTestModeDownloadUpload || config.mode == SpeedTestModeDownload {
		downloadURL, err := deriveSpeedTestDownloadURL(selected.URL, config.downloadImageSize)
		if err != nil {
			return outcome, fmt.Errorf("derive speed-test download URL: %w", err)
		}
		rate, transferred, err := runTransferPhase(
			ctx,
			client,
			SpeedTestStageDownload,
			config.timeout,
			config.connections,
			func(requestCtx context.Context, total *atomic.Int64) error {
				return downloadSpeedTestPayload(requestCtx, client, downloadURL, total)
			},
			func(rate int64, total int64) {
				progress(speedTestProgress{
					stage:                 SpeedTestStageDownload,
					downloadBitsPerSecond: rate,
					downloadBytes:         total,
				})
			},
		)
		outcome.downloadBitsPerSecond = rate
		outcome.downloadBytes = transferred
		if err != nil {
			return outcome, fmt.Errorf("download speed test: %w", err)
		}
	}

	if config.mode == SpeedTestModeDownloadUpload || config.mode == SpeedTestModeUpload {
		rate, transferred, err := runTransferPhase(
			ctx,
			client,
			SpeedTestStageUpload,
			config.timeout,
			config.connections,
			func(requestCtx context.Context, total *atomic.Int64) error {
				return uploadSpeedTestPayload(requestCtx, client, selected.URL, config.uploadPayloadBytes, total)
			},
			func(rate int64, total int64) {
				progress(speedTestProgress{
					stage:                 SpeedTestStageUpload,
					downloadBitsPerSecond: outcome.downloadBitsPerSecond,
					uploadBitsPerSecond:   rate,
					downloadBytes:         outcome.downloadBytes,
					uploadBytes:           total,
				})
			},
		)
		outcome.uploadBitsPerSecond = rate
		outcome.uploadBytes = transferred
		if err != nil {
			return outcome, fmt.Errorf("upload speed test: %w", err)
		}
	}

	return outcome, nil
}

func fetchSpeedTestServers(
	ctx context.Context,
	client *http.Client,
	primaryURL string,
	fallbackURL string,
) (speedtest.Servers, error) {
	primaryServers, primaryErr := fetchSpeedTestServerList(ctx, client, primaryURL)
	if primaryErr == nil && len(primaryServers) > 0 {
		return primaryServers, nil
	}

	fallbackServers, fallbackErr := fetchSpeedTestServerList(ctx, client, fallbackURL)
	if fallbackErr == nil && len(fallbackServers) > 0 {
		return fallbackServers, nil
	}
	if primaryErr != nil && fallbackErr != nil {
		return nil, fmt.Errorf("primary endpoint: %v; fallback endpoint: %v", primaryErr, fallbackErr)
	}
	return nil, speedtest.ErrServerNotFound
}

func fetchSpeedTestServerList(ctx context.Context, client *http.Client, endpoint string) (speedtest.Servers, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
	if err != nil {
		return nil, err
	}
	resp, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if err := requireSuccessfulStatus(resp); err != nil {
		return nil, err
	}
	body, err := io.ReadAll(io.LimitReader(resp.Body, speedTestMaximumServerListBytes+1))
	if err != nil {
		return nil, err
	}
	if len(body) > speedTestMaximumServerListBytes {
		return nil, errors.New("server list exceeds size limit")
	}
	if len(strings.TrimSpace(string(body))) == 0 {
		return nil, speedtest.ErrServerNotFound
	}
	return decodeSpeedTestServers(body)
}

func decodeSpeedTestServers(body []byte) (speedtest.Servers, error) {
	var servers speedtest.Servers
	if err := json.Unmarshal(body, &servers); err == nil && len(servers) > 0 {
		return servers, nil
	}

	var document struct {
		Servers speedtest.Servers `xml:"servers>server"`
	}
	if err := xml.Unmarshal(body, &document); err != nil {
		return nil, fmt.Errorf("decode server list: %w", err)
	}
	if len(document.Servers) == 0 {
		return nil, speedtest.ErrServerNotFound
	}
	return document.Servers, nil
}

func selectLowestLatencyServer(
	ctx context.Context,
	client *http.Client,
	servers speedtest.Servers,
) (*speedtest.Server, time.Duration, error) {
	type latencyResult struct {
		server  *speedtest.Server
		latency time.Duration
		err     error
	}

	results := make(chan latencyResult, len(servers))
	var waitGroup sync.WaitGroup
	for _, candidate := range servers {
		if candidate == nil || candidate.URL == "" {
			continue
		}
		server := candidate
		waitGroup.Add(1)
		go func() {
			defer waitGroup.Done()
			latency, err := measureSpeedTestLatency(ctx, client, server.URL)
			results <- latencyResult{server: server, latency: latency, err: err}
		}()
	}
	go func() {
		waitGroup.Wait()
		close(results)
	}()

	var selected *speedtest.Server
	var lowest time.Duration
	var firstErr error
	for result := range results {
		if result.err != nil {
			if firstErr == nil {
				firstErr = result.err
			}
			continue
		}
		if selected == nil || result.latency < lowest {
			selected = result.server
			lowest = result.latency
		}
	}
	if selected == nil {
		if err := ctx.Err(); err != nil {
			return nil, 0, err
		}
		if firstErr != nil {
			return nil, 0, firstErr
		}
		return nil, 0, speedtest.ErrServerNotFound
	}
	return selected, lowest, nil
}

func measureSpeedTestLatency(ctx context.Context, client *http.Client, uploadURL string) (time.Duration, error) {
	latencyURL, err := deriveSpeedTestLatencyURL(uploadURL)
	if err != nil {
		return 0, err
	}
	for attempt := 0; attempt < 2; attempt++ {
		req, err := http.NewRequestWithContext(ctx, http.MethodGet, latencyURL, nil)
		if err != nil {
			return 0, err
		}
		started := time.Now()
		resp, err := client.Do(req)
		elapsed := time.Since(started)
		if err != nil {
			return 0, err
		}
		_, copyErr := io.Copy(io.Discard, resp.Body)
		closeErr := resp.Body.Close()
		if statusErr := requireSuccessfulStatus(resp); statusErr != nil {
			return 0, statusErr
		}
		if copyErr != nil {
			return 0, copyErr
		}
		if closeErr != nil {
			return 0, closeErr
		}
		if attempt == 1 {
			return elapsed, nil
		}
	}
	return 0, errors.New("latency measurement did not run")
}

func deriveSpeedTestLatencyURL(uploadURL string) (string, error) {
	parsed, err := url.Parse(uploadURL)
	if err != nil {
		return "", err
	}
	if parsed.Host == "" {
		return "", errors.New("upload URL has no host")
	}
	parsed.Path = path.Dir(parsed.Path)
	return parsed.JoinPath("latency.txt").String(), nil
}

func deriveSpeedTestDownloadURL(uploadURL string, imageSize int) (string, error) {
	parsed, err := url.Parse(uploadURL)
	if err != nil {
		return "", err
	}
	if parsed.Host == "" || imageSize <= 0 {
		return "", errors.New("invalid upload URL or image size")
	}
	parsed.Path = path.Dir(parsed.Path)
	return parsed.JoinPath(fmt.Sprintf("random%dx%d.jpg", imageSize, imageSize)).String(), nil
}

func runTransferPhase(
	ctx context.Context,
	client *http.Client,
	stage string,
	duration time.Duration,
	connections int,
	request func(context.Context, *atomic.Int64) error,
	progress func(rate int64, transferred int64),
) (int64, int64, error) {
	if connections <= 0 {
		return 0, 0, errors.New("speed-test connections must be positive")
	}
	phaseCtx, cancel := context.WithTimeout(ctx, duration)
	defer cancel()
	started := time.Now()
	var transferred atomic.Int64
	errorChannel := make(chan error, 1)
	var waitGroup sync.WaitGroup

	for worker := 0; worker < connections; worker++ {
		waitGroup.Add(1)
		go func() {
			defer waitGroup.Done()
			for phaseCtx.Err() == nil {
				if err := request(phaseCtx, &transferred); err != nil {
					if phaseCtx.Err() != nil {
						return
					}
					select {
					case errorChannel <- err:
						cancel()
					default:
					}
					return
				}
			}
		}()
	}
	done := make(chan struct{})
	go func() {
		waitGroup.Wait()
		close(done)
	}()

	ticker := time.NewTicker(speedTestSampleInterval)
	defer ticker.Stop()
	for {
		select {
		case err := <-errorChannel:
			<-done
			return calculateSpeedTestRate(transferred.Load(), time.Since(started)), transferred.Load(), err
		case <-ticker.C:
			progress(calculateSpeedTestRate(transferred.Load(), time.Since(started)), transferred.Load())
		case <-done:
			total := transferred.Load()
			rate := calculateSpeedTestRate(total, time.Since(started))
			progress(rate, total)
			select {
			case err := <-errorChannel:
				return rate, total, err
			default:
			}
			if err := ctx.Err(); err != nil {
				return rate, total, err
			}
			if total == 0 {
				if err := phaseCtx.Err(); err != nil {
					return rate, total, err
				}
				return rate, total, fmt.Errorf("%s speed test transferred no data", stage)
			}
			return rate, total, nil
		}
	}
}

func downloadSpeedTestPayload(
	ctx context.Context,
	client *http.Client,
	downloadURL string,
	transferred *atomic.Int64,
) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, downloadURL, nil)
	if err != nil {
		return err
	}
	resp, err := client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if err := requireSuccessfulStatus(resp); err != nil {
		return err
	}
	_, err = io.Copy(io.Discard, &countingReader{reader: resp.Body, total: transferred})
	return err
}

func uploadSpeedTestPayload(
	ctx context.Context,
	client *http.Client,
	uploadURL string,
	payloadBytes int64,
	transferred *atomic.Int64,
) error {
	if payloadBytes <= 0 {
		return errors.New("upload payload size must be positive")
	}
	body := &countingReader{
		reader: io.LimitReader(zeroReader{}, payloadBytes),
		total:  transferred,
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, uploadURL, io.NopCloser(body))
	if err != nil {
		return err
	}
	req.ContentLength = payloadBytes
	req.Header.Set("Content-Type", "application/octet-stream")
	resp, err := client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if err := requireSuccessfulStatus(resp); err != nil {
		return err
	}
	_, err = io.Copy(io.Discard, resp.Body)
	return err
}

func runSimpleDownload(
	ctx context.Context,
	client *http.Client,
	downloadURL string,
	timeout time.Duration,
	progress speedTestProgressFunc,
) (int64, int64, error) {
	downloadCtx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()
	req, err := http.NewRequestWithContext(downloadCtx, http.MethodGet, downloadURL, nil)
	if err != nil {
		return 0, 0, err
	}
	started := time.Now()
	resp, err := client.Do(req)
	if err != nil {
		return 0, 0, err
	}
	defer resp.Body.Close()
	if err := requireSuccessfulStatus(resp); err != nil {
		return 0, 0, err
	}

	var transferred atomic.Int64
	done := make(chan error, 1)
	go func() {
		_, copyErr := io.Copy(io.Discard, &countingReader{reader: resp.Body, total: &transferred})
		done <- copyErr
	}()
	ticker := time.NewTicker(speedTestSampleInterval)
	defer ticker.Stop()
	for {
		select {
		case copyErr := <-done:
			total := transferred.Load()
			rate := calculateSpeedTestRate(total, time.Since(started))
			progress(speedTestProgress{
				stage:                 SpeedTestStageDownload,
				downloadBitsPerSecond: rate,
				downloadBytes:         total,
			})
			if copyErr != nil {
				return rate, total, copyErr
			}
			if total == 0 {
				return rate, total, errors.New("simple download transferred no data")
			}
			return rate, total, nil
		case <-ticker.C:
			total := transferred.Load()
			progress(speedTestProgress{
				stage:                 SpeedTestStageDownload,
				downloadBitsPerSecond: calculateSpeedTestRate(total, time.Since(started)),
				downloadBytes:         total,
			})
		case <-downloadCtx.Done():
			copyErr := <-done
			total := transferred.Load()
			rate := calculateSpeedTestRate(total, time.Since(started))
			if err := ctx.Err(); err != nil {
				return rate, total, err
			}
			if copyErr != nil && !errors.Is(copyErr, context.DeadlineExceeded) && !errors.Is(copyErr, context.Canceled) {
				return rate, total, copyErr
			}
			return rate, total, downloadCtx.Err()
		}
	}
}

func calculateSpeedTestRate(transferred int64, elapsed time.Duration) int64 {
	if transferred <= 0 || elapsed <= 0 {
		return 0
	}
	return int64(float64(transferred*8) / elapsed.Seconds())
}

func requireSuccessfulStatus(response *http.Response) error {
	if response.StatusCode >= http.StatusOK && response.StatusCode < http.StatusMultipleChoices {
		return nil
	}
	return fmt.Errorf("unexpected HTTP status %s", response.Status)
}

type countingReader struct {
	reader io.Reader
	total  *atomic.Int64
}

func (r *countingReader) Read(buffer []byte) (int, error) {
	read, err := r.reader.Read(buffer)
	if read > 0 {
		r.total.Add(int64(read))
	}
	return read, err
}

type zeroReader struct{}

func (zeroReader) Read(buffer []byte) (int, error) {
	for index := range buffer {
		buffer[index] = 0xAA
	}
	return len(buffer), nil
}
