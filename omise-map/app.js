const G={'ぱん':['🥐','#d4a017'],'カフェ':['☕','#8b5a2b'],'ラーメン':['🍜','#e53935'],'寿司':['🍣','#1e88e5'],'和菓子':['🍡','#d81b60'],'洋菓子':['🍰','#ec407a'],'アイス':['🍦','#4fc3f7'],'ドーナツ':['🍩','#f48fb1'],'たいやき':['🐟','#ff8f00'],'お好み焼き':["<svg viewBox='0 0 24 24' width='1.1em' height='1.1em' style='vertical-align:-0.2em'><ellipse cx='12' cy='13' rx='10.5' ry='8' fill='#c9853f'/><ellipse cx='12' cy='12' rx='9.5' ry='7' fill='#6b3a1e'/><path d='M5 10.5q2-2 4 0t4 0 4 0 3 0M4.5 13.5q2-2 4 0t4 0 4 0 4 0' stroke='#fffbe8' stroke-width='1.1' fill='none' stroke-linecap='round'/><path d='M8 8.5l1.5 1M14 8l-1 1.3M16.5 14.5l1.4.6M10 15.5l1.2-1' stroke='#f2b880' stroke-width='1.3' stroke-linecap='round'/><circle cx='15' cy='11.5' r='.8' fill='#4caf50'/><circle cx='8.5' cy='13' r='.7' fill='#4caf50'/></svg>",'#a1887f'],'洋食':['🍝','#fb8c00'],'和食':['🍱','#6d4c41'],'中華':['🥟','#c62828'],'焼き肉':['🥩','#b71c1c'],'カレー':['🍛','#f9a825'],'うどん・そば':['🍲','#795548'],'沖縄料理':['🌺','#00897b'],'多国籍料理':['🌏','#43a047'],'ベトナム':['🥖','#2e7d32'],'テイクアウト':['🥡','#7e57c2'],'ショッピング':['🛍','#5e35b1'],'公園など':['🌳','#388e3c'],'神社':['⛩','#d32f2f'],'お寺':['🛕','#616161'],'その他/観光':['📍','#546e7a']};
const cgOf=g=>(st.cg||[]).find(c=>c.name===g);
const gi=g=>G[g]||(cgOf(g)?[cgOf(g).emoji||'🏷',cgOf(g).color||'#607d8b']:G['その他/観光']);
const allG=()=>Object.keys(G).concat((st.cg||[]).map(c=>c.name));
const LS='omise-map-v1';
let st=JSON.parse(localStorage.getItem(LS)||'{}');st.p=st.p||{};st.added=st.added||[];st.cg=st.cg||[];st.dl=Array.isArray(st.dl)?st.dl:[];st.dp=Array.isArray(st.dp)?st.dp:[];
const save=()=>localStorage.setItem(LS,JSON.stringify(st));
let base=[],raw=[],all=[],fG=new Set(),me=null,pick=null,view='map';
const $=s=>document.querySelector(s),esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const map=L.map('map',{zoomControl:false}).setView([35.17,136.91],11);
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap'}).addTo(map);
const layer=L.layerGroup().addTo(map);let meMk=null;
function rebuild(){raw=base.concat(st.added.map(a=>({...a,lists:['手動追加'],added:true})));const dl=new Set(st.dl),dp=new Set(st.dp);all=raw.map(p=>({...p,lists:p.lists.filter(l=>!dl.has(l))})).filter(p=>p.lists.length&&!dp.has(p.id)).map(p=>{const o=(st.p[p.id]||{}).genres;const gs=(o&&o.length?o:(p.genres&&p.genres.length?p.genres:[p.genre])).filter(Boolean);const g2=gs.length?gs:['その他/観光'];return {...p,genres:g2,genre:g2[0],origGenre:p.genre};});}
function dist(p){if(!me||p.lat==null)return null;const R=6371,r=Math.PI/180,dl=(p.lat-me[0])*r,dn=(p.lng-me[1])*r;const a=Math.sin(dl/2)**2+Math.cos(me[0]*r)*Math.cos(p.lat*r)*Math.sin(dn/2)**2;return 2*R*Math.asin(Math.sqrt(a));}
const sdOf=p=>p.savedDate||'';
const fmtD=d=>d==null?'':d<1?Math.round(d*1000)+'m':d.toFixed(1)+'km';
function filtered(){const q=$('#q').value.trim().toLowerCase(),l=$('#listSel').value,v=$('#visSel').value;
 return all.filter(p=>{const s=st.p[p.id]||{};
  if(fG.size&&!p.genres.some(g=>fG.has(g)))return false;if(l&&l!=='__lm'&&!p.lists.includes(l))return false;
  if(v==='v'&&!s.visited)return false;if(v==='n'&&s.visited)return false;if(v==='w'&&!s.stars)return false;
  if(q&&!(p.name+' '+(p.memo||'')+' '+(s.memo||'')+' '+(p.area||'')+' '+p.lists.join(' ')).toLowerCase().includes(q))return false;return true;});}
