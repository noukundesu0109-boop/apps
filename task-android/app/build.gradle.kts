import java.io.File

plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "dev.nossam.task"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.nossam.task"
        minSdk = 26
        targetSdk = 35
        versionCode = 12
        versionName = "1.4.4"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        checkReleaseBuilds = false
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/AL2.0",
            "META-INF/LGPL2.1",
            "META-INF/DEPENDENCIES",
        )
    }
}

val generatedWeb = layout.buildDirectory.dir("generated/taskWeb")

val prepareWeb = tasks.register("prepareTaskWeb") {
    val web = rootProject.layout.projectDirectory.dir("../task-app")
    val bridge = layout.projectDirectory.file("src/main/webbridge/android-bridge.js")
    inputs.dir(web)
    inputs.file(bridge)
    outputs.dir(generatedWeb)
    doLast {
        val out = generatedWeb.get().asFile
        val source = web.asFile
        out.deleteRecursively()
        out.mkdirs()
        source.walkTopDown().filter { it.isFile && it.name != "sw.js" }.forEach { file ->
            val dest = out.resolve(file.relativeTo(source).path)
            dest.parentFile.mkdirs()
            if (file.name == "index.html") {
                dest.writeText(patchIndex(file.readText()))
            } else {
                file.copyTo(dest, overwrite = true)
            }
        }
        bridge.asFile.copyTo(File(out, "android-bridge.js"), overwrite = true)
        val index = File(out, "index.html").readText()
        check(index.contains("android-bridge.js")) { "bridge script was not injected" }
        check(index.contains("TaskNative.onState")) { "state hook was not injected" }
        check(index.contains("data-memo-time")) { "memo time field was not injected" }
        check(index.contains("task-cal-host")) { "calendar host was not injected" }
        check(index.contains("task-summary-host")) { "summary host was not injected" }
        check(index.contains("__taskSetMemoDate")) { "memo date bridge was not injected" }
        check(index.contains("__taskOpen")) { "editor bridge was not injected" }
        check(!index.contains("`cal`,`カレンダー`")) { "separate calendar tab is still present" }
        check(index.contains("__taskDriveDone")) { "drive save was not switched to Android sign-in" }
        check(index.contains("__taskShift")) { "postpone bridge was not injected" }
        check(index.contains("task-ring-host")) { "per-tab ring setting was not injected" }
        check(index.contains("data-voice")) { "voice button was not injected" }
        check(index.contains("__taskAddMemo")) { "voice save bridge was not injected" }
        check(index.contains("task-voice-host")) { "voice setting was not injected" }
        check(index.contains("task-ahead-host")) { "advance notice setting was not injected" }
        check(index.contains("data-memo-lead")) { "per-memo advance notice was not injected" }
        check(index.contains("nextTaskNotice")) { "next task setting was not kept in backups" }
        check(index.contains("doneOnStop")) { "done-on-stop setting was not kept in backups" }
        check(index.contains("にずらし中")) { "postpone label was not injected" }
        check(index.contains("新しい1件だけを残し")) { "drive keep-one text was not updated" }
        check(index.contains("ドライブから戻す")) { "drive restore button was not injected" }
        check(index.contains("__taskApplyBackup")) { "drive restore was not wired to the file restore" }
        check(!index.contains("serviceWorker.register")) { "service worker registration still present" }
    }
}

android.sourceSets.getByName("main").assets.srcDir(generatedWeb)
tasks.configureEach {
    if ((name.startsWith("merge") && name.endsWith("Assets")) || name.contains("LintVital")) {
        dependsOn(prepareWeb)
    }
}

