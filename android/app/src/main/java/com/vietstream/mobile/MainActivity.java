package com.vietstream.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
  private static final String APP_URL = "https://movie-app-vietstream.onrender.com/app";
  private FrameLayout root;
  private FrameLayout fullscreenContainer;
  private WebView web;
  private View customView;
  private WebChromeClient.CustomViewCallback customCallback;

  // View nút khóa màn hình bán trong suốt trên CustomView (Toàn màn hình native)
  private TextView lockOverlayButton;
  private View touchBlocker;
  private boolean isLocked = false;
  private Runnable hideLockRunnable;

  private int retryCount;

  public final class PlayerBridge {
    @JavascriptInterface
    public void keepScreenOn(boolean keepOn) {
      runOnUiThread(() -> {
        if (keepOn) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
      });
    }

    // Khóa màn hình khi ở chế độ Web Fullscreen (nếu không kích hoạt WebChromeClient)
    @JavascriptInterface
    public void lockOrientation(boolean lock) {
      runOnUiThread(() -> {
        isLocked = lock;
        if (lock) {
          setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        } else {
          setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        }
      });
    }

    @JavascriptInterface
    public void enterFullscreen() {
      runOnUiThread(() -> {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        getWindow().getDecorView().setSystemUiVisibility(
          View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
      });
    }

    @JavascriptInterface
    public void exitFullscreen() {
      runOnUiThread(() -> exitFullscreenMode());
    }
  }

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    // Luôn giữ sáng màn hình khi mở ứng dụng xem phim
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

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
      // Khi người dùng bấm nút toàn màn hình [ ] tích hợp sẵn của Video Player
      @Override
      public void onShowCustomView(View view, CustomViewCallback callback) {
        if (customView != null) {
          callback.onCustomViewHidden();
          return;
        }
        customView = view;
        customCallback = callback;

        // Container toàn màn hình
        fullscreenContainer = new FrameLayout(MainActivity.this);
        fullscreenContainer.setBackgroundColor(0xFF000000);
        fullscreenContainer.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Lớp chặn chạm nhầm khi đang khóa (trong suốt)
        touchBlocker = new View(MainActivity.this);
        touchBlocker.setVisibility(View.GONE);
        fullscreenContainer.addView(touchBlocker, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Nút khóa màn hình bán trong suốt
        lockOverlayButton = new TextView(MainActivity.this);
        lockOverlayButton.setText("🔓 Khóa xoay");
        lockOverlayButton.setTextColor(0xFFFFFFFF);
        lockOverlayButton.setTextSize(13);
        lockOverlayButton.setPadding(28, 14, 28, 14);
        lockOverlayButton.setBackgroundColor(0x99171922);

        FrameLayout.LayoutParams btnLp = new FrameLayout.LayoutParams(
          ViewGroup.LayoutParams.WRAP_CONTENT,
          ViewGroup.LayoutParams.WRAP_CONTENT
        );
        btnLp.gravity = Gravity.TOP | Gravity.RIGHT;
        btnLp.setMargins(0, 36, 36, 0);
        fullscreenContainer.addView(lockOverlayButton, btnLp);

        // Ban đầu ở toàn màn hình: Cho phép xoay 4 hướng theo cảm biến (FULL_SENSOR)
        isLocked = false;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        getWindow().getDecorView().setSystemUiVisibility(
          View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        // Tự động mờ nút sau 3.5 giây
        scheduleHideLock();

        // Xử lý chạm vào container để hiện nút khóa / mở khóa
        fullscreenContainer.setOnClickListener(v -> toggleControlsVisibility());
        touchBlocker.setOnClickListener(v -> toggleControlsVisibility());

        // Bấm nút khóa
        lockOverlayButton.setOnClickListener(v -> {
          isLocked = !isLocked;
          if (isLocked) {
            // KHÓA CỨNG: Cố định hướng màn hình hiện tại dù người dùng xoay 4 hướng hay nằm xuống
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
            touchBlocker.setVisibility(View.VISIBLE);
            lockOverlayButton.setText("🔒 Đã khóa (Chạm mở)");
            lockOverlayButton.setBackgroundColor(0xCCB31217);
          } else {
            // MỞ KHÓA: Cho phép xoay cảm biến ở cả 4 hướng
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
            touchBlocker.setVisibility(View.GONE);
            lockOverlayButton.setText("🔓 Khóa xoay");
            lockOverlayButton.setBackgroundColor(0x99171922);
          }
          scheduleHideLock();
        });

        root.addView(fullscreenContainer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        web.setVisibility(View.GONE);
      }

      @Override
      public void onHideCustomView() {
        exitFullscreenMode();
      }
    });

    root.addView(web);
    setContentView(root);

    if (state == null || web.restoreState(state) == null) {
      web.loadUrl(APP_URL);
    }
  }

  private void scheduleHideLock() {
    if (lockOverlayButton == null) return;
    if (hideLockRunnable != null) lockOverlayButton.removeCallbacks(hideLockRunnable);
    hideLockRunnable = () -> {
      if (lockOverlayButton != null) {
        if (isLocked) {
          // Khi đang khóa, hiển thị biểu tượng ổ khóa mờ nhỏ gọn để người dùng chạm mở
          lockOverlayButton.setText("🔒");
          lockOverlayButton.setAlpha(0.45f);
        } else {
          lockOverlayButton.setVisibility(View.GONE);
        }
      }
    };
    lockOverlayButton.postDelayed(hideLockRunnable, 3500);
  }

  private void toggleControlsVisibility() {
    if (lockOverlayButton == null) return;
    if (lockOverlayButton.getVisibility() == View.VISIBLE && lockOverlayButton.getAlpha() == 1.0f) {
      lockOverlayButton.setVisibility(View.GONE);
    } else {
      lockOverlayButton.setVisibility(View.VISIBLE);
      lockOverlayButton.setAlpha(1.0f);
      lockOverlayButton.setText(isLocked ? "🔒 Đang khóa (Chạm mở)" : "🔓 Khóa xoay");
      scheduleHideLock();
    }
  }

  private void exitFullscreenMode() {
    if (fullscreenContainer == null && customView == null) return;
    isLocked = false;
    if (fullscreenContainer != null) {
      root.removeView(fullscreenContainer);
      fullscreenContainer = null;
    }
    customView = null;
    web.setVisibility(View.VISIBLE);
    // Khi thoát toàn màn hình, trở lại không giới hạn hoặc dọc theo web app
    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    getWindow().getDecorView().setSystemUiVisibility(0);
    if (customCallback != null) {
      customCallback.onCustomViewHidden();
      customCallback = null;
    }
  }

  @Override
  public void onConfigurationChanged(Configuration configuration) {
    super.onConfigurationChanged(configuration);
    // Khi xoay máy: KHÔNG tải lại WebView, giữ nguyên thời điểm phát phim
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    web.saveState(state);
    super.onSaveInstanceState(state);
  }

  @Override
  public void onBackPressed() {
    if (customView != null || fullscreenContainer != null) {
      if (isLocked) {
        // Nếu đang khóa thì mở khóa trước
        isLocked = false;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        if (touchBlocker != null) touchBlocker.setVisibility(View.GONE);
        if (lockOverlayButton != null) {
          lockOverlayButton.setText("🔓 Khóa xoay");
          lockOverlayButton.setVisibility(View.VISIBLE);
          lockOverlayButton.setAlpha(1.0f);
        }
        return;
      }
      exitFullscreenMode();
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
