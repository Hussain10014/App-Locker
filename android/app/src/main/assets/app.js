/**
 * ZenLock - Phone Locker Application Logic
 * Integrates with Android Native Bridge (startLockTask, DND Interruption Filter)
 * and provides Unbreakable Lock Mode, Screen WakeLock, and Back Button Traps.
 */

// State
const state = {
  selectedMinutes: 25,
  secondsRemaining: 25 * 60,
  totalDurationSeconds: 25 * 60,
  lockEndTime: null, // wall-clock timestamp (ms) when the session should end
  timerInterval: null,
  wakeLock: null,
  unbreakableMode: true,
  hideNotifications: true,
  screenAwake: true,
  soundEnabled: true,
  isNativeApp: typeof window.AndroidLocker !== 'undefined',
  quotes: [
    "Almost everything will work again if you unplug it for a few minutes, including you.",
    "Focus is a muscle. Every second you resist distraction, you grow stronger.",
    "Your attention is your most precious currency. Spend it wisely.",
    "Discipline is choosing between what you want now and what you want most.",
    "In a world of constant noise, quiet focus is your ultimate superpower.",
    "Do not let a glowing glass rectangle control your future.",
    "Stay present. Real life is happening outside of your notifications."
  ],
  quoteIndex: 0,
  quoteInterval: null,
  penaltySeconds: 10,
  penaltyInterval: null
};

// DOM Elements
const setupScreen = document.getElementById('setupScreen');
const lockedScreen = document.getElementById('lockedScreen');
const completedScreen = document.getElementById('completedScreen');
const topNav = document.getElementById('topNav');
const nativeBadge = document.getElementById('nativeBadge');

const presetCards = document.querySelectorAll('.preset-card');
const customMinutesInput = document.getElementById('customMinutes');
const setCustomBtn = document.getElementById('setCustomBtn');
const lockBtnText = document.getElementById('lockBtnText');
const startLockBtn = document.getElementById('startLockBtn');
const lockNotice = document.getElementById('lockNotice');

const unbreakableToggle = document.getElementById('unbreakableToggle');
const hideNotificationsToggle = document.getElementById('hideNotificationsToggle');
const wakeLockToggle = document.getElementById('wakeLockToggle');
const soundToggle = document.getElementById('soundToggle');

const permissionsCard = document.getElementById('permissionsCard');
const permStatusText = document.getElementById('permStatusText');
const requestPermBtn = document.getElementById('requestPermBtn');

const timeRemainingEl = document.getElementById('timeRemaining');
const progressFill = document.getElementById('progressFill');
const wakeBadge = document.getElementById('wakeBadge');
const dndBadge = document.getElementById('dndBadge');
const quoteText = document.getElementById('quoteText');
const shieldStatusText = document.getElementById('shieldStatusText');

const emergencyControls = document.getElementById('emergencyControls');
const emergencyExitBtn = document.getElementById('emergencyExitBtn');
const emergencyDelayBox = document.getElementById('emergencyDelayBox');
const penaltyBar = document.getElementById('penaltyBar');
const penaltySecondsEl = document.getElementById('penaltySeconds');
const cancelEmergencyBtn = document.getElementById('cancelEmergencyBtn');
const unbreakableBanner = document.getElementById('unbreakableBanner');

const returnHomeBtn = document.getElementById('returnHomeBtn');
const completedSessionTime = document.getElementById('completedSessionTime');
const streakCount = document.getElementById('streakCount');

const statsBtn = document.getElementById('statsBtn');
const statsModal = document.getElementById('statsModal');
const closeStatsBtn = document.getElementById('closeStatsBtn');
const totalMinutesFocused = document.getElementById('totalMinutesFocused');
const totalSessionsCount = document.getElementById('totalSessionsCount');
const historyList = document.getElementById('historyList');

const helpModal = document.getElementById('helpModal');
const closeHelpBtn = document.getElementById('closeHelpBtn');
const understandBtn = document.getElementById('understandBtn');