function render(){const f=filtered();layer.clearLayers();
 f.forEach(p=>{if(p.lat==null)return;const [e,c]=gi(p.genre),v=(st.p[p.id]||{}).visited;
  L.marker([p.lat,p.lng],{icon:L.divIcon({className:'',html:`<div class="pin${v?' v':''}" style="background:${c}"><span>${v?'✅':e}</span></div>`,iconSize:[30,30],iconAnchor:[15,30]})}).on('click',()=>show(p.id)).addTo(layer);});
 const nog=f.filter(p=>p.lat==null).length;$('#count').textContent=`${f.length}件${nog?`（地図なし${nog}）`:''}`;
 const byNew=$('#sortSel').value==='new';const arr=f.map(p=>[p,dist(p)]).sort((a,b)=>byNew?((sdOf(b[0])>sdOf(a[0]))-(sdOf(b[0])<sdOf(a[0]))||a[0].name.localeCompare(b[0].name,'ja')):me?((a[1]??1e9)-(b[1]??1e9)):a[0].name.localeCompare(b[0].name,'ja'));
 $('#list').innerHTML=arr.length?arr.map(([p,d])=>{const s=st.p[p.id]||{};return `<div class="item" data-id="${esc(p.id)}"><div class="ic">${gi(p.genre)[0]}</div><div><div class="t">${esc(p.name)}</div><div class="s">${esc(p.genres.join('・'))}${p.area?' · '+esc(p.area):''}${s.visited?' · ✅行った':''}${s.stars?' · '+'★'.repeat(s.stars):''}${p.lat==null?' · 📍位置なし':''}</div></div><div class="d">${fmtD(d)}</div></div>`}).join(''):'<div class="empty">該当するお店がありません</div>';}
$('#list').onclick=e=>{const it=e.target.closest('.item');if(it)show(it.dataset.id);};
function chips(){const cnt={};all.forEach(p=>p.genres.forEach(g=>cnt[g]=(cnt[g]||0)+1));
 $('#genres').innerHTML=`<button class="chip${fG.size?'':' on'}" data-g="">すべて</button>`+allG().filter(g=>cnt[g]||cgOf(g)).map(g=>`<button class="chip${fG.has(g)?' on':''}" data-g="${esc(g)}" style="border-left:4px solid ${gi(g)[1]}">${gi(g)[0]} ${esc(g)} ${cnt[g]||0}</button>`).join('')+`<button class="chip" data-g="__m">＋ ジャンル編集</button>`;
 const ls={};all.forEach(p=>p.lists.forEach(l=>ls[l]=(ls[l]||0)+1));const cur=$('#listSel').value;
 $('#listSel').innerHTML='<option value="">リスト：すべて</option>'+Object.keys(ls).sort((a,b)=>a.localeCompare(b,'ja')).map(l=>`<option value="${esc(l)}">${esc(l)}（${ls[l]}）</option>`).join('')+'<option value="__lm">✏ リスト編集…</option>';$('#listSel').value=ls[cur]?cur:'';}
