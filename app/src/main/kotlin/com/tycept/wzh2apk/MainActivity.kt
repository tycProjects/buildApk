
package com.tycept.wzh2apk

import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.view.*
import android.widget.*
import org.json.*
import java.io.*
import java.net.*
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.*
import java.util.zip.*

class MainActivity : Activity() {
    companion object { const val API="https://zip-to-apk-ce53.onrender.com"; const val PICK_PROJECT=100 }
    private val BG=Color.rgb(13,15,17); private val CARD=Color.rgb(21,23,26); private val PANEL=Color.rgb(27,29,33)
    private val MUTED=Color.rgb(157,161,168); private val WHITE=Color.rgb(238,240,242); private val ACCENT=Color.rgb(99,230,213)
    private lateinit var content: LinearLayout; private lateinit var footerLimit: TextView; private lateinit var buildButton: Button
    private var sourceMode="zip"; private var codeLang="java"; private var token:String?=null; private var jobId:String?=null; private var selectedUri:Uri?=null
    private var appName:EditText?=null; private var packageSuffix:EditText?=null; private var nativeCode:EditText?=null; private var htmlCode:EditText?=null; private var status:TextView?=null
    private val io=Executors.newCachedThreadPool(); private val handler=Handler(Looper.getMainLooper()); private val prefs by lazy { getSharedPreferences("wzh",0) }
    private val selectedPermissions=mutableListOf<String>()

