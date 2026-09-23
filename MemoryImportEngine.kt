package com.example.virtualcompanion.importer

import com.example.virtualcompanion.model.MemoryItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipInputStream

object MemoryImportEngine {
    enum class Source(val label: String) { AUTO("自動辨識"), GEMINI("Gemini"), CHARACTER_AI("Character.AI"), GROK("Grok"), OTHER("其他 / 自訂") }
    data class Result(val items: List<MemoryItem>, val warnings: List<String> = emptyList(), val filesRead: Int = 1)

    fun import(input: InputStream, fileName: String, source: Source, userName: String, characterName: String): Result {
        val lower=fileName.lowercase(Locale.ROOT)
        return when {
            lower.endsWith(".zip") -> importZip(input, source, userName, characterName)
            lower.endsWith(".jsonl") -> parseJsonLines(input.bufferedReader().readText(), source, userName, characterName)
            lower.endsWith(".json") -> parseJson(input.bufferedReader().readText(), source, userName, characterName)
            lower.endsWith(".csv") -> parseCsv(input.bufferedReader().readText(), source, userName, characterName)
            lower.endsWith(".html") || lower.endsWith(".htm") -> parseText(stripHtml(input.bufferedReader().readText()), source, userName, characterName)
            else -> parseText(input.bufferedReader().readText(), source, userName, characterName)
        }
    }

    private fun importZip(input: InputStream, source: Source, user: String, char: String): Result {
        val all=mutableListOf<MemoryItem>(); val warnings=mutableListOf<String>(); var files=0
        ZipInputStream(BufferedInputStream(input)).use { zis ->
            var entry=zis.nextEntry
            while(entry!=null && files<300) {
                if(!entry.isDirectory && isSupported(entry.name) && entry.size <= 30_000_000L) {
                    val bytes=readEntryLimited(zis,30_000_000); files++
                    try { all += import(ByteArrayInputStream(bytes), entry.name, source, user, char).items }
                    catch(e:Exception){ warnings += "略過 ${entry.name}: ${e.message ?: "無法解析"}" }
                }
                zis.closeEntry(); entry=zis.nextEntry
            }
        }
        return Result(dedupe(all),warnings,files)
    }

    private fun isSupported(n:String):Boolean { val x=n.lowercase(Locale.ROOT); return listOf(".json",".jsonl",".txt",".md",".csv",".html",".htm").any{x.endsWith(it)} }
    private fun readEntryLimited(input:InputStream,limit:Int):ByteArray { val out=ByteArrayOutputStream(); val buf=ByteArray(8192); var total=0
        while(true){ val n=input.read(buf); if(n<=0)break; total+=n; if(total>limit)throw IOException("檔案過大"); out.write(buf,0,n) }; return out.toByteArray() }

    private fun parseJson(text:String, source:Source, user:String, char:String):Result {
        val root = try { JSONObject(text) } catch(_:Exception) { try { JSONArray(text) } catch(e:Exception) { return parseText(text,source,user,char) } }
        val out=mutableListOf<MemoryItem>(); walk(root,out,source,user,char,"root")
        return Result(dedupe(out))
    }

    private fun parseJsonLines(text:String, source:Source, user:String, char:String):Result {
        val out=mutableListOf<MemoryItem>(); text.lineSequence().filter{it.isNotBlank()}.forEachIndexed { i,line ->
            try { val x=try{JSONObject(line)}catch(_:Exception){JSONArray(line)}; walk(x,out,source,user,char,"line_$i") } catch(_:Exception){}
        }; return Result(dedupe(out))
    }

    private fun walk(node:Any?, out:MutableList<MemoryItem>, source:Source, user:String, char:String, conv:String) {
        when(node) {
            is JSONObject -> {
                val text = firstText(node,listOf("text","content","message","body","prompt","response","answer","value")) ?: partsText(node)
                val role = firstText(node,listOf("role","speaker","author","sender","name","type"))
                if(!text.isNullOrBlank() && text.length<200_000 && !looksMetadata(text)) {
                    out += MemoryItem(parseTime(node), normalizeSpeaker(role,user,char), text.trim(), source=source.name.lowercase(), conversationId=conv)
                }
                val keys=node.keys(); while(keys.hasNext()){ val k=keys.next(); val v=node.opt(k)
                    if(v is JSONObject || v is JSONArray) walk(v,out,source,user,char, conversationId(node,conv)) }
            }
            is JSONArray -> for(i in 0 until node.length()) walk(node.opt(i),out,source,user,char,"$conv:$i")
        }
    }