function delMgr(){const rows=st.dp.map(id=>raw.find(p=>p.id===id)||{id,name:'（不明なお店）',lists:[]});
 openS(`<h2>🗑 削除したお店</h2><p style="font-size:12px;color:#777">お店マップの中で非表示にしたお店です。Googleマップには影響しません。元に戻すと行った・★・メモも戻ります。</p>${rows.length?rows.map(p=>`<div style="display:flex;align-items:center;gap:8px;padding:8px 0;border-bottom:1px solid #eee"><div style="flex:1">${esc(p.name)}<div style="color:#888;font-size:12px">${esc(p.lists.join('・'))}</div></div><button class="btn dpR" data-id="${esc(p.id)}" style="width:auto;margin:0;padding:6px 12px">元に戻す</button></div>`).join(''):'<div style="color:#999;font-size:13px">ありません</div>'}`);
 document.querySelectorAll('.dpR').forEach(b=>b.onclick=()=>{st.dp=st.dp.filter(x=>x!==b.dataset.id);save();rebuild();chips();render();delMgr();});}
function listMgr(){const ls={};all.forEach(p=>p.lists.forEach(l=>ls[l]=(ls[l]||0)+1));const dc={};raw.forEach(p=>p.lists.forEach(l=>{if(st.dl.includes(l))dc[l]=(dc[l]||0)+1}));
 const row=(l,n,b)=>`<div style="display:flex;align-items:center;gap:8px;padding:8px 0;border-bottom:1px solid #eee"><div style="flex:1">${esc(l)} <span style="color:#888;font-size:12px">${n}件</span></div>${b}</div>`;
 openS(`<h2>✏ リスト編集</h2><p style="font-size:12px;color:#777">削除はお店マップの中だけです。Googleマップの保存リストは変わりません。他のリストにも入っているお店はそのまま残ります。</p>
 ${Object.keys(ls).sort((a,b)=>a.localeCompare(b,'ja')).map(l=>row(l,ls[l],`<button class="btn g lmD" data-l="${esc(l)}" style="width:auto;margin:0;padding:6px 12px;color:#d32f2f">削除</button>`)).join('')}
 <button class="btn g" id="toDp" style="margin-top:12px">🗑 削除したお店（${st.dp.length}）</button><h3 style="font-size:14px;margin-top:16px">削除したリスト</h3>${st.dl.length?st.dl.map(l=>row(l,dc[l]||0,`<button class="btn lmR" data-l="${esc(l)}" style="width:auto;margin:0;padding:6px 12px">元に戻す</button>`)).join(''):'<div style="color:#999;font-size:13px">ありません</div>'}`);
 const upd=()=>{save();rebuild();chips();render();listMgr();};$('#toDp').onclick=delMgr;
 document.querySelectorAll('.lmD').forEach(b=>b.onclick=()=>{const l=b.dataset.l;if(!confirm(`「${l}」をお店マップから削除しますか？\n\nお店マップの中で非表示にするだけで、Googleマップのリストは変わりません。\nこのリストだけに入っているお店は地図・一覧から消えます（行った・★・メモは保存されたままで、元に戻せます）。`))return;st.dl.push(l);upd();});
 document.querySelectorAll('.lmR').forEach(b=>b.onclick=()=>{st.dl=st.dl.filter(x=>x!==b.dataset.l);upd();});}
