package libcore

import (
	"errors"
	"fmt"
	"os"
	"testing"
)

func TestBoxCloseAfterStartFailureNormalizesAlreadyClosed(t *testing.T) {
	startErr := errors.New("missing outbound dependency")
	closeCalls := 0
	instance := &BoxInstance{
		startBox: func() error {
			return startErr
		},
		closeBox: func() error {
			closeCalls++
			return fmt.Errorf("rollback completed: %w", os.ErrClosed)
		},
	}

	if err := instance.Start(); !errors.Is(err, startErr) {
		t.Fatalf("Start() error = %v, want original error %v", err, startErr)
	}
	if instance.state != boxStateStartFailed {
		t.Fatalf("state after failed Start() = %s, want %s", instance.state, boxStateStartFailed)
	}
	if !errors.Is(instance.startErr, startErr) {
		t.Fatalf("recorded start error = %v, want %v", instance.startErr, startErr)
	}

	if err := instance.Close(); err != nil {
		t.Fatalf("Close() after rollback error = %v, want nil", err)
	}
	if instance.state != boxStateClosed {
		t.Fatalf("state after Close() = %s, want %s", instance.state, boxStateClosed)
	}
	if err := instance.Close(); err != nil {
		t.Fatalf("repeated Close() error = %v, want nil", err)
	}
	if closeCalls != 1 {
		t.Fatalf("underlying Close() calls = %d, want 1", closeCalls)
	}
}

func TestBoxCloseReturnsUnknownErrorOnceAndThenIsIdempotent(t *testing.T) {
	closeErr := errors.New("unexpected close failure")
	closeCalls := 0
	instance := &BoxInstance{
		startBox: func() error { return nil },
		closeBox: func() error {
			closeCalls++
			return closeErr
		},
	}

	if err := instance.Start(); err != nil {
		t.Fatalf("Start() error = %v, want nil", err)
	}
	if err := instance.Close(); !errors.Is(err, closeErr) {
		t.Fatalf("Close() error = %v, want %v", err, closeErr)
	}
	if instance.state != boxStateClosed {
		t.Fatalf("state after failed Close() = %s, want %s", instance.state, boxStateClosed)
	}
	if err := instance.Close(); err != nil {
		t.Fatalf("repeated Close() error = %v, want nil", err)
	}
	if closeCalls != 1 {
		t.Fatalf("underlying Close() calls = %d, want 1", closeCalls)
	}
}
