(function () {
  var today = tokyoIso(new Date());
  var cursor = today.slice(0, 7);
  var selected = today;
  var dirty = true;

  var style = document.createElement("style");
  style.textContent = [
    "#task-cal-host{color:#1c1917}",
    "#task-restore-ask{position:fixed;z-index:60;left:0;right:0;top:0;bottom:0;background:rgba(28,25,23,.45);display:flex;align-items:center;justify-content:center;padding:24px}",
    "#task-restore-ask .za-ask-card{background:#fffdf8;color:#1c1917;border-radius:16px;padding:18px 16px;max-width:320px;width:100%}",
    "#task-restore-ask p{margin:0 0 10px;font-size:15px;line-height:1.5}",
    "#task-restore-ask .za-ask-actions{display:flex;gap:8px;justify-content:flex-end;margin-top:6px}",
    "#task-restore-ask button{border:1px solid #e4ddd0;background:#fff;border-radius:999px;padding:8px 14px;font-size:15px;font-weight:700;color:#1c1917}",
    "#task-restore-ask button[data-ask=yes]{background:#1f5c45;color:#fffdf8;border-color:#1f5c45}",
    ".tc-card{background:#fffdf8;border:1px solid #e4ddd0;border-radius:12px;padding:12px}",
    ".tc-head{display:flex;align-items:center;justify-content:space-between;margin:0 0 8px}",
    ".tc-head h2{margin:0;font-size:18px}",
    ".tc-head button{border:1px solid #e4ddd0;background:#fffdf8;border-radius:8px;min-width:44px;min-height:40px;font-size:18px;color:#1c1917}",
    ".tc-week,.tc-grid{display:grid;grid-template-columns:repeat(7,1fr);gap:3px;text-align:center}",
    ".tc-week{color:#6f675c;font-size:12px;margin-bottom:4px}",
    ".tc-day{position:relative;border:0;background:#f7f3ea;border-radius:10px;min-height:44px;padding:4px 0 12px;font-size:14px;color:#1c1917}",
    ".tc-day.on{outline:2px solid #1f5c45;background:#fffdf8}",
    ".tc-day.today{font-weight:700}",
    ".tc-day.out{visibility:hidden}",
    ".tc-dots{position:absolute;left:0;right:0;bottom:4px;display:flex;justify-content:center;gap:2px;height:6px}",
    ".tc-dots i{width:6px;height:6px;border-radius:50%;display:block}",
    ".tc-dots .memo{background:#1f5c45}",
    ".tc-dots .clean{background:#9a6700}",
    ".tc-dots .bousai{background:#b42318}",
    ".tc-dots .car{background:#3d4f66}",
    ".tc-legend{display:flex;flex-wrap:wrap;gap:8px 12px;margin:8px 0 0;color:#6f675c;font-size:12px}",
    ".tc-legend span{display:inline-flex;align-items:center;gap:4px}",
    ".tc-legend i{width:8px;height:8px;border-radius:50%;display:block}",
    ".tc-legend .memo{background:#1f5c45}",
    ".tc-legend .clean{background:#9a6700}",
    ".tc-legend .bousai{background:#b42318}",
    ".tc-legend .car{background:#3d4f66}",
    ".tc-dayhead{margin:12px 0 0;font-size:15px}",
    ".tc-list{list-style:none;margin:8px 0 0;padding:0}",
    ".tc-list li,.tc-list button{background:#fffdf8;border:1px solid #e4ddd0;border-radius:10px;padding:10px 12px;margin:0 0 8px;display:flex;gap:8px;align-items:center;width:100%;text-align:left;font-size:14px;color:#1c1917}",
    ".tc-list b{font-variant-numeric:tabular-nums;min-width:4.5em}",
    ".tc-list .done span{color:#6f675c;text-decoration:line-through}",
    ".tc-kind{flex:none;font-size:11px;border-radius:999px;padding:1px 6px}",
    ".tc-kind.clean{color:#9a6700;background:#f8efd8}",
    ".tc-kind.bousai{color:#b42318;background:#f8e4e1}",
    ".tc-kind.car{color:#3d4f66;background:#e4e8ee}",
    ".tc-shift{margin-left:auto;color:#9a6700;font-size:11px}",
    ".tc-note{color:#6f675c;font-size:12px;line-height:1.5;margin:8px 0 0}",
    "#task-summary-host .ts-card{background:#fffdf8;border:1px solid #e4ddd0;border-radius:12px;padding:16px;color:#1c1917}",
    "#task-summary-host h2{margin:0 0 4px;font-size:16px}",
    "#task-summary-host p{margin:0 0 12px;color:#6f675c;font-size:14px;line-height:1.5}",
    "#task-summary-host label{display:flex;align-items:center;gap:8px;font-size:14px;color:#6f675c}",
    "#task-summary-host input{font-size:16px;padding:6px 8px;border:1px solid #e4ddd0;border-radius:8px;background:#fffdf8;color:#1c1917}",
    "#task-ring-host .tr-row{display:flex;align-items:center;gap:8px;padding:6px 0;font-size:15px}",
    "#task-ring-host input{width:18px;height:18px}",
    "#task-voice-host .ts-card{background:#fffdf8;border:1px solid #e4ddd0;border-radius:12px;padding:16px;color:#1c1917}",
    "#task-voice-host h2{margin:0 0 4px;font-size:16px}",
    "#task-voice-host p{margin:8px 0 0;color:#6f675c;font-size:14px;line-height:1.5}",
    "#task-voice-host label{display:flex;align-items:center;gap:8px;font-size:15px}",
    "#task-voice-host input{width:18px;height:18px}",
    "#task-ahead-host .ts-card{background:#fffdf8;border:1px solid #e4ddd0;border-radius:12px;padding:16px;color:#1c1917}",
    "#task-ahead-host h2{margin:0 0 4px;font-size:16px}",
    "#task-ahead-host p{margin:8px 0 0;color:#6f675c;font-size:14px;line-height:1.5}",
    "#task-ahead-host label{display:flex;align-items:center;gap:8px;font-size:15px;margin:8px 0}",
    "#task-ahead-host select{font-size:16px;padding:6px 8px;border:1px solid #e4ddd0;border-radius:8px;background:#fffdf8;color:#1c1917}",
    "#task-ahead-host input{width:18px;height:18px}",
    "#task-voice-sheet{position:fixed;inset:0;z-index:80;background:rgba(28,25,23,.45);display:flex;align-items:flex-end;justify-content:center}",
    "#task-voice-sheet .box{width:min(420px,100%);background:#fffdf8;color:#1c1917;border-radius:16px 16px 0 0;padding:16px 16px 28px;box-sizing:border-box}",
    "#task-voice-sheet h2{margin:0 0 8px;font-size:18px}",
    "#task-voice-sheet .spoken,#task-voice-sheet .when{margin:0 0 8px;color:#6f675c;font-size:14px;line-height:1.5}",
    "#task-voice-sheet label{display:block;margin:10px 0 4px;color:#6f675c;font-size:13px}",
    "#task-voice-sheet input{width:100%;box-sizing:border-box;font-size:16px;padding:8px 10px;border:1px solid #e4ddd0;border-radius:8px;background:#fff;color:#1c1917}",
    "#task-voice-sheet .warn{min-height:1.2em;margin:8px 0 0;color:#b42318;font-size:13px}",
    "#task-voice-sheet .hint{margin:8px 0 0;color:#6f675c;font-size:13px;line-height:1.4}",
    "#task-voice-sheet .listen{display:none;align-items:center;gap:8px;margin-top:6px;color:#1f5c45;font-size:14px}",
    "#task-voice-sheet .listen.on{display:flex}",
    "#task-voice-sheet .listen i{width:8px;height:8px;border-radius:50%;background:#1f5c45;display:block}",
    "#task-voice-sheet .row{display:flex;gap:8px;margin-top:12px}",
    "#task-voice-sheet .row button{flex:1;min-height:44px;border-radius:10px;border:1px solid #e4ddd0;background:#fff;color:#1c1917;font-size:15px}",
    "#task-voice-sheet .row button.save{background:#1f5c45;color:#fff;border-color:#1f5c45}",
    "#task-voice-sheet button.mic{display:inline-flex;width:auto;min-height:36px;margin-top:8px;padding:0 12px;border-radius:10px;border:1px solid #e4ddd0;background:#fff;color:#1f5c45;font-size:14px}",
    "#task-voice-sheet.listening button.mic{display:none}"
  ].join("");
  document.head.appendChild(style);

  function tokyoIso(date) {
    try {
      return new Intl.DateTimeFormat("en-CA", {
        timeZone: "Asia/Tokyo",
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
      }).format(date);
    } catch (e) {
      return "2026-11-03";
    }
  }
  function pad(n) { return String(n).padStart(2, "0"); }
  function isoDate(value) { return /^\d{4}-\d{2}-\d{2}$/.test(value || ""); }
  function hasTime(value) { return /^\d{2}:\d{2}$/.test(value || ""); }
  function esc(value) {
    return String(value == null ? "" : value).replace(/[&<>"']/g, function (ch) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[ch];
    });
  }
  function cmp(a, b) {
    a = String(a || "");
    b = String(b || "");
    if (a < b) return -1;
    if (a > b) return 1;
    return 0;
  }
  function addDays(iso, days) {
    var p = iso.split("-").map(Number);
    return new Date(Date.UTC(p[0], p[1] - 1, p[2]) + days * 86400000).toISOString().slice(0, 10);
  }
  function addMonths(iso, months) {
    var p = iso.split("-").map(Number);
    var total = p[0] * 12 + (p[1] - 1) + months;
    var year = Math.floor(total / 12);
    var month0 = total % 12;
    if (month0 < 0) month0 += 12;
    var dim = new Date(Date.UTC(year, month0 + 1, 0)).getUTCDate();
    return year + "-" + pad(month0 + 1) + "-" + pad(Math.min(p[2], dim));
  }
  function jpDate(iso) {
    var p = iso.split("-").map(Number);
    var dow = "日月火水木金土"[new Date(Date.UTC(p[0], p[1] - 1, p[2])).getUTCDay()];
    return p[1] + "月" + p[2] + "日（" + dow + "）";
  }
  function getData() {
    return typeof window.__taskGet === "function" ? window.__taskGet() : null;
  }
  function wholeNumber(value) {
    return typeof value === "number" && isFinite(value) && Math.floor(value) === value ? value : null;
  }
  function nextClean(item) {
    var cycle = wholeNumber(item && item.cycle);
    if (!item || !isoDate(item.done) || cycle == null) return null;
    return addDays(item.done, cycle);
  }
  function nextCar(item) {
    var months = wholeNumber(item && item.months);
    if (!item || !isoDate(item.done) || months == null || months === 0) return null;
    return addMonths(item.done, months);
  }
  function cleanTitle(item) {
    return [item.place, item.task].map(function (v) { return String(v || "").trim(); }).filter(Boolean).join(" ") || "掃除";
  }
  function bousaiTitle(item) {
    var name = String(item.item || "").trim() || "防災グッズ";
    var qty = String(item.qty || "").trim();
    return qty ? name + "（" + qty + "）" : name;
  }
  var EXTRA_COLORS = ["#6b4c9a", "#0f6f8a", "#c45c26", "#5b7a3a", "#8a3d6b", "#3f5f8a", "#8f4d4d", "#2f6f62", "#7a5b2e", "#4c6e8a"];
  function shown(natural, item) {
    return item && isoDate(item.shift) ? item.shift : natural;
  }
  function board() {
    var data = getData() || {};
    var removed = {};
    (Array.isArray(data.removed) ? data.removed : []).forEach(function (kind) { removed[kind] = true; });
    var memos = (Array.isArray(data.memos) ? data.memos : []).filter(function (memo) {
      return memo && String(memo.text || "").trim();
    });
    var cleaning = removed.clean || !Array.isArray(data.cleaning) ? [] : data.cleaning.filter(Boolean);
    var bousai = removed.bousai || !Array.isArray(data.bousai) ? [] : data.bousai.filter(Boolean);
    var cars = [];
    var extras = [];
    var customIds = [];
    if (Array.isArray(data.tabs)) {
      data.tabs.forEach(function (tab) {
        if (!tab || !Array.isArray(tab.items)) return;
        if (tab.kind === "car") {
          if (removed.car) return;
          tab.items.forEach(function (item) {
            if (item) cars.push({ tabId: tab.id || "car", name: tab.name || "車", item: item });
          });
        } else {
          customIds.push(tab.id || "tab");
          tab.items.forEach(function (item) {
            if (item) extras.push({ tabId: tab.id || "tab", name: tab.name || "タブ", item: item });
          });
        }
      });
    }
    return { memos: memos, cleaning: cleaning, bousai: bousai, cars: cars, extras: extras, customIds: customIds, removed: removed, rings: data.rings || {} };
  }
  function byTime(rows) {
    return rows.sort(function (a, b) {
      var at = a.time ? 0 : 1;
      var bt = b.time ? 0 : 1;
      if (at !== bt) return at - bt;
      var byTime = cmp(a.time, b.time);
      if (at === 0 && byTime) return byTime;
      return cmp(a.title, b.title) || cmp(a.kind, b.kind) || cmp(a.id, b.id);
    });
  }
  function itemsOn(iso) {
    var data = board();
    var rows = [];
    data.memos.forEach(function (memo) {
      if (memo.due !== iso) return;
      rows.push({ kind: "memo", title: String(memo.text).trim(), time: hasTime(memo.time) ? memo.time : null, done: !!memo.done, label: "メモ" });
    });
    data.cleaning.forEach(function (item) {
      if (shown(nextClean(item), item) !== iso) return;
      rows.push({ kind: "clean", id: item.id, title: cleanTitle(item), tabId: "clean", time: hasTime(item.time) ? item.time : null, label: "掃除", shifted: isoDate(item.shift) });
    });
    data.bousai.forEach(function (item) {
      if (shown(isoDate(item.due) ? item.due : null, item) !== iso) return;
      rows.push({ kind: "bousai", id: item.id, title: bousaiTitle(item), tabId: "bousai", time: hasTime(item.time) ? item.time : null, label: "防災", shifted: isoDate(item.shift) });
    });
    data.cars.forEach(function (row) {
      if (shown(nextCar(row.item), row.item) !== iso) return;
      rows.push({ kind: "car", id: row.item.id, title: String(row.item.name || "").trim() || "車", tabId: row.tabId, time: hasTime(row.item.time) ? row.item.time : null, label: "車", shifted: isoDate(row.item.shift) });
    });
    data.extras.forEach(function (row) {
      if (shown(nextCar(row.item), row.item) !== iso) return;
      rows.push({ kind: "x:" + row.tabId, id: row.item.id, title: String(row.item.name || "").trim() || row.name, tabId: row.tabId, time: hasTime(row.item.time) ? row.item.time : null, label: row.name, shifted: isoDate(row.item.shift) });
    });
    return byTime(rows);
  }
  function marksFor(month) {
    var marks = {};
    function add(iso, kind) {
      if (!isoDate(iso) || iso.slice(0, 7) !== month) return;
      (marks[iso] || (marks[iso] = {}))[kind] = true;
    }
    var data = board();
    data.memos.forEach(function (memo) { if (isoDate(memo.due)) add(memo.due, "memo"); });
    data.cleaning.forEach(function (item) { add(shown(nextClean(item), item), "clean"); });
    data.bousai.forEach(function (item) { add(shown(isoDate(item.due) ? item.due : null, item), "bousai"); });
    data.cars.forEach(function (row) { add(shown(nextCar(row.item), row.item), "car"); });
    data.extras.forEach(function (row) { add(shown(nextCar(row.item), row.item), "x:" + row.tabId); });
    return marks;
  }
  function extraColor(kind) {
    var data = board();
    var id = String(kind).slice(2);
    var index = data.customIds.indexOf(id);
    if (index < 0) index = 0;
    return EXTRA_COLORS[index % EXTRA_COLORS.length];
  }
  function dotHtml(kind) {
    if (kind === "memo" || kind === "clean" || kind === "bousai" || kind === "car") return '<i class="' + kind + '"></i>';
    return '<i style="background:' + extraColor(kind) + '"></i>';
  }
  function dots(kinds) {
    if (!kinds) return "";
    var order = ["memo", "clean", "bousai", "car"].concat(Object.keys(kinds).filter(function (kind) { return kind.slice(0, 2) === "x:"; }));
    var html = "";
    order.forEach(function (kind) { if (kinds[kind]) html += dotHtml(kind); });
    return html ? '<span class="tc-dots">' + html + "</span>" : "";
  }
  function legendHtml() {
    var data = board();
    var html = '<span><i class="memo"></i>メモ</span>';
    if (!data.removed.clean) html += '<span><i class="clean"></i>掃除</span>';
    if (!data.removed.bousai) html += '<span><i class="bousai"></i>防災</span>';
    if (!data.removed.car) html += '<span><i class="car"></i>車</span>';
    var seen = {};
    data.extras.forEach(function (row) {
      if (seen[row.tabId]) return;
      seen[row.tabId] = true;
      html += '<span><i style="background:' + extraColor("x:" + row.tabId) + '"></i>' + esc(row.name) + "</span>";
    });
    return '<div class="tc-legend">' + html + "</div>";
  }
  function rowHtml(row) {
    var when = row.time || (row.kind === "memo" ? "時刻なし" : "");
    var shifted = row.shifted ? '<span class="tc-shift">ずらし中</span>' : "";
    if (row.kind === "memo") {
      return '<li class="' + (row.done ? "done" : "") + '"><b>' + esc(when) + "</b><span>" + esc(row.title) + "</span></li>";
    }
    var swatch = row.kind.slice(0, 2) === "x:" ? ' style="background:' + extraColor(row.kind) + ';color:#fff"' : "";
    return '<li><button type="button" data-cal-open="' + (row.kind.slice(0, 2) === "x:" ? "tab" : row.kind) + '" data-cal-id="' + esc(row.id) + '" data-cal-tab="' + esc(row.tabId) + '">' +
      (when ? "<b>" + esc(when) + "</b>" : "") +
      '<span class="tc-kind ' + (row.kind.slice(0, 2) === "x:" ? "" : row.kind) + '"' + swatch + ">" + esc(row.label) + "</span><span>" + esc(row.title) + "</span>" + shifted + "</button></li>";
  }

  function renderCalendar() {
    var root = document.getElementById("task-cal-host");
    if (!root) return;
    var parts = cursor.split("-").map(Number);
    var year = parts[0];
    var month = parts[1];
    var startPad = new Date(Date.UTC(year, month - 1, 1)).getUTCDay();
    var days = new Date(Date.UTC(year, month, 0)).getUTCDate();
    var marked = marksFor(cursor);
    var cells = [];
    for (var i = 0; i < startPad; i++) cells.push('<button type="button" class="tc-day out" tabindex="-1"></button>');
    for (var day = 1; day <= days; day++) {
      var iso = cursor + "-" + pad(day);
      var cls = "tc-day" + (iso === selected ? " on" : "") + (iso === today ? " today" : "");
      cells.push('<button type="button" class="' + cls + '" data-cal-day="' + iso + '">' + day + dots(marked[iso]) + "</button>");
    }
    var rows = itemsOn(selected).map(rowHtml).join("");
    root.innerHTML =
      '<div class="tc-card" data-calview="1">' +
      '<div class="tc-head"><button type="button" data-cal-nav="-1" aria-label="前の月">‹</button><h2>' + year + "年" + month + '月</h2><button type="button" data-cal-nav="1" aria-label="次の月">›</button></div>' +
      '<div class="tc-week"><span>日</span><span>月</span><span>火</span><span>水</span><span>木</span><span>金</span><span>土</span></div>' +
      '<div class="tc-grid">' + cells.join("") + "</div>" +
      legendHtml() +
      '<h3 class="tc-dayhead">' + esc(jpDate(selected)) + "</h3>" +
      (rows ? '<ul class="tc-list">' + rows + "</ul>" : '<p class="tc-note">この日の予定はありません。</p>') +
      '<p class="tc-note">日を押すと、その日がメモの日付になります。項目を押すと、そのタブで開けます。</p>' +
      "</div>";
    dirty = false;
  }

  function summaryTime() {
    try {
      if (window.TaskNative && TaskNative.getSummaryTime) {
        var value = TaskNative.getSummaryTime();
        if (hasTime(value)) return value;
      }
    } catch (e) {}
    return "06:00";
  }
  function renderSummary() {
    var host = document.getElementById("task-summary-host");
    if (!host || host.querySelector("[data-summary]")) return;
    host.innerHTML =
      '<div class="ts-card" data-summary="1">' +
      "<h2>朝のまとめ</h2>" +
      "<p>毎日1回、その日のメモと、期限のある掃除・防災・車・自分のタブを通知します。何もない日は出しません。</p>" +
      '<label>通知の時刻<input type="time" step="60" data-summary-time aria-label="朝のまとめの時刻"></label>' +
      "</div>";
    var input = host.querySelector("[data-summary-time]");
    input.value = summaryTime();
    input.addEventListener("change", function () {
      if (!hasTime(input.value)) return;
      try {
        if (window.TaskNative && TaskNative.setSummaryTime) TaskNative.setSummaryTime(input.value);
      } catch (e) {}
    });
  }
  function ringOn(id) {
    var rings = board().rings;
    if (id === "memo") return rings.memo !== false;
    return rings[id] === true;
  }
  function renderRings() {
    var host = document.getElementById("task-ring-host");
    if (!host) return;
    var data = getData() || {};
    var removed = board().removed;
    var rows = [{ id: "memo", name: "メモ" }];
    if (!removed.clean) rows.push({ id: "clean", name: "掃除" });
    if (!removed.bousai) rows.push({ id: "bousai", name: "防災" });
    (Array.isArray(data.tabs) ? data.tabs : []).forEach(function (tab) {
      if (!tab) return;
      if (tab.kind === "car" && removed.car) return;
      rows.push({ id: tab.id || (tab.kind === "car" ? "car" : "tab"), name: tab.name || (tab.kind === "car" ? "車" : "タブ") });
    });
    var list = rows.map(function (row) {
      return '<label class="tr-row"><input type="checkbox" data-ring="' + esc(row.id) + '"' + (ringOn(row.id) ? " checked" : "") + "><span>" + esc(row.name) + "</span></label>";
    }).join("");
    host.innerHTML =
      '<div class="ts-card" data-rings="1">' +
      "<h2>時刻に鳴らすタブ</h2>" +
      "<p>オンのタブは、時刻を入れた項目がその時刻に鳴ります。メモだけ最初からオンです。朝のまとめは、オンオフに関係なく出ます。</p>" +
      list +
      "</div>";
    host.querySelectorAll("[data-ring]").forEach(function (box) {
      box.addEventListener("change", function () {
        if (window.__taskSetRing) window.__taskSetRing(box.getAttribute("data-ring"), box.checked);
      });
    });
  }

  function openChore(kind, id, tabId) {
    var data = getData();
    if (!data || typeof window.__taskOpen !== "function") return;
    if (kind === "clean") {
      var clean = (data.cleaning || []).filter(function (item) { return item && String(item.id) === String(id); })[0];
      if (!clean) return;
      if (window.__taskSetTab) window.__taskSetTab("clean");
      window.__taskOpen({ t: "c", item: clean });
    } else if (kind === "bousai") {
      var gear = (data.bousai || []).filter(function (item) { return item && String(item.id) === String(id); })[0];
      if (!gear) return;
      if (window.__taskSetTab) window.__taskSetTab("bousai");
      window.__taskOpen({ t: "b", item: gear });
    } else if (kind === "car" || kind === "tab") {
      var tab = (data.tabs || []).filter(function (item) { return item && String(item.id) === String(tabId); })[0];
      var car = tab && (tab.items || []).filter(function (item) { return item && String(item.id) === String(id); })[0];
      if (!car) return;
      if (window.__taskSetTab) window.__taskSetTab(tabId);
      window.__taskOpen({ t: "t", tid: tabId, item: car });
    }
  }

  function stopCommandListen() {
    try { if (window.TaskNative && TaskNative.stopVoice) TaskNative.stopVoice(); } catch (e) {}
  }
  function listenForCommand() {
    try { if (window.TaskNative && TaskNative.listenForCommand) TaskNative.listenForCommand(); } catch (e) {}
  }
  function discardVoiceSheet() {
    var old = document.getElementById("task-voice-sheet");
    if (old) old.remove();
  }
  function removeVoiceSheet() {
    stopCommandListen();
    discardVoiceSheet();
  }
  function setVoiceListening(on) {
    var sheet = document.getElementById("task-voice-sheet");
    if (!sheet) return;
    sheet.classList.toggle("listening", !!on);
    var row = sheet.querySelector(".listen");
    if (row) row.classList.toggle("on", !!on);
  }
  function refreshVoiceWhen(sheet) {
    var date = sheet.querySelector("#task-voice-date").value;
    var time = sheet.querySelector("#task-voice-time").value;
    var when = sheet.querySelector(".when");
    if (when) when.textContent = describeWhen(date, time);
  }
  function voiceBox(html) {
    discardVoiceSheet();
    var sheet = document.createElement("div");
    sheet.id = "task-voice-sheet";
    sheet.innerHTML = '<div class="box" role="dialog" aria-modal="true">' + html + "</div>";
    document.body.appendChild(sheet);
    return sheet;
  }
  function describeWhen(date, time) {
    if (!date && !time) return "日付なしのメモになります";
    var label = "";
    if (date && /^\d{4}-\d{2}-\d{2}$/.test(date)) {
      var p = date.split("-").map(Number);
      label = p[1] + "月" + p[2] + "日";
    }
    if (time) label += (label ? " " : "") + time;
    if (date && !time) label += "（時刻なし）";
    return label || "日付なしのメモになります";
  }
  function showVoiceCapture(text) {
    var sheet = voiceBox(
      "<h2>声で入れる</h2>" +
      "<p class='spoken' id='task-voice-draft'>" + esc(text || "聞いています。ゆっくり話してください。") + "</p>" +
      "<p class='hint'>言い終わったら「できた」と言うか、押してください。少し間を置いても大丈夫です。「やめる」でも止められます。</p>" +
      '<div class="listen on"><i></i><span>聞いています</span></div>' +
      '<div class="row"><button type="button" class="save" data-voice-done>できた</button>' +
      '<button type="button" data-voice-close>やめる</button></div>'
    );
    sheet.querySelector("[data-voice-done]").addEventListener("click", function () {
      try { if (window.TaskNative && TaskNative.finishDictation) TaskNative.finishDictation(); } catch (e) {}
    });
    sheet.querySelector("[data-voice-close]").addEventListener("click", function () {
      try { if (window.TaskNative && TaskNative.stopVoice) TaskNative.stopVoice(); } catch (e) {}
      discardVoiceSheet();
    });
  }
  window.__taskVoiceDraft = function (raw) {
    var data = null;
    try { data = typeof raw === "string" ? JSON.parse(raw) : raw; } catch (e) { data = null; }
    if (!data) return;
    if (data.cancel) {
      discardVoiceSheet();
      return;
    }
    if (data.message) {
      showVoiceMessage(data.message);
      return;
    }
    var sheet = document.getElementById("task-voice-sheet");
    if (!sheet || !sheet.querySelector("[data-voice-done]")) showVoiceCapture(data.text);
    sheet = document.getElementById("task-voice-sheet");
    var draft = sheet && sheet.querySelector("#task-voice-draft");
    if (draft) draft.textContent = data.text || "聞いています。ゆっくり話してください。";
  };
  function showVoiceMessage(message) {
    var sheet = voiceBox(
      "<h2>声で入れる</h2><p class='spoken'>" + esc(message) + "</p>" +
      '<div class="row"><button type="button" data-voice-close>閉じる</button></div>'
    );
    sheet.querySelector("[data-voice-close]").addEventListener("click", removeVoiceSheet);
  }
  function showVoiceConfirm(data) {
    var sheet = voiceBox(
      "<h2>この内容で保存します</h2>" +
      "<p class='spoken'>聞いたこと：" + esc(data.spoken || "") + "</p>" +
      "<p class='when'>" + esc(describeWhen(data.date, data.time)) + "</p>" +
      "<label for='task-voice-text'>内容</label>" +
      "<input id='task-voice-text' type='text' value='" + esc(data.text || "") + "'>" +
      "<label for='task-voice-date'>日付（なくても可）</label>" +
      "<input id='task-voice-date' type='date' value='" + esc(data.date || "") + "'>" +
      "<label for='task-voice-time'>時刻（なくても可）</label>" +
      "<input id='task-voice-time' type='time' value='" + esc(data.time || "") + "'>" +
      "<p class='warn' id='task-voice-warn'></p>" +
      '<p class="hint">『保存』『やり直し』『やめる』と話せます</p>' +
      '<div class="listen" id="task-voice-listen"><i></i><span>聞いています</span></div>' +
      '<button type="button" class="mic" data-voice-mic aria-label="もう一度聞く">もう一度聞く</button>' +
      '<div class="row"><button type="button" class="save" data-voice-save>保存</button>' +
      '<button type="button" data-voice-retry>やり直す</button>' +
      '<button type="button" data-voice-close>やめる</button></div>'
    );
    sheet.querySelector("#task-voice-date").addEventListener("input", function () { refreshVoiceWhen(sheet); });
    sheet.querySelector("#task-voice-time").addEventListener("input", function () { refreshVoiceWhen(sheet); });
    sheet.querySelector("[data-voice-mic]").addEventListener("click", function () {
      var warn = sheet.querySelector("#task-voice-warn");
      if (warn) warn.textContent = "";
      listenForCommand();
    });
    sheet.querySelector("[data-voice-close]").addEventListener("click", function () {
      removeVoiceSheet();
    });
    sheet.querySelector("[data-voice-retry]").addEventListener("click", function () {
      stopCommandListen();
      removeVoiceSheet();
      try { if (window.TaskNative && TaskNative.startVoice) TaskNative.startVoice(); } catch (e) {}
    });
    sheet.querySelector("[data-voice-save]").addEventListener("click", function () {
      stopCommandListen();
      var text = sheet.querySelector("#task-voice-text").value.trim();
      var warn = sheet.querySelector("#task-voice-warn");
      if (!text) {
        warn.textContent = "内容を入れてください";
        return;
      }
      if (typeof window.__taskAddMemo !== "function") {
        warn.textContent = "まだ保存できません。少し待ってから、もう一度保存を押してください";
        return;
      }
      window.__taskAddMemo(text, sheet.querySelector("#task-voice-date").value, sheet.querySelector("#task-voice-time").value);
      removeVoiceSheet();
    });
    listenForCommand();
  }
  window.__taskVoiceCommand = function (raw) {
    var sheet = document.getElementById("task-voice-sheet");
    if (!sheet) return;
    var data = null;
    try { data = typeof raw === "string" ? JSON.parse(raw) : raw; } catch (e) { data = null; }
    if (!data) return;
    if (!data.command && typeof data.listening === "boolean") {
      setVoiceListening(data.listening);
      return;
    }
    if (data.message) {
      var warn = sheet.querySelector("#task-voice-warn");
      if (warn) warn.textContent = data.message;
      setVoiceListening(false);
      return;
    }
    if (!data.ok) {
      setVoiceListening(false);
      return;
    }
    if (data.command === "save") {
      sheet.querySelector("[data-voice-save]").click();
      return;
    }
    if (data.command === "retry") {
      sheet.querySelector("[data-voice-retry]").click();
      return;
    }
    if (data.command === "cancel") {
      sheet.querySelector("[data-voice-close]").click();
      return;
    }
    if (data.command === "time") {
      sheet.querySelector("#task-voice-time").value = data.time || "";
      refreshVoiceWhen(sheet);
      listenForCommand();
      return;
    }
    if (data.command === "date") {
      sheet.querySelector("#task-voice-date").value = data.date || "";
      refreshVoiceWhen(sheet);
      listenForCommand();
      return;
    }
    if (data.command === "content") {
      sheet.querySelector("#task-voice-text").value = data.text || "";
      listenForCommand();
    }
  };
  window.__taskVoiceHeard = function (raw) {
    var data = null;
    try { data = typeof raw === "string" ? JSON.parse(raw) : raw; } catch (e) { data = null; }
    if (!data || data.ok === false) {
      showVoiceMessage((data && data.message) || "聞き取れませんでした。もう一度話してください");
      return;
    }
    showVoiceConfirm(data);
  };
  function renderAhead() {
    var host = document.getElementById("task-ahead-host");
    if (!host) return;
    var data = typeof window.__taskGet === "function" ? window.__taskGet() : null;
    var choices = [0, 5, 10, 15, 30, 60];
    var lead = data && choices.indexOf(data.leadMinutes) >= 0 ? String(data.leadMinutes) : "10";
    var on = !data || data.nextTaskNotice !== false;
    var doneOnStop = !!(data && data.doneOnStop);
    var sig = lead + "|" + (on ? "1" : "0") + "|" + (doneOnStop ? "1" : "0");
    if (host.getAttribute("data-sig") === sig && host.querySelector("[data-ahead]")) return;
    host.setAttribute("data-sig", sig);
    var labels = { "0": "なし", "5": "5分前", "10": "10分前", "15": "15分前", "30": "30分前", "60": "1時間前" };
    var options = choices.map(function (n) {
      var value = String(n);
      return '<option value="' + value + '"' + (value === lead ? " selected" : "") + ">" + labels[value] + "</option>";
    }).join("");
    host.innerHTML =
      '<div class="ts-card" data-ahead="1">' +
      "<h2>次のタスク</h2>" +
      '<label><input type="checkbox" id="task-next-notice"' + (on ? " checked" : "") + "><span>次のタスクを通知に出す</span></label>" +
      "<p>オンにすると、今日の次の予定を通知に出し続けます。音は鳴らしません。最初はオンです。</p>" +
      '<label><input type="checkbox" id="task-done-on-stop"' + (doneOnStop ? " checked" : "") + "><span>止めたら済みにする</span></label>" +
      "<p>オンにすると、通知のボタン以外でアラームを止めたときも済みにします。スワイプで消したときも同じです。最初はオフです。</p>" +
      '<label>前もって知らせる<select id="task-lead-default" aria-label="前もって知らせる">' + options + "</select></label>" +
      "<p>時刻の前にも知らせます。メモごとに変えることもできます。最初は10分前です。</p>" +
      "</div>";
    var box = host.querySelector("#task-next-notice");
    var doneBox = host.querySelector("#task-done-on-stop");
    var select = host.querySelector("#task-lead-default");
    if (box) box.addEventListener("change", function () {
      if (window.__taskSetNextNotice) window.__taskSetNextNotice(box.checked);
    });
    if (doneBox) doneBox.addEventListener("change", function () {
      if (window.__taskSetDoneOnStop) window.__taskSetDoneOnStop(doneBox.checked);
    });
    if (select) select.addEventListener("change", function () {
      if (window.__taskSetLead) window.__taskSetLead(select.value);
    });
  }

  function renderVoiceSetting() {
    var host = document.getElementById("task-voice-host");
    if (!host || host.querySelector("[data-voice-setting]")) return;
    var on = false;
    try {
      if (window.TaskNative && TaskNative.getListenOnOpen) on = String(TaskNative.getListenOnOpen()) === "true";
    } catch (e) {}
    host.innerHTML =
      '<div class="ts-card" data-voice-setting="1">' +
      "<h2>声で予定を入れる</h2>" +
      '<label><input type="checkbox" id="task-listen-open"' + (on ? " checked" : "") + "><span>開いたらすぐ声で入れる</span></label>" +
      "<p>オンにすると、アプリを開いたときにすぐ話しかけられます。最初はオフです。</p>" +
      "</div>";
    var box = host.querySelector("#task-listen-open");
    if (!box) return;
    box.addEventListener("change", function () {
      try {
        if (window.TaskNative && TaskNative.setListenOnOpen) TaskNative.setListenOnOpen(box.checked ? "true" : "false");
      } catch (e) {}
    });
  }

  document.addEventListener("click", function (event) {
    var target = event.target;
    if (!target || !target.closest) return;
    var open = target.closest("[data-cal-open]");
    if (open) {
      openChore(open.getAttribute("data-cal-open"), open.getAttribute("data-cal-id"), open.getAttribute("data-cal-tab"));
      return;
    }
    var nav = target.closest("[data-cal-nav]");
    if (nav && document.getElementById("task-cal-host")) {
      var parts = cursor.split("-").map(Number);
      var next = new Date(Date.UTC(parts[0], parts[1] - 1 + Number(nav.getAttribute("data-cal-nav")), 1));
      cursor = next.getUTCFullYear() + "-" + pad(next.getUTCMonth() + 1);
      dirty = true;
      renderCalendar();
      return;
    }
    var day = target.closest("[data-cal-day]");
    if (day && day.getAttribute("data-cal-day")) {
      selected = day.getAttribute("data-cal-day");
      try { if (window.__taskSetMemoDate) window.__taskSetMemoDate(selected); } catch (e) {}
      dirty = true;
      renderCalendar();
    }
  });

  window.__taskOnData = function () {
    dirty = true;
    renderCalendar();
    renderSummary();
    renderRings();
    renderVoiceSetting();
    renderAhead();
  };

  window.__taskAskRestore = function (when, onYes) {
    var old = document.getElementById("task-restore-ask");
    if (old) old.remove();
    var host = document.createElement("div");
    host.id = "task-restore-ask";
    host.innerHTML =
      '<div class="za-ask-card" role="dialog" aria-modal="true">' +
      "<p>" + String(when || "日時不明") + " に保存したバックアップです。</p>" +
      "<p><b>今のデータをこのバックアップで置きかえます。よろしいですか？</b></p>" +
      '<div class="za-ask-actions"><button type="button" data-ask="no">やめる</button><button type="button" data-ask="yes">戻す</button></div></div>';
    document.body.appendChild(host);
    host.addEventListener("click", function (event) {
      var button = event.target.closest && event.target.closest("button");
      if (!button) return;
      var yes = button.getAttribute("data-ask") === "yes";
      host.remove();
      if (yes && onYes) onYes();
    });
  };

  var appRoot = document.getElementById("root");
  if (appRoot) {
    new MutationObserver(function () {
      var root = document.getElementById("task-cal-host");
      if (root && (dirty || !root.querySelector("[data-calview]"))) renderCalendar();
      renderSummary();
      var ringHost = document.getElementById("task-ring-host");
      if (ringHost && !ringHost.querySelector("[data-rings]")) renderRings();
      renderVoiceSetting();
      renderAhead();
    }).observe(appRoot, { childList: true, subtree: true });
  }

  function keepFieldAboveKeyboard() {
    var el = document.activeElement;
    if (!el || !el.getBoundingClientRect) return;
    var tag = (el.tagName || "").toLowerCase();
    if (tag !== "input" && tag !== "textarea") return;
    var type = (el.getAttribute("type") || "text").toLowerCase();
    if (type === "checkbox" || type === "radio" || type === "button" || type === "range" || type === "file") return;
    var view = window.visualViewport;
    var top = view ? view.offsetTop : 0;
    var bottom = view ? view.offsetTop + view.height : window.innerHeight;
    var rect = el.getBoundingClientRect();
    if (rect.bottom > bottom - 12 || rect.top < top + 8) {
      try { el.scrollIntoView({ block: "center", inline: "nearest" }); }
      catch (e) { el.scrollIntoView(false); }
    }
  }
  if (window.visualViewport) {
    window.visualViewport.addEventListener("resize", keepFieldAboveKeyboard);
    window.visualViewport.addEventListener("scroll", keepFieldAboveKeyboard);
  }
  document.addEventListener("focusin", function () {
    setTimeout(keepFieldAboveKeyboard, 60);
    setTimeout(keepFieldAboveKeyboard, 320);
  });
})();
