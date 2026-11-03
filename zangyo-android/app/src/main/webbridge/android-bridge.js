(function () {
  var DEFAULTS = {
    early: [
      { label: "目覚まし", time: "02:00" },
      { label: "出勤", time: "04:20" },
      { label: "移動", time: "05:55" },
      { label: "休憩", time: "11:18" }
    ],
    day: [
      { label: "目覚まし", time: "02:15" },
      { label: "出勤", time: "04:35" },
      { label: "移動", time: "06:07" },
      { label: "休憩", time: "11:18" }
    ],
    night: [
      { label: "目覚まし", time: "14:30" },
      { label: "移動", time: "16:47" },
      { label: "休憩", time: "21:59" }
    ],
    off: [],
    leave: []
  };
  var KINDS = [
    ["early", "早出の昼勤"],
    ["day", "通常の昼勤"],
    ["night", "夜勤"],
    ["off", "休み"],
    ["leave", "年休"]
  ];
  var TABS = [
    ["shift", "勤務", "cal"],
    ["year", "年間", "list"],
    ["leave", "年休", "leaf"],
    ["alarms", "アラーム", "bell"],
    ["settings", "控え", "note"]
  ];

  var current = "shift";
  var alarmDraft = null;
  var schedule = null;
  var perm = null;

  var style = document.createElement("style");
  style.textContent = [
    "#root nav{display:none!important}",
    "#root .pb-24{padding-bottom:calc(76px + env(safe-area-inset-bottom))!important}",
    "#za-nav{position:fixed;z-index:40;left:0;right:0;bottom:0;display:grid;grid-template-columns:repeat(5,1fr);background:#fffdf8;border-top:1px solid #e4ddd0;padding:2px 0 env(safe-area-inset-bottom)}",
    "#za-nav button{appearance:none;border:0;background:transparent;color:#6f675c;font-size:10px;line-height:1.15;padding:6px 0 8px;display:flex;flex-direction:column;align-items:center;gap:2px;min-width:0}",
    "#za-nav button span{white-space:nowrap}",
    "#za-nav button.on{color:#1f5c45;font-weight:700}",
    "#za-nav svg{width:20px;height:20px;display:block}",
    "#za-panel{display:none;position:fixed;z-index:20;left:0;right:0;top:0;bottom:58px;overflow:auto;background:#f3efe6;color:#1c1917;padding:16px 16px 28px;-webkit-overflow-scrolling:touch}",
    "#za-panel.open{display:block}",
    "#za-panel h1{font-size:22px;line-height:1.3;color:#1f5c45;margin:0 0 8px}",
    "#za-panel h2{font-size:16px;margin:0 0 8px}",
    ".za-lead,.za-muted{color:#6f675c;font-size:14px;line-height:1.55;margin:0 0 12px}",
    ".za-card{background:#fffdf8;border:1px solid #e4ddd0;border-radius:12px;padding:14px;margin:0 0 12px}",
    ".za-row{display:flex;gap:8px;align-items:center;margin:8px 0}",
    ".za-input{box-sizing:border-box;border:1px solid #e4ddd0;background:#fff;border-radius:8px;padding:10px 12px;font-size:16px;color:#1c1917}",
    ".za-grow{flex:1;min-width:0;width:100%}",
    ".za-time{width:118px;flex:0 0 118px}",
    ".za-btn{display:inline-flex;align-items:center;justify-content:center;min-height:44px;padding:8px 14px;border-radius:8px;border:1px solid #e4ddd0;background:#fffdf8;color:#1c1917;font-size:15px}",
    ".za-btn.primary{background:#1f5c45;color:#fffdf8;border-color:#1f5c45}",
    ".za-btn.block{width:100%;margin-top:8px}",
    ".za-del{border:0;background:transparent;color:#b42318;font-size:14px;padding:8px 4px;min-width:44px}",
    ".za-actions{display:flex;flex-wrap:wrap;gap:8px;margin-top:8px}",
    ".za-next{list-style:none;margin:0;padding:0}",
    ".za-next li{display:flex;flex-wrap:wrap;gap:6px 10px;align-items:baseline;padding:8px 0;border-top:1px solid #e4ddd0;font-size:16px}",
    ".za-next li:first-child{border-top:0}",
    ".za-next b{font-variant-numeric:tabular-nums}",
    ".za-next span{color:#6f675c;font-size:13px;flex:1}",
    ".za-next li.za-skipped,.za-next li.za-skipped b,.za-next li.za-skipped span{color:#9a948a}",
    ".za-skip{border:0;background:transparent;color:#1f5c45;font-size:14px;padding:8px 4px;min-width:44px;font-weight:700}",
    ".za-warn{background:#fde8e6;color:#b42318;border-radius:10px;padding:10px 12px;font-size:14px;line-height:1.5;margin:0 0 12px}",
    ".za-ok{background:#e7f1eb;color:#1f5c45;border-radius:10px;padding:10px 12px;font-size:14px;margin:0 0 12px}",
    ".za-msg{min-height:1.2em;color:#b42318;font-size:14px;margin:8px 0 0}",
    "label.za-field{display:block;margin:10px 0;font-size:14px;color:#6f675c}",
    "label.za-field .za-input{margin-top:4px}",
    ".za-on{display:flex;align-items:center;gap:4px;font-size:13px;color:#6f675c;min-width:52px}",
    ".za-on input{width:18px;height:18px}",
    ".za-overdot{position:absolute;right:3px;top:3px;width:7px;height:7px;border-radius:50%;background:#1f5c45}",
    "#za-day-alarm{margin-top:14px}",
    "#za-day-alarm h3{margin:0 0 8px;font-size:14px}"
  ].join("");
  document.head.appendChild(style);

  var nav = document.createElement("nav");
  nav.id = "za-nav";
  var panel = document.createElement("div");
  panel.id = "za-panel";
  document.body.appendChild(panel);
  document.body.appendChild(nav);

  function icon(name) {
    var paths = {
      cal: '<rect x="4" y="5" width="16" height="15" rx="2"/><path d="M8 3v4M16 3v4M4 10h16"/>',
      list: '<path d="M8 7h11M8 12h11M8 17h11"/><path d="M5 7h.01M5 12h.01M5 17h.01"/>',
      leaf: '<path d="M5 19c8 0 14-8 14-14-6 0-14 6-14 14z"/><path d="M8 16c2-3 5-6 9-8"/>',
      note: '<path d="M7 3h7l5 5v13a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z"/><path d="M14 3v6h6"/>',
      bell: '<path d="M6 16V11a6 6 0 1 1 12 0v5l1.5 2h-15z"/><path d="M10 19a2 2 0 0 0 4 0"/>'
    };
    return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">' + (paths[name] || "") + "</svg>";
  }

  TABS.forEach(function (tab) {
    var button = document.createElement("button");
    button.type = "button";
    button.dataset.za = tab[0];
    button.innerHTML = icon(tab[2]) + "<span>" + tab[1] + "</span>";
    button.addEventListener("click", function () {
      if (tab[0] === "alarms") openExtra(tab[0]);
      else openReact(tab[0]);
    });
    nav.appendChild(button);
  });

  function setActive(id) {
    current = id;
    Array.prototype.forEach.call(nav.children, function (button) {
      button.classList.toggle("on", button.dataset.za === id);
    });
  }

  function openReact(id) {
    panel.classList.remove("open");
    var button = document.querySelector('#root button[data-tab="' + id + '"]');
    if (button) button.click();
    setActive(id);
    window.scrollTo(0, 0);
  }

  function openExtra(id) {
    panel.classList.add("open");
    setActive(id);
    renderAlarms();
    panel.scrollTop = 0;
  }

  window.__zangyoBack = function () {
    if (panel.classList.contains("open")) {
      openReact("shift");
      return true;
    }
    return false;
  };

  function esc(value) {
    return String(value == null ? "" : value).replace(/[&<>"']/g, function (ch) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[ch];
    });
  }

  function permHtml() {
    if (!perm) return "";
    var missing = [];
    if (!perm.notifications) missing.push("通知");
    if (!perm.fullScreen) missing.push("ロック画面");
    if (!perm.exact) missing.push("時刻どおり");
    if (!perm.battery) missing.push("バッテリー");
    if (!missing.length) return '<p class="za-ok">スマホの許可はそろっています。</p>';
    return '<p class="za-warn">まだオフです: ' + esc(missing.join("、")) + "。下の「スマホの許可を確認」を押してください。</p>";
  }

  function nextHtml() {
    if (!schedule) return '<p class="za-muted">読み込み中です…</p>';
    var items = schedule.items || [];
    if (!items.length) return '<p class="za-muted">この先、鳴る予定はありません。休日と年休の日は鳴りません。</p>';
    var rows = items.map(function (item) {
      var skipped = !!item.skipped;
      var button = item.source === "snooze" ? "" : (
        skipped
          ? '<button type="button" class="za-skip" data-act="restore-alarm" data-id="' + esc(item.id) + '">戻す</button>'
          : '<button type="button" class="za-skip" data-act="skip-alarm" data-id="' + esc(item.id) + '">解除</button>'
      );
      return '<li class="' + (skipped ? "za-skipped" : "") + '"><b>' + esc(item.time) + "</b> " + esc(item.title) + "<span>" + esc(item.detail) + "</span>" + button + "</li>";
    }).join("");
    var days = schedule.horizonDays || 21;
    return '<ul class="za-next">' + rows + "</ul>" +
      '<p class="za-muted">勤務のアラームは今から' + days + "日分です。オフの時刻は鳴りません。解除した予定は灰色のまま残ります。「戻す」で再び鳴ります。その日の勤務の種類を変えると、その日の解除は消えます。この日だけのアラームを入れた日は、その時刻が優先されます。</p>";
  }

  function refreshBits() {
    var permNode = document.getElementById("za-perm");
    if (permNode) permNode.innerHTML = permHtml();
    var nextNode = document.getElementById("za-next");
    if (nextNode) nextNode.innerHTML = nextHtml();
  }

  window.__zangyoShowSchedule = function (value) {
    try { schedule = typeof value === "string" ? JSON.parse(value) : value; } catch (e) { return; }
    refreshBits();
  };
  window.__zangyoPermStatus = function (value) {
    try { perm = typeof value === "string" ? JSON.parse(value) : value; } catch (e) { return; }
    refreshBits();
  };

  function copyDefaults(key) {
    return (DEFAULTS[key] || []).map(function (row) { return { label: row.label, time: row.time, enabled: true }; });
  }

  function blankAlarms() {
    var alarms = {};
    KINDS.forEach(function (kind) { alarms[kind[0]] = copyDefaults(kind[0]); });
    return alarms;
  }

  function rowFrom(row) {
    var time = row && /^\d{2}:\d{2}$/.test(row.time) ? row.time : "";
    return {
      label: row && row.label != null ? String(row.label) : "",
      time: time,
      enabled: !(row && row.enabled === false)
    };
  }

  function normalizeAlarms(raw) {
    var alarms = raw ? JSON.parse(JSON.stringify(raw)) : blankAlarms();
    KINDS.forEach(function (kind) {
      var key = kind[0];
      if (!Array.isArray(alarms[key])) alarms[key] = copyDefaults(key);
      else alarms[key] = alarms[key].map(rowFrom);
    });
    return alarms;
  }

  function loadAlarms() {
    var data = typeof window.__zangyoGet === "function" ? window.__zangyoGet() : null;
    alarmDraft = normalizeAlarms(data && data.alarms);
  }

  function commitAlarms(alarms) {
    alarmDraft = alarms;
    if (typeof window.__zangyoSet === "function") {
      var copy = JSON.parse(JSON.stringify(alarms));
      window.__zangyoSet(function (state) { return Object.assign({}, state, { alarms: copy }); });
    }
    fillForm();
  }

  function fillForm() {
    var host = document.getElementById("za-form");
    if (!host || !alarmDraft) return;
    host.innerHTML = KINDS.map(function (kind) {
      var key = kind[0];
      var rows = alarmDraft[key].map(function (row, index) {
        return '<div class="za-row">' +
          '<label class="za-on"><input type="checkbox" data-kind="' + key + '" data-i="' + index + '" data-field="enabled"' + (row.enabled === false ? "" : " checked") + ">オン</label>" +
          '<input class="za-input za-grow" data-kind="' + key + '" data-i="' + index + '" data-field="label" value="' + esc(row.label) + '" placeholder="名前（なくても可）" aria-label="' + esc(kind[1]) + 'の名前">' +
          '<input class="za-input za-time" type="time" step="60" data-kind="' + key + '" data-i="' + index + '" data-field="time" value="' + esc(row.time) + '" aria-label="' + esc(kind[1]) + 'の時刻">' +
          '<button type="button" class="za-del" data-act="del" data-kind="' + key + '" data-i="' + index + '">削除</button>' +
          "</div>";
      }).join("");
      return '<section class="za-card"><h2>' + esc(kind[1]) + "</h2>" + rows +
        '<div class="za-actions">' +
        '<button type="button" class="za-btn" data-act="add" data-kind="' + key + '">行を追加</button>' +
        '<button type="button" class="za-btn" data-act="reset" data-kind="' + key + '">初期の時刻に戻す</button>' +
        "</div></section>";
    }).join("");
  }

  function renderAlarms() {
    if (!alarmDraft) loadAlarms();
    panel.innerHTML =
      "<h1>アラーム</h1>" +
      '<p class="za-lead">勤務の種類ごとに、鳴る時刻をいくつでも入れられます。名前は空でも大丈夫です。オフにした時刻は鳴りません。休みと年休は、最初は空です。ロック中でも画面いっぱいに出て、5分後にもう一度鳴らせます。</p>' +
      '<div id="za-perm"></div>' +
      '<section class="za-card"><h2>次に鳴る</h2><div id="za-next"></div></section>' +
      "<h2>アラームの設定</h2>" +
      '<div id="za-form"></div>' +
      '<button type="button" class="za-btn block" data-act="perm">スマホの許可を確認</button>';
    fillForm();
    refreshBits();
  }

  panel.addEventListener("change", function (event) {
    var target = event.target;
    if (!target.dataset || !target.dataset.kind || target.dataset.field == null) return;
    var alarms = alarmDraft || blankAlarms();
    var row = alarms[target.dataset.kind] && alarms[target.dataset.kind][Number(target.dataset.i)];
    if (!row) return;
    if (target.dataset.field === "enabled") row.enabled = !!target.checked;
    else row[target.dataset.field] = target.value;
    commitAlarms(alarms);
  });

  panel.addEventListener("click", function (event) {
    var target = event.target.closest("[data-act]");
    if (!target) return;
    var act = target.dataset.act;
    if (act === "perm") {
      if (window.ZangyoNative && ZangyoNative.openPermissions) ZangyoNative.openPermissions();
      return;
    }
    if (act === "skip-alarm" || act === "restore-alarm") {
      if (window.ZangyoNative && ZangyoNative.setAlarmSkipped) {
        ZangyoNative.setAlarmSkipped(target.dataset.id, act === "skip-alarm");
      }
      return;
    }
    if (act === "add" || act === "del" || act === "reset") {
      var alarms = alarmDraft ? JSON.parse(JSON.stringify(alarmDraft)) : blankAlarms();
      var key = target.dataset.kind;
      if (!Array.isArray(alarms[key])) alarms[key] = [];
      if (act === "add") alarms[key].push({ label: "", time: "00:00", enabled: true });
      if (act === "del") alarms[key].splice(Number(target.dataset.i), 1);
      if (act === "reset") alarms[key] = copyDefaults(key);
      commitAlarms(alarms);
    }
  });

  function stateNow() {
    return typeof window.__zangyoGet === "function" ? window.__zangyoGet() : null;
  }

  function writeDayOverride(iso, value) {
    if (typeof window.__zangyoSet !== "function") return;
    window.__zangyoSet(function (state) {
      var dayAlarms = Object.assign({}, state.dayAlarms || {});
      if (!value) delete dayAlarms[iso];
      else dayAlarms[iso] = value;
      return Object.assign({}, state, { dayAlarms: dayAlarms });
    });
  }

  function renderDayAlarm() {
    var host = document.getElementById("za-day-alarm");
    if (!host) return;
    var iso = host.getAttribute("data-day");
    if (!iso) return;
    var data = stateNow() || {};
    var current = data.dayAlarms && data.dayAlarms[iso];
    var body = "";
    if (current && current.mute) {
      body = '<p class="za-muted">この日は鳴りません。</p>' +
        '<button type="button" class="za-btn" data-day-act="clear" data-day="' + esc(iso) + '">いつもの時刻に戻す</button>';
    } else if (current && Array.isArray(current.times) && current.times.length) {
      body = current.times.map(function (row, index) {
        return '<div class="za-row">' +
          '<label class="za-on"><input type="checkbox" data-day-field="enabled" data-day="' + esc(iso) + '" data-i="' + index + '"' + (row.enabled === false ? "" : " checked") + ">オン</label>" +
          '<input class="za-input za-grow" data-day-field="label" data-day="' + esc(iso) + '" data-i="' + index + '" value="' + esc(row.label || "") + '" placeholder="名前（なくても可）">' +
          '<input class="za-input za-time" type="time" step="60" data-day-field="time" data-day="' + esc(iso) + '" data-i="' + index + '" value="' + esc(row.time || "") + '">' +
          '<button type="button" class="za-del" data-day-act="del" data-day="' + esc(iso) + '" data-i="' + index + '">削除</button>' +
          "</div>";
      }).join("") +
        '<div class="za-actions">' +
        '<button type="button" class="za-btn" data-day-act="add" data-day="' + esc(iso) + '">時刻を追加</button>' +
        '<button type="button" class="za-btn" data-day-act="clear" data-day="' + esc(iso) + '">いつもの時刻に戻す</button>' +
        "</div>";
    } else {
      body = '<p class="za-muted">この日は、勤務の種類の時刻が鳴ります。</p>' +
        '<div class="za-actions">' +
        '<button type="button" class="za-btn" data-day-act="add" data-day="' + esc(iso) + '">この日だけのアラーム</button>' +
        '<button type="button" class="za-btn" data-day-act="mute" data-day="' + esc(iso) + '">この日は鳴らさない</button>' +
        "</div>";
    }
    var html = '<section class="za-card"><h3>この日だけのアラーム</h3>' + body + "</section>";
    if (host.getAttribute("data-sig") === iso + html) return;
    host.setAttribute("data-sig", iso + html);
    host.innerHTML = html;
  }

  function markCalendar() {
    var data = stateNow() || {};
    var overrides = data.dayAlarms || {};
    Array.prototype.forEach.call(document.querySelectorAll("[data-cal]"), function (button) {
      var iso = button.getAttribute("data-cal");
      var on = !!(overrides[iso] && (overrides[iso].mute || (overrides[iso].times && overrides[iso].times.length)));
      var dot = button.querySelector(".za-overdot");
      if (on && !dot) {
        dot = document.createElement("span");
        dot.className = "za-overdot";
        dot.setAttribute("data-daymark", iso);
        button.appendChild(dot);
      } else if (!on && dot) {
        dot.remove();
      }
    });
  }

  function timesOf(iso) {
    var data = stateNow() || {};
    var current = data.dayAlarms && data.dayAlarms[iso];
    var times = current && Array.isArray(current.times) ? current.times.map(rowFrom) : [];
    return times;
  }

  document.addEventListener("click", function (event) {
    var target = event.target.closest && event.target.closest("[data-day-act]");
    if (!target) return;
    var iso = target.getAttribute("data-day");
    var act = target.getAttribute("data-day-act");
    if (act === "clear") writeDayOverride(iso, null);
    if (act === "mute") writeDayOverride(iso, { mute: true, times: [] });
    if (act === "add") {
      var times = timesOf(iso);
      times.push({ label: "", time: "07:00", enabled: true });
      writeDayOverride(iso, { mute: false, times: times });
    }
    if (act === "del") {
      var next = timesOf(iso);
      next.splice(Number(target.getAttribute("data-i")), 1);
      writeDayOverride(iso, next.length ? { mute: false, times: next } : null);
    }
    setTimeout(function () { renderDayAlarm(); markCalendar(); }, 0);
  }, true);

  document.addEventListener("change", function (event) {
    var target = event.target;
    if (!target || !target.getAttribute || !target.getAttribute("data-day-field")) return;
    var iso = target.getAttribute("data-day");
    var times = timesOf(iso);
    var row = times[Number(target.getAttribute("data-i"))];
    if (!row) return;
    if (target.getAttribute("data-day-field") === "enabled") row.enabled = !!target.checked;
    else row[target.getAttribute("data-day-field")] = target.value;
    writeDayOverride(iso, { mute: false, times: times });
    markCalendar();
  });

  var appRoot = document.getElementById("root");
  if (appRoot) {
    new MutationObserver(function () {
      markCalendar();
      renderDayAlarm();
    }).observe(appRoot, { childList: true, subtree: true });
  }

  setActive("shift");
})();
