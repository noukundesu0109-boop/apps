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
        versionCode = 2
        versionName = "1.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
        check(!index.contains("serviceWorker.register")) { "service worker registration still present" }
    }
}

android.sourceSets.getByName("main").assets.srcDir(generatedWeb)
tasks.configureEach {
    if (name.startsWith("merge") && name.endsWith("Assets")) {
        dependsOn(prepareWeb)
    }
}

dependencies {
    implementation(project(":alarm-core"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.activity:activity-ktx:1.9.3")
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
        """function wt(e,t){return{id:String(e?.id??`m${'$'}{t}-${'$'}{Date.now()}`),text:String(e?.text??``).trim(),due:ht(e?.due),time:/^\d{2}:\d{2}$/.test(e?.time)?e.time:null,done:ht(e?.done),at:Number.isFinite(e?.at)?e.at:0}}""",
    )
    text = replaceOne(
        text,
        """D=(e,t,i)=>{Se(n=>[...n,{id:`m${'$'}{Date.now()}`,text:e,due:t,done:null,at:Date.now()}]);""",
        """D=(e,t,i,tm)=>{let time=/^\d{2}:\d{2}$/.test(tm)?tm:null;Se(n=>[...n,{id:`m${'$'}{Date.now()}`,text:e,due:t,time,done:null,at:Date.now()}]);""",
    )
    text = replaceOne(
        text,
        """function en({memos:e,today:t,onAdd:n,onToggle:r,onEdit:i,frequent:a,onEditFreq:o}){let[s,c]=(0,l.useState)(``),[u,d]=(0,l.useState)(null),[f,p]=(0,l.useState)(!1),m=(0,l.useRef)(null),h=()=>{let e=s.trim();e&&(n(e,u),c(``),d(null)),m.current?.focus()}""",
        """function en({memos:e,today:t,onAdd:n,onToggle:r,onEdit:i,frequent:a,onEditFreq:o}){let[s,c]=(0,l.useState)(``),[u,d]=(0,l.useState)(null),[tm,setTm]=(0,l.useState)(``),[f,p]=(0,l.useState)(!1),m=(0,l.useRef)(null);window.__taskSetMemoDate=function(v){d(v)};let h=()=>{let e=s.trim();e&&(n(e,u,!1,tm),c(``),d(null),setTm(``)),m.current?.focus()}""",
    )
    text = replaceOne(
        text,
        """onClick:()=>{n(e.text,u,!0),d(null)}""",
        """onClick:()=>{n(e.text,u,!0,tm),d(null),setTm(``)}""",
    )
    text = replaceOne(
        text,
        """(0,A.jsx)(`div`,{className:`mt-2`,children:(0,A.jsx)(Yt,{value:u,onChange:d,today:t,name:`add`})}),""",
        """(0,A.jsx)(`div`,{className:`mt-2`,children:(0,A.jsx)(Yt,{value:u,onChange:d,today:t,name:`add`})}),(0,A.jsxs)(`label`,{className:`mt-2 flex items-center gap-2 text-sm text-muted`,children:[`時刻`,(0,A.jsx)(`input`,{type:`time`,step:`60`,"aria-label":`時刻（なくても可）`,"data-memo-time":!0,className:`${'$'}{N} w-32`,value:tm,onChange:e=>setTm(e.target.value)}),`空なら鳴りません`]}),""",
    )
    text = replaceOne(
        text,
        """function Qt({memo:e,today:t,onSave:n,onDelete:r,onClose:i}){let[a,o]=(0,l.useState)(e.text),[s,c]=(0,l.useState)(e.due),u=()=>{let t=a.trim();t?n({...e,text:t,due:s}):alert(`内容を入れてください`)};""",
        """function Qt({memo:e,today:t,onSave:n,onDelete:r,onClose:i}){let[a,o]=(0,l.useState)(e.text),[s,c]=(0,l.useState)(e.due),[tm,setTm]=(0,l.useState)(e.time||``),u=()=>{let t=a.trim();t?n({...e,text:t,due:s,time:/^\d{2}:\d{2}$/.test(tm)?tm:null}):alert(`内容を入れてください`)};""",
    )
    text = replaceOne(
        text,
        """(0,A.jsxs)(`div`,{className:`mb-4`,children:[(0,A.jsx)(`span`,{className:`mb-1 block text-sm text-muted`,children:`日付`}),(0,A.jsx)(Yt,{value:s,onChange:c,today:t,name:`edit`})]}),""",
        """(0,A.jsxs)(`div`,{className:`mb-4`,children:[(0,A.jsx)(`span`,{className:`mb-1 block text-sm text-muted`,children:`日付`}),(0,A.jsx)(Yt,{value:s,onChange:c,today:t,name:`edit`})]}),(0,A.jsxs)(`label`,{className:`mb-4 flex items-center gap-2 text-sm text-muted`,children:[`時刻`,(0,A.jsx)(`input`,{type:`time`,step:`60`,"aria-label":`時刻（なくても可）`,"data-memo-time":!0,className:`${'$'}{N} w-32`,value:tm,onChange:e=>setTm(e.target.value)})]}),""",
    )
    text = replaceOne(
        text,
        """function Zt({m:e,g:t,onToggle:n,onEdit:r}){let i=!!e.done,a=i?`完了 ${'$'}{St(e.done)}`:t===`over`||t===`later`?St(e.due):``;""",
        """function Zt({m:e,g:t,onToggle:n,onEdit:r}){let i=!!e.done,a=i?`完了 ${'$'}{St(e.done)}`:((t===`over`||t===`later`?St(e.due):``)+(e.time?` ${'$'}{e.time}`:``)).trim();""",
    )
    text = replaceOne(
        text,
        """(0,l.useEffect)(()=>{try{localStorage.setItem(Ze,JSON.stringify(n))}catch{}},[n])""",
        """(0,l.useEffect)(()=>{try{localStorage.setItem(Ze,JSON.stringify(n))}catch(e){}try{window.__taskGet=function(){return n};window.__taskSet=r;window.__taskSetTab=a;window.__taskOpen=v}catch(e){}try{window.TaskNative&&window.TaskNative.onState(JSON.stringify(n))}catch(e){}try{window.__taskOnData&&window.__taskOnData(n)}catch(e){}},[n])""",
    )
    text = replaceOne(
        text,
        """return(0,A.jsxs)(`div`,{"data-memoview":!0,children:[(0,A.jsxs)(`div`,{className:`mb-4 rounded-xl border border-line bg-card p-3`,children:""",
        """return(0,A.jsxs)(`div`,{"data-memoview":!0,children:[(0,A.jsx)(`div`,{id:`task-cal-host`,className:`mb-4`}),(0,A.jsxs)(`div`,{className:`mb-4 rounded-xl border border-line bg-card p-3`,children:""",
    )
    text = replaceOne(
        text,
        """i===`settings`&&(0,A.jsxs)(`div`,{className:`space-y-3`,children:[(0,A.jsxs)(`div`,{className:`rounded-xl border border-line bg-card p-4`,children:""",
        """i===`settings`&&(0,A.jsxs)(`div`,{className:`space-y-3`,children:[(0,A.jsx)(`div`,{id:`task-summary-host`,className:`mb-3`}),(0,A.jsxs)(`div`,{className:`rounded-xl border border-line bg-card p-4`,children:""",
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
