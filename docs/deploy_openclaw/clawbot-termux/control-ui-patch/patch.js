(function() {
  console.log('[clawbot-patch] v10 loaded');

  // ── 1. Click the refresh button (index 8 in current openclaw UI) ───────────
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

  // ── 2. Intercept WebSocket ─────────────────────────────────────────────────
  // Format: { type:"event", event:"agent", payload:{ stream:"lifecycle", data:{ phase:"start|end|error" } } }
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
})();
