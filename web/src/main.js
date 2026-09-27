/**
 * Scanly × TheKubics — Official Website Script
 * Shimmer loader, theme switcher, reveals, counters, interactive filter engine
 */

// ─── SHIMMER LOADER ──────────────────────────────────────
window.addEventListener('load', () => {
  const loader = document.getElementById('loader');
  if (loader) {
    setTimeout(() => {
      loader.classList.add('hide');
      // Trigger hero animations after loader
      document.body.classList.add('loaded');
    }, 200);
  }
});

// ─── THEME ───────────────────────────────────────────────
const THEME_KEY = 'scanly-theme';

function getSystemTheme() {
  return window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
}

function applyTheme(choice) {
  const resolved = choice === 'system' ? getSystemTheme() : choice;
  document.documentElement.setAttribute('data-theme', resolved);
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', resolved === 'dark' ? '#000000' : '#FAFAF9');

  document.querySelectorAll('.theme-btn').forEach(btn => {
    const isActive = btn.dataset.theme === choice;
    btn.setAttribute('aria-pressed', isActive ? 'true' : 'false');
    btn.classList.toggle('active', isActive);
  });
}

function initTheme() {
  const saved = localStorage.getItem(THEME_KEY) || 'dark';
  applyTheme(saved);

  document.querySelectorAll('.theme-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const choice = btn.dataset.theme;
      localStorage.setItem(THEME_KEY, choice);
      applyTheme(choice);
    });
  });

  window.matchMedia('(prefers-color-scheme: light)').addEventListener('change', () => {
    if (localStorage.getItem(THEME_KEY) === 'system') {
      applyTheme('system');
    }
  });
}

// ─── SCROLL REVEAL ───────────────────────────────────────
function initReveals() {
  const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  if (prefersReducedMotion) {
    document.querySelectorAll('.reveal').forEach(el => el.classList.add('revealed'));
    document.querySelectorAll('.stagger').forEach(el => el.classList.add('revealed'));
    return;
  }

  const revealObserver = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        entry.target.classList.add('revealed');
        revealObserver.unobserve(entry.target);
      }
    });
  }, { threshold: 0.1, rootMargin: '0px 0px -40px 0px' });

  const staggerObserver = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        entry.target.classList.add('revealed');
        staggerObserver.unobserve(entry.target);
      }
    });
  }, { threshold: 0.15, rootMargin: '0px 0px -60px 0px' });

  document.querySelectorAll('.reveal').forEach(el => revealObserver.observe(el));
  document.querySelectorAll('.stagger').forEach(el => staggerObserver.observe(el));
}

// ─── COUNTER ANIMATION ──────────────────────────────────
function animateCounters() {
  const counters = document.querySelectorAll('[data-target]');

  const observer = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        const el = entry.target;
        const target = parseInt(el.dataset.target, 10);
        const duration = 1200;
        const start = performance.now();

        function step(now) {
          const progress = Math.min((now - start) / duration, 1);
          const eased = 1 - Math.pow(1 - progress, 3);
          el.textContent = Math.round(eased * target);
          if (progress < 1) requestAnimationFrame(step);
        }

        requestAnimationFrame(step);
        observer.unobserve(el);
      }
    });
  }, { threshold: 0.3 });

  counters.forEach(c => observer.observe(c));
}



// ─── LAZY LOAD IMAGES ────────────────────────────────────
function initLazyImages() {
  if ('loading' in HTMLImageElement.prototype) {
    document.querySelectorAll('img[loading="lazy"]').forEach(img => {
      img.addEventListener('load', () => img.classList.add('loaded'));
      if (img.complete) img.classList.add('loaded');
    });
  } else {
    // Fallback for older browsers
    const lazyObserver = new IntersectionObserver((entries) => {
      entries.forEach(entry => {
        if (entry.isIntersecting) {
          const img = entry.target;
          img.src = img.dataset.src || img.src;
          img.classList.add('loaded');
          lazyObserver.unobserve(img);
        }
      });
    }, { rootMargin: '100px' });

    document.querySelectorAll('img[loading="lazy"]').forEach(img => {
      if (img.dataset.src) {
        img.src = img.dataset.src;
      }
      lazyObserver.observe(img);
    });
  }
}

