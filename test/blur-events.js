/*
 * Throttle + flush logic for test/blur-fixture.html.
 *
 * Modeled on the Canvas LMS blur/focus event stream:
 *   - canvas-lms/ui/shared/page_blurred.js
 *   - canvas-lms/ui/shared/page_focused.js
 *   - canvas-lms/ui/shared/event_manager.js
 *
 * Kept as a module so Node can test the behavior without a browser.
 */

export const THROTTLE_MS = 5000;
export const FLUSH_MS = 15000;
export const MAX_ROWS = 500;
export const STORAGE_KEY = 'peek.blurFocusEvents.v1';

function loadStoredEvents(storage, storageKey, maxRows) {
  try {
    const parsed = JSON.parse(storage.getItem(storageKey) || '[]');
    return Array.isArray(parsed) ? parsed.slice(-maxRows) : [];
  } catch (_) {
    return [];
  }
}

/**
 * @param {object} [options]
 * @param {Storage} [options.storage]
 * @param {string} [options.storageKey]
 * @param {() => number} [options.now] monotonic clock for the throttle
 * @param {() => string} [options.timestamp] wall-clock label written to rows
 * @param {number} [options.throttleMs]
 * @param {number} [options.flushMs]
 * @param {number} [options.maxRows]
 * @param {Function} [options.setIntervalFn]
 * @param {Function} [options.clearIntervalFn]
 * @param {() => void} [options.onFlush]
 */
export function createBlurFocusTracker(options = {}) {
  const storage = options.storage ?? globalThis.localStorage;
  const storageKey = options.storageKey ?? STORAGE_KEY;
  const now = options.now ?? (() => globalThis.performance.now());
  const timestamp = options.timestamp ?? (() => new Date().toISOString());
  const throttleMs = options.throttleMs ?? THROTTLE_MS;
  const flushMs = options.flushMs ?? FLUSH_MS;
  const maxRows = options.maxRows ?? MAX_ROWS;
  const setIntervalFn = options.setIntervalFn ?? globalThis.setInterval;
  const clearIntervalFn = options.clearIntervalFn ?? globalThis.clearInterval;
  const onFlush = options.onFlush ?? (() => {});

  let events = loadStoredEvents(storage, storageKey, maxRows);
  let pending = [];
  let lastRecordedAt = Number.NEGATIVE_INFINITY;
  let suppressed = 0;
  let skipped = 0;
  let flushes = 0;
  let timer = null;

  function persist() {
    let error = null;
    try {
      storage.setItem(storageKey, JSON.stringify(events.slice(-maxRows)));
    } catch (caught) {
      error = caught;
    }
    pending = [];
    flushes += 1;
    onFlush();
    return error;
  }

  function flush() {
    return { flushes, pending: pending.length, error: persist() };
  }

  /**
   * @param {'blur'|'focus'} type
   * @param {{tag?: string, label?: string}} [activeElement]
   * @returns {'accepted'|'suppressed'|'skipped'}
   */
  function handleWindowEvent(type, activeElement = {}) {
    if (activeElement.tag === 'IFRAME') {
      skipped += 1;
      return 'skipped';
    }
    const current = now();
    if (current - lastRecordedAt < throttleMs) {
      suppressed += 1;
      return 'suppressed';
    }
    lastRecordedAt = current;
    const event = {
      sequence: events.length + 1,
      at: timestamp(),
      type,
      activeElement: activeElement.label ?? '(none)'
    };
    events.push(event);
    if (events.length > maxRows) events = events.slice(-maxRows);
    pending.push(event);
    return 'accepted';
  }

  function startAutoFlush() {
    if (timer !== null) return timer;
    timer = setIntervalFn(flush, flushMs);
    return timer;
  }

  function stopAutoFlush() {
    if (timer !== null) clearIntervalFn(timer);
    timer = null;
  }

  function clear() {
    try {
      storage.removeItem(storageKey);
    } catch (_) {
      /* ignore storage failures */
    }
    events = [];
    pending = [];
    lastRecordedAt = Number.NEGATIVE_INFINITY;
    suppressed = 0;
    skipped = 0;
  }

  function state() {
    return {
      events: events.slice(),
      suppressed,
      skipped,
      flushes,
      pending: pending.length
    };
  }

  return {
    handleWindowEvent,
    flush,
    startAutoFlush,
    stopAutoFlush,
    clear,
    state,
    throttleMs,
    flushMs
  };
}
