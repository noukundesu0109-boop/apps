const fs = require("fs");
const htmlPath = process.argv[2];
const jsonPath = process.argv[3];
const html = fs.readFileSync(htmlPath, "utf8");
const fixture = JSON.parse(fs.readFileSync(jsonPath, "utf8")).shifts;
const ueStart = html.indexOf("var Ue=");
const ueEnd = html.indexOf("},We=e((");
if (ueStart < 0 || ueEnd < 0) throw new Error("company calendar was not found");
eval(html.slice(ueStart, ueEnd + 1));
const atStart = html.indexOf("at=e=>{let t={};if(e&&typeof e==`object`&&!Array.isArray(e))for(let[n,r]of Object.entries(e))");
const ptEnd = html.indexOf(",mt=e=>{let t=pt(e)", atStart);
if (atStart < 0 || ptEnd < 0) throw new Error("migration function was not found");
const nt = (value) => JSON.parse(JSON.stringify(value));
const it = { day: 1, night: 1, off: 1 };
const Ye = (n) => String(n).padStart(2, "0");
const Le = { leave: ["2024-02-15", "2026-03-12", "2026-04-09"], leaveCfg: { lots: [{ expires: "2027-08-31", days: 18 }] } };
eval(html.slice(atStart, ptEnd));

for (const [day, shift] of Object.entries(fixture)) {
  if (Ue[day] !== shift) throw new Error(`${day} is ${Ue[day]}, expected ${shift}`);
}
if (Ue["2026-03-11"] !== "day") throw new Error("March 11 must stay a day shift");
if (Ue["2026-04-01"] !== "night") throw new Error("April 1 was overwritten");

const fresh = pt({ days: { "2026-01-05": { over: 1 } }, adjust: { "2026-01": 0.5 } });
let day = 0;
let night = 0;
let off = 0;
for (let time = Date.UTC(2026, 0, 1); time <= Date.UTC(2026, 11, 31); time += 86400000) {
  const iso = new Date(time).toISOString().slice(0, 10);
  const shift = fresh.shifts[iso];
  if (shift === "day") day += 1;
  else if (shift === "night") night += 1;
  else if (shift === "off") off += 1;
  else throw new Error(`fresh calendar is missing ${iso}`);
}
if (day + night + off !== 365) throw new Error(`2026 covers ${day + night + off} days`);
if (!fresh.leave.includes("2026-03-12")) throw new Error("fresh leave is missing March 12");
if (fresh.days["2026-01-05"].over !== 1) throw new Error("overtime was changed");

const april = {};
for (const [iso, shift] of Object.entries(Ue)) if (iso >= "2026-04-01") april[iso] = shift;
april["2026-01-15"] = "night";
const edited = pt({ shifts: april, leave: ["2025-01-08"], days: { keep: 1 }, alarms: { day: [] } });
if (edited.shifts["2026-01-15"] !== "night") throw new Error("an edited January day was overwritten");
if (edited.shifts["2026-01-01"] !== Ue["2026-01-01"]) throw new Error("a blank January day was not filled");
if (edited.shifts["2026-04-01"] !== "night") throw new Error("April changed");
if (!edited.leave.includes("2026-03-12") || !edited.leave.includes("2025-01-08")) throw new Error("leave list changed wrongly");
if (edited.days.keep !== 1 || !edited.alarms) throw new Error("other fields changed");
delete edited.shifts["2026-02-02"];
const second = pt(edited);
if ("2026-02-02" in second.shifts) throw new Error("a cleared day came back");
const once = pt({ shifts: april, leave: ["2026-03-12"] });
if (once.leave.filter((iso) => iso === "2026-03-12").length !== 1) throw new Error("March 12 was added twice");
console.log(`2026 ${day} day, ${night} night, ${off} off`);
