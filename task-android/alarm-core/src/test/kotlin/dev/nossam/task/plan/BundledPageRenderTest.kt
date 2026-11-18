package dev.nossam.task.plan

import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BundledPageRenderTest {
    @Test(timeout = 20000)
    fun bundledPageRendersOnFirstLoadWithoutJavaScriptErrors() {
        val web = File(repoRoot(), "task-app")
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val raw = exchange.requestURI.path.let { if (it == "/") "/index.html" else it }
            val file = File(web, raw.removePrefix("/")).canonicalFile
            if (!file.path.startsWith(web.canonicalPath) || !file.isFile) {
                exchange.sendResponseHeaders(404, -1)
                exchange.close()
                return@createContext
            }
            val bytes = file.readBytes()
            exchange.responseHeaders.add("Content-Type", contentType(file))
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        val profile = Files.createTempDirectory("task-chrome").toFile()
        val chrome = ProcessBuilder(
            chromeBinary(),
            "--headless=new",
            "--no-sandbox",
            "--disable-gpu",
            "--disable-dev-shm-usage",
            "--remote-debugging-port=0",
            "--user-data-dir=${profile.absolutePath}",
            "about:blank",
        ).redirectError(ProcessBuilder.Redirect.DISCARD)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()
        try {
            val port = waitForPort(File(profile, "DevToolsActivePort"))
            val pageSocket = pageSocket(port)
            val session = DevTools(pageSocket)
            session.open()
            session.send(1, "Runtime.enable")
            session.send(2, "Page.enable")
            session.send(3, "Page.navigate", """{"url":"http://127.0.0.1:${server.address.port}/"}""")
            assertTrue(session.await("Page.loadEventFired", 8))
            Thread.sleep(400)
            session.send(
                4,
                "Runtime.evaluate",
                """{"expression":"document.body.innerText","returnByValue":true}""",
            )
            val text = session.awaitResult(4, 5)
            assertTrue(text, text.contains("日常"))
            assertTrue(text, text.contains("メモ"))
            assertTrue(text, text.contains("やることはありません"))
            val failures = session.messages.filter {
                it.contains("Runtime.exceptionThrown") || it.contains("e.split is not a function")
            }
            assertTrue(failures.joinToString("\n"), failures.isEmpty())
            assertFalse(text.contains("TypeError"))
        } finally {
            chrome.descendants().forEach { it.destroyForcibly() }
            chrome.destroyForcibly()
            server.stop(0)
            profile.deleteRecursively()
        }
    }

    private fun contentType(file: File) = when (file.extension) {
        "html" -> "text/html; charset=utf-8"
        "js" -> "text/javascript; charset=utf-8"
        "webmanifest" -> "application/manifest+json"
        "png" -> "image/png"
        else -> "application/octet-stream"
    }

    private fun chromeBinary(): String {
        val named = System.getenv("CHROME_PATH")
        val candidates = listOfNotNull(
            named,
            "/usr/local/bin/google-chrome",
            "/usr/bin/google-chrome",
            "/usr/bin/google-chrome-stable",
            "/usr/bin/chromium",
            "/usr/bin/chromium-browser",
        )
        candidates.firstOrNull { File(it).canExecute() }?.let { return it }
        val found = ProcessBuilder("which", "google-chrome")
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
            .inputStream.bufferedReader().readText().trim()
        check(found.isNotEmpty() && File(found).canExecute()) {
            "Chrome is required to render task-app/index.html"
        }
        return found
    }

    private fun waitForPort(file: File): Int {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
        while (System.nanoTime() < deadline) {
            if (file.isFile) {
                val port = file.readText().lineSequence().firstOrNull()?.toIntOrNull()
                if (port != null) return port
            }
            Thread.sleep(50)
        }
        error("Chrome did not open a DevTools port")
    }

    private fun pageSocket(port: Int): String {
        val client = HttpClient.newHttpClient()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            val body = client.send(
                java.net.http.HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/json/list")).build(),
                java.net.http.HttpResponse.BodyHandlers.ofString(),
            ).body()
            val match = Regex(""""webSocketDebuggerUrl"\s*:\s*"(ws://[^"]+)"""").find(body)
            if (match != null) return match.groupValues[1]
            Thread.sleep(50)
        }
        error("Chrome did not expose a page socket")
    }

    private fun repoRoot(): File {
        var dir = File("").absoluteFile
        repeat(6) {
            if (File(dir, "task-app/index.html").isFile) return dir
            dir = dir.parentFile ?: return dir
        }
        return dir
    }

    private class DevTools(private val url: String) : WebSocket.Listener {
        private val client = HttpClient.newHttpClient()
        private val opened = CountDownLatch(1)
        private val buffer = StringBuilder()
        val messages = CopyOnWriteArrayList<String>()
        private lateinit var socket: WebSocket

        fun open() {
            socket = client.newWebSocketBuilder().buildAsync(URI.create(url), this).join()
            check(opened.await(5, TimeUnit.SECONDS)) { "DevTools socket did not open" }
        }

        fun send(id: Int, method: String, params: String = "{}") {
            socket.sendText("""{"id":$id,"method":"$method","params":$params}""", true).join()
        }

        fun await(fragment: String, seconds: Long): Boolean {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
            while (System.nanoTime() < deadline) {
                if (messages.any { it.contains(fragment) }) return true
                Thread.sleep(25)
            }
            return false
        }

        fun awaitResult(id: Int, seconds: Long): String {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
            val marker = """"id":$id"""
            while (System.nanoTime() < deadline) {
                val message = messages.lastOrNull { it.contains(marker) && it.contains(""""value":"""") }
                if (message != null) return jsonString(message)
                Thread.sleep(25)
            }
            error("No evaluate result for id $id")
        }

        private fun jsonString(message: String): String {
            val key = """"value":""""
            val start = message.indexOf(key)
            check(start >= 0) { message }
            val chars = StringBuilder()
            var i = start + key.length
            while (i < message.length) {
                val c = message[i]
                if (c == '\\') {
                    when (val n = message[++i]) {
                        'n' -> chars.append('\n')
                        'r' -> chars.append('\r')
                        't' -> chars.append('\t')
                        '"' -> chars.append('"')
                        '\\' -> chars.append('\\')
                        'u' -> {
                            chars.append(message.substring(i + 1, i + 5).toInt(16).toChar())
                            i += 4
                        }
                        else -> chars.append(n)
                    }
                } else if (c == '"') {
                    break
                } else {
                    chars.append(c)
                }
                i++
            }
            return chars.toString()
        }

        override fun onOpen(webSocket: WebSocket) {
            opened.countDown()
            webSocket.request(1)
        }

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*> {
            buffer.append(data)
            if (last) {
                messages.add(buffer.toString())
                buffer.setLength(0)
            }
            webSocket.request(1)
            return CompletableFuture.completedFuture(null)
        }
    }
}