// SVG Ring Constants
const CIRCLE_RADIUS = 140;
const CIRCUMFERENCE = 2 * Math.PI * CIRCLE_RADIUS;

if (progressFill) {
  progressFill.style.strokeDasharray = `${CIRCUMFERENCE} ${CIRCUMFERENCE}`;
  progressFill.style.strokeDashoffset = '0';
}

// ============================================================================
// Android Native Bridge Detection & Permissions
// ============================================================================
function checkNativeEnvironment() {
  if (state.isNativeApp) {
    nativeBadge.style.display = 'inline-block';
    nativeBadge.textContent = 'NATIVE';

    // Check native permissions in priority order: DND -> exact alarm -> accessibility
    const hasDnd = window.AndroidLocker.isDNDGranted ? window.AndroidLocker.isDNDGranted() : false;
    const hasAlarm = window.AndroidLocker.isExactAlarmGranted ? window.AndroidLocker.isExactAlarmGranted() : true;
    const hasAccessibility = window.AndroidLocker.isAccessibilityGranted ? window.AndroidLocker.isAccessibilityGranted() : true;

    if (hasDnd && hasAlarm && hasAccessibility) {
      permissionsCard.style.display = 'none';
    } else if (!hasDnd) {
      permStatusText.textContent = 'Tap Grant to allow ZenLock to hide incoming notifications during lock sessions.';
      requestPermBtn.textContent = 'Grant DND';
      requestPermBtn.dataset.mode = 'dnd';
    } else if (!hasAlarm) {
      permStatusText.textContent = 'Tap Grant to allow ZenLock to schedule the exact unlock time (Alarms & reminders).';
      requestPermBtn.textContent = 'Grant Alarm';
      requestPermBtn.dataset.mode = 'alarm';
    } else {
      permStatusText.textContent = 'Tap Grant, then switch ON the ZenLock service to block escape attempts. If the toggle looks greyed out, first go to Settings > Apps > ZenLock > ⋮ > "Allow restricted settings".';
      requestPermBtn.textContent = 'Grant Lock';
      requestPermBtn.dataset.mode = 'accessibility';
    }
  } else {
    nativeBadge.style.display = 'none';
    permStatusText.textContent = 'For 100% unclosable mode, tap "Guide" to use Android App Pinning & Do Not Disturb.';
    requestPermBtn.textContent = 'Guide';
    requestPermBtn.dataset.mode = 'guide';
  }
}

requestPermBtn.addEventListener('click', () => {
  const mode = requestPermBtn.dataset.mode;
  if (state.isNativeApp && mode === 'accessibility' && window.AndroidLocker.requestAccessibilityPermission) {
    window.AndroidLocker.requestAccessibilityPermission();
  } else if (state.isNativeApp && mode === 'alarm' && window.AndroidLocker.requestExactAlarmPermission) {
    window.AndroidLocker.requestExactAlarmPermission();
  } else if (state.isNativeApp && window.AndroidLocker.requestDNDPermission) {
    window.AndroidLocker.requestDNDPermission();
  } else {
    helpModal.classList.add('open');
  }
});

closeHelpBtn.addEventListener('click', () => helpModal.classList.remove('open'));
understandBtn.addEventListener('click', () => helpModal.classList.remove('open'));

// ============================================================================
// Android Back Button Trap & Navigation Prevention
// ============================================================================
function trapNavigation() {
  // Push multiple history states
  history.pushState({ lockState: 'active' }, null, location.href);
  window.addEventListener('popstate', (e) => {
    if (lockedScreen.classList.contains('active')) {
      // Re-trap back gesture
      history.pushState({ lockState: 'active' }, null, location.href);
      if (state.isNativeApp && window.AndroidLocker.showToast) {
        window.AndroidLocker.showToast('ZenLock is active. Exiting is disabled!');
      }
    }
  });
}

