package com.example.virtualcompanion.ui

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.*
import com.example.virtualcompanion.ai.AiCoordinator
import com.example.virtualcompanion.data.*
import com.example.virtualcompanion.engine.GroupDialogueEngine
import com.example.virtualcompanion.engine.LongTermMemoryEngine
import com.example.virtualcompanion.engine.SocialDynamicsEngine
import com.example.virtualcompanion.model.*

class GroupChatActivity : android.app.Activity() {
    private lateinit var directory: CharacterDirectory
    private lateinit var groups: GroupChatRepository
    private lateinit var social: SocialRelationshipRepository
    private val coordinators = mutableMapOf<String, AiCoordinator>()
    private val main = Handler(Looper.getMainLooper())
    private var currentRoomId: String? = null
    private var responding = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        directory = CharacterDirectory(this)
        groups = GroupChatRepository(this)
        social = SocialRelationshipRepository(this)
        currentRoomId = intent.getStringExtra(EXTRA_ROOM_ID)
        render()
    }

    private fun render() {
        val room = currentRoomId?.let { groups.room(it) }
        if (room == null) renderRoomList() else renderRoom(room)
    }

    private fun renderRoomList() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(28)); setBackgroundColor(Color.rgb(250,248,255)) }
        root.addView(title("多人聊天室"))
        root.addView(note("建立一個有至少兩隻角色的聊天室。角色會保有各自人格，也會逐漸形成彼此的好感、信任、依戀、吃醋、競爭與不滿。"))
        root.addView(action("＋ 建立群聊") { createRoomDialog() })
        val rooms = groups.rooms()
        if (rooms.isEmpty()) root.addView(note("目前還沒有群聊。先建立兩隻以上角色，再建立聊天室。"))
        rooms.forEach { room ->
            val names = room.participantIds.mapNotNull { id -> if (id in directory.ids()) ProfileRepository(this,id).load().characterName else null }
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0,dp(10),0,dp(10)) }
            row.addView(TextView(this).apply { text = room.name; textSize = 18f })
            row.addView(note(names.joinToString("、") + "　｜　互動強度：" + dramaLabel(room.dramaLevel)))
            val buttons = LinearLayout(this)
            buttons.addView(action("進入") { currentRoomId = room.roomId; render() }, LinearLayout.LayoutParams(0,dp(46),1f))
            buttons.addView(action("刪除") { confirmDelete(room) }, LinearLayout.LayoutParams(0,dp(46),1f))
            row.addView(buttons); root.addView(row)
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun createRoomDialog() {
        val ids = directory.ids()
        if (ids.size < 2) { toast("至少建立兩隻角色才能開多人聊天室"); return }
        val chosen = ids.toMutableSet()
        val box = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(18),0,dp(18),0) }
        val name = EditText(this).apply { hint="聊天室名稱"; setText("大家的房間") }; box.addView(name)
        box.addView(note("選擇參加角色（至少 2 隻）"))
        ids.forEach { id ->
            val p = ProfileRepository(this,id).load()
            box.addView(CheckBox(this).apply { text=p.characterName; isChecked=true; setOnCheckedChangeListener { _, checked -> if(checked) chosen += id else chosen -= id } })
        }
        val drama = Spinner(this).apply { adapter = ArrayAdapter(this@GroupChatActivity, android.R.layout.simple_spinner_dropdown_item, listOf("和平","輕微","自然","戲劇化")); setSelection(2) }
        box.addView(note("角色之間的衝突／吃醋強度")); box.addView(drama)
        AlertDialog.Builder(this).setTitle("建立多人聊天室").setView(box).setNegativeButton("取消",null).setPositiveButton("建立",null).create().also { d ->
            d.setOnShowListener {
                d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    if (chosen.size < 2) { toast("至少選兩隻角色"); return@setOnClickListener }
                    val room = groups.create(name.text.toString(), ids.filter { it in chosen }, drama.selectedItemPosition)
                    currentRoomId = room.roomId; d.dismiss(); render()
                }
            }; d.show()
        }
    }

    private fun renderRoom(room: GroupRoom) {
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(14),dp(14),dp(14),dp(24)); setBackgroundColor(Color.rgb(250,248,255)) }
        val top = LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        top.addView(action("←") { currentRoomId=null; render() }, LinearLayout.LayoutParams(dp(56),dp(46)))
        top.addView(TextView(this).apply { text=room.name; textSize=22f; setPadding(dp(8),0,0,0) }, LinearLayout.LayoutParams(0,dp(50),1f)); root.addView(top)
        val participants = room.participantIds.filter { it in directory.ids() }.map { ProfileRepository(this,it).load() }
        root.addView(note(participants.joinToString("、") { it.characterName } + "　｜　" + dramaLabel(room.dramaLevel)))
        val tools = LinearLayout(this)
        tools.addView(action("關係") { showRelationships(room) }, LinearLayout.LayoutParams(0,dp(44),1f))
        tools.addView(action("強度") { changeDrama(room) }, LinearLayout.LayoutParams(0,dp(44),1f))
        tools.addView(action("清空聊天") { confirmClear(room) }, LinearLayout.LayoutParams(0,dp(44),1f)); root.addView(tools)

        val transcript = TextView(this).apply { textSize=15f; setTextColor(Color.rgb(40,36,46)); setPadding(dp(8),dp(10),dp(8),dp(10)); text = transcript(room) }
        val scroll = ScrollView(this).apply { addView(transcript) }
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1f))

        val input = EditText(this).apply { hint = if(responding) "角色們正在回覆……" else "在群聊裡說點什麼……"; minLines=2; isEnabled=!responding }
        root.addView(input)
        root.addView(Button(this).apply { text=if(responding) "回覆中…" else "送出給大家"; isEnabled=!responding; setOnClickListener { val q=input.text.toString().trim(); if(q.isNotBlank()) send(room,q) } })
        setContentView(root)
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun transcript(room: GroupRoom): String {
        val msgs = groups.messages(room.roomId).takeLast(120)
        if (msgs.isEmpty()) return "聊天室還很安靜。你可以先開口。"
        return msgs.joinToString("\n\n") { m -> "${m.speakerName}：${m.text}" }
    }

    private fun send(room: GroupRoom, text: String) {
        if (responding) return
        val firstProfile = room.participantIds.firstOrNull { it in directory.ids() }?.let { ProfileRepository(this,it).load() }
        val userName = firstProfile?.userName?.ifBlank { "你" } ?: "你"
        groups.addMessage(room.roomId, GroupMessage(System.currentTimeMillis(), "user", userName, text, EmotionCatalog.CALM, "user"))
        room.participantIds.filter { it in directory.ids() }.forEach { id ->
            val p = ProfileRepository(this,id).load()
            if (p.memoryEnabled) MemoryRepository(this,id).add(MemoryItem(System.currentTimeMillis(),userName,text,EmotionCatalog.CALM,"group:${room.roomId}",room.roomId))
            if (p.longTermMemoryEnabled) LongTermMemoryEngine.extractCandidate(text)?.let { LongTermMemoryRepository(this,id).addOrMerge(it) }
        }
        applyUserDynamics(room, text)
        responding = true; render()
        val ids = selectResponders(room, text)
        respondNext(room, text, ids, 0)
    }


    private fun selectResponders(room: GroupRoom, text: String): List<String> {
        val ids = room.participantIds.filter { it in directory.ids() }
        if (ids.size <= 2 || listOf("大家", "你們", "所有人").any { text.contains(it) }) return ids
        val profiles = ids.associateWith { ProfileRepository(this,it).load() }
        val scores = ids.associateWith { id ->
            val p = profiles.getValue(id)
            var score = if (text.contains(p.characterName, true)) 120 else 10
            val others = ids.filter { it != id }
            score += others.maxOfOrNull { oid -> social.get(id,oid).jealousy + social.get(id,oid).irritation } ?: 0
            score += kotlin.math.abs((text + id).hashCode() % 17)
            score
        }
        val max = when(room.dramaLevel) { 0,1 -> 2; 2 -> 3; else -> minOf(4,ids.size) }
        return ids.sortedByDescending { scores[it] ?: 0 }.take(max)
    }

    private fun respondNext(room: GroupRoom, userText: String, ids: List<String>, index: Int) {
        if (index >= ids.size) { responding=false; render(); return }
        val id = ids[index]
        val state = StateRepository(this,id).load()
        if (state.sleeping) { respondNext(room,userText,ids,index+1); return }
        val profile = ProfileRepository(this,id).load()
        val others = ids.filter { it != id }.map { oid -> ProfileRepository(this,oid).load() }
        val pairs = others.map { it to social.get(id,it.characterId) }
        val groupHistory = groups.messages(room.roomId)
        val recent = groupHistory.takeLast(22).map { MemoryItem(it.timestamp,it.speakerName,it.text,it.emotion,"group:${room.roomId}",room.roomId) }
        val local = GroupDialogueEngine.reply(profile,userText,pairs,groupHistory,room.dramaLevel)
        val socialContext = SocialDynamicsEngine.context(profile,pairs,groupHistory,room.dramaLevel)
        val coordinator = coordinators.getOrPut(id) { AiCoordinator(this,id) }
        coordinator.replyAsync(profile,userText,recent,socialContext,local) { r ->
            val before = groups.messages(room.roomId).lastOrNull { it.speakerId != "user" && it.speakerId != id }
            groups.addMessage(room.roomId, GroupMessage(System.currentTimeMillis(),id,profile.characterName,r.text,r.emotion,if(r.usedFallback)"local_fallback" else r.provider))
            if (profile.memoryEnabled) MemoryRepository(this,id).add(MemoryItem(System.currentTimeMillis(),profile.characterName,r.text,r.emotion,"group:${room.roomId}",room.roomId))
            if (before != null) SocialDynamicsEngine.onCharacterMessage(id,before.speakerId,r.text)?.let { d -> social.adjust(id,d.targetId,d.affinity,d.trust,d.attachment,d.jealousy,d.rivalry,d.irritation,d.event) }
            render()
            main.postDelayed({ respondNext(room,userText,ids,index+1) }, 280L)
        }
    }

    private fun applyUserDynamics(room: GroupRoom, text: String) {
        val profiles = room.participantIds.filter { it in directory.ids() }.map { ProfileRepository(this,it).load() }
        profiles.forEach { self ->
            val others = profiles.filter { it.characterId != self.characterId }
            SocialDynamicsEngine.onUserMessage(self,others,text,room.dramaLevel).forEach { d ->
                social.adjust(self.characterId,d.targetId,d.affinity,d.trust,d.attachment,d.jealousy,d.rivalry,d.irritation,d.event)
            }
        }
    }

    private fun showRelationships(room: GroupRoom) {
        val ps = room.participantIds.filter { it in directory.ids() }.map { ProfileRepository(this,it).load() }
        val text = buildString {
            ps.forEach { a -> ps.filter { it.characterId != a.characterId }.forEach { b ->
                val r=social.get(a.characterId,b.characterId)
                append("${a.characterName} → ${b.characterName}：${r.dominantFeeling()}\n")
                append("好感 ${r.affinity}｜信任 ${r.trust}｜依戀 ${r.attachment}｜吃醋 ${r.jealousy}｜競爭 ${r.rivalry}｜不滿 ${r.irritation}\n\n")
            } }
        }.ifBlank { "目前沒有關係資料。" }
        AlertDialog.Builder(this).setTitle("角色彼此的感受").setMessage(text).setNegativeButton("關閉",null)
            .setNeutralButton("全部冷靜一點") { _,_ -> calmRoom(ps) }.show()
    }

    private fun calmRoom(ps: List<CharacterProfile>) {
        ps.forEach { a -> ps.filter { it.characterId != a.characterId }.forEach { b -> social.adjust(a.characterId,b.characterId,affinity=1,trust=1,jealousy=-18,rivalry=-12,irritation=-25,event="玩家讓大家冷靜") } }
        toast("大家的火氣稍微降下來了")
    }

    private fun changeDrama(room: GroupRoom) {
        val labels=arrayOf("和平","輕微","自然","戲劇化")
        AlertDialog.Builder(this).setTitle("互動／衝突強度").setSingleChoiceItems(labels,room.dramaLevel) { d,which -> room.dramaLevel=which; groups.saveRoom(room); d.dismiss(); render() }.show()
    }

    private fun confirmClear(room: GroupRoom) { AlertDialog.Builder(this).setTitle("清空這個群聊？").setMessage("只清除聊天室紀錄，不會刪除角色的彼此關係。").setNegativeButton("取消",null).setPositiveButton("清空") { _,_ -> groups.clearMessages(room.roomId); render() }.show() }
    private fun confirmDelete(room: GroupRoom) { AlertDialog.Builder(this).setTitle("刪除 ${room.name}？").setNegativeButton("取消",null).setPositiveButton("刪除") { _,_ -> groups.deleteRoom(room.roomId); render() }.show() }
    private fun dramaLabel(v:Int)=listOf("和平","輕微","自然","戲劇化")[v.coerceIn(0,3)]
    private fun title(t:String)=TextView(this).apply{text=t;textSize=28f;setTextColor(Color.rgb(40,36,46))}
    private fun note(t:String)=TextView(this).apply{text=t;textSize=13f;setTextColor(Color.DKGRAY);setPadding(0,dp(5),0,dp(8))}
    private fun action(t:String,c:()->Unit)=Button(this).apply{text=t;setOnClickListener{c()}}
    private fun toast(t:String)=Toast.makeText(this,t,Toast.LENGTH_SHORT).show()
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()

    override fun onDestroy() { coordinators.values.forEach { it.shutdown() }; coordinators.clear(); super.onDestroy() }

    companion object { const val EXTRA_ROOM_ID="roomId" }
}
