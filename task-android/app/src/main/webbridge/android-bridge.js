(function () {
  var cursor = tokyoMonth(new Date());
  var selected = tokyoToday();
  var dirty = true;

  var style = document.createElement("style");
  style.textContent = [
    "#task-cal-root{color:#1c1917}",
    ".tc-head{display:flex;align-items:center;justify-content:space-between;margin:0 0 10px}",
    ".tc-head h2{margin:0;font-size:18px}",
    ".tc-head button{border:1px solid #e4ddd0;background:#fffdf8;border-radius:8px;min-width:44px;min-height:40px;font-size:18px}",
    ".tc-week,.tc-grid{display:grid;grid-template-columns:repeat(7,1fr);gap:4px;text-align:center}",
    ".tc-week{color:#6f675c;font-size:12px;margin-bottom:4px}",
    ".tc-day{position:relative;border:0;background:#fffdf8;border-radius:10px;min-height:46px;font-size:15px;color:#1c1917}",
    ".tc-day.on{outline:2px solid #1f5c45}",
    ".tc-day.mark::after{content:'';position:absolute;left:50%;bottom:6px;width:6px;height:6px;margin-left:-3px;border-radius:50%;background:#1f5c45}",
    ".tc-day.out{visibility:hidden}",
    ".tc-list{list-style:none;margin:12px 0 0;padding:0}",
    ".tc-list li{background:#fffdf8;border:1px solid #e4ddd0;border-radius:10px;padding:10px 12px;margin:0 0 8px;display:flex;gap:8px;align-items:baseline}",
    ".tc-list b{font-variant-numeric:tabular-nums}",
    ".tc-list .done{color:#6f675c;text-decoration:line-through}",
    ".tc-note{color:#6f675c;font-size:13px;line-height:1.5;margin:8px 0 0}"
  ].join("");
  document.head.appendChild(style);

  function tokyoToday() {
    try {
      return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Tokyo", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date());
    } catch (e) {
      return "";
    }
  }
  function tokyoMonth(date) {
    var iso = tokyoToday();
    return iso ? iso.slice(0, 7) : "2026-11";
  }
  function esc(value) {
    return String(value == null ? "" : value).replace(/[&<>"']/g, function (ch) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[ch];
    });
  }
  function memos() {
    var data = typeof window.__taskGet === "function" ? window.__taskGet() : null;
    return data && Array.isArray(data.memos) ? data.memos : [];
  }
  function hasTime(value) { return /^\d{2}:\d{2}$/.test(value || ""); }
  function onDay(iso) {
    return memos().filter(function (memo) { return memo && memo.due === iso; }).sort(function (a, b) {
      var at = hasTime(a.time) ? a.time : "99:99";
      var bt = hasTime(b.time) ? b.time : "99:99";
      if (at !== bt) return at < bt ? -1 : 1;
      return String(a.text || "").localeCompare(String(b.text || ""), "ja");
    });
  }

  function renderCalendar() {
    var root = document.getElementById("task-cal-root");
    if (!root) return;
    var parts = cursor.split("-").map(Number);
    var year = parts[0];
    var month = parts[1];
    var first = new Date(Date.UTC(year, month - 1, 1));
    var startPad = first.getUTCDay();
    var days = new Date(Date.UTC(year, month, 0)).getUTCDate();
    var marks = {};
    memos().forEach(function (memo) {
      if (memo && /^\d{4}-\d{2}-\d{2}$/.test(memo.due || "")) marks[memo.due] = true;
    });
    var cells = [];
    for (var i = 0; i < startPad; i++) cells.push('<button type="button" class="tc-day out" tabindex="-1"></button>');
    for (var day = 1; day <= days; day++) {
      var iso = cursor + "-" + String(day).padStart(2, "0");
      var cls = "tc-day" + (marks[iso] ? " mark" : "") + (iso === selected ? " on" : "");
      cells.push('<button type="button" class="' + cls + '" data-cal-day="' + iso + '">' + day + "</button>");
    }
    var rows = onDay(selected).map(function (memo) {
      var when = hasTime(memo.time) ? memo.time : "時刻なし";
      var done = memo.done ? " done" : "";
      return "<li class=\"" + done.trim() + "\"><b>" + esc(when) + "</b><span>" + esc(memo.text) + "</span></li>";
    }).join("");
    var label = year + "年" + month + "月";
    var selLabel = selected ? selected.replace(/-/g, "/") : "";
    root.innerHTML =
      '<div data-calview="1">' +
      '<div class="tc-head"><button type="button" data-cal-nav="-1" aria-label="前の月">‹</button><h2>' + label + '</h2><button type="button" data-cal-nav="1" aria-label="次の月">›</button></div>' +
      '<div class="tc-week"><span>日</span><span>月</span><span>火</span><span>水</span><span>木</span><span>金</span><span>土</span></div>' +
      '<div class="tc-grid">' + cells.join("") + "</div>" +
      "<h2 style=\"margin:14px 0 0;font-size:16px\">" + esc(selLabel) + "</h2>" +
      (rows ? '<ul class="tc-list">' + rows + "</ul>" : '<p class="tc-note">この日のメモはありません。</p>') +
      '<p class="tc-note">点のついた日にメモがあります。時刻を入れたメモは、その時刻に画面いっぱいで鳴ります。5分後にもう一度もできます。</p>' +
      "</div>";
    dirty = false;
  }

  document.addEventListener("click", function (event) {
    var nav = event.target.closest("[data-cal-nav]");
    if (nav && document.getElementById("task-cal-root")) {
      var parts = cursor.split("-").map(Number);
      var next = new Date(Date.UTC(parts[0], parts[1] - 1 + Number(nav.dataset.calNav), 1));
      cursor = next.getUTCFullYear() + "-" + String(next.getUTCMonth() + 1).padStart(2, "0");
      dirty = true;
      renderCalendar();
      return;
    }
    var day = event.target.closest("[data-cal-day]");
    if (day) {
      selected = day.dataset.calDay;
      dirty = true;
      renderCalendar();
    }
  });

  window.__taskOnData = function () {
    dirty = true;
    renderCalendar();
  };

  var appRoot = document.getElementById("root");
  if (appRoot) {
    new MutationObserver(function () {
      var root = document.getElementById("task-cal-root");
      if (root && (dirty || !root.querySelector("[data-calview]"))) renderCalendar();
    }).observe(appRoot, { childList: true, subtree: true });
  }
})();
