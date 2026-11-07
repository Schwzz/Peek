import assert from 'node:assert/strict';
import test from 'node:test';

import {
  createBlurFocusTracker,
  FLUSH_MS,
  STORAGE_KEY,
  THROTTLE_MS
} from './blur-events.js';

function memoryStorage() {
  const values = new Map();
  return {
    getItem: (key) => (values.has(key) ? values.get(key) : null),
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: (key) => values.delete(key)
  };
}

function fixedClock() {
  let now = 0;
  return {
    now: () => now,
    advance: (ms) => { now += ms; }
  };
}

function trackerAt(clock, storage, extra = {}) {
  return createBlurFocusTracker({
    storage,
    now: clock.now,
    timestamp: () => '2026-11-06T00:00:00.000Z',
    ...extra
  });
}

test('the first blur is recorded immediately (leading edge)', () => {
  const clock = fixedClock();
  const tracker = trackerAt(clock, memoryStorage());

  assert.equal(tracker.handleWindowEvent('blur', { label: 'body' }), 'accepted');
  assert.equal(tracker.state().events.length, 1);
  assert.equal(tracker.state().events[0].type, 'blur');
});

test('a second event inside 5000ms is suppressed', () => {
  const clock = fixedClock();
  const tracker = trackerAt(clock, memoryStorage());

  tracker.handleWindowEvent('blur');
  clock.advance(THROTTLE_MS - 1);
  assert.equal(tracker.handleWindowEvent('focus'), 'suppressed');
  assert.equal(tracker.state().events.length, 1);
  assert.equal(tracker.state().suppressed, 1);
});

test('an event after the throttle window is recorded again', () => {
  const clock = fixedClock();
  const tracker = trackerAt(clock, memoryStorage());

  tracker.handleWindowEvent('blur');
  clock.advance(THROTTLE_MS + 1);

  assert.equal(tracker.handleWindowEvent('focus'), 'accepted');
  assert.equal(tracker.state().events.length, 2);
});

test('activeElement === IFRAME skips without consuming the throttle', () => {
  const clock = fixedClock();
  const tracker = trackerAt(clock, memoryStorage());

  assert.equal(tracker.handleWindowEvent('blur', { tag: 'IFRAME' }), 'skipped');
  assert.equal(tracker.state().events.length, 0);
  assert.equal(tracker.state().skipped, 1);

  assert.equal(tracker.handleWindowEvent('blur', { tag: 'BODY' }), 'accepted');
  assert.equal(tracker.state().events.length, 1);
});

test('flush runs on a 15000ms interval and clears pending events', () => {
  const storage = memoryStorage();
  let scheduled = null;
  let delay = 0;
  const clock = fixedClock();
  const tracker = trackerAt(clock, storage, {
    setIntervalFn: (fn, ms) => { scheduled = fn; delay = ms; return 1; },
    clearIntervalFn: () => { scheduled = null; }
  });

  tracker.handleWindowEvent('blur');
  assert.equal(tracker.state().pending, 1);

  tracker.startAutoFlush();
  assert.equal(delay, FLUSH_MS);
  assert.equal(typeof scheduled, 'function');

  scheduled();

  assert.equal(tracker.state().pending, 0);
  assert.equal(tracker.state().flushes, 1);
  const stored = JSON.parse(storage.getItem(STORAGE_KEY));
  assert.equal(stored.length, 1);
  assert.equal(stored[0].type, 'blur');

  tracker.stopAutoFlush();
  assert.equal(scheduled, null);
});