$('#listSel').addEventListener('change',()=>{if($('#listSel').value==='__lm'){$('#listSel').value='';render();listMgr();}});
$('#genres').onclick=e=>{const b=e.target.closest('.chip');if(!b)return;const g=b.dataset.g;if(g==='__m')return genreMgr();if(!g)fG.clear();else fG.has(g)?fG.delete(g):fG.add(g);chips();render();};
['#q','#listSel','#visSel','#sortSel'].forEach(s=>$(s).addEventListener('input',render));
const openS=h=>{$('#sc').innerHTML=h;$('#sheet').classList.add('open');},closeS=()=>$('#sheet').classList.remove('open');$('#sx').onclick=closeS;
function show(id){const p=all.find(x=>x.id===id);if(!p)return;const s=st.p[id]||{};const d=dist(p);
 openS(`<h2>${gi(p.genre)[0]} ${esc(p.name)}</h2><div class="s" style="color:#777;font-size:13px">${esc(p.genres.join('・'))}${p.area?' · '+esc(p.area):''}${d!=null?' · '+fmtD(d):''}</div>
 <div style="margin:6px 0">${p.lists.map(l=>`<span class="tag">${esc(l)}</span>`).join('')}</div>
 <div style="font-size:13px;color:#555;margin:4px 0">${p.added?'保存日':'保存日（取り込み日）'}：${p.savedDate?esc(p.savedDate)+(p.added?'':'ごろ'):'不明'}</div>
 ${p.memo?`<div class="memo">${esc(p.memo)}</div>`:''}
 ${p.url?`<a class="btn" href="${esc(p.url)}" target="_blank" rel="noopener">Googleマップで開く</a>`:''}
 ${p.lat!=null?`<button class="btn g" id="goMap">地図で表示</button>`:'<div style="font-size:12px;color:#999;margin-top:6px">位置情報なし（地図にピンは出ません）</div>'}
 <label>ジャンル（複数可・最初に選んだものがピンの色）</label><div id="pg">${gPick(p.genres)}</div>${(st.p[id]||{}).genres?'<button class="btn g" id="pgReset">ジャンルを元に戻す</button>':''}
 <label><input type="checkbox" id="vis" ${s.visited?'checked':''}> ✅ 行った</label><input type="date" id="vdate" value="${s.vdate||''}" ${s.visited?'':'style="display:none"'}>
 <label>行きたい度</label><div class="stars">${[1,2,3].map(n=>`<button data-n="${n}" class="${(s.stars||0)>=n?'on':''}">★</button>`).join('')}</div>
 <label>自分メモ</label><textarea id="pm" rows="3">${esc(s.memo||'')}</textarea>
 <button class="btn" id="del" style="background:#d32f2f;color:#fff;margin-top:14px">🗑 このお店を消す</button>`);
 const up=f=>{st.p[id]={...(st.p[id]||{}),...f};save();render();};
 $('#vis').onchange=e=>{const on=e.target.checked,dt=on?($('#vdate').value||new Date().toLocaleDateString('sv')):'';$('#vdate').value=dt;$('#vdate').style.display=on?'':'none';up({visited:on,vdate:dt});};
 $('#vdate').onchange=e=>up({vdate:e.target.value});
 $('.stars').onclick=e=>{const b=e.target.closest('button');if(!b)return;let n=+b.dataset.n;if((st.p[id]||{}).stars===n)n=0;up({stars:n});document.querySelectorAll('.stars button').forEach(x=>x.classList.toggle('on',+x.dataset.n<=n));};
 $('#pm').oninput=e=>up({memo:e.target.value});
 bindPick('#pg',p.genres,gs=>{st.p[id]={...(st.p[id]||{}),genres:gs};save();rebuild();chips();render();show(id);});
 if($('#pgReset'))$('#pgReset').onclick=()=>{const o={...(st.p[id]||{})};delete o.genres;st.p[id]=o;save();rebuild();chips();render();show(id);};
 if($('#goMap'))$('#goMap').onclick=()=>{setView('map');map.setView([p.lat,p.lng],17);closeS();};
 if($('#del'))$('#del').onclick=()=>{if(!confirm(`「${p.name}」をお店マップから消しますか？\n\nお店マップの中で非表示にするだけで、Googleマップの保存は変わりません。\n行った・★・メモは保存されたままで、「削除したお店」からいつでも元に戻せます。`))return;if(!st.dp.includes(id))st.dp.push(id);save();rebuild();chips();render();closeS();};}
function setView(v){view=v;$('#map').style.visibility=v==='map'?'visible':'hidden';$('#list').style.display=v==='list'?'block':'none';$('#tMap').classList.toggle('on',v==='map');$('#tList').classList.toggle('on',v==='list');if(v==='map')setTimeout(()=>map.invalidateSize(),50);}
$('#tMap').onclick=()=>setView('map');$('#tList').onclick=()=>setView('list');
$('#nearBtn').onclick=()=>{if(!navigator.geolocation)return alert('位置情報が使えません');$('#nearBtn').textContent='📡 取得中…';
 navigator.geolocation.getCurrentPosition(pos=>{me=[pos.coords.latitude,pos.coords.longitude];$('#nearBtn').textContent='📡 近くのお店';
  if(meMk)meMk.remove();meMk=L.circleMarker(me,{radius:8,color:'#fff',weight:3,fillColor:'#1a73e8',fillOpacity:1}).addTo(map);map.setView(me,14);render();setView('list');},
  err=>{$('#nearBtn').textContent='📡 近くのお店';alert('現在地を取得できませんでした：'+err.message);},{enableHighAccuracy:true,timeout:15000});};
