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
  var weekdayDraft = [];
  var schedule = null;
  var perm = null;
  var noticeOn = true;

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
    ".za-notice{display:flex;align-items:flex-start;gap:8px;font-size:15px;line-height:1.4;margin:0 0 8px;color:#1c1917}",
    ".za-notice input{width:18px;height:18px;margin-top:2px}",
    ".za-on{display:flex;align-items:center;gap:4px;font-size:13px;color:#6f675c;min-width:52px}",
    ".za-on input{width:18px;height:18px}",
    ".za-overdot{position:absolute;right:3px;top:3px;width:7px;height:7px;border-radius:50%;background:#1f5c45}",
    ".za-chips{display:flex;flex-wrap:wrap;gap:6px;margin:6px 0}",
    ".za-chip{min-width:40px;min-height:36px;border-radius:999px;border:1px solid #e4ddd0;background:#fff;color:#1c1917;font-size:14px}",
    ".za-chip.on{background:#1f5c45;color:#fffdf8;border-color:#1f5c45;font-weight:700}",
    "#za-day-alarm{margin-top:14px}",
    "#za-day-alarm h3{margin:0 0 8px;font-size:14px}",
    "#za-year-count .za-counts{display:flex;flex-wrap:wrap;gap:8px;justify-content:center}",
    "#za-year-count .za-ycard{box-sizing:border-box;width:calc((100% - 16px) / 3);aspect-ratio:1;border-radius:22px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:1px;padding:8px 4px;text-align:center}",
    "#za-year-count .za-ico{width:22px;height:22px;display:block}",
    "#za-year-count .za-ico svg{width:22px;height:22px;display:block}",
    "#za-year-count .za-label{font-size:12px;font-weight:700;line-height:1.2}",
    "#za-year-count .za-num{font-size:28px;font-weight:800;line-height:1;font-variant-numeric:tabular-nums}",
    "#za-year-count .za-num span{font-size:12px;font-weight:700;margin-left:1px}",
    "#za-year-count .za-day{background:#fbe7b0;color:#1c1917}",
    "#za-year-count .za-night{background:#b8bcc4;color:#1c1917}",
    "#za-year-count .za-work{background:#1f5c45;color:#fffdf8}",
    "#za-year-count .za-off{background:#fff;color:#b42318;border:1px solid #e4ddd0}",
    "#za-year-count .za-leave{background:#c5e88a;color:#4d7c0f}",
    ".za-ehours{display:grid;grid-template-columns:repeat(7,minmax(0,1fr));gap:4px;margin:6px 0}",
    ".za-eh{display:flex;flex-direction:column;align-items:center;gap:2px;font-size:12px;color:#6f675c}",
    ".za-eh input{width:100%;box-sizing:border-box;font-size:14px;padding:6px 2px;text-align:center;border:1px solid #e4ddd0;border-radius:8px;background:#fff;color:#1c1917}",
    "#za-early-hours{margin-top:10px}",
    "#za-early-hours .za-ehday{background:#fffdf8;border:1px solid #e4ddd0;border-radius:12px;padding:12px}",
    "#za-early-hours label{display:flex;align-items:center;gap:8px;font-size:15px;color:#1c1917}",
    "#za-early-hours input{width:96px;font-size:16px;padding:8px;border:1px solid #e4ddd0;border-radius:8px;background:#fff;color:#1c1917}",
    "#za-early-hours p{margin:8px 0 0;color:#6f675c;font-size:13px;line-height:1.4}",
    "#za-early-hours button{margin-top:8px;border:0;background:transparent;color:#1f5c45;font-size:14px;font-weight:700;padding:4px 0}"
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

  function yearIcon(name) {
    var paths = {
      sun: '<circle cx="12" cy="12" r="3.2"/><path d="M12 3.4v2.2M12 18.4v2.2M3.4 12h2.2M18.4 12h2.2M6 6l1.6 1.6M16.4 16.4 18 18M18 6l-1.6 1.6M7.6 16.4 6 18"/>',
      moon: '<path d="M15.6 3.6A7.4 7.4 0 1 0 20.4 14 5.7 5.7 0 0 1 15.6 3.6z"/>',
      brief: '<rect x="3.5" y="7.5" width="17" height="12" rx="2"/><path d="M9 7.5V5.8A1.8 1.8 0 0 1 10.8 4h2.4A1.8 1.8 0 0 1 15 5.8v1.7M3.5 12.5h17"/>',
      house: '<path d="M4 11.2 12 4.2l8 7"/><path d="M6.5 10.2V20h11V10.2"/><path d="M10 20v-4.5h4V20"/>',
      flower: '<g fill="currentColor" stroke="none"><circle cx="12" cy="6.6" r="2.15"/><circle cx="16.7" cy="10" r="2.15"/><circle cx="14.9" cy="15.4" r="2.15"/><circle cx="9.1" cy="15.4" r="2.15"/><circle cx="7.3" cy="10" r="2.15"/><circle cx="12" cy="11.2" r="1.55"/></g>'
    };
    return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + (paths[name] || "") + "</svg>";
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

  function readNotice() {
    try {
      if (window.ZangyoNative && ZangyoNative.getNextAlarmNotice) {
        noticeOn = String(ZangyoNative.getNextAlarmNotice()) === "true";
      }
    } catch (e) {}
  }
  function noticeHtml() {
    readNotice();
    var denied = noticeOn && perm && perm.notifications === false
      ? '<p class="za-warn">通知が許可されていないので、次のアラームは表示できません。設定から通知をオンにしてください。</p>'
      : "";
    return '<label class="za-notice"><input type="checkbox" data-act="next-notice"' + (noticeOn ? " checked" : "") + ">次のアラームを通知に出す</label>" +
      '<p class="za-muted">オンにすると、次に鳴る時刻を通知に出し続けます。最初はオンです。</p>' +
      denied;
  }
  function nextHtml() {
    var head = noticeHtml();
    if (!schedule) return head + '<p class="za-muted">読み込み中です…</p>';
    var items = schedule.items || [];
    if (!items.length) return head + '<p class="za-muted">この先、鳴る予定はありません。休日と年休の日は鳴りません。</p>';
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
    return head + '<ul class="za-next">' + rows + "</ul>" +
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
    weekdayDraft = Array.isArray(data && data.earlyWeekdays)
      ? data.earlyWeekdays.map(Number).filter(function (n) { return n >= 1 && n <= 7; })
      : [];
  }

  function commitWeekdays(days) {
    weekdayDraft = days.slice().sort(function (a, b) { return a - b; });
    if (typeof window.__zangyoSet === "function") {
      var copy = weekdayDraft.slice();
      window.__zangyoSet(function (state) { return Object.assign({}, state, { earlyWeekdays: copy }); });
    }
    fillForm();
  }

  function weekdayHtml() {
    var names = ["月", "火", "水", "木", "金", "土", "日"];
    var chips = names.map(function (name, index) {
      var n = index + 1;
      var on = weekdayDraft.indexOf(n) >= 0;
      return '<button type="button" class="za-chip' + (on ? " on" : "") + '" data-act="weekday" data-dow="' + n + '" aria-pressed="' + (on ? "true" : "false") + '">' + name + "</button>";
    }).join("");
    return '<p class="za-muted">早出にする曜日</p><div class="za-chips">' + chips + "</div>" +
      '<p class="za-muted">選んだ曜日の昼勤は、自動で早出の時刻になります。その日だけやめるときは、勤務の日を開いて早出をオフにしてください。</p>' +
      weekdayHoursHtml();
  }

  function formatHours(value) {
    var rounded = Math.round(Number(value) * 100) / 100;
    if (!isFinite(rounded)) return "0";
    return String(rounded);
  }
  function parseHours(raw) {
    var text = String(raw == null ? "" : raw).trim().replace(/[０-９]/g, function (ch) {
      return String.fromCharCode(ch.charCodeAt(0) - 0xFEE0);
    }).replace(/．/g, ".").replace(/。/g, ".");
    if (!text) return null;
    var number = Number(text);
    if (!isFinite(number) || number < 0) return null;
    return Math.min(24, Math.round(number * 100) / 100);
  }
  function weekdayHoursHtml() {
    var data = stateNow() || {};
    var map = data.earlyHoursWeekday || {};
    var names = ["月", "火", "水", "木", "金", "土", "日"];
    var inputs = names.map(function (name, index) {
      var dow = index + 1;
      var value = map[dow] != null ? map[dow] : map[String(dow)];
      var shown = value != null && Number(value) > 0 ? formatHours(value) : "";
      return '<label class="za-eh"><span>' + name + '</span><input type="number" min="0" max="24" step="0.25" inputmode="decimal" data-early-hours="' + dow + '" value="' + esc(shown) + '" aria-label="' + name + 'の早出時間" placeholder="0"></label>';
    }).join("");
    return '<p class="za-muted">曜日ごとの早出時間（時間）</p><div class="za-ehours">' + inputs + "</div>" +
      '<p class="za-muted">空欄は 0 時間です。早出の日には、その曜日の時間が入ります。その日だけ変えるときは、勤務の日を開いて直してください。</p>';
  }
  function commitWeekdayHours(dow, raw) {
    var hours = parseHours(raw);
    if (typeof window.__zangyoSet !== "function") return;
    window.__zangyoSet(function (state) {
      var map = Object.assign({}, state.earlyHoursWeekday || {});
      if (hours == null || hours === 0) delete map[String(dow)];
      else map[String(dow)] = hours;
      return Object.assign({}, state, { earlyHoursWeekday: map });
    });
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
      return '<section class="za-card"><h2>' + esc(kind[1]) + "</h2>" + (key === "early" ? weekdayHtml() : "") + rows +
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
    if (target.dataset && target.dataset.earlyHours) {
      commitWeekdayHours(Number(target.dataset.earlyHours), target.value);
      return;
    }
    if (target.dataset && target.dataset.act === "next-notice") {
      noticeOn = !!target.checked;
      try {
        if (window.ZangyoNative && ZangyoNative.setNextAlarmNotice) {
          ZangyoNative.setNextAlarmNotice(noticeOn ? "true" : "false");
        }
      } catch (e) {}
      refreshBits();
      return;
    }
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
    if (act === "weekday") {
      var dow = Number(target.dataset.dow);
      var next = weekdayDraft.indexOf(dow) >= 0
        ? weekdayDraft.filter(function (n) { return n !== dow; })
        : weekdayDraft.concat(dow);
      commitWeekdays(next);
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

  function realDate(iso) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(iso)) return false;
    var parts = iso.split("-").map(Number);
    var date = new Date(Date.UTC(parts[0], parts[1] - 1, parts[2]));
    return date.getUTCFullYear() === parts[0] && date.getUTCMonth() === parts[1] - 1 && date.getUTCDate() === parts[2];
  }
  function weekdayOf(iso) {
    var parts = iso.split("-").map(Number);
    var dow = new Date(Date.UTC(parts[0], parts[1] - 1, parts[2])).getUTCDay();
    return dow === 0 ? 7 : dow;
  }
  function hasDate(list, iso) {
    return Array.isArray(list) && list.indexOf(iso) >= 0;
  }
  function isEarlyDay(data, iso) {
    var shifts = data && data.shifts || {};
    if (shifts[iso] !== "day") return false;
    if (hasDate(data.earlySkip, iso)) return false;
    if (hasDate(data.early, iso)) return true;
    if (!realDate(iso)) return false;
    var weekdays = data.earlyWeekdays || [];
    var dow = weekdayOf(iso);
    return weekdays.indexOf(dow) >= 0 || weekdays.indexOf(String(dow)) >= 0;
  }
  function weekdayHours(data, dow) {
    var map = data && data.earlyHoursWeekday || {};
    var value = map[dow] != null ? map[dow] : map[String(dow)];
    var number = Number(value);
    if (!isFinite(number) || number <= 0) return 0;
    return Math.min(24, Math.round(number * 100) / 100);
  }
  function dayEarlyHours(data, iso) {
    if (!isEarlyDay(data, iso)) return 0;
    var map = data && data.earlyHoursDay || {};
    if (Object.prototype.hasOwnProperty.call(map, iso)) {
      var number = Number(map[iso]);
      if (isFinite(number) && number >= 0) return Math.min(24, Math.round(number * 100) / 100);
    }
    return weekdayHours(data, weekdayOf(iso));
  }
  function countYear(data, year) {
    var counts = { day: 0, night: 0, off: 0, leave: 0 };
    var prefix = String(year) + "-";
    var shifts = data && data.shifts || {};
    Object.keys(shifts).forEach(function (iso) {
      if (iso.indexOf(prefix) !== 0 || !realDate(iso)) return;
      if (shifts[iso] === "day") counts.day += 1;
      else if (shifts[iso] === "night") counts.night += 1;
      else if (shifts[iso] === "off") counts.off += 1;
    });
    counts.attended = counts.day + counts.night;
    var seen = {};
    var leave = data && Array.isArray(data.leave) ? data.leave : [];
    leave.forEach(function (iso) {
      if (typeof iso !== "string" || seen[iso] || iso.indexOf(prefix) !== 0 || !realDate(iso)) return;
      seen[iso] = true;
      counts.leave += 1;
    });
    return counts;
  }
  function displayedYear(host) {
    var select = host.parentElement && host.parentElement.querySelector("select");
    if (select && /^\d{4}$/.test(String(select.value))) return Number(select.value);
    return null;
  }
  function renderYearCount() {
    var host = document.getElementById("za-year-count");
    if (!host) return;
    var year = displayedYear(host);
    if (year == null) return;
    var counts = countYear(stateNow(), year);
    var cards = [
      ["day", "昼勤", "sun", counts.day],
      ["night", "夜勤", "moon", counts.night],
      ["work", "出勤", "brief", counts.attended],
      ["off", "休日", "house", counts.off],
      ["leave", "年休", "flower", counts.leave]
    ];
    var sig = year + "|" + cards.map(function (card) { return card[3]; }).join(",");
    if (host.getAttribute("data-sig") === sig) return;
    host.setAttribute("data-sig", sig);
    host.innerHTML = '<div class="za-counts" data-year-count="1">' + cards.map(function (card) {
      return '<div class="za-ycard za-' + card[0] + '"><span class="za-ico">' + yearIcon(card[2]) + '</span><span class="za-label">' + card[1] + '</span><span class="za-num">' + card[3] + "<span>日</span></span></div>";
    }).join("") + "</div>";
  }
  function writeEarlyDay(iso, hours) {
    if (typeof window.__zangyoSet !== "function") return;
    window.__zangyoSet(function (state) {
      var map = Object.assign({}, state.earlyHoursDay || {});
      if (hours == null) delete map[iso];
      else map[iso] = hours;
      return Object.assign({}, state, { earlyHoursDay: map });
    });
  }
  function renderEarlyHours() {
    var host = document.getElementById("za-early-hours");
    if (!host) return;
    var iso = host.getAttribute("data-day");
    if (!iso) return;
    var data = stateNow() || {};
    if (!isEarlyDay(data, iso)) {
      if (host.getAttribute("data-sig") === iso + "|off") return;
      host.setAttribute("data-sig", iso + "|off");
      host.innerHTML = "";
      return;
    }
    var base = weekdayHours(data, weekdayOf(iso));
    var map = data.earlyHoursDay || {};
    var override = Object.prototype.hasOwnProperty.call(map, iso);
    var shown = override ? formatHours(map[iso]) : (base > 0 ? formatHours(base) : "");
    var sig = iso + "|" + shown + "|" + (override ? "1" : "0") + "|" + base;
    if (host.getAttribute("data-sig") === sig) return;
    host.setAttribute("data-sig", sig);
    host.innerHTML =
      '<div class="za-ehday">' +
      '<label>この日の早出時間<input type="number" min="0" max="24" step="0.25" inputmode="decimal" data-early-day="' + esc(iso) + '" value="' + esc(shown) + '" aria-label="この日の早出時間" placeholder="0"></label>' +
      "<p>" + (override ? "この日だけの時間です。" : "曜日の設定です。直すと、この日だけ変わります。") + " 空欄にすると、曜日の設定に戻ります。</p>" +
      (override ? '<button type="button" data-early-clear="' + esc(iso) + '">曜日の設定に戻す</button>' : "") +
      "</div>";
  }
  window.__zangyoOnData = function () {
    renderYearCount();
    renderEarlyHours();
  };

  document.addEventListener("change", function (event) {
    var target = event.target;
    if (!target) return;
    if (target.getAttribute && target.getAttribute("data-early-day")) {
      var iso = target.getAttribute("data-early-day");
      var data = stateNow() || {};
      var parsed = parseHours(target.value);
      var base = weekdayHours(data, weekdayOf(iso));
      writeEarlyDay(iso, parsed == null || parsed === base ? null : parsed);
      return;
    }
    if (target.tagName !== "SELECT" || !document.getElementById("za-year-count")) return;
    renderYearCount();
  }, true);
  document.addEventListener("click", function (event) {
    var target = event.target.closest && event.target.closest("[data-early-clear]");
    if (!target) return;
    writeEarlyDay(target.getAttribute("data-early-clear"), null);
  }, true);

  var appRoot = document.getElementById("root");
  if (appRoot) {
    new MutationObserver(function () {
      markCalendar();
      renderDayAlarm();
      renderEarlyHours();
      renderYearCount();
    }).observe(appRoot, { childList: true, subtree: true });
  }

  setActive("shift");
})();
