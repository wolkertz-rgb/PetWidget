package com.sting.petwidget

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Нейросеть выбирает намерение; непрерывность жизни обеспечивает автономный слой. */
internal class LocalMind(context: Context) : AutoCloseable {
    private val app=context.applicationContext
    private val prefs=app.getSharedPreferences("pet_state",Context.MODE_PRIVATE)
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private val busy=AtomicBoolean(false)
    private var engine: LlmInference?=null
    @Volatile private var closed=false
    @Volatile private var revision=0L
    private var retryAt=0L
    private var messageId=prefs.getLong("mind_processed_message_id",0L)
    private var previouslyEnabled=false
    private var previousState=PetSim.State.IDLE
    private var energy=prefs.getFloat("mind_energy",.8f)
    private var social=prefs.getFloat("mind_social",.5f)
    private var curiosity=prefs.getFloat("mind_curiosity",.7f)
    private var pending: Decision?=null
    private var lastSpeech=0L
    private val recent=ArrayDeque<String>()
    private val memories=ArrayDeque<String>()
    private data class Choice(val action: String,val target: String="",val label: String)
    private data class Decision(val choice: Choice,val yard: Boolean,val version: Long,val message: Long,val expires: Long)
    init {
        prefs.getString("mind_memories","")?.lines()?.filter {it.isNotBlank()}?.takeLast(10)?.forEach {memories.addLast(it)}
        if(!prefs.contains("mind_personality"))prefs.edit().putString("mind_personality",
            listOf("gentle and playful","calm and affectionate","adventurous and curious").random()).apply()
    }
    private fun remember(event: String) {
        memories.addLast(event.take(100));while(memories.size>10)memories.removeFirst()
        prefs.edit().putString("mind_memories",memories.joinToString("\n")).apply()
    }
    fun notice(event: String) {
        interrupt();remember(event);social=(social-.2f).coerceAtLeast(0f);retryAt=0
    }
    fun interrupt() { revision++;pending=null;MindModel.cancel() }
    private fun available(version: Long)=!closed && revision==version && prefs.getBoolean("mind_enabled",false) &&
        (app.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isInteractive
    private fun choices(sim: PetSim): List<Choice> = buildList {
        sim.items.filter {it.ageMs>=0 && (!it.used || it.def.kind==ItemDefs.Kind.FOOD)}.forEach {
            val eat=it.def.kind==ItemDefs.Kind.FOOD
            add(Choice(if(eat)"EAT" else "PLAY",it.defId,if(eat)"Поесть: ${it.def.name}" else "Поиграть: ${it.def.name}"))
        }
        add(Choice("WALK",label="Прогуляться"));add(Choice("LOOK",label="Осмотреться"))
        add(Choice("STRETCH",label="Потянуться"));add(Choice("WAVE",label="Помахать хозяину"))
        add(Choice("ROLL",label="Покувыркаться"));add(Choice("REST",label="Отдохнуть"))
        add(Choice("TRAVEL",label=if(sim.outing.yard)"Вернуться домой" else "Выйти во двор"))
        if(sim.outing.yard)add(Choice("SWING",label="Покачаться на качелях"))
        else add(Choice("LIGHT",label="Переключить свет"))
    }
    private fun key(c: Choice)="${c.action}:${c.target}"
    private fun autonomous(sim: PetSim): Choice {
        val options=choices(sim)
        options.firstOrNull {it.action=="EAT" && sim.hunger>55}?.let {return it}
        if(energy<.25f)return options.first {it.action=="REST"}
        return options.maxBy { c ->
            val interest=when(c.action){"PLAY"->3f+curiosity;"EAT"->sim.hunger/30f;"WAVE"->social*3;"REST"->(1-energy)*2;"LIGHT"->.2f;else->1f}
            interest+kotlin.random.Random.nextFloat()*2-recent.count {it==key(c)}*3
        }
    }
    private fun execute(sim: PetSim,c: Choice,model: Boolean): Boolean {
        if(!sim.performIntention(c.action,c.target))return false
        recent.addLast(key(c));while(recent.size>5)recent.removeFirst()
        remember(c.label)
        prefs.edit().putString("mind_plan",c.label).putString("mind_source",if(model)"Нейросеть" else "Самостоятельное поведение")
            .putFloat("mind_energy",energy).putFloat("mind_social",social).putFloat("mind_curiosity",curiosity).apply()
        if(c.action=="PLAY"){curiosity=(curiosity-.15f).coerceAtLeast(0f);prefs.edit().putString("mind_favourite",c.target).apply()}
        if(c.action=="WAVE")social=(social-.3f).coerceAtLeast(0f)
        val now=SystemClock.uptimeMillis()
        if(now-lastSpeech>25_000 && (model || c.action in listOf("EAT","PLAY","WAVE"))) {
            // Реплики привязаны к фактическому действию, а не к ненадёжному тексту модели.
            sim.speech=when(c.action){
                "EAT"->listOf("Кажется, пора перекусить!","О, что-то вкусное!").random()
                "PLAY"->listOf("Поиграю немного!","Интересно, что умеет эта штука?").random()
                "REST"->"Немного отдохну."
                "WAVE"->listOf("Привет! Я тут!","Рад тебя видеть!").random()
                "TRAVEL"->if(sim.outing.yard)"Пора домой!" else "Пойду подышу свежим воздухом!"
                "SWING"->"Покатаюсь на качелях!"
                "LIGHT"->"Попробую поменять освещение."
                "LOOK"->"Интересно, что вокруг?"
                "ROLL"->"Смотри, как я умею!"
                else->"Пойду разомнусь!"
            };sim.speechMs=6000;lastSpeech=now
        }
        return true
    }
    fun tick(sim: PetSim,dt: Long) {
        sim.externalBrain=prefs.getBoolean("mind_enabled",false)
        if(previouslyEnabled && !sim.externalBrain) {
            interrupt();worker.execute {try {engine?.close()}catch(_: Exception){};engine=null}
        }
        previouslyEnabled=sim.externalBrain
        if(!sim.externalBrain || closed)return
        if(previousState==PetSim.State.INTERACT && sim.state==PetSim.State.HAPPY)remember("Закончил занятие с предметом")
        previousState=sim.state
        val minutes=dt/60_000f
        energy=(energy+minutes*(if(sim.state==PetSim.State.SLEEP).07f else if(sim.state==PetSim.State.SIT).025f else -.018f)).coerceIn(0f,1f)
        social=(social+minutes*.025f).coerceIn(0f,1f);curiosity=(curiosity+minutes*.02f).coerceIn(0f,1f)
        val now=SystemClock.uptimeMillis()
        val incomingId=prefs.getLong("mind_message_id",0)
        val free=!sim.outing.busy && sim.state==PetSim.State.IDLE
        pending?.let { d ->
            if(d.version!=revision || d.message!=incomingId || d.yard!=sim.outing.yard || now>d.expires)pending=null
            else if(free){pending=null;if(execute(sim,d.choice,true))return}
        }
        // Подход к предмету, игра и отдых завершаются без прерывания запросами.
        if(free && sim.stateTime>=1200)execute(sim,autonomous(sim),false)
        if(now<retryAt || busy.get() || pending!=null || sim.outing.busy)return
        if(!MindModel.file(app).exists()){status("Модель не загружена; самостоятельное поведение активно");retryAt=now+30_000;return}
        val menu=choices(sim)
        val version=revision;val yard=sim.outing.yard
        val incoming=if(incomingId!=messageId)prefs.getString("mind_message","").orEmpty().take(180) else ""
        val prompt="""<|im_start|>system
Choose one next activity for a virtual pet. Return ONLY JSON {"choice":0}. Use an integer index from the menu. No speech, no explanation. Follow the owner's request if possible. Hunger 100=hungry; energy 0=tired. Avoid repeating recent activities.
<|im_end|>
<|im_start|>user
Character:${prefs.getString("mind_personality","curious")}. Place:${if(yard)"yard" else "room"}. Hunger:${sim.hunger.toInt()}. Energy:${(energy*100).toInt()}. Company need:${(social*100).toInt()}.
Recent:${recent.joinToString(",")}. Favourite:${prefs.getString("mind_favourite","")}.
Memories:${memories.takeLast(3).joinToString(";").take(180)}.
Owner:$incoming
Menu:
${menu.mapIndexed { i,c -> "$i=${c.action} ${c.target} (${c.label})" }.joinToString("\n")}
<|im_end|>
<|im_start|>assistant
"""
        if(!busy.compareAndSet(false,true))return
        val start=SystemClock.uptimeMillis()
        status("Живёт самостоятельно; нейросеть выбирает следующее занятие")
        worker.execute {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            try {
                check(available(version)){"Отменено"}
                val loaded=engine ?: run {
                    val manager=app.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                    val info=android.app.ActivityManager.MemoryInfo();manager.getMemoryInfo(info)
                    check(info.availMem>1_700_000_000L){"Недостаточно свободной RAM"}
                    MindModel.open(app).also {engine=it}
                }
                val raw=MindModel.infer(loaded,prompt){available(version) && prefs.getLong("mind_message_id",0)==incomingId}
                val first=raw.indexOf('{');val last=raw.lastIndexOf('}')
                check(first>=0 && last>first){"Ответ не содержит JSON"}
                val index=JSONObject(raw.substring(first,last+1)).optInt("choice",-1)
                check(index in menu.indices){"Недоступное занятие"}
                main.post {
                    if(available(version) && prefs.getLong("mind_message_id",0)==incomingId && sim.outing.yard==yard) {
                        pending=Decision(menu[index],yard,version,incomingId,SystemClock.uptimeMillis()+45_000)
                        messageId=incomingId
                        if(incoming.isNotEmpty())remember("Хозяин: $incoming")
                        prefs.edit().putLong("mind_processed_message_id",messageId).apply()
                        status("Выбрано: ${menu[index].label}")
                    }
                    finishRequest(start,30_000)
                }
            } catch(e: Exception) {
                main.post {if(available(version))status("Самостоятельное поведение активно. ИИ: ${e.message?.take(80)}");finishRequest(start,45_000)}
            } catch(e: LinkageError) {
                main.post {status("Движок ИИ недоступен; самостоятельное поведение активно");finishRequest(start,Long.MAX_VALUE)}
            } catch(e: OutOfMemoryError) {
                main.post {prefs.edit().putBoolean("mind_enabled",false).apply();status("Не хватило RAM; включено обычное поведение");finishRequest(start,45_000)}
            }
        }
    }
    private fun finishRequest(start: Long,delay: Long) {
        prefs.edit().putLong("mind_latency_ms",SystemClock.uptimeMillis()-start).apply()
        retryAt=if(delay==Long.MAX_VALUE)delay else SystemClock.uptimeMillis()+delay
        busy.set(false)
    }
    private fun status(text: String){prefs.edit().putString("mind_status",text).apply()}
    override fun close(){closed=true;interrupt();worker.execute {try{engine?.close()}catch(_: Exception){};engine=null};worker.shutdown()}
}
