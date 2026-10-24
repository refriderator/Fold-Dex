package com.folddeck.launcher;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Fold Deck: a home-screen app that is one full-screen WebView running the deck UI,
 * plus a small JavaScript bridge ("Deck") for the things a web page can't do.
 */
public class MainActivity extends Activity {
    private WebView web;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private SpeechRecognizer rec;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Window w = getWindow();
        w.setStatusBarColor(Color.BLACK);
        w.setNavigationBarColor(Color.BLACK);
        WindowManager.LayoutParams lp = w.getAttributes();
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        w.setAttributes(lp);

        WebView.setWebContentsDebuggingEnabled(true);
        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setHapticFeedbackEnabled(false);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(), "Deck");
        web.loadUrl("file:///android_asset/index.html");
        setContentView(web);
        immersive();

        List<String> need = new ArrayList<>();
        for (String p : new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION})
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) need.add(p);
        if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), 1);
    }

    private void immersive() {
        web.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override public void onWindowFocusChanged(boolean f) { super.onWindowFocusChanged(f); if (f) immersive(); }
    @Override protected void onResume() { super.onResume(); js("window.deckResume&&deckResume()"); }
    @Override public void onBackPressed() { js("window.deckBack&&deckBack()"); }
    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); js("window.deckHome&&deckHome()"); }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != 7) return;
        ArrayList<String> l = data == null ? null : data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (l != null && !l.isEmpty()) cb("deckSpeech", l.get(0), true, "");
        else cb("deckSpeech", "", true, "speech error 7");
    }

    private void js(final String code) { ui.post(() -> { if (web != null) web.evaluateJavascript(code, null); }); }
    private void cb(String fn, Object... args) {
        StringBuilder sb = new StringBuilder("window." + fn + "&&" + fn + "(");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(',');
            Object a = args[i];
            sb.append(a instanceof String ? JSONObject.quote((String) a) : String.valueOf(a));
        }
        js(sb.append(')').toString());
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192]; int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        return bo.toString("UTF-8");
    }

    class Bridge {
        @JavascriptInterface public String info() {
            JSONObject o = new JSONObject();
            try {
                BatteryManager bm = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
                o.put("bat", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY));
                Intent bi = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                o.put("chg", bi != null && bi.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0);
                ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                boolean vpn = false, wifi = false, cell = false, net = false;
                for (Network n : cm.getAllNetworks()) {
                    NetworkCapabilities c = cm.getNetworkCapabilities(n);
                    if (c == null) continue;
                    if (c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) vpn = true;
                    if (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) wifi = true;
                    if (c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) cell = true;
                    if (c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) net = true;
                }
                o.put("vpn", vpn); o.put("wifi", wifi); o.put("cell", cell); o.put("online", net);
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                String ssid = "";
                if (wm != null && wm.isWifiEnabled()) {
                    WifiInfo wi = wm.getConnectionInfo();
                    if (wi != null && wi.getSSID() != null) ssid = wi.getSSID().replace("\"", "");
                    if ("<unknown ssid>".equals(ssid)) ssid = "";
                    if (wi != null) o.put("rssi", wi.getRssi());
                }
                o.put("ssid", ssid);
                o.put("wifiOn", wm != null && wm.isWifiEnabled());
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                int mx = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                o.put("vol", Math.round(am.getStreamVolume(AudioManager.STREAM_MUSIC) * 10f / mx));
                int br = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128);
                o.put("brtRaw", br);
                o.put("speech", SpeechRecognizer.isRecognitionAvailable(MainActivity.this));
                String rs = Settings.Secure.getString(getContentResolver(), "voice_recognition_service");
                o.put("recog", rs == null ? "" : rs.replaceAll("/.*", ""));
            } catch (Exception e) { try { o.put("err", e.toString()); } catch (Exception ignored) {} }
            return o.toString();
        }

        @JavascriptInterface public String apps() {
            PackageManager pm = getPackageManager();
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> l = pm.queryIntentActivities(i, 0);
            List<String[]> rows = new ArrayList<>();
            for (ResolveInfo r : l) {
                String p = r.activityInfo.packageName;
                if (p.equals(getPackageName())) continue;
                rows.add(new String[]{String.valueOf(r.loadLabel(pm)), p});
            }
            Collections.sort(rows, (a, c) -> a[0].compareToIgnoreCase(c[0]));
            JSONArray out = new JSONArray();
            for (String[] r : rows) { JSONArray x = new JSONArray(); x.put(r[0]); x.put(r[1]); out.put(x); }
            return out.toString();
        }

        @JavascriptInterface public boolean launch(String pkg) {
            Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i == null) return false;
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { startActivity(i); return true; } catch (Exception e) { return false; }
        }

        @JavascriptInterface public boolean has(String pkg) {
            try { getPackageManager().getPackageInfo(pkg, 0); return true; } catch (Exception e) { return false; }
        }

        @JavascriptInterface public boolean settings(String action) {
            try { startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true; }
            catch (Exception e) { return false; }
        }

        @JavascriptInterface public boolean view(String uri, String pkg) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (pkg != null && !pkg.isEmpty()) i.setPackage(pkg);
                startActivity(i); return true;
            } catch (Exception e) { return false; }
        }

        @JavascriptInterface public void setVol(int v) {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            int mx = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(Math.max(0, Math.min(100, v)) * mx / 100f), 0);
        }

        /** Run a shell command as root. Output comes back through window.deckSh(id, out, code). */
        @JavascriptInterface public void sh(final String id, final String cmd) {
            new Thread(() -> {
                String out; int code = -1;
                try {
                    // The command's stdio are files owned by root rather than our app's pipes:
                    // system services (pm, am, settings, dumpsys) refuse file descriptors from apps.
                    File f = new File(getCacheDir(), "c" + id + ".sh");
                    try (OutputStream os = new FileOutputStream(f)) { os.write(cmd.getBytes("UTF-8")); }
                    String o = "/data/local/tmp/deck_" + id;
                    String wrap = "sh " + f.getAbsolutePath() + " </dev/null >" + o + " 2>&1; e=$?; cat " + o + "; rm -f " + o + "; exit $e";
                    Process p = new ProcessBuilder("su", "-c", wrap).redirectErrorStream(true).start();
                    p.getOutputStream().close();
                    out = readAll(p.getInputStream());
                    code = p.waitFor();
                    f.delete();
                } catch (Exception e) { out = "deck: " + e.getMessage(); }
                cb("deckSh", id, out, code);
            }).start();
        }

        /** HTTP for APIs that block browser requests. Result through window.deckHttp(id, status, body). */
        @JavascriptInterface public void http(final String id, final String method, final String url, final String headers, final String body) {
            new Thread(() -> {
                int st = -1; String res;
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setRequestMethod(method);
                    c.setConnectTimeout(15000); c.setReadTimeout(120000);
                    JSONObject h = new JSONObject(headers == null || headers.isEmpty() ? "{}" : headers);
                    for (Iterator<String> it = h.keys(); it.hasNext(); ) { String k = it.next(); c.setRequestProperty(k, h.getString(k)); }
                    if (body != null && !body.isEmpty() && !method.equals("GET")) {
                        c.setDoOutput(true);
                        try (OutputStream os = c.getOutputStream()) { os.write(body.getBytes("UTF-8")); }
                    }
                    st = c.getResponseCode();
                    res = readAll(st >= 400 ? c.getErrorStream() : c.getInputStream());
                } catch (Exception e) { res = e.toString(); }
                cb("deckHttp", id, st, res);
            }).start();
        }

        /** Speech to text. Partial and final text through window.deckSpeech(text, isFinal, error). */
        @JavascriptInterface public void listen() {
            ui.post(() -> {
                if (!SpeechRecognizer.isRecognitionAvailable(MainActivity.this)) {
                    Intent pi = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                    pi.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                    pi.putExtra(RecognizerIntent.EXTRA_PROMPT, "speak");
                    try { startActivityForResult(pi, 7); }
                    catch (Exception e) { cb("deckSpeech", "", true, "no speech service on this phone"); }
                    return;
                }
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 2);
                    cb("deckSpeech", "", true, "speech error 9");
                    return;
                }
                // fresh recognizer every time: a reused one gets stuck busy (error 8) or silently dead
                if (rec != null) { try { rec.cancel(); rec.destroy(); } catch (Exception ignored) {} rec = null; }
                {
                    rec = SpeechRecognizer.createSpeechRecognizer(MainActivity.this);
                    rec.setRecognitionListener(new RecognitionListener() {
                        public void onReadyForSpeech(Bundle p) { cb("deckSpeechState", "ready"); }
                        public void onBeginningOfSpeech() { cb("deckSpeechState", "talk"); }
                        public void onRmsChanged(float v) {}
                        public void onBufferReceived(byte[] b) {}
                        public void onEndOfSpeech() { cb("deckSpeechState", "end"); }
                        public void onError(int e) { cb("deckSpeech", "", true, "speech error " + e); }
                        public void onResults(Bundle r) { send(r, true); }
                        public void onPartialResults(Bundle r) { send(r, false); }
                        public void onEvent(int t, Bundle p) {}
                        private void send(Bundle r, boolean fin) {
                            ArrayList<String> l = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                            cb("deckSpeech", l != null && !l.isEmpty() ? l.get(0) : "", fin, "");
                        }
                    });
                }
                Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
                i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
                i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
                try { rec.startListening(i); } catch (Exception e) { cb("deckSpeech", "", true, "speech error 5"); }
            });
        }
        @JavascriptInterface public void vibe(int ms) {
            android.os.Vibrator v = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            v.vibrate(android.os.VibrationEffect.createOneShot(Math.max(1, Math.min(200, ms)), ms < 12 ? 60 : 160));
        }
        @JavascriptInterface public void stopListen() { ui.post(() -> { if (rec != null) rec.stopListening(); }); }
    }
}