// Prevent closing tab / accidental exit
window.addEventListener('beforeunload', (e) => {
  if (lockedScreen.classList.contains('active')) {
    e.preventDefault();
    e.returnValue = 'ZenLock is currently engaged. Exiting will cancel your focus session!';
    return e.returnValue;
  }
});

// ============================================================================
// Web Audio API Sound Synthesizer
// ============================================================================
class SoundEffects {
  constructor() {
    this.ctx = null;
  }

  init() {
    if (!this.ctx) {
      const AudioCtx = window.AudioContext || window.webkitAudioContext;
      if (AudioCtx) this.ctx = new AudioCtx();
    }
    if (this.ctx && this.ctx.state === 'suspended') {
      this.ctx.resume();
    }
  }

  playLockStart() {
    if (!state.soundEnabled) return;
    this.init();
    if (!this.ctx) return;

    const notes = [329.63, 440.0, 659.25];
    notes.forEach((freq, idx) => {
      const osc = this.ctx.createOscillator();
      const gain = this.ctx.createGain();
      osc.type = 'sine';
      osc.frequency.setValueAtTime(freq, this.ctx.currentTime + idx * 0.12);
      gain.gain.setValueAtTime(0.2, this.ctx.currentTime + idx * 0.12);
      gain.gain.exponentialRampToValueAtTime(0.001, this.ctx.currentTime + idx * 0.12 + 0.6);
      osc.connect(gain);
      gain.connect(this.ctx.destination);
      osc.start(this.ctx.currentTime + idx * 0.12);
      osc.stop(this.ctx.currentTime + idx * 0.12 + 0.6);
    });
  }

  playLockComplete() {
    if (!state.soundEnabled) return;
    this.init();
    if (!this.ctx) return;

    const chord = [523.25, 659.25, 783.99, 1046.50];
    chord.forEach((freq) => {
      const osc = this.ctx.createOscillator();
      const gain = this.ctx.createGain();
      osc.type = 'sine';
      osc.frequency.setValueAtTime(freq, this.ctx.currentTime);
      gain.gain.setValueAtTime(0.18, this.ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.0001, this.ctx.currentTime + 2.0);
      osc.connect(gain);
      gain.connect(this.ctx.destination);
      osc.start(this.ctx.currentTime);
      osc.stop(this.ctx.currentTime + 2.0);
    });
  }
}

const sounds = new SoundEffects();

// ============================================================================
// Screen Wake Lock API
// ============================================================================
async function requestScreenWakeLock() {
  if ('wakeLock' in navigator && state.screenAwake) {
    try {
      state.wakeLock = await navigator.wakeLock.request('screen');
      if (wakeBadge) wakeBadge.style.display = 'flex';
      state.wakeLock.addEventListener('release', () => {
        if (wakeBadge) wakeBadge.style.display = 'none';
      });
    } catch (err) {
      console.warn('Wake Lock failed:', err.message);
      if (wakeBadge) wakeBadge.style.display = 'none';
    }
  }
}

function releaseScreenWakeLock() {
  if (state.wakeLock !== null) {
    state.wakeLock.release().catch(() => {});
    state.wakeLock = null;
  }
}

document.addEventListener('visibilitychange', async () => {
  if (state.wakeLock !== null && document.visibilityState === 'visible' && lockedScreen.classList.contains('active')) {
    await requestScreenWakeLock();
  }
  if (document.visibilityState === 'visible') {
    resyncTimerFromWallClock();
    checkNativeEnvironment(); // refresh permission card after returning from Settings
  }
});