function addForm(v={}){openS(`<h2>＋ お店を追加</h2><label>店名</label><input type="text" id="an" value="${esc(v.name||'')}">
 <label>ジャンル</label><div id="ag">${gPick(v.genres||[])}</div>
 <label>GoogleマップのURL（任意）</label><input type="url" id="au" value="${esc(v.url||'')}">
 <label>メモ（任意）</label><textarea id="am" rows="2">${esc(v.memo||'')}</textarea>
 <label>緯度・経度（任意）</label><div style="display:flex;gap:6px"><input type="number" step="any" id="ala" placeholder="緯度" value="${v.lat??''}"><input type="number" step="any" id="aln" placeholder="経度" value="${v.lng??''}"></div>
 <button class="btn g" id="pickB">🗺 地図で場所を選ぶ</button><button class="btn" id="saveA">保存</button>`);
 let agOrd=[...(v.genres||[])];bindPick('#ag',agOrd,gs=>{agOrd=gs;document.getElementById('ag').innerHTML=gPick(gs);bindAg();});function bindAg(){bindPick('#ag',agOrd,gs=>{agOrd=gs;document.getElementById('ag').innerHTML=gPick(gs);bindAg();});}
 const cur=()=>({name:$('#an').value,genres:[...document.querySelectorAll('#ag .chip.on')].map(b=>b.dataset.g).sort((a,b)=>agOrd.indexOf(a)-agOrd.indexOf(b)),url:$('#au').value,memo:$('#am').value,lat:$('#ala').value,lng:$('#aln').value});
 $('#pickB').onclick=()=>{pick=cur();closeS();setView('map');$('#pickmsg').style.display='block';};
 $('#saveA').onclick=()=>{const c=cur();if(!c.name.trim())return alert('店名を入力してください');const la=parseFloat(c.lat),ln=parseFloat(c.lng);
  const p={id:'m'+Date.now(),name:c.name.trim(),genre:c.genres[0]||'その他/観光',genres:c.genres.length?c.genres:['その他/観光'],url:c.url.trim(),memo:c.memo,area:'',lat:isFinite(la)?la:null,lng:isFinite(ln)?ln:null,savedDate:new Date().toLocaleDateString('sv')};
  st.added.push(p);save();rebuild();chips();render();closeS();show(p.id);};}
$('#addBtn').onclick=()=>addForm();
map.on('click',e=>{if(!pick)return;const v={...pick,lat:e.latlng.lat.toFixed(6),lng:e.latlng.lng.toFixed(6)};pick=null;$('#pickmsg').style.display='none';addForm(v);});
const ymd=()=>new Date().toLocaleDateString('sv').replace(/-/g,'');
$('#menuBtn').onclick=()=>openS(`<h2>⚙ バックアップ</h2><p style="font-size:13px;color:#555">「行った」「行きたい度」「自分メモ」「手動追加」はこの端末のブラウザ内に保存されています。機種変更やデータ消去に備えて書き出しておいてください。</p>
 <button class="btn" id="exB">⬇ 書き出し（JSON）</button><button class="btn g" id="imB">⬆ 読み込み（JSON）</button><button class="btn" id="drB" style="background:#1a73e8">☁ ドライブに保存</button><button class="btn g" id="drL">☁ ドライブから読み込み</button><p style="font-size:12px;color:#555">押すとGoogleの許可画面が出て、ドライブの「Grok」フォルダへ omise-map-backup-日付.json を直接保存します。保存を確認できたときだけ、お店マップのバックアップは新しい2つを残し、古いものはゴミ箱へ移します（他のアプリのファイルは触りません）。※この方式（drive.file 権限）では「Grok」以外の既存フォルダ（例：財）へは直接保存できません。</p><button class="btn g" id="toDp2">🗑 削除したお店（${st.dp.length}）</button><button class="btn g" id="toLm">✏ リスト編集</button><p style="font-size:12px;color:#999">登録件数：${Object.keys(st.p).length}件の記録 / 手動追加${st.added.length}件</p>`)||bk();
