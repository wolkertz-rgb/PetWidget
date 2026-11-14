package com.sting.petwidget

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import com.google.mediapipe.tasks.genai.llminference.LlmInference

/** Worker получает снимок мира; исполнять решения может только поток симуляции. */
internal class LocalMind(context: Context) : AutoCloseable {
    private val app=context.applicationContext
    private val prefs=app.getSharedPreferences("pet_state",Context.MODE_PRIVATE)
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private val busy=AtomicBoolean(false)
    private var engine: LlmInference?=null
    @Volatile private var closed=false
    private var retryAt=0L
    private var messageId=0L
    private var lastAction=""
    private var previouslyEnabled=false
    private var previousState=PetSim.State.IDLE
    private var energy=prefs.getFloat("mind_energy",.8f)
    private var social=prefs.getFloat("mind_social",.5f)
    private var curiosity=prefs.getFloat("mind_curiosity",.7f)
    private val memories=ArrayDeque<String>()
    init {
        prefs.getString("mind_memories","")?.split("\n")?.filter {it.isNotBlank()}?.takeLast(10)?.forEach { memories.addLast(it) }
        if(!prefs.contains("mind_personality")) prefs.edit().putString("mind_personality",
            listOf("gentle, curious, playful","calm, observant, affectionate","adventurous, shy, fond of nature").random()).apply()
    }
    fun notice(event: String) {
        memories.addLast(event.take(100));while(memories.size>10)memories.removeFirst()
        prefs.edit().putString("mind_memories",memories.joinToString("\n")).apply()
        retryAt=0
    }
    fun tick(sim: PetSim,dt: Long) {
        sim.externalBrain=prefs.getBoolean("mind_enabled",false)
        if(previouslyEnabled && !sim.externalBrain)worker.execute { engine?.close();engine=null }
        previouslyEnabled=sim.externalBrain
        if(!sim.externalBrain || closed)return
        if(previousState!=sim.state && sim.state==PetSim.State.HAPPY)notice("Finished an interaction with an object")
        previousState=sim.state
        val minutes=dt/60_000f
        energy=(energy+minutes*(if(sim.state==PetSim.State.SLEEP).07f else if(sim.state==PetSim.State.SIT).025f else -.018f)).coerceIn(0f,1f)
        social=(social+minutes*.025f).coerceIn(0f,1f)
        curiosity=(curiosity+minutes*.02f).coerceIn(0f,1f)
        val now=android.os.SystemClock.uptimeMillis()
        if(sim.outing.busy || sim.state !in listOf(PetSim.State.IDLE,PetSim.State.SIT) || now<retryAt || busy.get())return
        if(!MindModel.file(app).exists()) { if(now>=retryAt){status("Сначала загрузи модель в разделе «Мозг»");retryAt=now+30_000};return }
        val yard=sim.outing.yard
        val incomingId=prefs.getLong("mind_message_id",0)
        val incoming=if(incomingId!=messageId)prefs.getString("mind_message","")?.take(180).orEmpty() else ""
        val items=sim.items.filter {it.ageMs>=0}.joinToString(",") {"${it.defId}:${it.def.kind}"}
        val prompt="""<|im_start|>system
You are Petey, a virtual pet. Choose your next intention from personality, needs, memories and surroundings. Be varied. Never claim to see camera or other apps. Reply ONLY JSON: {"action":"LOOK","target":"","say":"short Russian phrase","plan":"short intention"}.
Allowed: IDLE,WALK,PLAY,EAT,REST,LOOK,STRETCH,WAVE,LIGHT,TRAVEL,SWING,THINK,SHOW. PLAY/EAT/SHOW target is a listed item id. LIGHT indoors, SWING in yard. Say may be empty; do not speak every turn. Maximum 55 characters of Russian speech. No reasoning text.
<|im_end|>
<|im_start|>user
Personality: ${prefs.getString("mind_personality","curious")}.
Place:${if(yard)"yard" else "room"}. Hunger:${sim.hunger.toInt()}/100. Energy:${(energy*100).toInt()}. Need company:${(social*100).toInt()}. Curiosity:${(curiosity*100).toInt()}.
Items:$items. Last action:$lastAction. Favourite:${prefs.getString("mind_favourite","none")}.
Recent memories:${memories.takeLast(5).joinToString(";").take(350)}.
Owner says:${incoming.ifEmpty {"nothing new"}}.
Choose a small plan and one available action.
<|im_end|>
<|im_start|>assistant
"""
        if(!busy.compareAndSet(false,true))return
        status("Обдумывает следующее занятие")
        worker.execute {
            try {
                val loaded=engine ?: run {
                    val manager=app.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                    val info=android.app.ActivityManager.MemoryInfo();manager.getMemoryInfo(info)
                    check(info.availMem>1_700_000_000L) {"Недостаточно свободной RAM. Закрой другие приложения."}
                    MindModel.open(app).also {engine=it}
                }
                val raw=MindModel.infer(loaded,prompt)
                val first=raw.indexOf('{');val last=raw.lastIndexOf('}')
                check(first>=0 && last>first) {"Модель не вернула действие"}
                val result=JSONObject(raw.substring(first,last+1))
                val action=result.optString("action").uppercase()
                val target=result.optString("target").take(32)
                val say=result.optString("say").replace(Regex("[\\p{Cntrl}<>]"),"").take(55)
                val plan=result.optString("plan").replace('\n',' ').take(100)
                main.post {
                    try {
                        val power=app.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                        if(!closed && power.isInteractive && prefs.getBoolean("mind_enabled",false) && yard==sim.outing.yard &&
                            !sim.outing.busy && sim.state in listOf(PetSim.State.IDLE,PetSim.State.SIT)) {
                            if(sim.performIntention(action,target)) {
                                lastAction=action;messageId=incomingId
                                if(incoming.isNotEmpty())notice("Owner said: $incoming")
                                notice("Chose $action $target. $plan")
                                if(say.isNotBlank()){sim.speech=say;sim.speechMs=8000}
                                if(action=="PLAY"){curiosity=(curiosity-.2f).coerceAtLeast(0f);prefs.edit().putString("mind_favourite",target).apply()}
                                if(action=="WAVE")social=(social-.3f).coerceAtLeast(0f)
                                prefs.edit().putString("mind_plan",plan).putFloat("mind_energy",energy)
                                    .putFloat("mind_social",social).putFloat("mind_curiosity",curiosity).apply()
                                status("Локальный мозг работает")
                            } else status("Обдумывает другое доступное действие")
                        }
                    } finally { retryAt=android.os.SystemClock.uptimeMillis()+5000;busy.set(false) }
                }
            } catch(e: Exception) {
                main.post { if(!closed)status("ИИ: ${e.message?.take(100)}");retryAt=android.os.SystemClock.uptimeMillis()+30_000;busy.set(false) }
            } catch(e: LinkageError) {
                main.post { if(!closed)status("Нейросеть недоступна на этом устройстве");busy.set(false);retryAt=Long.MAX_VALUE }
            } catch(e: OutOfMemoryError) {
                main.post {prefs.edit().putBoolean("mind_enabled",false).apply();status("Не хватило RAM: нейросетевой режим выключен");busy.set(false)}
            }
        }
    }
    private fun status(text: String) {prefs.edit().putString("mind_status",text).apply()}
    override fun close() {
        closed=true
        worker.execute {try {engine?.close()}catch(_: Exception){};engine=null}
        worker.shutdown()
    }
}
