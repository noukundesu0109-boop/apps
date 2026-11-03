import java.io.File

plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "dev.nossam.zangyo"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.nossam.zangyo"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "1.0.7"
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

val generatedWeb = layout.buildDirectory.dir("generated/zangyoWeb")

val prepareWeb = tasks.register("prepareZangyoWeb") {
    val web = rootProject.layout.projectDirectory.dir("../zangyo-app")
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
        check(index.contains("ZangyoNative.onState")) { "state hook was not injected" }
        check(index.contains("za-day-alarm")) { "per-day alarm host was not injected" }
        check(index.contains("dayAlarms:da")) { "day alarm backup field was not kept" }
        check(index.contains("earlyWeekdays:ew")) { "early weekday setting was not kept" }
        check(index.contains("enabled:e.enabled===!1")) { "alarm on/off was not kept" }
        check(index.contains("__zangyoDriveDone")) { "drive save was not switched to Android sign-in" }
        check(index.contains("新しい1件だけを残し")) { "drive keep-one text was not updated" }
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
        """(0,l.useEffect)(()=>{try{localStorage.setItem(Ge,JSON.stringify(n))}catch{}},[n])""",
        """(0,l.useEffect)(()=>{try{localStorage.setItem(Ge,JSON.stringify(n))}catch(e){}try{window.__zangyoGet=function(){return n};window.__zangyoSet=r;window.__zangyoSetTab=a}catch(e){}try{window.ZangyoNative&&window.ZangyoNative.onState(JSON.stringify(n))}catch(e){}try{window.__zangyoOnData&&window.__zangyoOnData(n)}catch(e){}},[n])""",
    )
    text = replaceOne(
        text,
        "_e=async()=>{if(!me){ge(!0),p(`Googleドライブに保存しています…`);try{let e=await Ie(ue(),fe(),/^zangyo-backup-/);",
        "_e=async()=>{if(!me){if(window.ZangyoNative&&ZangyoNative.saveToDrive){ge(!0);p(`Googleドライブに保存しています…`);window.__zangyoDriveDone=function(raw){ge(!1);try{let e=JSON.parse(raw);if(!e.ok){p(e.message||`Googleドライブに保存できませんでした。通信状態やGoogleの許可を確認して、もう一度試してください。だめなときは「バックアップを保存」を使ってください。`);return}p(`Googleドライブの「Grok」に保存しました：`+(e.name||``)+(e.cleaned?`。古いバックアップを`+e.cleaned+`件ゴミ箱へ移しました。`:``)+(e.unverified?`（確認ができなかったため、古いバックアップは削除していません）`:e.cleanErr?`。古いバックアップの整理はできませんでした。`:``))}catch(err){p(`Googleドライブに保存できませんでした。通信状態やGoogleの許可を確認して、もう一度試してください。だめなときは「バックアップを保存」を使ってください。`)}};try{ZangyoNative.saveToDrive(ue(),fe())}catch(err){ge(!1);p(`Googleドライブに保存できませんでした。通信状態やGoogleの許可を確認して、もう一度試してください。だめなときは「バックアップを保存」を使ってください。`)}return}ge(!0),p(`Googleドライブに保存しています…`);try{let e=await Ie(ue(),fe(),/^zangyo-backup-/);",
    )
    text = replaceOne(
        text,
        "最新2件を残し、古いものはゴミ箱へ",
        "新しい1件だけを残し、前のファイルはゴミ箱へ",
    )
    text = replaceOne(
        text,
        "ve=async()=>{let e=ue(),t=fe();try{if(typeof File",
        "ve=async()=>{let e=ue(),t=fe();try{if(window.ZangyoNative&&window.ZangyoNative.saveBackup){window.ZangyoNative.saveBackup(e,t);p(`保存先を選んでください`);return}}catch(e){}try{if(typeof File",
    )
    text = replaceOne(
        text,
        "データはこのブラウザにだけ残ります。月に1回はバックアップを保存してください。",
        "データはこのアプリの中だけに残ります。月に1回はバックアップを保存してください。鳴る時刻は「アラーム」です。",
    )
    text = replaceOne(
        text,
        """dt=e=>{let t={};for(let[n]of ut)t[n]=e&&typeof e==`object`&&Array.isArray(e[n])?e[n].filter(e=>e&&typeof e==`object`).map(e=>({label:String(e.label??``),time:/^\d{2}:\d{2}$/.test(e.time)?e.time:``})):nt(lt[n]);return t}""",
        """dt=e=>{let t={},keys=[`early`,`day`,`night`,`off`,`leave`];for(let n of keys){let fallback=n===`off`||n===`leave`?[]:nt(lt[n]).map(e=>({...e,enabled:!0}));t[n]=e&&typeof e==`object`&&Array.isArray(e[n])?e[n].filter(e=>e&&typeof e==`object`).map(e=>({label:String(e.label??``),time:/^\d{2}:\d{2}$/.test(e.time)?e.time:``,enabled:e.enabled===!1?!1:!0})):fallback}return t}""",
    )
    text = replaceOne(
        text,
        """mt=e=>{let t=pt(e);return{...t,early:ft(t.early).filter(e=>t.shifts[e]===`day`),alarms:dt(t.alarms)}}""",
        """mt=e=>{let t=pt(e),da={};if(t.dayAlarms&&typeof t.dayAlarms==`object`&&!Array.isArray(t.dayAlarms))for(let k of Object.keys(t.dayAlarms)){if(!ct.test(k))continue;let v=t.dayAlarms[k];if(!v||typeof v!=`object`)continue;if(v.mute){da[k]={mute:!0,times:[]};continue}let times=Array.isArray(v.times)?v.times.filter(e=>e&&typeof e==`object`).map(e=>({label:String(e.label??``),time:/^\d{2}:\d{2}$/.test(e.time)?e.time:``,enabled:e.enabled===!1?!1:!0})):[] ;if(times.length)da[k]={mute:!1,times}}let ew=Array.isArray(t.earlyWeekdays)?[...new Set(t.earlyWeekdays.map(Number).filter(n=>n>=1&&n<=7))].sort((a,b)=>a-b):[],es=ft(t.earlySkip||[]);return{...t,early:ft(t.early).filter(e=>t.shifts[e]===`day`),alarms:dt(t.alarms),dayAlarms:da,earlyWeekdays:ew,earlySkip:es}}""",
    )
    text = replaceOne(
        text,
        """(0,A.jsx)(`p`,{className:`mt-4 mb-1 text-sm font-semibold`,children:`時間の記録`}),""",
        """(0,A.jsx)(`div`,{id:`za-day-alarm`,"data-day":x}),(0,A.jsx)(`p`,{className:`mt-4 mb-1 text-sm font-semibold`,children:`時間の記録`}),""",
    )
    text = replaceOne(
        text,
        """v=(0,l.useMemo)(()=>new Set(n.early),[n.early]),y=e=>r(t=>({...t,early:t.early.includes(e)?t.early.filter(t=>t!==e):[...t.early,e].sort()}))""",
        """v=(0,l.useMemo)(()=>{let s=new Set(n.early||[]),k=new Set(n.earlySkip||[]),w=new Set(n.earlyWeekdays||[]);return{has(e){if(k.has(e))return!1;if(s.has(e))return!0;if(!w.size||(n.shifts||{})[e]!==`day`)return!1;let[t,r,i]=e.split(`-`).map(Number),a=new Date(Date.UTC(t,r-1,i)).getUTCDay();return w.has(a===0?7:a)}}},[n.early,n.earlySkip,n.earlyWeekdays,n.shifts]),y=e=>r(t=>{let w=new Set(t.earlyWeekdays||[]),[yy,mm,dd]=e.split(`-`).map(Number),a=new Date(Date.UTC(yy,mm-1,dd)).getUTCDay(),wd=a===0?7:a,auto=t.shifts[e]===`day`&&w.has(wd),on=!(t.earlySkip||[]).includes(e)&&((t.early||[]).includes(e)||auto);return on?{...t,early:(t.early||[]).filter(n=>n!==e),earlySkip:auto?[...new Set([...(t.earlySkip||[]),e])].sort():(t.earlySkip||[]).filter(n=>n!==e)}:{...t,early:[...new Set([...(t.early||[]),e])].sort(),earlySkip:(t.earlySkip||[]).filter(n=>n!==e)}})""",
    )
    text = moveLeaveSettings(text)
    return text
}