// ============================================================================
// Resync helper - called from visibilitychange above AND directly by native
// Android (MainActivity.onResume) via window.zenLockResync(), so the display
// snaps to the correct remaining time the instant the screen comes back on,
// instead of waiting for a possibly-throttled setInterval tick to catch up.
// ============================================================================
function resyncTimerFromWallClock() {
  if (!lockedScreen.classList.contains('active') || !state.lockEndTime) return;
  const remainingMs = state.lockEndTime - Date.now();
  state.secondsRemaining = Math.max(0, Math.round(remainingMs / 1000));
  updateTimerDisplay();
  if (remainingMs <= 0) {
    completeSession();
  }
}
window.zenLockResync = resyncTimerFromWallClock;

// ============================================================================
// TRUE EMERGENCY FAILSAFE
// A full, uninterrupted 5-second hold on the shield/status indicator always
// unlocks immediately, bypassing Unbreakable Mode entirely -- no cooldown,
// no confirmation. This exists specifically so that a real emergency is
// never blocked by the app's own restrictions. It is deliberately NOT a
// quick tap: releasing before the full 5 seconds resets the hold to zero,
// so it can't be triggered by accident or by a momentary impulse, but a
// genuinely deliberate hold always works.
//
// This is intentionally a bit hidden rather than a labeled button (a
// visible "emergency exit" button would just get used as the normal exit
// and defeat the app's purpose) -- but make sure YOU remember it exists,
// and consider telling a trusted person about it too.
// ============================================================================
const FAILSAFE_HOLD_MS = 5000;
let failsafeTimer = null;
let failsafeStartTime = null;
const shieldIndicator = document.querySelector('.lock-shield-indicator');

function startFailsafeHold() {
  if (failsafeTimer || !lockedScreen.classList.contains('active')) return;
  failsafeStartTime = Date.now();
  shieldStatusText.textContent = 'HOLD FOR EMERGENCY UNLOCK...';
  failsafeTimer = setInterval(() => {
    if (Date.now() - failsafeStartTime >= FAILSAFE_HOLD_MS) {
      cancelFailsafeHold();
      triggerEmergencyOverride();
    }
  }, 100);
}

function cancelFailsafeHold() {
  if (failsafeTimer) {
    clearInterval(failsafeTimer);
    failsafeTimer = null;
  }
  if (lockedScreen.classList.contains('active')) {
    shieldStatusText.textContent = state.hideNotifications
      ? 'PHONE LOCKED • NOTIFICATIONS HIDDEN'
      : 'PHONE LOCKED • FOCUS IN PROGRESS';
  }
}

function triggerEmergencyOverride() {
  if (state.isNativeApp && window.AndroidLocker.showToast) {
    window.AndroidLocker.showToast('Emergency override — ZenLock unlocked.');
  }
  abortSession();
}

if (shieldIndicator) {
  shieldIndicator.addEventListener('pointerdown', startFailsafeHold);
  shieldIndicator.addEventListener('pointerup', cancelFailsafeHold);
  shieldIndicator.addEventListener('pointerleave', cancelFailsafeHold);
  shieldIndicator.addEventListener('pointercancel', cancelFailsafeHold);
}

// ============================================================================
// Fullscreen Control
// ============================================================================
function enterFullscreen() {
  const elem = document.documentElement;
  if (elem.requestFullscreen) {
    elem.requestFullscreen().catch(() => {});
  } else if (elem.webkitRequestFullscreen) {
    elem.webkitRequestFullscreen();
  } else if (elem.mozRequestFullScreen) {
    elem.mozRequestFullScreen();
  } else if (elem.msRequestFullscreen) {
    elem.msRequestFullscreen();
  }
}

function exitFullscreen() {
  if (document.fullscreenElement || document.webkitFullscreenElement) {
    if (document.exitFullscreen) {
      document.exitFullscreen().catch(() => {});
    } else if (document.webkitExitFullscreen) {
      document.webkitExitFullscreen();
    }
  }
}

