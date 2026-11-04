package com.kems.fleet;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.ConsoleMessage;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final String TAG = "KEMS_FLEET";
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            Log.i(TAG, "Demarrage de KEMS FLEET");

            webView = new WebView(this);
            setContentView(webView);

            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            settings.setAllowFileAccess(true);
            settings.setAllowContentAccess(true);

            webView.setWebViewClient(new WebViewClient());

            webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public boolean onConsoleMessage(ConsoleMessage message) {
                    Log.e(TAG, "JS: " + message.message()
                            + " ligne " + message.lineNumber());
                    return true;
                }
            });

            webView.loadUrl("file:///android_asset/index.html");

            Log.i(TAG, "Chargement HTML demande");

        } catch (Exception e) {
            Log.e(TAG, "ERREUR DEMARRAGE", e);
            Toast.makeText(this, e.toString(),
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