    override fun onCreate(b:Bundle?){super.onCreate(b);window.statusBarColor=Color.rgb(12,14,16);shell();ensureSession();refreshLimit()}
    private fun tv(s:String,sp:Float,c:Int)=TextView(this).apply{ text=s;textSize=sp;setTextColor(c);setPadding(0,6,0,6)}
    private fun bg(c:Int,r:Float,stroke:Int=0,sc:Int=0)=GradientDrawable().apply{setColor(c);cornerRadius=r;if(stroke>0)setStroke(stroke,sc)}
    private fun btn(s:String)=Button(this).apply{text=s;textSize=13f;setTextColor(WHITE);isAllCaps=false;background=bg(PANEL,16f,1,Color.rgb(55,58,63));setPadding(12,4,12,4)}
    private fun edit(h:String)=EditText(this).apply{hint=h;setHintTextColor(Color.rgb(100,104,110));setTextColor(WHITE);textSize=15f;setSingleLine();setPadding(16,0,16,0);background=bg(Color.rgb(24,26,30),14f,1,Color.rgb(52,55,60))}
    private fun add(v:View,top:Int=0){content.addView(v,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,top,0,0)})}
    private fun shell(){
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(BG)}
        val head=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(18,12,12,10);setBackgroundColor(Color.BLACK)}
        head.addView(tv("↗",28f,ACCENT).apply{gravity=Gravity.CENTER;background=bg(Color.BLACK,0f,2,WHITE)},LinearLayout.LayoutParams(58,58))
        head.addView(LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(16,0,0,0);addView(tv("WZH 2 APK",23f,WHITE));addView(tv(".zip  →  .apk  ·  by Tycept",13f,MUTED))},LinearLayout.LayoutParams(0,-2,1f))
        head.addView(btn("↻").apply{setTextSize(25f);setOnClickListener{ensureSession();refreshLimit()}},LinearLayout.LayoutParams(54,54));head.addView(btn("?").apply{setTextSize(20f);setOnClickListener{help()}},LinearLayout.LayoutParams(54,54));root.addView(head)
        val tabs=LinearLayout(this).apply{setPadding(8,8,8,8);setBackgroundColor(Color.rgb(17,19,22))};listOf("Build","Options","History","Progress").forEach{n->tabs.addView(btn(n).apply{setOnClickListener{show(n)}},LinearLayout.LayoutParams(0,48,1f))};root.addView(tabs)
        val scroll=ScrollView(this).apply{isFillViewport=true};content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,6,24,120)};scroll.addView(content);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val foot=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,8,24,10);setBackgroundColor(Color.rgb(10,11,13))};footerLimit=tv("Builds today: —",13f,MUTED).apply{gravity=Gravity.CENTER};foot.addView(footerLimit);buildButton=btn("BUILD APK").apply{setTextSize(16f);isEnabled=false;setOnClickListener{startBuild()}};foot.addView(buildButton,LinearLayout.LayoutParams(-1,58));root.addView(foot);setContentView(root);show("Build")
    }
    private fun clear(){content.removeAllViews()}; private fun show(tab:String){clear();when(tab){"Build"->build();"Options"->options();"History"->history();else->progressTab()}}
    private fun section(label:String,desc:String){add(tv(label.uppercase(),12f,ACCENT),12);add(tv(desc,13f,MUTED),0)}
    private fun build(){add(tv("Build your app",27f,WHITE),12);add(tv("Turn a ZIP project, website, or Java/Kotlin source into an installable Android build.",14f,MUTED));section("Source","Choose what you're starting from.");listOf("Upload File" to "zip","Website link" to "url","Write code" to "code").forEach{(label,mode)->add(btn((if(sourceMode==mode)"✓  " else "")+label).apply{setOnClickListener{sourceMode=mode;show("Build")}},8)}
        when(sourceMode){"zip"->{add(btn("SELECT PROJECT ZIP / HTML").apply{setOnClickListener{pick()}},16);add(tv(selectedUri?.lastPathSegment?.let{"Selected: $it"} ?: "No project selected",14f,MUTED),4)}
            "url"->{val u=edit("https://example.com");u.setText(prefs.getString("remoteUrl",""));add(u,16);add(btn("USE THIS WEBSITE").apply{setOnClickListener{prefs.edit().putString("remoteUrl",u.text.toString().trim()).apply();buildButton.isEnabled=true}},8)}
            else->{val langs=LinearLayout(this);listOf("html","java","kotlin").forEach{l->langs.addView(btn((if(codeLang==l)"✓  " else "")+l.replaceFirstChar{it.uppercase()}).apply{setOnClickListener{codeLang=l;show("Build")}},LinearLayout.LayoutParams(0,52,1f))};add(langs,16);if(codeLang=="html"){htmlCode=code("<!doctype html>\n<html><body><h1>Hello from WZH</h1></body></html>");add(htmlCode!!,8)}else{nativeCode=code(if(codeLang=="java")javaTemplate(derivedPackage()) else kotlinTemplate(derivedPackage()));add(nativeCode!!,8)}add(btn("RESET TEMPLATE").apply{setOnClickListener{nativeCode?.setText(if(codeLang=="java")javaTemplate(derivedPackage()) else kotlinTemplate(derivedPackage()))}},8)}}
        buildButton.isEnabled=sourceMode=="url"||selectedUri!=null||(sourceMode=="code"&&((if(codeLang=="html")htmlCode else nativeCode)?.text?.isNotBlank()==true))
    }
    private fun code(s:String)=EditText(this).apply{text=s;textSize=13f;setTextColor(Color.rgb(220,225,228));typeface=Typeface.MONOSPACE;gravity=Gravity.TOP or Gravity.START;setPadding(14,14,14,14);background=bg(Color.rgb(10,12,14),14f,1,Color.rgb(54,58,63));minHeight=260;setSingleLine(false)}
    private fun derivedPackage():String{var s=(packageSuffix?.text?.toString()?.trim().takeUnless{it.isNullOrEmpty()} ?: appName?.text?.toString()?.trim().takeUnless{it.isNullOrEmpty()} ?: "myapp");s=s!!.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"),".").replace(Regex("\\.+"),".");return "com."+s.trim('.')}
    private fun javaTemplate(p:String)="""package $p;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText("Hello from Java!");
        tv.setTextSize(20);
        tv.setPadding(40,40,40,40);
        setContentView(tv);
    }
}
"""
    private fun kotlinTemplate(p:String)="""package $p

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this)
        tv.text = "Hello from Kotlin!"
        tv.textSize = 20f
        tv.setPadding(40,40,40,40)
        setContentView(tv)
    }
}
"""
    private fun options(){add(tv("Options",27f,WHITE),12);add(tv("Configure the Android build without changing your project source.",14f,MUTED));section("App details","Identity used by the generated Android project.");appName=edit("App name").apply{setText(prefs.getString("appName",""))};packageSuffix=edit("Package suffix (optional)").apply{setText(prefs.getString("pkg",""))};add(appName!!,4);add(packageSuffix!!,8);add(edit("App link host (optional)"),8);add(edit("Google web client ID (optional)"),8);section("Protection","Build-time hardening options.");listOf("Harden with Dex2C","Sign release build","Fast cache").forEach{add(CheckBox(this).apply{text=it;setTextColor(WHITE);isChecked=it=="Sign release build"})};section("Startup loading","Select the splash animation and speed.");add(Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,arrayOf("Off","Tumble","Fade","Typewriter","Pulse","Slide","Custom"))},4);add(Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,arrayOf("Normal","Slow","Fast"))},4);section("Permissions","Optional Android permissions sent to the server.");val perms=arrayOf("Internet","Network state","Notifications","Keep screen awake","Background tasks","Biometric unlock","Camera","Microphone","Photos","Videos","Audio files","Precise location","Approximate location","Background location","Files & media (legacy)","Read contacts","Edit contacts","Read calendar","Edit calendar","Make calls","Phone state","Call log","Read SMS","Send SMS","Bluetooth","Nearby Wi-Fi devices","Body sensors","Physical activity","Vibrate");perms.forEach{p->add(CheckBox(this).apply{text=p;setTextColor(WHITE);isChecked=p in arrayOf("Internet","Network state","Camera","Microphone","Precise location","Approximate location","Files & media (legacy)","Vibrate")})};add(btn("SAVE OPTIONS").apply{setOnClickListener{prefs.edit().putString("appName",appName!!.text.toString()).putString("pkg",packageSuffix!!.text.toString()).apply();Toast.makeText(this,"Options saved",Toast.LENGTH_SHORT).show()}},20)}
    private fun history(){add(tv("Build history",27f,WHITE),12);add(tv("Your past builds from the WZH server.",14f,MUTED));val body=tv("Loading…",14f,MUTED);add(btn("REFRESH HISTORY").apply{setOnClickListener{loadHistory(body)}},14);add(body,12);loadHistory(body)}
    private fun progressTab(){add(tv("Build progress",27f,WHITE),12);status=tv(if(jobId==null)"No active build." else "Job: $jobId",15f,WHITE);add(status!!);add(tv("The server status is polled every two seconds.",14f,MUTED),8);add(btn("CANCEL BUILD").apply{setOnClickListener{cancelBuild()}},14)}

    private fun ensureSession(){io.execute{try{token=JSONObject(get("$API/api/session",null)).optString("token");ui("Build session ready")}catch(e:Exception){ui("Session: ${e.message}")}}}
    private fun refreshLimit(){io.execute{try{val o=JSONObject(get("$API/api/build-limit",null));runOnUiThread{footerLimit.text="Builds today: ${o.optInt("used")}/${o.optInt("limit")} used"}}catch(_:Exception){}}}
    private fun get(url:String,t:String?):String{val c=URL(url).openConnection() as HttpURLConnection;c.connectTimeout=30000;c.readTimeout=30000;if(t!=null)c.setRequestProperty("X-Session-Token",t);c.setRequestProperty("Accept","application/json");c.setRequestProperty("Origin",API);val code=c.responseCode;val s=read(if(code>=400)c.errorStream else c.inputStream);if(code !in 200..299)throw IOException("HTTP $code $s");return s}
    private fun post(url:String,body:String):String{val c=URL(url).openConnection() as HttpURLConnection;c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("X-Session-Token",token);c.setRequestProperty("Origin",API);c.outputStream.use{it.write(body.toByteArray())};val code=c.responseCode;val s=read(if(code>=400)c.errorStream else c.inputStream);if(code !in 200..299)throw IOException("HTTP $code $s");return s}
    private fun read(i:InputStream?):String=i?.bufferedReader()?.readText()?:("")
    private fun ui(s:String)=runOnUiThread{Toast.makeText(this,s,Toast.LENGTH_SHORT).show()}
    private fun pick(){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="application/zip"},PICK_PROJECT)}
    override fun onActivityResult(r:Int,c:Int,d:Intent?){super.onActivityResult(r,c,d);if(r==PICK_PROJECT&&c==RESULT_OK){selectedUri=d?.data;show("Build")}}

    private fun startBuild(){io.execute{try{if(token.isNullOrBlank())token=JSONObject(get("$API/api/session",null)).optString("token");val zip=prepare();val b="----WZH${System.currentTimeMillis()}";val c=URL("$API/api/build").openConnection() as HttpURLConnection;c.requestMethod="POST";c.doOutput=true;c.connectTimeout=60000;c.readTimeout=120000;c.setRequestProperty("X-Session-Token",token);c.setRequestProperty("Origin",API);c.setRequestProperty("Content-Type","multipart/form-data; boundary=$b");c.outputStream.use{out->Multipart(out,b).apply{file("projectZip",zip.name,"application/zip",zip);text("buildType","apk");text("platform","android");text("clientId",prefs.getString("clientId",UUID.randomUUID().toString())!!);text("signRelease","true");text("dex2c","false");text("cacheMode","fresh");text("splashStyle","off");text("splashSpeed","normal");text("permissions",JSONArray(selectedPermissions).toString());if(sourceMode=="url")text("remoteUrl",prefs.getString("remoteUrl","")!!);end()}};val code=c.responseCode;val s=read(if(code>=400)c.errorStream else c.inputStream);if(code !in 200..299)throw IOException("HTTP $code $s");jobId=JSONObject(s).optString("jobId");refreshLimit();runOnUiThread{show("Progress")};poll()}catch(e:Exception){ui("Build failed: ${e.message}")}}}
    private fun prepare():File{val f=File(cacheDir,"wzh-project.zip");if(sourceMode=="zip"&&selectedUri!=null){contentResolver.openInputStream(selectedUri!!).use{input->FileOutputStream(f).use{out->input!!.copyTo(out)}};return f};ZipOutputStream(FileOutputStream(f)).use{z->if(sourceMode=="url")add(z,"index.html","<script>location.replace(${JSONObject.quote(prefs.getString("remoteUrl","")!!)});</script>") else if(codeLang=="html")add(z,"index.html",htmlCode!!.text.toString()) else createNative(z)};return f}
    private fun createNative(z:ZipOutputStream){val p=derivedPackage();var src=nativeCode!!.text.toString();src=src.replace(Regex("(?s)^\\s*package\\s+[\\w.]+;?\\s*"),"");src="package $p${if(codeLang=="java")";" else ""}\n\n$src";add(z,"settings.gradle", "pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }\ndependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }\nrootProject.name='GeneratedApp'\ninclude ':app'\n");add(z,"build.gradle",if(codeLang=="kotlin")"plugins { id 'com.android.application' version '8.7.2' apply false\n id 'org.jetbrains.kotlin.android' version '1.9.24' apply false }" else "plugins { id 'com.android.application' version '8.7.2' apply false }");add(z,"app/build.gradle","plugins { id 'com.android.application'${if(codeLang=="kotlin")"\n id 'org.jetbrains.kotlin.android'" else ""} }\nandroid { namespace '$p'; compileSdk 35; defaultConfig { applicationId '$p'; minSdk 23; targetSdk 35; versionCode 1; versionName '1.0' }; buildTypes { release { minifyEnabled false } }; compileOptions { sourceCompatibility JavaVersion.VERSION_17; targetCompatibility JavaVersion.VERSION_17 }${if(codeLang=="kotlin")"; kotlinOptions { jvmTarget='17' }" else ""} }");add(z,"app/src/main/AndroidManifest.xml","<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"><application android:theme=\"@style/AppTheme\" android:label=\"Generated App\"><activity android:name=\".MainActivity\" android:exported=\"true\"><intent-filter><action android:name=\"android.intent.action.MAIN\"/><category android:name=\"android.intent.category.LAUNCHER\"/></intent-filter></activity></application></manifest>");add(z,"app/src/main/res/values/styles.xml", "<resources><style name=\"AppTheme\" parent=\"android:style/Theme.Material.NoActionBar\"><item name=\"android:fontFamily\">sans</item><item name=\"android:colorAccent\">#63E6D5</item></style></resources>");add(z,"app/src/main/"+(if(codeLang=="kotlin")"kotlin" else "java")+"/"+p.replace('.','/')+"/MainActivity."+(if(codeLang=="kotlin")"kt" else "java"),src)}
    private fun add(z:ZipOutputStream,n:String,s:String){z.putNextEntry(ZipEntry(n));z.write(s.toByteArray(StandardCharsets.UTF_8));z.closeEntry()}
    private fun poll(){val id=jobId?:return;io.execute{try{val o=JSONObject(get("$API/api/status/$id",token));runOnUiThread{status?.text="Job: $id\n${o.optString("status")}\n${o.optString("message",o.optString("log",""))}"};val st=o.optString("status").lowercase();if(st.contains("done")||st.contains("success")||st.contains("failed")||st.contains("error")||st.contains("cancel"))return@execute;handler.postDelayed({poll()},2000)}catch(_:Exception){handler.postDelayed({poll()},4000)}}}
    private fun cancelBuild(){jobId?.let{id->io.execute{try{post("$API/api/cancel/$id","{}");ui("Build cancelled")}catch(e:Exception){ui("Cancel failed: ${e.message}")}}}}
    private fun loadHistory(view:TextView){io.execute{try{val cid=prefs.getString("clientId","")!!;val raw=get("$API/api/history?clientId="+URLEncoder.encode(cid,"UTF-8"),token);runOnUiThread{view.text=raw}}catch(e:Exception){runOnUiThread{view.text="Could not load history.\n${e.message}"}}}}
    private fun help(){AlertDialog.Builder(this).setTitle("WZH 2 APK").setMessage("Native Android client for the WZH build service. It uses native views and HTTP instead of a WebView. If your server enforces a browser-only Origin allowlist, that backend policy must explicitly permit the native client.").setPositiveButton("OK",null).show()}
    private class Multipart(private val out:OutputStream,private val boundary:String){fun text(n:String,v:String){out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$n\"\r\n\r\n$v\r\n".toByteArray())};fun file(n:String,fn:String,type:String,f:File){out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$n\"; filename=\"$fn\"\r\nContent-Type: $type\r\n\r\n".toByteArray());f.inputStream().use{it.copyTo(out)};out.write("\r\n".toByteArray())};fun end(){out.write("--$boundary--\r\n".toByteArray())}}
}