    private fun partsText(o:JSONObject):String? {
        val p=o.opt("parts") as? JSONArray ?: return null
        val xs=mutableListOf<String>(); for(i in 0 until p.length()){ val v=p.opt(i); if(v is String)xs+=v else if(v is JSONObject)firstText(v,listOf("text","content"))?.let{xs+=it} }
        return xs.joinToString("\n").takeIf{it.isNotBlank()}
    }
    private fun firstText(o:JSONObject,keys:List<String>):String? { for(k in keys){ val v=o.opt(k); if(v is String && v.isNotBlank())return v }; return null }
    private fun conversationId(o:JSONObject,fallback:String)=firstText(o,listOf("conversationId","conversation_id","chatId","chat_id","id"))?:fallback
    private fun parseTime(o:JSONObject):Long { for(k in listOf("timestamp","time","createdAt","created_at","create_time","createdTime")){
        val v=o.opt(k); if(v is Number){ val x=v.toLong(); return if(x<10_000_000_000L)x*1000 else x }; if(v is String)parseDate(v)?.let{return it} }
        return System.currentTimeMillis() }
    private fun parseDate(s:String):Long? { val formats=listOf("yyyy-MM-dd'T'HH:mm:ss.SSSXXX","yyyy-MM-dd'T'HH:mm:ssXXX","yyyy-MM-dd HH:mm:ss")
        for(f in formats)try{return SimpleDateFormat(f,Locale.US).parse(s)?.time}catch(_:Exception){}; return null }

    private fun parseCsv(text:String,source:Source,user:String,char:String):Result {
        val rows=parseCsvRows(text); if(rows.isEmpty())return Result(emptyList())
        val header=rows.first().map{it.trim().lowercase()}; val out=mutableListOf<MemoryItem>()
        fun idx(vararg names:String)=header.indexOfFirst{it in names}
        val si=idx("speaker","role","author","sender","name"); val ti=idx("text","content","message","body","prompt","response"); val timei=idx("timestamp","time","created_at","createdat")
        rows.drop(1).forEachIndexed { n,r -> if(ti in r.indices && r[ti].isNotBlank()) out += MemoryItem(
            timestamp = if(timei in r.indices) parseDate(r[timei]) ?: System.currentTimeMillis() else System.currentTimeMillis()+n,
            speaker=normalizeSpeaker(if(si in r.indices)r[si] else null,user,char), text=r[ti].trim(), source=source.name.lowercase(), conversationId="csv") }
        return Result(dedupe(out))
    }

    private fun parseCsvRows(s:String):List<List<String>> { val rows=mutableListOf<List<String>>(); var row=mutableListOf<String>(); val cell=StringBuilder(); var q=false; var i=0
        while(i<s.length){ val c=s[i]; when { c=='"' -> { if(q && i+1<s.length && s[i+1]=='"'){cell.append('"');i++} else q=!q }
            c==',' && !q -> {row.add(cell.toString());cell.clear()} ; (c=='\n'||c=='\r')&&!q -> { if(c=='\r'&&i+1<s.length&&s[i+1]=='\n')i++; row.add(cell.toString());cell.clear(); if(row.any{it.isNotBlank()})rows.add(row);row=mutableListOf() }
            else -> cell.append(c) }; i++ }
        row.add(cell.toString()); if(row.any{it.isNotBlank()})rows.add(row); return rows }

    private fun parseText(text:String,source:Source,user:String,char:String):Result {
        val out=mutableListOf<MemoryItem>(); val marker=Regex("^(User|You|Human|Assistant|AI|Gemini|Grok|Character|Bot|你|我|使用者|助手|角色)\\s*[:：]\\s*(.*)$",RegexOption.IGNORE_CASE)
        var currentSpeaker:String?=null; val buf=StringBuilder(); var n=0
        fun flush(){ val t=buf.toString().trim(); if(t.isNotBlank())out+=MemoryItem(System.currentTimeMillis()+n++,normalizeSpeaker(currentSpeaker,user,char),t,source=source.name.lowercase(),conversationId="text");buf.clear() }
        text.lineSequence().forEach { line -> val m=marker.find(line.trim()); if(m!=null){flush();currentSpeaker=m.groupValues[1];buf.append(m.groupValues[2])} else { if(buf.isNotEmpty())buf.append('\n');buf.append(line) } }; flush()
        if(out.size<=1){ out.clear(); text.split(Regex("\\n\\s*\\n+")).map{it.trim()}.filter{it.length>1}.forEachIndexed{i,t->out+=MemoryItem(System.currentTimeMillis()+i,if(i%2==0)user else char,t,source=source.name.lowercase(),conversationId="paragraph") } }
        return Result(dedupe(out))
    }

    private fun normalizeSpeaker(role:String?,user:String,char:String):String { val r=role?.trim()?.lowercase() ?: return char
        return when { r in listOf("user","you","human","你","使用者","我") -> user.ifBlank{"你"}
            r in listOf("assistant","ai","model","bot","gemini","grok","character","助手","角色") -> char.ifBlank{"角色"}
            else -> role ?: char }
    }
    private fun looksMetadata(t:String)=t.startsWith("http://")||t.startsWith("https://")||t.length<1
    private fun stripHtml(s:String)=s.replace(Regex("(?is)<script.*?</script>|<style.*?</style>")," ").replace(Regex("(?i)<br\\s*/?>"),"\n").replace(Regex("(?i)</p>|</div>|</li>"),"\n").replace(Regex("<[^>]+>")," ").replace("&nbsp;"," ").replace("&amp;","&").replace("&lt;","<").replace("&gt;",">")
    private fun dedupe(xs:List<MemoryItem>):List<MemoryItem> { val seen=mutableSetOf<String>(); return xs.filter { it.text.isNotBlank() && seen.add(it.speaker+"\u0000"+it.text) }.sortedBy{it.timestamp} }
}
