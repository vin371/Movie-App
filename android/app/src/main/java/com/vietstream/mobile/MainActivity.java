package com.vietstream.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.JavascriptInterface;
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
  private boolean isOrientationLocked = false;
  private int lockedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;

  public final class PlayerBridge {
    @JavascriptInterface
    public void keepScreenOn(boolean keepOn) {
      runOnUiThread(() -> {
        if (keepOn) {
          getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
          getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
      });
    }

    // Toggle khóa cứng góc máy hiện tại: khi người dùng nằm xuống hay xoay ngang/dọc, giữ cố định hướng đó
    @JavascriptInterface
    public boolean toggleLockCurrentOrientation() {
      final boolean[] result = new boolean[1];
      runOnUiThread(() -> {
        isOrientationLocked = !isOrientationLocked;
        result[0] = isOrientationLocked;
        if (isOrientationLocked) {
          // Bắt góc hướng hiện tại và khóa cứng
          int orient = getResources().getConfiguration().orientation;
          if (orient == Configuration.ORIENTATION_LANDSCAPE) {
            lockedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED;
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
          } else {
            lockedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
          }
        } else {
          // Tắt khóa: cho phép xoay theo cảm biến
          setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        }
      });
      return isOrientationLocked;
    }

    @JavascriptInterface
    public void enterPlayerMode() {
      runOnUiThread(() -> {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (!isOrientationLocked) {
          setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        }
      });
    }

    @JavascriptInterface
    public void enterPortraitMode() {
      runOnUiThread(() -> {
        isOrientationLocked = false;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
      });
    }

    @JavascriptInterface
    public void enterFullscreen() {
      runOnUiThread(() -> {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (!isOrientationLocked) {
          setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
        getWindow().getDecorView().setSystemUiVisibility(
          View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
      });
    }

    @JavascriptInterface
    public void exitFullscreen() {
      runOnUiThread(() -> exitCustomFullscreen());
    }
  }

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    // Luôn giữ sáng màn hình và khởi đầu ở chế độ dọc
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

    root = new FrameLayout(this);
    web = new WebView(this);
    web.setKeepScreenOn(true);
    WebSettings settings = web.getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setDomStorageEnabled(true);
    settings.setCacheMode(WebSettings.LOAD_DEFAULT);
    settings.setMediaPlaybackRequiresUserGesture(false);
    settings.setSupportZoom(false);
    settings.setBuiltInZoomControls(false);
    settings.setDisplayZoomControls(false);

    web.addJavascriptInterface(new PlayerBridge(), "VietStreamAndroid");
    web.setWebViewClient(new WebViewClient() {
      @Override
      public void onPageFinished(WebView view, String url) {
        retryCount = 0;
      }

      @Override
      public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
        if (!request.isForMainFrame()) return;
        if (retryCount++ < 2) {
          web.postDelayed(() -> web.loadUrl(APP_URL), 3000);
        } else {
          new AlertDialog.Builder(MainActivity.this)
            .setTitle("Không kết nối được VietStream")
            .setMessage("Kiểm tra mạng rồi thử lại.")
            .setPositiveButton("Thử lại", (dialog, which) -> {
              retryCount = 0;
              web.loadUrl(APP_URL);
            })
            .show();
        }
      }
    });

    web.setWebChromeClient(new WebChromeClient() {
      @Override
      public void onShowCustomView(View view, CustomViewCallback callback) {
        if (fullscreenView != null) {
          callback.onCustomViewHidden();
          return;
        }
        fullscreenView = view;
        fullscreenCallback = callback;
        root.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        web.setVisibility(View.GONE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (!isOrientationLocked) {
          setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
        getWindow().getDecorView().setSystemUiVisibility(
          View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
      }

      @Override
      public void onHideCustomView() {
        exitCustomFullscreen();
      }
    });

    root.addView(web);
    setContentView(root);

    if (state == null || web.restoreState(state) == null) {
      web.loadUrl(APP_URL);
    }
  }

  @Override
  public void onConfigurationChanged(Configuration configuration) {
    super.onConfigurationChanged(configuration);
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    web.saveState(state);
    super.onSaveInstanceState(state);
  }

  private void exitCustomFullscreen() {
    if (fullscreenView == null) return;
    root.removeView(fullscreenView);
    fullscreenView = null;
    web.setVisibility(View.VISIBLE);
    if (!isOrientationLocked) {
      setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }
    getWindow().getDecorView().setSystemUiVisibility(0);
    if (fullscreenCallback != null) {
      fullscreenCallback.onCustomViewHidden();
      fullscreenCallback = null;
    }
  }

  @Override
  public void onBackPressed() {
    if (fullscreenView != null) {
      exitCustomFullscreen();
      return;
    }

    // Gửi tín hiệu lùi trang cho Web trước (giữ lại tìm kiếm/chi tiết)
    web.evaluateJavascript(
      "(function(){ return window.handleAndroidBack ? window.handleAndroidBack() : false; })()",
      result -> {
        if (!"true".equals(result)) {
          if (web.canGoBack()) {
            web.goBack();
          } else {
            super.onBackPressed();
          }
        }
      }
    );
  }
}
