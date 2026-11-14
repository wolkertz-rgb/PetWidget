package com.sting.petwidget

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.Context
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Настройка локального мозга, явная загрузка модели и сообщения владельца. */
class MindActivity : Activity() {
    private val handler=Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private val prefs by lazy {getSharedPreferences("pet_state",Context.MODE_PRIVATE)}
    private val ticker=object: Runnable {
        override fun run() {
            val ready=MindModel.file(this@MindActivity).exists()
            status.text=when {
                MindModel.downloading -> "Загрузка модели: "+MindModel.progress+"%"
                MindModel.downloadError.isNotEmpty() -> MindModel.downloadError
                !ready -> "Модель пока не загружена"
                !prefs.getBoolean("mind_enabled",false) -> "Модель готова. Нейросетевой режим выключен.\n"+prefs.getString("mind_status","")
                else -> prefs.getString("mind_status","Мозг готов")
            }+"\n\nПлан: "+prefs.getString("mind_plan","пока не выбран")+
                "\n\nПоследние события:\n"+prefs.getString("mind_memories","пока нет")
            handler.postDelayed(this,1000)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dp=resources.displayMetrics.density
        val body=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding((20*dp).toInt(),(28*dp).toInt(),(20*dp).toInt(),(30*dp).toInt())
            setBackgroundColor(0xFFF3F7F3.toInt())
        }
        setContentView(ScrollView(this).apply {addView(body)})
        fun text(s: String,size: Float=15f)=TextView(this).apply {text=s;textSize=size;setTextColor(0xFF263D34.toInt());setPadding(0,12,0,12)}
        fun button(label: String,action: ()->Unit) {
            body.addView(Button(this).apply {text=label;isAllCaps=false;setOnClickListener {action()} })
        }
        body.addView(text("Локальный мозг Petey",25f))
        body.addView(text("Экспериментальный режим Qwen2.5 0.5B. Один раз скачай около 522 МиБ по Wi-Fi, оставив этот экран открытым до завершения. Затем решения и реплики работают без интернета. Подготовь 1.1 ГБ свободного места. Скорость зависит от телефона."))
        button("Загрузить модель") {MindModel.download(this)}
        button("Включить нейросетевой режим") {
            if(!MindModel.file(this).exists())Toast.makeText(this,"Сначала дождись загрузки модели",Toast.LENGTH_SHORT).show()
            else {prefs.edit().putBoolean("mind_enabled",true).putString("mind_status","Ждёт свободного момента в виджете").apply();PetWidgetProvider.tryStartService(this)}
        }
        button("Вернуть обычное поведение") {prefs.edit().putBoolean("mind_enabled",false).putString("mind_status","Выбрано обычное поведение").apply()}
        val message=EditText(this).apply {hint="Что сказать пету?";maxLines=3}
        body.addView(message)
        button("Сказать пету") {
            val value=message.text.toString().trim().take(180)
            if(value.isNotEmpty() && !prefs.getBoolean("mind_enabled",false)) {
                Toast.makeText(this,"Сначала загрузи модель и включи нейросетевой режим",Toast.LENGTH_LONG).show()
            } else if(value.isNotEmpty()) {
                prefs.edit().putString("mind_message",value).putLong("mind_message_id",System.currentTimeMillis()).apply()
                PetWidgetProvider.tryStartService(this);message.text.clear()
                Toast.makeText(this,"Сообщение передано. Пет ответит, когда освободится.",Toast.LENGTH_LONG).show()
            }
        }
        status=text("");body.addView(status)
        body.addView(text("Память и сообщения хранятся на телефоне. Облачного сервера и ключа API нет. Модель может ошибаться и повторяться. Если режим не запускается, выключи его и пришли текст статуса."))
        button("Назад") {finish()}
    }
    override fun onResume(){super.onResume();handler.post(ticker)}
    override fun onPause(){handler.removeCallbacks(ticker);super.onPause()}
}