// ─── SCROLL PROGRESS & NAV ───────────────────────────────
function initNav() {
  const nav = document.getElementById('nav');
  const toggle = document.querySelector('.menu-toggle');
  const links = document.getElementById('nav-links');
  const progressFill = document.querySelector('.scroll-progress-fill');
  const percentOut = document.querySelector('.scroll-percent');

  window.addEventListener('scroll', () => {
    const scrollY = window.scrollY;
    
    // Navbar elevation on scroll
    if (nav) {
      nav.classList.toggle('scrolled', scrollY > 24);
    }

    // Progress bar
    if (progressFill) {
      const docHeight = document.documentElement.scrollHeight - window.innerHeight;
      const pct = docHeight > 0 ? (scrollY / docHeight) * 100 : 0;
      progressFill.style.width = pct + '%';
      if (percentOut) percentOut.firstChild.textContent = Math.round(pct);
    }
  }, { passive: true });

  // Menu toggle (hamburger)
  toggle?.addEventListener('click', () => {
    const isOpen = toggle.getAttribute('aria-expanded') === 'true';
    toggle.setAttribute('aria-expanded', !isOpen);
    links?.classList.toggle('open', !isOpen);
    document.body.style.overflow = !isOpen ? 'hidden' : '';
  });

  // Close menu on link click
  links?.querySelectorAll('a').forEach(a => {
    a.addEventListener('click', () => {
      toggle?.setAttribute('aria-expanded', 'false');
      links.classList.remove('open');
      document.body.style.overflow = '';
    });
  });

  // Highlight active section on scroll
  const sections = document.querySelectorAll('section[id]');
  const navAnchors = links?.querySelectorAll('a[href^="#"]');

  const sectionObserver = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        const id = entry.target.id;
        navAnchors?.forEach(link => {
          link.classList.toggle('active', link.getAttribute('href') === '#' + id);
        });
      }
    });
  }, { threshold: 0.25, rootMargin: '-60px 0px -40% 0px' });

  sections.forEach(s => sectionObserver.observe(s));
  
  // Close menu on Escape key
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && links?.classList.contains('open')) {
      toggle?.setAttribute('aria-expanded', 'false');
      links.classList.remove('open');
      document.body.style.overflow = '';
    }
  });
}

// ─── INTERACTIVE FILTER & ADJUSTMENT ENGINE ──────────────
function initEnhanceDemo() {
  const modePills = document.querySelectorAll('.mode-pill');
  const doc = document.getElementById('interactiveDoc');
  const brightnessSlider = document.getElementById('brightnessSlider');
  const contrastSlider = document.getElementById('contrastSlider');
  const brightnessVal = document.getElementById('brightnessVal');
  const contrastVal = document.getElementById('contrastVal');
  const resetBtn = document.getElementById('resetFiltersBtn');

  let currentMode = 'original';

  function applyFilters() {
    if (!doc) return;
    const b = parseInt(brightnessSlider?.value || 0, 10);
    const c = parseInt(contrastSlider?.value || 0, 10);

    const bPct = 100 + b;
    const cPct = 100 + c * 1.5;

    let filterStr = `brightness(${bPct}%) contrast(${cPct}%)`;

    switch (currentMode) {
      case 'auto':
        filterStr += ' saturate(130%) contrast(115%)';
        break;
      case 'grayscale':
        filterStr += ' grayscale(100%) contrast(120%)';
        break;
      case 'bw':
        filterStr += ' grayscale(100%) contrast(250%) brightness(110%)';
        break;
      case 'contrast':
        filterStr += ' contrast(190%)';
        break;
      case 'color':
        filterStr += ' saturate(150%) contrast(110%)';
        break;
      case 'original':
      default:
        break;
    }

    doc.style.filter = filterStr;
  }

  modePills.forEach(pill => {
    pill.addEventListener('click', () => {
      modePills.forEach(p => p.classList.remove('active'));
      pill.classList.add('active');
      currentMode = pill.dataset.mode;
      applyFilters();
    });
    
    // Keyboard support
    pill.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        pill.click();
      }
    });
  });

  brightnessSlider?.addEventListener('input', () => {
    if (brightnessVal) brightnessVal.textContent = brightnessSlider.value;
    applyFilters();
  });

  contrastSlider?.addEventListener('input', () => {
    if (contrastVal) contrastVal.textContent = contrastSlider.value;
    applyFilters();
  });

  resetBtn?.addEventListener('click', () => {
    if (brightnessSlider) brightnessSlider.value = 0;
    if (contrastSlider) contrastSlider.value = 0;
    if (brightnessVal) brightnessVal.textContent = '0';
    if (contrastVal) contrastVal.textContent = '0';
    modePills.forEach(p => p.classList.toggle('active', p.dataset.mode === 'original'));
    currentMode = 'original';
    applyFilters();
  });
}

