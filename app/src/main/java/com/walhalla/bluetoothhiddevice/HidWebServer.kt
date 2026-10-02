package com.walhalla.bluetoothhiddevice

import com.walhalla.bluetoothhiddevice.presets.PresetActionCodec
import com.walhalla.bluetoothhiddevice.presets.PresetActionEntity
import com.walhalla.bluetoothhiddevice.presets.PresetExecutor
import com.walhalla.bluetoothhiddevice.presets.PresetRepository
import com.walhalla.bluetoothhiddevice.presets.PresetShortcutDraft
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Tiny LAN web server that lives in [HidForegroundService].
 * GET / serves a single static page; everything under /api/ needs the token
 * (header `X-Token` or `Authorization: Bearer <token>`).
 *
 * Presets are read from and written to the same Room database the app uses.
 * Sensitive presets and presets that need confirmation on the phone are listed (without their
 * content) but cannot be run, shown or edited from the web.
 */
class HidWebServer(
    port: Int,
    private val token: String,
    private val hid: HidDeviceManager,
    private val presets: PresetRepository,
    private val executor: PresetExecutor
) : NanoHTTPD(port) {

    private val presetActionPath = Regex("^/api/presets/(\\d+)/(run|duplicate|delete|update)$")

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        if (uri == "/" || uri == "/index.html") {
            return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", INDEX_HTML)
        }
        if (!uri.startsWith("/api/")) {
            return json(Response.Status.NOT_FOUND, """{"error":"not found"}""")
        }
        if (!isAuthorized(session)) {
            return json(Response.Status.UNAUTHORIZED, """{"error":"unauthorized"}""")
        }
        return try {
            route(session, uri)
        } catch (e: Exception) {
            json(Response.Status.INTERNAL_ERROR, JSONObject().put("error", e.message ?: e.javaClass.simpleName).toString())
        }
    }

    private fun route(session: IHTTPSession, uri: String): Response {
        val match = presetActionPath.matchEntire(uri)
        return when {
            uri == "/api/status" && session.method == Method.GET ->
                json(Response.Status.OK, """{"connected":${hid.isConnected()}}""")

            uri == "/api/release-all" && session.method == Method.POST -> {
                hid.releaseAll()
                json(Response.Status.OK, """{"ok":true}""")
            }

            uri == "/api/type" && session.method == Method.POST -> {
                session.parseBody(HashMap())
                val text = session.parameters["text"]?.firstOrNull().orEmpty()
                if (!hid.isConnected()) {
                    json(Response.Status.CONFLICT, """{"error":"host not connected"}""")
                } else {
                    if (text.isNotEmpty()) hid.sendString(text)
                    json(Response.Status.OK, """{"ok":true,"chars":${text.length}}""")
                }
            }

            uri == "/api/presets" && session.method == Method.GET ->
                json(Response.Status.OK, runBlocking { buildPresetList() }.toString())

            uri == "/api/presets/add" && session.method == Method.POST -> {
                session.parseBody(HashMap())
                runBlocking { addPreset(session) }
            }

            match != null && session.method == Method.POST -> {
                session.parseBody(HashMap())
                val id = match.groupValues[1].toLong()
                runBlocking { presetAction(id, match.groupValues[2], session) }
            }

            else -> json(Response.Status.NOT_FOUND, """{"error":"not found"}""")
        }
    }

    private class Simple(val type: String, val value: String)

    private fun isLocked(preset: com.walhalla.bluetoothhiddevice.presets.PresetEntity, actions: List<PresetActionEntity>): Boolean =
        preset.isSensitive || preset.requiresConfirmation || actions.any {
            it.type == PresetActionCodec.TYPE_TYPE_SENSITIVE_TEXT || it.type == PresetActionCodec.TYPE_CREDENTIAL
        }

    /** Type and text of a preset that has exactly one plain action, or null when it is not simple. */
    private fun simpleOf(actions: List<PresetActionEntity>): Simple? {
        if (actions.size != 1) return null
        val action = actions[0]
        val payload = JSONObject(action.payloadJson)
        return when (action.type) {
            PresetActionCodec.TYPE_TYPE_TEXT -> Simple(action.type, payload.optString("text"))
            PresetActionCodec.TYPE_RUN_WINDOWS_COMMAND -> Simple(action.type, payload.optString("command"))
            PresetActionCodec.TYPE_KEY_COMBO, PresetActionCodec.TYPE_KEY_PRESS -> {
                val shortcut = runCatching {
                    PresetShortcutDraft.fromAction(PresetActionCodec.fromEntity(action))?.toShortcutString()
                }.getOrNull()
                shortcut?.let { Simple(PresetActionCodec.TYPE_KEYBOARD_SHORTCUT, it) }
            }
            else -> null
        }
    }

    private suspend fun buildPresetList(): JSONObject {
        val categories = presets.categories.first()
        val allPresets = presets.allPresets.first()
        val actionsByPreset = presets.allActions.first().groupBy { it.presetId }
        val presetsByCategory = allPresets.groupBy { it.categoryId }

        val result = JSONArray()
        for (category in categories) {
            val items = JSONArray()
            for (preset in presetsByCategory[category.id].orEmpty().sortedBy { it.sortOrder }) {
                val actions = actionsByPreset[preset.id].orEmpty()
                val locked = isLocked(preset, actions)
                val simple = if (locked) null else simpleOf(actions)
                items.put(
                    JSONObject()
                        .put("id", preset.id)
                        .put("title", preset.title)
                        .put("description", if (locked) "" else preset.description)
                        .put("builtIn", preset.isBuiltIn)
                        .put("locked", locked)
                        .put("actions", actions.size)
                        .put("editable", simple != null)
                        .put("type", simple?.type ?: JSONObject.NULL)
                        .put("value", simple?.value ?: JSONObject.NULL)
                )
            }
            result.put(
                JSONObject()
                    .put("id", category.id)
                    .put("title", category.title)
                    .put("builtIn", category.isBuiltIn)
                    .put("presets", items)
            )
        }
        return JSONObject().put("connected", hid.isConnected()).put("categories", result)
    }

    private fun param(session: IHTTPSession, name: String): String? =
        session.parameters[name]?.firstOrNull()

    private fun isAllowedType(type: String?): Boolean =
        type == PresetActionCodec.TYPE_TYPE_TEXT ||
            type == PresetActionCodec.TYPE_RUN_WINDOWS_COMMAND ||
            type == PresetActionCodec.TYPE_KEYBOARD_SHORTCUT

    private fun error(status: Response.IStatus, message: String): Response =
        json(status, JSONObject().put("error", message).toString())

    private suspend fun addPreset(session: IHTTPSession): Response {
        val categoryId = param(session, "categoryId")?.toLongOrNull()
            ?: return error(Response.Status.BAD_REQUEST, "categoryId required")
        if (presets.categories.first().none { it.id == categoryId }) {
            return error(Response.Status.NOT_FOUND, "category not found")
        }
        val title = param(session, "title")?.trim().orEmpty()
        val value = param(session, "value").orEmpty()
        val type = param(session, "type")
        if (title.isEmpty()) return error(Response.Status.BAD_REQUEST, "title required")
        if (value.isBlank()) return error(Response.Status.BAD_REQUEST, "value required")
        if (!isAllowedType(type)) return error(Response.Status.BAD_REQUEST, "unsupported type")
        val description = param(session, "description")?.trim().orEmpty().ifEmpty { title }
        val sortOrder = presets.allPresets.first().filter { it.categoryId == categoryId }
            .maxOfOrNull { it.sortOrder }?.plus(1) ?: 0
        return try {
            presets.addSingleActionPreset(
                categoryId = categoryId,
                title = title,
                description = description,
                value = value,
                sortOrder = sortOrder,
                actionType = type!!,
                isSensitive = false
            )
            json(Response.Status.OK, """{"ok":true}""")
        } catch (e: Exception) {
            error(Response.Status.BAD_REQUEST, e.message ?: "invalid value")
        }
    }

    private suspend fun presetAction(id: Long, action: String, session: IHTTPSession): Response {
        val source = presets.getPresetWithActions(id)
            ?: return error(Response.Status.NOT_FOUND, "preset not found")
        val locked = isLocked(source.preset, source.actions)

        return when (action) {
            "run" -> {
                if (locked) return error(Response.Status.FORBIDDEN, "this preset must be confirmed on the phone")
                if (!hid.isConnected()) return error(Response.Status.CONFLICT, "host not connected")
                val ok = executor.execute(source.actions.map { PresetActionCodec.fromEntity(it) })
                if (ok) json(Response.Status.OK, """{"ok":true}""")
                else error(Response.Status.CONFLICT, "preset failed: no HID connection or unsupported key")
            }

            "duplicate" -> {
                if (locked) return error(Response.Status.FORBIDDEN, "locked preset")
                presets.duplicatePreset(id)
                json(Response.Status.OK, """{"ok":true}""")
            }

            "delete" -> {
                if (source.preset.isBuiltIn) return error(Response.Status.FORBIDDEN, "built-in preset cannot be deleted")
                presets.deletePreset(id)
                json(Response.Status.OK, """{"ok":true}""")
            }

            else -> {
                if (locked) return error(Response.Status.FORBIDDEN, "locked preset")
                if (simpleOf(source.actions) == null) {
                    return error(Response.Status.CONFLICT, "preset with several actions can be edited only on the phone")
                }
                val type = param(session, "type")
                val value = param(session, "value").orEmpty()
                if (!isAllowedType(type)) return error(Response.Status.BAD_REQUEST, "unsupported type")
                if (value.isBlank()) return error(Response.Status.BAD_REQUEST, "value required")
                val title = param(session, "title")?.trim().orEmpty().ifEmpty { source.preset.title }
                val description = param(session, "description")?.trim().orEmpty().ifEmpty { title }
                try {
                    presets.updateSingleActionPreset(
                        presetId = id,
                        title = title,
                        description = description,
                        value = value,
                        actionType = type!!,
                        isSensitive = false
                    )
                    json(Response.Status.OK, """{"ok":true}""")
                } catch (e: Exception) {
                    error(Response.Status.BAD_REQUEST, e.message ?: "invalid value")
                }
            }
        }
    }

    private fun isAuthorized(session: IHTTPSession): Boolean {
        val headers = session.headers
        val given = headers["x-token"]
            ?: headers["authorization"]?.removePrefix("Bearer ")?.trim()
            ?: return false
        return MessageDigest.isEqual(given.toByteArray(), token.toByteArray())
    }

    private fun json(status: Response.IStatus, body: String): Response =
        newFixedLengthResponse(status, "application/json; charset=utf-8", body)

    private companion object {
        const val INDEX_HTML = """<!doctype html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>HID remote</title>
<style>
body{font-family:sans-serif;max-width:1100px;margin:16px auto;padding:0 12px}
.layout{display:flex;gap:24px;align-items:flex-start}
main{flex:1 1 0;min-width:0}
aside{flex:0 0 340px;position:sticky;top:16px;max-height:calc(100vh - 32px);overflow:auto;border:1px solid #ccc;border-radius:8px;padding:0 12px 12px}
@media(max-width:800px){.layout{flex-direction:column}aside{position:static;flex:none;width:100%;max-height:none}}
input,textarea,select,button{box-sizing:border-box;font-size:16px;margin:4px 0;padding:8px}
input,textarea,select{width:100%}
.card{border:1px solid #ccc;border-radius:8px;padding:8px;margin:6px 0}
.card small{color:#666;display:block}
.card code{display:block;background:#f3f3f3;padding:4px;margin:4px 0;white-space:pre-wrap;word-break:break-all}
.row button{margin-right:4px}
.lock{color:#a60}
#out{background:#f3f3f3;padding:6px;min-height:1.5em}
</style></head>
<body><h2>Bluetooth HID remote</h2>
<div class="layout"><main>
<input id="token" placeholder="Token (shown in the app)">
<button onclick="load()">Connect</button> <span id="conn"></span>
<pre id="out"></pre>
<details><summary>Quick type</summary>
<textarea id="text" rows="3" placeholder="Text to type on the host"></textarea>
<button onclick="send()">Type on host</button>
<button onclick="rel()">Release all keys</button>
</details>
<h3>Presets</h3>
<input id="filter" placeholder="Filter by title" oninput="render()">
<div id="list"></div>
</main>
<aside>
<h3 id="formTitle">Add preset</h3>
<select id="fcat"></select>
<input id="ftitle" placeholder="Title">
<input id="fdesc" placeholder="Description (optional)">
<select id="ftype">
<option value="TYPE_TEXT">Type text</option>
<option value="RUN_WINDOWS_COMMAND">Run Windows command (Win+R)</option>
<option value="KEYBOARD_SHORTCUT">Keyboard shortcut (e.g. Ctrl+Shift+Esc)</option>
</select>
<textarea id="fvalue" rows="3" placeholder="Text, command or shortcut"></textarea>
<button id="fsave" onclick="save()">Add</button>
<button id="fcancel" onclick="cancelEdit()" style="display:none">Cancel</button>
</aside></div>
<script>
function el(i){return document.getElementById(i)}
var t=el('token');t.value=localStorage.getItem('hidToken')||'';
var data={categories:[]};var editId=null;
if(location.hash.length>1){t.value=location.hash.substring(1);localStorage.setItem('hidToken',t.value);history.replaceState(null,'','/');}
function api(m,p,b){localStorage.setItem('hidToken',t.value);
 return fetch(p,{method:m,headers:{'X-Token':t.value},body:b}).then(function(r){
  return r.json().then(function(j){j._s=r.status;return j},function(){return {_s:r.status}})})}
function msg(j){el('out').textContent=j.error?('Error: '+j.error):'OK'}
function load(){return api('GET','/api/presets').then(function(j){
 if(j.error){msg(j);return}
 data=j;el('conn').textContent=j.connected?'host connected':'host NOT connected';el('out').textContent='';
 var s=el('fcat'),keep=s.value;s.innerHTML='';
 j.categories.forEach(function(c){var o=document.createElement('option');o.value=c.id;o.textContent=c.title;s.appendChild(o)});
 if(keep)s.value=keep;render()})}
function btn(label,fn,disabled){var b=document.createElement('button');b.textContent=label;b.disabled=!!disabled;b.onclick=fn;return b}
function render(){var box=el('list');box.innerHTML='';var f=el('filter').value.toLowerCase();
 data.categories.forEach(function(c){
  var items=c.presets.filter(function(p){return !f||p.title.toLowerCase().indexOf(f)>=0});
  if(!items.length)return;
  var h=document.createElement('h4');h.textContent=c.title;box.appendChild(h);
  items.forEach(function(p){
   var d=document.createElement('div');d.className='card';
   var b=document.createElement('b');b.textContent=p.title;d.appendChild(b);
   if(p.locked){var l=document.createElement('small');l.className='lock';l.textContent='Locked: run it on the phone';d.appendChild(l)}
   else{
    if(p.description&&p.description!==p.title){var s=document.createElement('small');s.textContent=p.description;d.appendChild(s)}
    if(p.value!==null){var cd=document.createElement('code');cd.textContent=p.value;d.appendChild(cd)}
    else{var s2=document.createElement('small');s2.textContent=p.actions+' actions';d.appendChild(s2)}
   }
   var r=document.createElement('div');r.className='row';
   r.appendChild(btn('Run',function(){api('POST','/api/presets/'+p.id+'/run','').then(msg)},p.locked));
   r.appendChild(btn('Edit',function(){startEdit(p,c)},!p.editable));
   r.appendChild(btn('Copy',function(){api('POST','/api/presets/'+p.id+'/duplicate','').then(function(j){msg(j);load()})},p.locked));
   r.appendChild(btn('Delete',function(){if(confirm('Delete "'+p.title+'"?'))api('POST','/api/presets/'+p.id+'/delete','').then(function(j){msg(j);load()})},p.builtIn));
   d.appendChild(r);box.appendChild(d)})})}
function startEdit(p,c){editId=p.id;el('formTitle').textContent='Edit preset';el('fcat').value=c.id;el('fcat').disabled=true;
 el('ftitle').value=p.title;el('fdesc').value=p.description||'';el('ftype').value=p.type;el('fvalue').value=p.value;
 el('fsave').textContent='Save changes';el('fcancel').style.display='';el('ftitle').scrollIntoView()}
function cancelEdit(){editId=null;el('formTitle').textContent='Add preset';el('fcat').disabled=false;
 el('ftitle').value='';el('fdesc').value='';el('fvalue').value='';el('fsave').textContent='Add';el('fcancel').style.display='none'}
function save(){var body=new URLSearchParams({categoryId:el('fcat').value,title:el('ftitle').value,description:el('fdesc').value,type:el('ftype').value,value:el('fvalue').value});
 var url=editId===null?'/api/presets/add':'/api/presets/'+editId+'/update';
 api('POST',url,body).then(function(j){msg(j);if(!j.error){cancelEdit();load()}})}
function rel(){api('POST','/api/release-all','').then(msg)}
function send(){api('POST','/api/type',new URLSearchParams({text:el('text').value})).then(msg)}
if(t.value)load();
</script></body></html>"""
    }
}