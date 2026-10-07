package net.zash.panel;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.Window;
import android.webkit.*;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private static final int PORT = 27900;
    private static final int FILE_REQ = 42;
    private static LocalServer server;
    private static int serverPort = -1;
    private WebView web;
    private ValueCallback<Uri[]> fileCallback;

    private static final String DOWNLOAD_HOOK =
        "(function(){if(window.__zpHook)return;window.__zpHook=1;" +
        "function save(blob,name){var r=new FileReader();r.onload=function(){var d=r.result;var i=d.indexOf(',');" +
        "ZashAndroid.saveFile(name||'download',d.substring(i+1),blob.type||'application/octet-stream');};r.readAsDataURL(blob);}" +
        "var oc=HTMLAnchorElement.prototype.click;HTMLAnchorElement.prototype.click=function(){var h=this.href||'';" +
        "if(h.indexOf('blob:')===0||h.indexOf('data:')===0){var n=this.download;fetch(h).then(function(x){return x.blob()}).then(function(b){save(b,n)});return;}" +
        "return oc.apply(this,arguments);};" +
        "document.addEventListener('click',function(e){var a=e.target&&e.target.closest?e.target.closest('a[download]'):null;" +
        "if(a&&a.href&&(a.href.indexOf('blob:')===0||a.href.indexOf('data:')===0)){e.preventDefault();var n=a.download;fetch(a.href).then(function(x){return x.blob()}).then(function(b){save(b,n)});}},true);" +
        "})();";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (server == null) {
            try {
                server = new LocalServer(getAssets(), PORT);
                serverPort = server.start();
            } catch (Exception e) {
                Toast.makeText(this, "本地服务启动失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setUserAgentString(s.getUserAgentString() + " ClashPanel/1.0");
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.addJavascriptInterface(new Bridge(), "ZashAndroid");
        if (Build.VERSION.SDK_INT >= 26) web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                String host = u.getHost();
                if ("127.0.0.1".equals(host) && u.getPort() == serverPort) return false;
                openExternal(u);
                return true;
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript(DOWNLOAD_HOOK, null);
                view.evaluateJavascript("(function(){var m=document.querySelector('meta[name=theme-color]');return m?m.content:'';})()", v -> applyBarColor(v));
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                try {
                    startActivityForResult(p.createIntent(), FILE_REQ);
                } catch (ActivityNotFoundException e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });
        web.setDownloadListener((url, ua, cd, mime, len) -> {
            if (url.startsWith("http")) openExternal(Uri.parse(url));
        });

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl("http://127.0.0.1:" + (serverPort > 0 ? serverPort : PORT) + "/");
    }

    private void applyBarColor(String v) {
        try {
            if (v == null) return;
            v = v.replace("\"", "").trim();
            if (!v.startsWith("#")) return;
            int c = Color.parseColor(v);
            Window w = getWindow();
            w.setStatusBarColor(c);
            double lum = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255.0;
            View d = w.getDecorView();
            int f = d.getSystemUiVisibility();
            if (lum > 0.6) f |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR; else f &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            d.setSystemUiVisibility(f);
        } catch (Exception ignored) { }
    }

    private void openExternal(Uri u) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, u)); }
        catch (Exception e) { Toast.makeText(this, "无法打开链接", Toast.LENGTH_SHORT).show(); }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == FILE_REQ && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            fileCallback = null;
            return;
        }
        super.onActivityResult(req, res, data);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else moveTaskToBack(true);
    }

    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }
    @Override protected void onResume() { super.onResume(); web.onResume(); web.resumeTimers(); }
    @Override protected void onPause() { web.onPause(); super.onPause(); }

    private class Bridge {
        @JavascriptInterface
        public void saveFile(String name, String b64, String mime) {
            try {
                byte[] data = Base64.decode(b64, Base64.DEFAULT);
                String safe = name.replaceAll("[\\\\/:*?\"<>|]", "_");
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Downloads.DISPLAY_NAME, safe);
                    cv.put(MediaStore.Downloads.MIME_TYPE, mime);
                    cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) { os.write(data); }
                } else {
                    File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (dir == null || !(dir.exists() || dir.mkdirs())) dir = getExternalFilesDir(null);
                    try (FileOutputStream fo = new FileOutputStream(new File(dir, safe))) { fo.write(data); }
                }
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "已保存到下载: " + safe, Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "保存失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }
    }
}