function bk(){$('#toDp2').onclick=delMgr;$('#toLm').onclick=listMgr;$('#exB').onclick=()=>{const b=new Blob([JSON.stringify({app:'omise-map',version:1,exported:new Date().toISOString(),data:st},null,1)],{type:'application/json'});const a=document.createElement('a');a.href=URL.createObjectURL(b);a.download=`omise-map-backup-${ymd()}.json`;document.body.appendChild(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(a.href),5000);};
 $('#imB').onclick=()=>$('#fileIn').click();$('#drB').onclick=drSave;$('#drL').onclick=drLoad;}
function applyBackup(j){const d=j&&j.app==='omise-map'?j.data:j;if(!d||typeof d.p!=='object')return false;if(!confirm('現在の記録をバックアップの内容で置き換えます。よろしいですか？'))return null;st={p:d.p||{},added:d.added||[],cg:Array.isArray(d.cg)?d.cg:[],dl:Array.isArray(d.dl)?d.dl:[],dp:Array.isArray(d.dp)?d.dp:[]};save();rebuild();chips();render();closeS();alert('読み込みました');return true;}
$('#fileIn').onchange=async e=>{const f=e.target.files[0];e.target.value='';if(!f)return;try{const j=JSON.parse(await f.text());const d=j.app==='omise-map'?j.data:j;if(!d||typeof d.p!=='object')throw 0;
 if(!confirm('現在の記録をバックアップの内容で置き換えます。よろしいですか？'))return;st={p:d.p||{},added:d.added||[],cg:Array.isArray(d.cg)?d.cg:[]};save();rebuild();chips();render();closeS();alert('読み込みました');}catch(_){alert('お店マップのバックアップファイルではないようです');}};
fetch('places.json').then(r=>r.json()).then(d=>{base=d;rebuild();chips();render();});
if('serviceWorker'in navigator)navigator.serviceWorker.register('sw.js');

/* ---- Google Drive backup (GIS + drive.file, shared 「Grok」 folder) ---- */
const GOOGLE_CLIENT_ID='182175171319-0kfoj0i0qq0p2q7vulqcnfftmgc1gjcm.apps.googleusercontent.com';
function loadGis(){return new Promise((res,rej)=>{if(window.google?.accounts?.oauth2)return res();const sc=document.createElement('script');sc.src='https://accounts.google.com/gsi/client';sc.async=true;sc.defer=true;sc.onload=()=>res();sc.onerror=()=>rej(new Error('gis-load'));document.head.appendChild(sc)})}
function getToken(){return new Promise((res,rej)=>{try{const tc=google.accounts.oauth2.initTokenClient({client_id:GOOGLE_CLIENT_ID,scope:'https://www.googleapis.com/auth/drive.file',callback:r=>r?.access_token?res(r.access_token):rej(new Error('token')),error_callback:()=>rej(new Error('token'))});tc.requestAccessToken({prompt:''})}catch(e){rej(e)}})}
const gapi=(tk,url,opt={})=>fetch(url,{...opt,headers:{Authorization:`Bearer ${tk}`,...(opt.headers||{})}});
async function grokFolderId(tk){const q=encodeURIComponent(`name='Grok' and mimeType='application/vnd.google-apps.folder' and trashed=false`);
  const r=await gapi(tk,`https://www.googleapis.com/drive/v3/files?q=${q}&fields=files(id%2Cname)&spaces=drive`);if(!r.ok)throw new Error('drive-list');
  const j=await r.json();if(j.files?.length)return j.files[0].id;
  const cr=await gapi(tk,'https://www.googleapis.com/drive/v3/files',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({name:'Grok',mimeType:'application/vnd.google-apps.folder'})});
  if(!cr.ok)throw new Error('drive-folder');return (await cr.json()).id}
const OMISE_BACKUP_RE=/^omise-map-backup-\d{8}.*\.json$/;
async function listMine(tk,fid){const lq=encodeURIComponent(`'${fid}' in parents and trashed=false`);
  const lr=await gapi(tk,`https://www.googleapis.com/drive/v3/files?q=${lq}&orderBy=createdTime%20desc&pageSize=200&fields=files(id%2Cname%2CcreatedTime)&spaces=drive`);
  if(!lr.ok)throw new Error('drive-clean-list');return ((await lr.json()).files||[]).filter(f=>OMISE_BACKUP_RE.test(f.name||''))}
