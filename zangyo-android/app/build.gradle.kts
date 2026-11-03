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
        versionCode = 2
        versionName = "1.0.1"
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
        """(0,l.useEffect)(()=>{try{localStorage.setItem(Ge,JSON.stringify(n))}catch{}},[n])""",
        """(0,l.useEffect)(()=>{try{localStorage.setItem(Ge,JSON.stringify(n))}catch(e){}try{window.__zangyoGet=function(){return n};window.__zangyoSet=r;window.__zangyoSetTab=a}catch(e){}try{window.ZangyoNative&&window.ZangyoNative.onState(JSON.stringify(n))}catch(e){}try{window.__zangyoOnData&&window.__zangyoOnData(n)}catch(e){}},[n])""",
    )
    text = replaceOne(
        text,
        "fe=()=>JSON.stringify({...n,app:`zangyo`,savedAt:new Date().toISOString()},null,2)+`\n`",
        "fe=()=>JSON.stringify({...n,app:`zangyo`,savedAt:new Date().toISOString(),reminders:window.__zangyoExportReminders?window.__zangyoExportReminders():void 0},null,2)+`\n`",
    )
    text = replaceOne(
        text,
        "ve=async()=>{let e=ue(),t=fe();try{if(typeof File",
        "ve=async()=>{let e=ue(),t=fe();try{if(window.ZangyoNative&&window.ZangyoNative.saveBackup){window.ZangyoNative.saveBackup(e,t);p(`保存先を選んでください`);return}}catch(e){}try{if(typeof File",
    )
    text = replaceOne(
        text,
        "p(n?`復元しました`:`復元しました（勤務カレンダーは今のまま）`)}catch",
        "p(n?`復元しました`:`復元しました（勤務カレンダーは今のまま）`);try{window.__zangyoImportReminders&&window.__zangyoImportReminders(t.reminders)}catch(e){}}catch",
    )
    text = replaceOne(
        text,
        "データはこのブラウザにだけ残ります。月に1回はバックアップを保存してください。",
        "データはこのアプリの中だけに残ります。月に1回はバックアップを保存してください。鳴る時刻は「アラーム」、単発の予定は「リマインド」です。",
    )
    return text
}

private fun replaceOne(text: String, old: String, new: String): String {
    val at = text.indexOf(old)
    if (at < 0) error("就業アプリの更新で、Android用のつなぎ目が見つかりません: ${old.take(72)}")
    if (text.indexOf(old, at + old.length) >= 0) error("つなぎ目が複数あります: ${old.take(72)}")
    return text.replaceRange(at, at + old.length, new)
}