// ─── COMPOSER INTERACTION ────────────────────────────────
function initComposerDemo() {
  const pages = document.querySelectorAll('.composer-page');
  const footerIndicator = document.querySelector('.composer-footer span');
  const pageTitles = [
    'PAGE 01 OF 04 • A4 PORTRAIT • 300 DPI (COVER SHEET)',
    'PAGE 02 OF 04 • A4 PORTRAIT • 300 DPI (EXECUTIVE SUMMARY)',
    'PAGE 03 OF 04 • A4 PORTRAIT • 300 DPI (FINANCIAL STATEMENTS)',
    'PAGE 04 OF 04 • A4 PORTRAIT • 300 DPI (APPENDIX & NOTES)'
  ];

  pages.forEach((page, index) => {
    page.addEventListener('click', () => {
      pages.forEach(p => p.classList.remove('active'));
      page.classList.add('active');
      if (footerIndicator && pageTitles[index]) {
        footerIndicator.textContent = pageTitles[index];
      }
    });

    // Keyboard support
    page.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        page.click();
      }
    });
    
    // Make focusable
    page.setAttribute('tabindex', '0');
    page.setAttribute('role', 'button');
    page.setAttribute('aria-label', `Page ${index + 1}`);
  });
}

// ─── SMOOTH SCROLL ───────────────────────────────────────
function initSmoothScroll() {
  document.querySelectorAll('a[href^="#"]').forEach(a => {
    a.addEventListener('click', (e) => {
      const href = a.getAttribute('href');
      if (href === '#' || href === '') return;
      const target = document.querySelector(href);
      if (target) {
        e.preventDefault();
        const offset = 85;
        const top = target.getBoundingClientRect().top + window.scrollY - offset;
        window.scrollTo({ top, behavior: 'smooth' });
        history.replaceState(null, '', href);
      }
    });
  });
}

// ─── PERFORMANCE: DEFER NON-CRITICAL ─────────────────────
function initDeferred() {
  // Preload critical images
  const criticalImages = [
    './images/scanly-logo.jpg',
    './images/TheKubics-cube.png'
  ];
  
  criticalImages.forEach(src => {
    const link = document.createElement('link');
    link.rel = 'preload';
    link.as = 'image';
    link.href = src;
    document.head.appendChild(link);
  });
  
  // Initialize lazy loading after a delay
  setTimeout(initLazyImages, 1000);
}

// ─── LIVE PHONE STATUS BAR ───────────────────────────────
function initPhoneStatus() {
  const timeEl = document.getElementById('ui-time');
  const battEl = document.getElementById('ui-batt-fill');
  const pctEl = document.getElementById('ui-batt-pct');
  const sigEl = document.getElementById('ui-signal');
  const wifiEl = document.getElementById('ui-wifi');
  if (!timeEl) return;

  const tick = () => {
    const now = new Date();
    timeEl.textContent = now.toLocaleTimeString([], {
      hour: 'numeric',
      minute: '2-digit',
      hour12: true
    });
    const online = navigator.onLine !== false;
    if (wifiEl) wifiEl.dataset.state = online ? 'on' : 'off';
    if (sigEl) sigEl.dataset.state = online ? 'full' : 'none';
  };
  tick();
  setInterval(tick, 1000);

  window.addEventListener('online', tick);
  window.addEventListener('offline', tick);

  const paintBattery = (level, charging) => {
    const pct = Math.round(level * 100);
    if (pctEl) pctEl.textContent = pct + '%';
    if (battEl) {
      battEl.style.width = Math.max(4, pct) + '%';
      battEl.dataset.level = pct <= 15 ? 'low' : pct <= 40 ? 'mid' : 'high';
      battEl.dataset.charging = charging ? 'true' : 'false';
    }
  };

  if ('getBattery' in navigator) {
    navigator.getBattery().then(b => {
      const sync = () => paintBattery(b.level, b.charging);
      sync();
      b.addEventListener('levelchange', sync);
      b.addEventListener('chargingchange', sync);
    }).catch(() => paintBattery(0.86, false));
  } else {
    paintBattery(0.86, false);
  }
}

// ─── INITIALIZATION ──────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
  initTheme();
  initReveals();
  animateCounters();
  initNav();
  initPhoneStatus();
  initEnhanceDemo();
  initComposerDemo();
  initSmoothScroll();
  initDeferred();
});

// ─── ERROR HANDLING ──────────────────────────────────────
window.addEventListener('error', (e) => {
  console.warn('[Scanly Web] Error caught:', e.message);
});
