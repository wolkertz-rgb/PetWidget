package com.sting.petwidget

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.PromptTemplates
import java.io.File
import java.net.URL
import java.security.MessageDigest

/** Сеть нужна только для явной загрузки весов; inference полностью локальный. */
internal object MindModel {
    @Volatile private var activeSession: LlmInferenceSession? = null
    const val SIZE = 546660344L
    private const val SHA = "e608953f169aeb1bd7b9155fec2559825e08453fc209b84eda3a781ed0452fd2"
    private const val MODEL_URL = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"
    fun file(c: Context) = File(c.noBackupFilesDir,"pet-mind-qwen.task")
    @Volatile var downloading = false; private set
    @Volatile var progress = 0; private set
    @Volatile var downloadError = ""; private set
    @Synchronized fun download(c: Context) {
        if (downloading || file(c).exists()) return
        downloading=true;progress=0;downloadError=""
        val app=c.applicationContext
        Thread({
            val temp=File(app.noBackupFilesDir,"pet-mind.download")
            var connection: java.net.HttpURLConnection?=null
            try {
                check(app.noBackupFilesDir.usableSpace>SIZE*2) {"Нужно 1.1 ГБ свободного места"}
                val conn=URL(MODEL_URL).openConnection() as java.net.HttpURLConnection
                connection=conn;conn.connectTimeout=30_000;conn.readTimeout=60_000
                check(conn.responseCode==200) {"Ошибка загрузки: "+conn.responseCode}
                val digest=MessageDigest.getInstance("SHA-256")
                var total=0L
                conn.inputStream.use { input -> temp.outputStream().use { output ->
                    val buffer=ByteArray(64*1024)
                    while(true) {
                        val read=input.read(buffer);if(read<0)break
                        total+=read;check(total<=SIZE) {"Неверный размер модели"}
                        digest.update(buffer,0,read);output.write(buffer,0,read)
                        progress=(total*100/SIZE).toInt()
                    }
                } }
                val hash=digest.digest().joinToString("") {"%02x".format(it)}
                check(total==SIZE && hash==SHA) {"Проверка модели не пройдена"}
                check(temp.renameTo(file(app))) {"Не удалось сохранить модель"}
            } catch(e: Exception) { downloadError=e.message?:"Ошибка загрузки" }
            finally { connection?.disconnect();downloading=false }
        },"pet-model-download").start()
    }
    fun open(c: Context): LlmInference = LlmInference.createFromOptions(c,
        LlmInference.LlmInferenceOptions.builder().setModelPath(file(c).path)
            .setMaxTokens(1024).setPreferredBackend(LlmInference.Backend.CPU).build())
    fun cancel() {
        try { activeSession?.cancelGenerateResponseAsync() } catch (_: Exception) { }
    }
    fun infer(engine: LlmInference,prompt: String, allowed: () -> Boolean): String =
        LlmInferenceSession.createFromOptions(engine,
            LlmInferenceSession.LlmInferenceSessionOptions.builder()
                // ChatML уже включён в prompt: библиотека не должна оборачивать его повторно.
                .setPromptTemplates(PromptTemplates.builder().build())
                .setTopK(32).setTemperature(.75f).setRandomSeed(kotlin.random.Random.nextInt()).build()).use {
            check(allowed()) {"Вычисление отменено"}
            it.addQueryChunk(prompt)
            check(allowed()) {"Вычисление отменено"}
            activeSession=it
            try {
                val response=it.generateResponseAsync()
                if(!allowed())it.cancelGenerateResponseAsync()
                try { response.get(30,java.util.concurrent.TimeUnit.SECONDS) }
                catch(e: java.util.concurrent.TimeoutException) {
                    it.cancelGenerateResponseAsync()
                    // Дождаться подтверждения отмены перед закрытием native-сессии.
                    try {response.get()}catch(_: Exception){}
                    throw IllegalStateException("Превышено время выбора занятия",e)
                }
            } finally { activeSession=null }
        }
}