async function saveToDrive(name,text){
  if(navigator.onLine===false)return {ok:false,reason:'offline'};
  await loadGis();const tk=await getToken();const fid=await grokFolderId(tk);
  const bd='omise_'+Date.now();const meta=JSON.stringify({name,parents:[fid],mimeType:'application/json'});
  const body=`--${bd}\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n${meta}\r\n--${bd}\r\nContent-Type: application/json\r\n\r\n${text}\r\n--${bd}--`;
  const r=await gapi(tk,'https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id%2Cname%2Cparents',{method:'POST',headers:{'Content-Type':`multipart/related; boundary=${bd}`},body});
  if(!r.ok)throw new Error('drive-upload');const j=await r.json();
  if(!j?.id||!(j.parents||[]).includes(fid))return {ok:true,name,cleaned:0,unverified:true};
  let cleaned=0,cleanErr=false;
  try{const mine=await listMine(tk,fid);
    if(!mine.some(f=>f.id===j.id))throw new Error('upload-not-listed'); // 確認できないときは消さない
    for(const f of mine.slice(2)){const tr=await gapi(tk,`https://www.googleapis.com/drive/v3/files/${f.id}`,{method:'PATCH',headers:{'Content-Type':'application/json'},body:JSON.stringify({trashed:true})});tr.ok?cleaned++:(cleanErr=true)}
  }catch(e){cleanErr=true}
  return {ok:true,name:j.name||name,cleaned,cleanErr}}
const backupText=()=>JSON.stringify({app:'omise-map',version:1,exported:new Date().toISOString(),data:st},null,1);
async function drSave(){const btn=$('#drB');if(btn.disabled)return;btn.disabled=true;const old=btn.textContent;btn.textContent='ドライブに保存中…';
  try{const r=await saveToDrive(`omise-map-backup-${ymd()}.json`,backupText());
    if(!r.ok)alert(r.reason==='offline'?'オフラインのため保存できません':'保存できませんでした');
    else if(r.unverified)alert('保存しました（確認できなかったので古いものは消していません）');
    else alert(`ドライブに保存しました：${r.name}`+(r.cleaned?`（古い${r.cleaned}件をゴミ箱へ）`:'')+(r.cleanErr?'（古いものの整理はできませんでした）':''))}
  catch(e){const m=String(e&&e.message||e);alert(m==='token'?'Googleの許可が得られませんでした':m==='gis-load'?'Googleの読み込みに失敗しました':'ドライブに保存できませんでした（何も消していません）')}
  finally{btn.disabled=false;btn.textContent=old}}
async function drLoad(){const btn=$('#drL');if(btn.disabled)return;btn.disabled=true;const old=btn.textContent;btn.textContent='読み込み中…';
  try{if(navigator.onLine===false)throw new Error('offline');await loadGis();const tk=await getToken();const fid=await grokFolderId(tk);
    const mine=await listMine(tk,fid);if(!mine.length){alert('「Grok」フォルダにお店マップのバックアップが見つかりません');return}
    const f=mine[0];const r=await gapi(tk,`https://www.googleapis.com/drive/v3/files/${f.id}?alt=media`);if(!r.ok)throw new Error('dl');
    const res=applyBackup(await r.json());if(res===false)alert('お店マップのバックアップファイルではないようです');}
  catch(e){const m=String(e&&e.message||e);alert(m==='offline'?'オフラインのため読み込めません':m==='token'?'Googleの許可が得られませんでした':m==='gis-load'?'Googleの読み込みに失敗しました':'ドライブから読み込めませんでした')}
  finally{btn.disabled=false;btn.textContent=old}}