dependencies {
    implementation(project(":alarm-core"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("com.google.android.gms:play-services-auth:21.3.0")
}

fun patchIndex(html: String): String {
    var text = html
    text = replaceOne(
        text,
        """<script>if(location.protocol==='https:'&&'serviceWorker' in navigator){window.addEventListener('load',function(){navigator.serviceWorker.register('./sw.js',{scope:'./'}).catch(function(){})})}</script>""",
        """<script src="./android-bridge.js"></script>""",
    )
    text = replaceOne(
        text,
        """function wt(e,t){return{id:String(e?.id??`m${'$'}{t}-${'$'}{Date.now()}`),text:String(e?.text??``).trim(),due:ht(e?.due),done:ht(e?.done),at:Number.isFinite(e?.at)?e.at:0}}""",
        """function wt(e,t){return{id:String(e?.id??`m${'$'}{t}-${'$'}{Date.now()}`),text:String(e?.text??``).trim(),due:ht(e?.due),time:/^\d{2}:\d{2}$/.test(e?.time)?e.time:null,lead:[0,5,10,15,30,60].includes(e?.lead)?e.lead:null,done:ht(e?.done),at:Number.isFinite(e?.at)?e.at:0}}""",
    )
    text = replaceOne(
        text,
        """D=(e,t,i)=>{Se(n=>[...n,{id:`m${'$'}{Date.now()}`,text:e,due:t,done:null,at:Date.now()}]);""",
        """D=(e,t,i,tm,lead)=>{let time=/^\d{2}:\d{2}$/.test(tm)?tm:null;let ahead=[0,5,10,15,30,60].includes(lead)?lead:null;Se(n=>[...n,{id:`m${'$'}{Date.now()}`,text:e,due:t,time,lead:ahead,done:null,at:Date.now()}]);""",
    )
    text = replaceOne(
        text,
        """function en({memos:e,today:t,onAdd:n,onToggle:r,onEdit:i,frequent:a,onEditFreq:o}){let[s,c]=(0,l.useState)(``),[u,d]=(0,l.useState)(null),[f,p]=(0,l.useState)(!1),m=(0,l.useRef)(null),h=()=>{let e=s.trim();e&&(n(e,u),c(``),d(null)),m.current?.focus()}""",
        """function en({memos:e,today:t,onAdd:n,onToggle:r,onEdit:i,frequent:a,onEditFreq:o}){let[s,c]=(0,l.useState)(``),[u,d]=(0,l.useState)(null),[tm,setTm]=(0,l.useState)(``),[ld,setLd]=(0,l.useState)(``),[f,p]=(0,l.useState)(!1),m=(0,l.useRef)(null);window.__taskSetMemoDate=function(v){d(v)};let h=()=>{let e=s.trim();e&&(n(e,u,!1,tm,ld===``?null:Number(ld)),c(``),d(null),setTm(``),setLd(``)),m.current?.focus()}""",
    )
    text = replaceOne(
        text,
        """onClick:()=>{n(e.text,u,!0),d(null)}""",
        """onClick:()=>{n(e.text,u,!0,tm,ld===``?null:Number(ld)),d(null),setTm(``),setLd(``)}""",
    )
    text = replaceOne(
        text,
        """(0,A.jsx)(`div`,{className:`mt-2`,children:(0,A.jsx)(Yt,{value:u,onChange:d,today:t,name:`add`})}),""",
        """(0,A.jsx)(`div`,{className:`mt-2`,children:(0,A.jsx)(Yt,{value:u,onChange:d,today:t,name:`add`})}),(0,A.jsxs)(`label`,{className:`mt-2 flex items-center gap-2 text-sm text-muted`,children:[`時刻`,(0,A.jsx)(`input`,{type:`time`,step:`60`,"aria-label":`時刻（なくても可）`,"data-memo-time":!0,className:`${'$'}{N} w-32`,value:tm,onChange:e=>setTm(e.target.value)}),`空なら鳴りません`]}),(0,A.jsxs)(`label`,{className:`mt-2 flex items-center gap-2 text-sm text-muted`,children:[`前の通知`,(0,A.jsxs)(`select`,{"aria-label":`前の通知`,"data-memo-lead":!0,className:`${'$'}{N} w-36`,value:ld,onChange:e=>setLd(e.target.value),children:[(0,A.jsx)(`option`,{value:``,children:`控えと同じ`}),(0,A.jsx)(`option`,{value:`0`,children:`なし`}),(0,A.jsx)(`option`,{value:`5`,children:`5分前`}),(0,A.jsx)(`option`,{value:`10`,children:`10分前`}),(0,A.jsx)(`option`,{value:`15`,children:`15分前`}),(0,A.jsx)(`option`,{value:`30`,children:`30分前`}),(0,A.jsx)(`option`,{value:`60`,children:`1時間前`})]})]}),""",
    )
    text = replaceOne(
        text,
        """function Qt({memo:e,today:t,onSave:n,onDelete:r,onClose:i}){let[a,o]=(0,l.useState)(e.text),[s,c]=(0,l.useState)(e.due),u=()=>{let t=a.trim();t?n({...e,text:t,due:s}):alert(`内容を入れてください`)};""",
        """function Qt({memo:e,today:t,onSave:n,onDelete:r,onClose:i}){let[a,o]=(0,l.useState)(e.text),[s,c]=(0,l.useState)(e.due),[tm,setTm]=(0,l.useState)(e.time||``),[ld,setLd]=(0,l.useState)(e.lead==null?``:String(e.lead)),u=()=>{let t=a.trim();t?n({...e,text:t,due:s,time:/^\d{2}:\d{2}$/.test(tm)?tm:null,lead:[0,5,10,15,30,60].includes(Number(ld))?Number(ld):null}):alert(`内容を入れてください`)};""",
    )
    text = replaceOne(
        text,
        """(0,A.jsxs)(`div`,{className:`mb-4`,children:[(0,A.jsx)(`span`,{className:`mb-1 block text-sm text-muted`,children:`日付`}),(0,A.jsx)(Yt,{value:s,onChange:c,today:t,name:`edit`})]}),""",
        """(0,A.jsxs)(`div`,{className:`mb-4`,children:[(0,A.jsx)(`span`,{className:`mb-1 block text-sm text-muted`,children:`日付`}),(0,A.jsx)(Yt,{value:s,onChange:c,today:t,name:`edit`})]}),(0,A.jsxs)(`label`,{className:`mb-4 flex items-center gap-2 text-sm text-muted`,children:[`時刻`,(0,A.jsx)(`input`,{type:`time`,step:`60`,"aria-label":`時刻（なくても可）`,"data-memo-time":!0,className:`${'$'}{N} w-32`,value:tm,onChange:e=>setTm(e.target.value)})]}),(0,A.jsxs)(`label`,{className:`mb-4 flex items-center gap-2 text-sm text-muted`,children:[`前の通知`,(0,A.jsxs)(`select`,{"aria-label":`前の通知`,"data-memo-lead":!0,className:`${'$'}{N} w-36`,value:ld,onChange:e=>setLd(e.target.value),children:[(0,A.jsx)(`option`,{value:``,children:`控えと同じ`}),(0,A.jsx)(`option`,{value:`0`,children:`なし`}),(0,A.jsx)(`option`,{value:`5`,children:`5分前`}),(0,A.jsx)(`option`,{value:`10`,children:`10分前`}),(0,A.jsx)(`option`,{value:`15`,children:`15分前`}),(0,A.jsx)(`option`,{value:`30`,children:`30分前`}),(0,A.jsx)(`option`,{value:`60`,children:`1時間前`})]})]}),""",
    )
    text = replaceOne(
        text,
        """function Zt({m:e,g:t,onToggle:n,onEdit:r}){let i=!!e.done,a=i?`完了 ${'$'}{St(e.done)}`:t===`over`||t===`later`?St(e.due):``;""",
        """function Zt({m:e,g:t,onToggle:n,onEdit:r}){let i=!!e.done,a=i?`完了 ${'$'}{St(e.done)}`:((t===`over`||t===`later`?St(e.due):``)+(e.time?` ${'$'}{e.time}`:``)).trim();""",
    )
    text = replaceOne(
        text,
        """(0,l.useEffect)(()=>{try{localStorage.setItem(Ze,JSON.stringify(n))}catch{}},[n])""",
        """(0,l.useEffect)(()=>{try{localStorage.setItem(Ze,JSON.stringify(n))}catch(e){}try{window.__taskGet=function(){return n};window.__taskSet=r;window.__taskSetTab=a;window.__taskOpen=v;window.__taskShift=function(kind,id,tabId,date){let s=/^\d{4}-\d{2}-\d{2}$/.test(date)?date:null;if(kind===`c`||kind===`clean`)ce(id,{shift:s});else if(kind===`b`||kind===`bousai`)ue(id,{shift:s});else if(tabId)me(tabId,id,{shift:s})};window.__taskSetRing=function(id,on){r(function(s){let rings=Object.assign({memo:!0},s.rings||{});rings[id]=!!on;return Object.assign({},s,{rings:rings})})};window.__taskAddMemo=function(text,due,time){let body=String(text||``).trim();if(!body)return!1;let day=/^\d{4}-\d{2}-\d{2}$/.test(String(due||``))?due:null;let clock=/^\d{2}:\d{2}$/.test(String(time||``))?time:``;try{a(`memo`)}catch(err){}D(body,day,!1,clock,null);return!0};window.__taskMarkDone=function(raw){let e=typeof raw==`string`?JSON.parse(raw):raw;if(!e||!e.id)return!1;let today=et(),ok=!1;if(e.kind===`memo`){Se(n=>n.map(m=>m.id===e.id?{...m,done:m.done||today}:m));ok=!0}else if(e.kind===`clean`){ce(e.id,{done:today,shift:null});ok=!0}else if(e.kind===`bousai`){ue(e.id,{due:null,shift:null});ok=!0}else if(e.tabId){me(e.tabId,e.id,{done:today,shift:null});ok=!0}if(ok){try{window.TaskNative&&TaskNative.ackDone(e.kind,e.id)}catch(err){}}return ok};window.__taskSetLead=function(min){let n=Number(min);r(function(s){return Object.assign({},s,{leadMinutes:[0,5,10,15,30,60].includes(n)?n:10})})};window.__taskSetNextNotice=function(on){r(function(s){return Object.assign({},s,{nextTaskNotice:!!on})})};window.__taskSetDoneOnStop=function(on){r(function(s){return Object.assign({},s,{doneOnStop:!!on})})}}catch(e){}try{window.TaskNative&&window.TaskNative.onState(JSON.stringify(n))}catch(e){}try{window.__taskOnData&&window.__taskOnData(n)}catch(e){}},[n])""",
    )
    text = replaceOne(
        text,
        """function vt(e,t){return{id:String(e?.id??`i${'$'}{t}-${'$'}{Date.now()}`),name:String(e?.name??``),done:ht(e?.done),doneKm:gt(e?.doneKm),months:_t(e?.months),km:_t(e?.km),memo:String(e?.memo??``)}}""",
        """function vt(e,t){return{id:String(e?.id??`i${'$'}{t}-${'$'}{Date.now()}`),name:String(e?.name??``),done:ht(e?.done),doneKm:gt(e?.doneKm),months:_t(e?.months),km:_t(e?.km),memo:String(e?.memo??``),time:/^\d{2}:\d{2}$/.test(e?.time)?e.time:null,shift:ht(e?.shift)}}""",
    )
    text = replaceOne(
        text,
        """let g={version:2,cleaning:u,bousai:d,tabs:s,memos:f,frequent:m};return o.length&&(g.removed=[...new Set(o)]),g}""",
        """let g={version:2,cleaning:u,bousai:d,tabs:s,memos:f,frequent:m};g.rings=e.rings&&typeof e.rings==`object`?{memo:!0,...e.rings}:{memo:!0};g.leadMinutes=[0,5,10,15,30,60].includes(e.leadMinutes)?e.leadMinutes:10;g.nextTaskNotice=e.nextTaskNotice!==!1;g.doneOnStop=e.doneOnStop===!0;return o.length&&(g.removed=[...new Set(o)]),g}""",
    )
    text = replaceOne(
        text,
        """ve=e=>{let t=e.done;ce(e.id,{done:o}),ge(`「${'$'}{e.place} ${'$'}{e.task}」をやった日=今日にしました`,()=>ce(e.id,{done:t}))}""",
        """ve=e=>{let t=e.done,sh=e.shift??null;ce(e.id,{done:o,shift:null}),ge(`「${'$'}{e.place} ${'$'}{e.task}」をやった日=今日にしました`,()=>ce(e.id,{done:t,shift:sh}))}""",
    )
    text = replaceOne(
        text,
        """be=(e,t)=>{let n={done:t.done,doneKm:t.doneKm},r={done:o,doneKm:e.odometer??(e.kind===`car`?null:t.doneKm)};me(e.id,t.id,r);""",
        """be=(e,t)=>{let n={done:t.done,doneKm:t.doneKm,shift:t.shift??null},r={done:o,doneKm:e.odometer??(e.kind===`car`?null:t.doneKm),shift:null};me(e.id,t.id,r);""",
    )
    text = replaceOne(
        text,
        """let[o,s]=(0,l.useState)({category:a[0]??``,item:``,qty:``,prepared:et(),due:``,...e,...t===`swap`?{prepared:et(),due:``}:{}})""",
        """let[o,s]=(0,l.useState)({category:a[0]??``,item:``,qty:``,prepared:et(),due:``,...e,...t===`swap`?{prepared:et(),due:ht(e?.prepared)&&ht(e?.due)&&tt(e.due)>=tt(e.prepared)?rt(et(),tt(e.due)-tt(e.prepared)):``,shift:null}:{}})""",
    )
    text = replaceOne(
        text,
        """n({...o,prepared:o.prepared||null,due:o.due||null})""",
        """n({...o,prepared:o.prepared||null,due:o.due||null,shift:t===`swap`?null:ht(o.shift)})""",
    )
    text = replaceOne(
        text,
        """function Vt({kind:e,item:t,today:n,onAction:r,onEdit:i,tabName:a}){let o=e===`c`?it(t):t.due,s=at(o,n),c=ot(s),""",
        """function Vt({kind:e,item:t,today:n,onAction:r,onEdit:i,tabName:a}){let base=e===`c`?it(t):t.due,o=ht(t.shift)||base,s=at(o,n),c=ot(s),""",
    )
    text = replaceOne(
        text,
        """(0,A.jsx)(M,{onClick:r,className:`shrink-0 px-2.5`,"aria-label":e===`c`?`やった`:`入れ替えた`,children:""",
        """base?(0,A.jsxs)(`div`,{className:`flex shrink-0 flex-col items-end gap-1`,children:[ht(t.shift)?(0,A.jsxs)(`span`,{className:`text-[11px] text-gold`,"data-shifted":!0,children:[Number(String(t.shift).slice(5,7)),`/`,Number(String(t.shift).slice(8)),`にずらし中`]}):null,ht(t.shift)?(0,A.jsx)(`button`,{type:`button`,className:`text-[12px] font-medium text-forest`,onClick:()=>window.__taskShift&&window.__taskShift(e,t.id,null,null),children:`元に戻す`}):(0,A.jsxs)(`label`,{className:`text-[12px] text-muted`,children:[`ずらす`,(0,A.jsx)(`input`,{type:`date`,"aria-label":`ずらす日`,className:`mt-1 block w-28 rounded border border-line bg-card px-1 py-0.5 text-[12px]`,onChange:ev=>window.__taskShift&&window.__taskShift(e,t.id,null,ev.target.value||null)})]})]}):null,(0,A.jsx)(M,{onClick:r,className:`shrink-0 px-2.5`,"aria-label":e===`c`?`やった`:`入れ替えた`,children:""",
    )
    text = replaceOne(
        text,
        """function Ht({item:e,tab:t,today:n,onAction:r,onEdit:i,tabName:a}){let o=Pt(e,t,n),s=o.st,""",
        """function Ht({item:e,tab:t,today:n,onAction:r,onEdit:i,tabName:a}){let baseDue=Pt(e,t,n).due,o=Pt(e,t,n);if(ht(e.shift)&&baseDue){let left=at(e.shift,n);o={...o,due:e.shift,leftD:left,st:ot(left)}}let s=o.st,""",
    )
    text = replaceOne(
        text,
        """(0,A.jsx)(M,{onClick:r,className:`shrink-0 px-2.5`,"aria-label":t.kind===`car`?`交換した`:`やった`,children:""",
        """baseDue?(0,A.jsxs)(`div`,{className:`flex shrink-0 flex-col items-end gap-1`,children:[ht(e.shift)?(0,A.jsxs)(`span`,{className:`text-[11px] text-gold`,"data-shifted":!0,children:[Number(String(e.shift).slice(5,7)),`/`,Number(String(e.shift).slice(8)),`にずらし中`]}):null,ht(e.shift)?(0,A.jsx)(`button`,{type:`button`,className:`text-[12px] font-medium text-forest`,onClick:()=>window.__taskShift&&window.__taskShift(`t`,e.id,t.id,null),children:`元に戻す`}):(0,A.jsxs)(`label`,{className:`text-[12px] text-muted`,children:[`ずらす`,(0,A.jsx)(`input`,{type:`date`,"aria-label":`ずらす日`,className:`mt-1 block w-28 rounded border border-line bg-card px-1 py-0.5 text-[12px]`,onChange:ev=>window.__taskShift&&window.__taskShift(`t`,e.id,t.id,ev.target.value||null)})]})]}):null,(0,A.jsx)(M,{onClick:r,className:`shrink-0 px-2.5`,"aria-label":t.kind===`car`?`交換した`:`やった`,children:""",
    )
    text = replaceOne(
        text,
        """enterKeyHint:`done`,onChange:e=>c(e.target.value),onKeyDown:e=>{e.key===`Enter`&&!e.nativeEvent.isComposing&&e.keyCode!==229&&(e.preventDefault(),h())}}),(0,A.jsxs)(M,{className:`shrink-0 px-4`,"data-memo-add":!0,onClick:h,children:[(0,A.jsx)(O,{size:16}),`追加`]})""",
        """enterKeyHint:`done`,onChange:e=>c(e.target.value),onKeyDown:e=>{e.key===`Enter`&&!e.nativeEvent.isComposing&&e.keyCode!==229&&(e.preventDefault(),h())}}),(0,A.jsx)(`button`,{type:`button`,className:`grid h-11 w-11 shrink-0 place-items-center rounded-lg border border-line bg-card text-forest`,"data-voice":!0,"aria-label":`声で入れる`,onClick:()=>{try{window.TaskNative&&TaskNative.startVoice()}catch(e){}},children:(0,A.jsx)(`svg`,{width:`22`,height:`22`,viewBox:`0 0 24 24`,fill:`currentColor`,"aria-hidden":`true`,children:(0,A.jsx)(`path`,{d:`M12 14a3 3 0 0 0 3-3V6a3 3 0 0 0-6 0v5a3 3 0 0 0 3 3zm5-3a5 5 0 0 1-10 0H5a7 7 0 0 0 6 6.92V21h2v-3.08A7 7 0 0 0 19 11h-2z`})})}),(0,A.jsxs)(M,{className:`shrink-0 px-4`,"data-memo-add":!0,onClick:h,children:[(0,A.jsx)(O,{size:16}),`追加`]})""",
    )
    text = replaceOne(
        text,
        """(0,A.jsx)(Wt,{label:`やった日`,children:(0,A.jsx)(`input`,{type:`date`,className:N,value:a.done??``,onChange:s(`done`)})}),(0,A.jsx)(Wt,{label:`周期（日）`,""",
        """(0,A.jsx)(Wt,{label:`やった日`,children:(0,A.jsx)(`input`,{type:`date`,className:N,value:a.done??``,onChange:s(`done`)})}),(0,A.jsx)(Wt,{label:`時刻（なくても可）`,children:(0,A.jsx)(`input`,{type:`time`,step:`60`,className:N,value:a.time??``,onChange:s(`time`)})}),(0,A.jsx)(Wt,{label:`周期（日）`,""",
    )
    text = replaceOne(
        text,
        """(0,A.jsx)(Wt,{label:`期限`,children:(0,A.jsx)(`input`,{type:`date`,className:N,value:o.due??``,onChange:c(`due`)})}),""",
        """(0,A.jsx)(Wt,{label:`期限`,children:(0,A.jsx)(`input`,{type:`date`,className:N,value:o.due??``,onChange:c(`due`)})}),(0,A.jsx)(Wt,{label:`時刻（なくても可）`,children:(0,A.jsx)(`input`,{type:`time`,step:`60`,className:N,value:o.time??``,onChange:c(`time`)})}),""",
    )
    text = replaceOne(
        text,
        """(0,A.jsx)(Wt,{label:`そのときの距離（km）`,children:(0,A.jsx)(`input`,{type:`number`,min:`0`,inputMode:`numeric`,className:N,value:u(o.doneKm),onChange:c(`doneKm`),"data-f":`doneKm`})}),""",
        """(0,A.jsx)(Wt,{label:`そのときの距離（km）`,children:(0,A.jsx)(`input`,{type:`number`,min:`0`,inputMode:`numeric`,className:N,value:u(o.doneKm),onChange:c(`doneKm`),"data-f":`doneKm`})}),(0,A.jsx)(Wt,{label:`時刻（なくても可）`,children:(0,A.jsx)(`input`,{type:`time`,step:`60`,className:N,value:o.time??``,onChange:c(`time`)})}),""",
    )
    text = replaceOne(
        text,
        """return(0,A.jsxs)(`div`,{"data-memoview":!0,children:[(0,A.jsxs)(`div`,{className:`mb-4 rounded-xl border border-line bg-card p-3`,children:""",
        """return(0,A.jsxs)(`div`,{"data-memoview":!0,children:[(0,A.jsx)(`div`,{id:`task-cal-host`,className:`mb-4`}),(0,A.jsxs)(`div`,{className:`mb-4 rounded-xl border border-line bg-card p-3`,children:""",
    )
    text = replaceOne(
        text,
        """i===`settings`&&(0,A.jsxs)(`div`,{className:`space-y-3`,children:[(0,A.jsxs)(`div`,{className:`rounded-xl border border-line bg-card p-4`,children:""",
        """i===`settings`&&(0,A.jsxs)(`div`,{className:`space-y-3`,children:[(0,A.jsx)(`div`,{id:`task-summary-host`,className:`mb-3`}),(0,A.jsx)(`div`,{id:`task-ring-host`,className:`mb-3`}),(0,A.jsx)(`div`,{id:`task-voice-host`,className:`mb-3`}),(0,A.jsx)(`div`,{id:`task-ahead-host`,className:`mb-3`}),(0,A.jsxs)(`div`,{className:`rounded-xl border border-line bg-card p-4`,children:""",
    )
    text = replaceOne(
        text,
        """ht=async e=>{if(e)try{let t=JSON.parse(await e.text());if(!lt(t)){x(`このファイルはタスクのバックアップではありません。`);return}""",
        """__taskApplyBackup=(window.__taskApplyBackup=function(text){try{let t=JSON.parse(text);if(!lt(t)){x(`このバックアップは読み取れません。このアプリのファイルか確認してください。`);return!1}r(Et(t,n.tabs,n.memos,n.frequent,n.removed));x(`復元しました`);return!0}catch(err){x(`このバックアップは読み取れません。このアプリのファイルか確認してください。`);return!1}}),__taskDriveRestore=(window.__taskDriveRestore=function(raw){ft(!1);let e=null;try{e=typeof raw==`string`?JSON.parse(raw):raw}catch(err){x(`このバックアップは読み取れません。このアプリのファイルか確認してください。`);return}if(!e||e.ok===!1){x(e&&e.message||`Googleドライブから戻せませんでした。通信状態やGoogleの許可を確認して、もう一度試してください。`);return}if(e.phase===`preview`){let when=e.when||`日時不明`;x(when+` に保存したバックアップです。`);if(window.__taskAskRestore)window.__taskAskRestore(when,function(){ft(!0);x(`バックアップを戻しています…`);try{TaskNative.restoreFromDrive(`download`)}catch(err){ft(!1);x(`Googleドライブから戻せませんでした。通信状態やGoogleの許可を確認して、もう一度試してください。`)}});return}if(e.phase===`content`)window.__taskApplyBackup(e.content)}),ht=async e=>{if(e)try{let t=JSON.parse(await e.text());if(!lt(t)){x(`このファイルはタスクのバックアップではありません。`);return}""",
    )
    text = replaceOne(
        text,
        """onChange:e=>{ht(e.target.files[0]),e.target.value=``}})]})]})]}),""",
        """onChange:e=>{ht(e.target.files[0]),e.target.value=``}})]}),(0,A.jsx)(M,{variant:`outline`,onClick:()=>{if(window.TaskNative&&TaskNative.restoreFromDrive){ft(!0);x(`Googleドライブを確認しています…`);try{TaskNative.restoreFromDrive(`preview`)}catch(err){ft(!1);x(`Googleドライブから戻せませんでした。通信状態やGoogleの許可を確認して、もう一度試してください。`)}}},children:`ドライブから戻す`})]})]}),""",
    )
    text = replaceOne(
        text,
        "pt=async()=>{if(!dt){ft(!0),x(`Googleドライブに保存しています…`);try{let e=await Je(ct(),ut(),/^tasks-backup-/);",
        "pt=async()=>{if(!dt){if(window.TaskNative&&TaskNative.saveToDrive){ft(!0);x(`Googleドライブに保存しています…`);window.__taskDriveDone=function(raw){ft(!1);try{let e=JSON.parse(raw);if(!e.ok){x(e.message||`Googleドライブに保存できませんでした。通信状態やGoogleの許可を確認して、もう一度試してください。だめなときは「バックアップを保存」を使ってください。`);return}x(`Googleドライブの「Grok」に保存しました：`+(e.name||``)+(e.cleaned?`。古いバックアップを`+e.cleaned+`件ゴミ箱へ移しました。`:``)+(e.unverified?`（確認ができなかったため、古いバックアップは削除していません）`:e.cleanErr?`。古いバックアップの整理はできませんでした。`:``))}catch(err){x(`Googleドライブに保存できませんでした。通信状態やGoogleの許可を確認して、もう一度試してください。だめなときは「バックアップを保存」を使ってください。`)}};try{TaskNative.saveToDrive(ct(),ut())}catch(err){ft(!1);x(`Googleドライブに保存できませんでした。通信状態やGoogleの許可を確認して、もう一度試してください。だめなときは「バックアップを保存」を使ってください。`)}return}ft(!0),x(`Googleドライブに保存しています…`);try{let e=await Je(ct(),ut(),/^tasks-backup-/);",
    )
    text = replaceOne(
        text,
        "最新2件を残し、古いものはゴミ箱へ",
        "新しい1件だけを残し、前のファイルはゴミ箱へ",
    )
    text = replaceOne(
        text,
        "mt=async()=>{let e=ct(),t=ut();try{if(typeof File",
        "mt=async()=>{let e=ct(),t=ut();try{if(window.TaskNative&&window.TaskNative.saveBackup){window.TaskNative.saveBackup(e,t);x(`保存先を選んでください`);return}}catch(e){}try{if(typeof File",
    )
    text = replaceOne(
        text,
        "このブラウザにだけ残ります。月に1回はバックアップを保存して、Googleドライブに置いてください。",
        "このアプリの中だけに残ります。月に1回はバックアップを保存してください。時刻を入れたメモは、その時刻に鳴ります。朝のまとめは「控え」で時刻を変えられます。",
    )
    return text
}

fun replaceOne(text: String, old: String, new: String): String {
    val at = text.indexOf(old)
    if (at < 0) error("タスクアプリの更新で、Android用のつなぎ目が見つかりません: ${old.take(72)}")
    if (text.indexOf(old, at + old.length) >= 0) error("つなぎ目が複数あります: ${old.take(72)}")
    return text.replaceRange(at, at + old.length, new)
}