fun moveLeaveSettings(text: String): String {
    val cardStartMarker = "(0,A.jsxs)(yt,{className:`space-y-2 text-sm`,children:[(0,A.jsx)(`h2`,{className:`font-semibold`,children:`年休の設定`})"
    val backupMarker = ",(0,A.jsxs)(yt,{children:[(0,A.jsx)(`h2`,{className:`mb-1 font-semibold`,children:`バックアップ`})"
    val start = text.indexOf(cardStartMarker)
    if (start < 0) error("年休の設定が見つかりません")
    val end = text.indexOf(backupMarker, start)
    if (end < 0) error("バックアップの直前が見つかりません")
    val card = text.substring(start, end)
    val withoutCard = text.removeRange(start, end + 1)
    val anchor = "設定は「控え」タブで直せます。`]})]}),"
    val at = withoutCard.indexOf(anchor)
    if (at < 0 || withoutCard.indexOf(anchor, at + anchor.length) >= 0) error("年休タブの挿入位置が見つかりません")
    val insertion = "設定はこの画面で直せます。" + "`]})" + "," + card + "]})" + ","
    return withoutCard.replaceRange(at, at + anchor.length, insertion)
}

private fun replaceOne(text: String, old: String, new: String): String {
    val at = text.indexOf(old)
    if (at < 0) error("就業アプリの更新で、Android用のつなぎ目が見つかりません: ${old.take(72)}")
    if (text.indexOf(old, at + old.length) >= 0) error("つなぎ目が複数あります: ${old.take(72)}")
    return text.replaceRange(at, at + old.length, new)
}