// ============================================================================
// Screen Transitions
// ============================================================================
function showScreen(screenId) {
  [setupScreen, lockedScreen, completedScreen].forEach(scr => scr.classList.remove('active'));

  if (screenId === 'setup') {
    setupScreen.classList.add('active');
    topNav.style.display = 'flex';
  } else if (screenId === 'locked') {
    lockedScreen.classList.add('active');
    topNav.style.display = 'none';
  } else if (screenId === 'completed') {
    completedScreen.classList.add('active');
    topNav.style.display = 'none';
  }
}

// ============================================================================
// Preset & Setup Handling
// ============================================================================
presetCards.forEach(card => {
  card.addEventListener('click', () => {
    presetCards.forEach(c => c.classList.remove('active'));
    card.classList.add('active');
    const minutes = parseInt(card.dataset.minutes, 10);
    setDuration(minutes);
  });
});

setCustomBtn.addEventListener('click', () => {
  const val = parseInt(customMinutesInput.value, 10);
  if (val && val > 0 && val <= 720) {
    presetCards.forEach(c => c.classList.remove('active'));
    setDuration(val);
  } else {
    alert('Please enter a valid time between 1 and 720 minutes.');
  }
});

function setDuration(minutes) {
  state.selectedMinutes = minutes;
  state.totalDurationSeconds = minutes * 60;
  state.secondsRemaining = minutes * 60;
  lockBtnText.textContent = `Engage Lock (${minutes} Min)`;
}

unbreakableToggle.addEventListener('change', () => {
  state.unbreakableMode = unbreakableToggle.checked;
  if (state.unbreakableMode) {
    lockNotice.textContent = 'Unbreakable mode enabled: phone cannot be unlocked until timer finishes.';
    lockNotice.style.color = 'var(--accent-red)';
  } else {
    lockNotice.textContent = 'Normal mode: emergency unlock cooldown will be available.';
    lockNotice.style.color = 'var(--text-dim)';
  }
});

// ============================================================================
// Lock Session Lifecycle
// ============================================================================
startLockBtn.addEventListener('click', async () => {
  state.unbreakableMode = unbreakableToggle.checked;
  state.hideNotifications = hideNotificationsToggle.checked;
  state.screenAwake = wakeLockToggle.checked;
  state.soundEnabled = soundToggle.checked;

  sounds.playLockStart();
  enterFullscreen();
  await requestScreenWakeLock();
  trapNavigation();

  // Configure UI based on Unbreakable Mode
  if (state.unbreakableMode) {
    emergencyControls.style.display = 'none';
    unbreakableBanner.style.display = 'flex';
    document.getElementById('timerStatusLabel').textContent = 'UNBREAKABLE LOCK';
  } else {
    emergencyControls.style.display = 'block';
    emergencyExitBtn.style.display = 'inline-block';
    emergencyDelayBox.classList.add('hidden');
    unbreakableBanner.style.display = 'none';
    document.getElementById('timerStatusLabel').textContent = 'FOCUS SESSION';
  }

  // Handle Notifications (DND)
  if (state.hideNotifications) {
    dndBadge.style.display = 'flex';
    shieldStatusText.textContent = 'PHONE LOCKED • NOTIFICATIONS HIDDEN';
    // Native Android DND activation
    if (state.isNativeApp && window.AndroidLocker.setDND) {
      window.AndroidLocker.setDND(true);
    }
  } else {
    dndBadge.style.display = 'none';
    shieldStatusText.textContent = 'PHONE LOCKED • FOCUS IN PROGRESS';
  }

  // Native Android Screen Pinning / Lock Task Mode
  if (state.isNativeApp && window.AndroidLocker.startLockTask) {
    window.AndroidLocker.startLockTask();
  }

  // NEW: hand the real end time to native Android so it can guarantee the
  // unlock (DND restore) fires exactly on time via AlarmManager, even if the
  // screen is off and this page's own JS timers get throttled.
  if (state.isNativeApp && window.AndroidLocker.scheduleAutoUnlock) {
    window.AndroidLocker.scheduleAutoUnlock(state.totalDurationSeconds * 1000);
  }

  showScreen('locked');
  startTimer();
  startQuoteRotation();

  notifyBackend('start', state.selectedMinutes);
});

