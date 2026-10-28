package com.vietstream.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

public class MainActivity extends Activity {
  private static final String APP_URL = "https://movie-app-vietstream.onrender.com/app";
  private FrameLayout root;
  private WebView web;
  private View fullscreenView;
  private WebChromeClient.CustomViewCallback fullscreenCallback;
  private int retryCount;

  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    root = new FrameLayout(this);
    web = new WebView(this);
    WebSettings settings = web.getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setDomStorageEnabled(true);
    settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
    settings.setMediaPlaybackRequiresUserGesture(false);
    settings.setSupportZoom(true);
    settings.setBuiltInZoomControls(true);
    settings.setDisplayZoomControls(false);
    web.setWebViewClient(new WebViewClient() {
      @Override public void onPageFinished(WebView view, String url) { retryCount = 0; }
      @Override public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
        if (!request.isForMainFrame()) return;
        if (retryCount++ < 2) web.postDelayed(() -> web.loadUrl(APP_URL), 3000);
        else new AlertDialog.Builder(MainActivity.this)
          .setTitle("Không kết nối được VietStream")
          .setMessage("Kiểm tra mạng rồi thử lại.")
          .setPositiveButton("Thử lại", (dialog, which) -> { retryCount = 0; web.loadUrl(APP_URL); })
          .show();
      }
    });
    web.setWebChromeClient(new WebChromeClient() {
      @Override public void onShowCustomView(View view, CustomViewCallback callback) {
        if (fullscreenView != null) { callback.onCustomViewHidden(); return; }
        fullscreenView = view;
        fullscreenCallback = callback;
        root.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        web.setVisibility(View.GONE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
      }
      @Override public void onHideCustomView() { exitFullscreen(); }
    });
    root.addView(web);
    setContentView(root);
    web.loadUrl(APP_URL);
  }

  private void exitFullscreen() {
    if (fullscreenView == null) return;
    root.removeView(fullscreenView);
    fullscreenView = null;
    web.setVisibility(View.VISIBLE);
    getWindow().getDecorView().setSystemUiVisibility(0);
    if (fullscreenCallback != null) fullscreenCallback.onCustomViewHidden();
    fullscreenCallback = null;
  }

  @Override public void onBackPressed() {
    if (fullscreenView != null) exitFullscreen();
    else if (web.canGoBack()) web.goBack();
    else super.onBackPressed();
  }
}