/* ---- ジャンル選択（複数）・自分のジャンル ---- */
function gPick(sel){return allG().map(g=>{const i=sel.indexOf(g);return `<button type="button" class="chip${i>=0?' on':''}" data-g="${esc(g)}" style="margin:2px;border-left:4px solid ${gi(g)[1]}">${i===0?'①':''}${gi(g)[0]} ${esc(g)}</button>`}).join('')}
function bindPick(sel,cur,cb){const el=document.querySelector(sel);if(!el)return;el.onclick=e=>{const b=e.target.closest('.chip');if(!b)return;const g=b.dataset.g;let gs=[...cur];gs=gs.includes(g)?gs.filter(x=>x!==g):gs.concat(g);if(!gs.length)return alert('ジャンルは1つ以上選んでください');cb(gs);};}
function renameG(oldN,newN){const f=a=>a?a.map(g=>g===oldN?newN:g):a;Object.values(st.p).forEach(r=>{if(r.genres)r.genres=f(r.genres)});st.added.forEach(a=>{a.genres=f(a.genres);if(a.genre===oldN)a.genre=newN});if(fG.has(oldN)){fG.delete(oldN);if(newN)fG.add(newN)}}
function genreMgr(){openS(`<h2>🏷 ジャンル編集</h2><p style="font-size:12px;color:#777">自分で作ったジャンルは名前・色・絵文字を変えたり削除できます。最初からあるジャンルは変更できません。</p>
 ${(st.cg||[]).map((c,i)=>`<div style="display:flex;gap:6px;align-items:center;margin:6px 0"><input type="text" data-i="${i}" class="cgE" value="${esc(c.emoji||'')}" style="width:52px" placeholder="🏷"><input type="text" data-i="${i}" class="cgN" value="${esc(c.name)}"><input type="color" data-i="${i}" class="cgC" value="${esc(c.color||'#607d8b')}" style="width:44px;height:40px;border:0"><button class="cgD" data-i="${i}" style="border:0;background:none;font-size:20px">🗑</button></div>`).join('')||'<div style="color:#999;font-size:13px">まだありません</div>'}
 <button class="btn g" id="cgSave">変更を保存</button>
 <label>新しいジャンル</label><div style="display:flex;gap:6px;align-items:center"><input type="text" id="ngE" placeholder="🏷" style="width:52px"><input type="text" id="ngN" placeholder="名前"><input type="color" id="ngC" value="#009688" style="width:44px;height:40px;border:0"></div><button class="btn" id="ngAdd">＋ 追加</button>`);
 const taken=(n,skip)=>Object.keys(G).includes(n)||(st.cg||[]).some((c,j)=>j!==skip&&c.name===n);
 $('#ngAdd').onclick=()=>{const n=$('#ngN').value.trim();if(!n)return alert('名前を入力してください');if(taken(n,-1))return alert('同じ名前のジャンルがあります');st.cg.push({name:n,color:$('#ngC').value,emoji:$('#ngE').value.trim()});save();rebuild();chips();render();genreMgr();};
 $('#cgSave').onclick=()=>{const rows=[...document.querySelectorAll('.cgN')];for(const r of rows){const i=+r.dataset.i,n=r.value.trim();if(!n)return alert('名前を入力してください');if(taken(n,i)||rows.some(x=>x!==r&&x.value.trim()===n))return alert('同じ名前のジャンルがあります：'+n)}
  rows.forEach(r=>{const i=+r.dataset.i,c=st.cg[i],n=r.value.trim();if(n!==c.name)renameG(c.name,n);c.name=n;c.color=document.querySelector(`.cgC[data-i="${i}"]`).value;c.emoji=document.querySelector(`.cgE[data-i="${i}"]`).value.trim();});save();rebuild();chips();render();alert('保存しました');genreMgr();};
 document.querySelectorAll('.cgD').forEach(b=>b.onclick=()=>{const c=st.cg[+b.dataset.i];if(!confirm(`「${c.name}」を削除しますか？（このジャンルを付けたお店からは外れます）`))return;
  Object.values(st.p).forEach(r=>{if(r.genres){r.genres=r.genres.filter(g=>g!==c.name);if(!r.genres.length)delete r.genres}});st.added.forEach(a=>{a.genres=(a.genres||[]).filter(g=>g!==c.name);if(!a.genres.length)a.genres=['その他/観光'];a.genre=a.genres[0]});fG.delete(c.name);st.cg.splice(+b.dataset.i,1);save();rebuild();chips();render();genreMgr();});}