// ----------------------------------------------------------------------------
// FIX 2: Wall-clock based countdown.
// Instead of just decrementing a counter every tick (which permanently drifts
// if ticks get delayed by Doze / CPU throttling while the screen is off), we
// compute a fixed end timestamp up front and always derive the remaining time
// from Date.now(). If ticks lag or get skipped, the next tick that does fire
// will "catch up" instantly to the correct remaining time instead of running
// the session far longer than intended.
// ----------------------------------------------------------------------------
function startTimer() {
  state.lockEndTime = Date.now() + state.secondsRemaining * 1000;
  updateTimerDisplay();

  state.timerInterval = setInterval(() => {
    const remainingMs = state.lockEndTime - Date.now();
    state.secondsRemaining = Math.max(0, Math.round(remainingMs / 1000));
    updateTimerDisplay();

    if (remainingMs <= 0) {
      completeSession();
    }
  }, 1000);
}

function updateTimerDisplay() {
  const mins = Math.floor(state.secondsRemaining / 60);
  const secs = state.secondsRemaining % 60;
  timeRemainingEl.textContent = `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;

  if (progressFill) {
    const fraction = state.secondsRemaining / state.totalDurationSeconds;
    const offset = CIRCUMFERENCE * (1 - fraction);
    progressFill.style.strokeDashoffset = offset;
  }
}

function startQuoteRotation() {
  updateQuote();
  state.quoteInterval = setInterval(updateQuote, 40000);
}

function updateQuote() {
  state.quoteIndex = (state.quoteIndex + 1) % state.quotes.length;
  quoteText.style.opacity = '0';
  setTimeout(() => {
    quoteText.textContent = `"${state.quotes[state.quoteIndex]}"`;
    quoteText.style.opacity = '1';
  }, 400);
}

function stopTimer() {
  if (state.timerInterval) {
    clearInterval(state.timerInterval);
    state.timerInterval = null;
  }
  if (state.quoteInterval) {
    clearInterval(state.quoteInterval);
    state.quoteInterval = null;
  }
  state.lockEndTime = null;
}

// ============================================================================
// Completion & Unlock
// ============================================================================
function completeSession() {
  stopTimer();
  releaseScreenWakeLock();
  exitFullscreen();
  sounds.playLockComplete();

  // Restore Native Android State
  if (state.isNativeApp) {
    if (window.AndroidLocker.setDND) window.AndroidLocker.setDND(false);
    if (window.AndroidLocker.stopLockTask) window.AndroidLocker.stopLockTask();
    if (window.AndroidLocker.cancelAutoUnlock) window.AndroidLocker.cancelAutoUnlock();
  }

  saveSession(state.selectedMinutes);

  completedSessionTime.textContent = `${state.selectedMinutes}m`;
  const stats = getStoredStats();
  streakCount.textContent = stats.todayCount;

  showScreen('completed');
  notifyBackend('completed', state.selectedMinutes);
}

returnHomeBtn.addEventListener('click', () => {
  setDuration(state.selectedMinutes);
  showScreen('setup');
});

// ============================================================================
// Emergency Unlock (Only active when Unbreakable mode is OFF)
// ============================================================================
emergencyExitBtn.addEventListener('click', () => {
  if (state.unbreakableMode) {
    alert('Unbreakable Mode is active. Quitting is disabled until the timer completes.');
    return;
  }

  emergencyExitBtn.style.display = 'none';
  emergencyDelayBox.classList.remove('hidden');
  state.penaltySeconds = 10;
  penaltySecondsEl.textContent = `${state.penaltySeconds}s remaining`;
  penaltyBar.style.width = '100%';

  const startTime = Date.now();
  const totalMs = 10000;

  state.penaltyInterval = setInterval(() => {
    const elapsed = Date.now() - startTime;
    const remaining = Math.max(0, totalMs - elapsed);
    const secs = Math.ceil(remaining / 1000);

    penaltySecondsEl.textContent = `${secs}s remaining`;
    penaltyBar.style.width = `${(remaining / totalMs) * 100}%`;

    if (remaining <= 0) {
      clearInterval(state.penaltyInterval);
      state.penaltyInterval = null;
      abortSession();
    }
  }, 100);
});

cancelEmergencyBtn.addEventListener('click', () => {
  if (state.penaltyInterval) {
    clearInterval(state.penaltyInterval);
    state.penaltyInterval = null;
  }
  emergencyDelayBox.classList.add('hidden');
  emergencyExitBtn.style.display = 'inline-block';
});

function abortSession() {
  stopTimer();
  releaseScreenWakeLock();
  exitFullscreen();

  if (state.isNativeApp) {
    if (window.AndroidLocker.setDND) window.AndroidLocker.setDND(false);
    if (window.AndroidLocker.stopLockTask) window.AndroidLocker.stopLockTask();
    if (window.AndroidLocker.cancelAutoUnlock) window.AndroidLocker.cancelAutoUnlock();
  }

  emergencyDelayBox.classList.add('hidden');
  emergencyExitBtn.style.display = 'inline-block';

  setDuration(state.selectedMinutes);
  showScreen('setup');
  notifyBackend('aborted', state.selectedMinutes);
}

// ============================================================================
// Analytics & History
// ============================================================================
function getStoredStats() {
  const raw = localStorage.getItem('zenlock_stats');
  if (!raw) {
    return { totalMinutes: 0, totalSessions: 0, sessions: [], todayCount: 0 };
  }
  try {
    const data = JSON.parse(raw);
    const todayStr = new Date().toDateString();
    data.todayCount = (data.sessions || []).filter(s => new Date(s.timestamp).toDateString() === todayStr).length;
    return data;
  } catch (e) {
    return { totalMinutes: 0, totalSessions: 0, sessions: [], todayCount: 0 };
  }
}

function saveSession(minutes) {
  const data = getStoredStats();
  data.totalMinutes += minutes;
  data.totalSessions += 1;
  data.sessions = data.sessions || [];
  data.sessions.unshift({
    minutes: minutes,
    timestamp: new Date().toISOString()
  });
  if (data.sessions.length > 50) data.sessions.pop();
  localStorage.setItem('zenlock_stats', JSON.stringify(data));
}

function renderStats() {
  const stats = getStoredStats();
  totalMinutesFocused.textContent = stats.totalMinutes;
  totalSessionsCount.textContent = stats.totalSessions;

  if (!stats.sessions || stats.sessions.length === 0) {
    historyList.innerHTML = '<p class="empty-state">No completed sessions yet. Start your first phone lock!</p>';
    return;
  }

  historyList.innerHTML = stats.sessions.map(s => {
    const d = new Date(s.timestamp);
    const dateStr = d.toLocaleDateString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
    return `
      <div class="history-item">
        <span class="history-date">${dateStr}</span>
        <span class="history-duration">+${s.minutes} min</span>
      </div>
    `;
  }).join('');
}

statsBtn.addEventListener('click', () => {
  renderStats();
  statsModal.classList.add('open');
});

closeStatsBtn.addEventListener('click', () => statsModal.classList.remove('open'));
statsModal.addEventListener('click', (e) => {
  if (e.target === statsModal) statsModal.classList.remove('open');
});

// ============================================================================
// Backend Notification Sync
// ============================================================================
async function notifyBackend(event, minutes) {
  try {
    await fetch('/api/session', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        event: event,
        minutes: minutes,
        timestamp: new Date().toISOString()
      })
    });
  } catch (err) {}
}

// Initial Check
document.addEventListener('DOMContentLoaded', () => {
  checkNativeEnvironment();
});
