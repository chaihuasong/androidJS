(function() {
  console.log('[clawbot-patch] v14 loaded');

  // ── 自动设置语言为简体中文 ────────────────────────────────────────────────
  var TARGET_LOCALE = 'zh-CN';

  function setLang() {
    var changed = false;

    // 1. <select> 元素：找到含 zh-CN 选项的 select 并设值
    var selects = document.querySelectorAll('select');
    for (var i = 0; i < selects.length; i++) {
      var sel = selects[i];
      for (var j = 0; j < sel.options.length; j++) {
        var opt = sel.options[j];
        if (/zh.?CN|简体中文/i.test(opt.value + ' ' + opt.text)) {
          if (sel.value !== opt.value) {
            sel.value = opt.value;
            sel.dispatchEvent(new Event('change', { bubbles: true }));
            sel.dispatchEvent(new Event('input',  { bubbles: true }));
            console.log('[clawbot-patch] 语言 select 已设为:', opt.value);
            changed = true;
          }
          break;
        }
      }
    }

    // 2. 自定义下拉（role=option / role=menuitem / li）：点击含"简体中文"的可见项
    if (!changed) {
      var items = document.querySelectorAll('[role="option"],[role="menuitem"],li');
      for (var k = 0; k < items.length; k++) {
        var el = items[k];
        if (/简体中文/.test(el.textContent) && el.offsetParent !== null) {
          el.click();
          console.log('[clawbot-patch] 已点击简体中文选项');
          changed = true;
          break;
        }
      }
    }

    // 3. localStorage 持久化：覆盖常见 i18n key
    var keys = ['locale', 'language', 'lang', 'i18n', 'userLocale'];
    keys.forEach(function(k) {
      try {
        var v = localStorage.getItem(k);
        if (v !== null && v !== TARGET_LOCALE) {
          localStorage.setItem(k, TARGET_LOCALE);
          console.log('[clawbot-patch] localStorage.' + k + ' → ' + TARGET_LOCALE);
        }
      } catch(e) {}
    });

    return changed;
  }

  // 页面加载后立即尝试，再延迟重试（等待框架渲染）
  function initLang() {
    setLang();
    setTimeout(setLang, 800);
    setTimeout(setLang, 2000);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initLang);
  } else {
    initLang();
  }

  // ── 刷新按钮（Agent 运行期间每 5s 点击）────────────────────────────────────
  function clickRefresh() {
    var allBtns = document.querySelectorAll('button, [role="button"]');
    if (allBtns[8]) allBtns[8].click();
  }

  var _refreshTimer = null;

  function startRefreshLoop() {
    if (_refreshTimer) return;
    _refreshTimer = setInterval(clickRefresh, 5000);
  }

  function stopRefreshLoop() {
    if (_refreshTimer) { clearInterval(_refreshTimer); _refreshTimer = null; }
    setTimeout(clickRefresh, 300);
  }

  // ── WebSocket 拦截 ─────────────────────────────────────────────────────────
  var OrigWS = window.WebSocket;
  function PatchedWS(url, protocols) {
    var ws = protocols ? new OrigWS(url, protocols) : new OrigWS(url);
    ws.addEventListener('message', function(evt) {
      try {
        var msg = JSON.parse(evt.data);
        if (!msg || msg.type !== 'event' || msg.event !== 'agent') return;
        var p = msg.payload || {};
        var stream = p.stream;
        var phase = (p.data || {}).phase;

        if (stream === 'lifecycle' && phase === 'start') startRefreshLoop();
        if (stream === 'lifecycle' && (phase === 'end' || phase === 'error' || phase === 'complete')) stopRefreshLoop();
        if (stream === 'tool_start' || stream === 'tool_end') clickRefresh();
      } catch(e) {}
    });
    return ws;
  }
  PatchedWS.prototype = OrigWS.prototype;
  Object.keys(OrigWS).forEach(function(k) { try { PatchedWS[k] = OrigWS[k]; } catch(e) {} });
  window.WebSocket = PatchedWS;

  // ── DOM 观察：refresh recommended 提示 + 语言选择器出现 ──────────────────
  var _domObserver = new MutationObserver(function() {
    var body = document.body;
    if (!body) return;
    if (/refresh\s+recommended/i.test(body.innerText)) {
      setTimeout(clickRefresh, 200);
    }
    // 新 session 表单出现时自动设语言
    if (document.querySelector('select') || document.querySelector('[role="option"]')) {
      setLang();
    }
  });
  _domObserver.observe(document.documentElement, { childList: true, subtree: true, characterData: true });
})();
