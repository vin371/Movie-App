package com.vietstream.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
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

  // Lớp điều khiển phủ lên video (Overlay)
  private FrameLayout overlayLayout;
  private TextView lockButton;
  private TextView rotateButton;
  private float touchStartX, touchStartY;
  private boolean touchingPlayerButton;
  private View touchBlocker;
  private boolean isLocked = false;
  private Runnable hideControlsRunnable;

  private int retryCount;

  public final class PlayerBridge {
    @JavascriptInterface
    public void keepScreenOn(boolean keepOn) {
      runOnUiThread(() -> {
        if (keepOn) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else if (customView == null) getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
    // Luôn giữ màn hình sáng khi xem phim
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
        fullscreenContainer.setBackgroundColor(Color.BLACK);
        fullscreenContainer.setKeepScreenOn(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        fullscreenContainer.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Lớp chặn chạm nhầm khi đang khóa (trong suốt)
        touchBlocker = new View(MainActivity.this);
        touchBlocker.setVisibility(View.GONE);
        fullscreenContainer.addView(touchBlocker, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Lớp điều khiển phủ lên video (Overlay)
        overlayLayout = new FrameLayout(MainActivity.this);
        fullscreenContainer.addView(overlayLayout, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // NÚT KHÓA MÀN HÌNH RIÊNG VỚI BIỂU TƯỢNG 🔒
        // Nằm bên trái / giữa cạnh trái lớp điều khiển phủ lên video, kích thước lớn dễ bấm
        lockButton = new TextView(MainActivity.this);
        lockButton.setGravity(Gravity.CENTER);
        lockButton.setText("🔒\nKhóa");
        lockButton.setTextColor(Color.WHITE);
        lockButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);

        // Nền tối bán trong suốt bo tròn
        GradientDrawable normalBg = new GradientDrawable();
        normalBg.setColor(Color.parseColor("#99141721")); // Bán trong suốt 60%
        normalBg.setCornerRadius(28f);
        normalBg.setStroke(3, Color.parseColor("#44FFFFFF"));
        lockButton.setBackground(normalBg);

        int sizePx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 64, getResources().getDisplayMetrics());
        FrameLayout.LayoutParams lockLp = new FrameLayout.LayoutParams(sizePx, sizePx);
        lockLp.gravity = Gravity.CENTER_VERTICAL | Gravity.LEFT;
        lockLp.leftMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics());
        overlayLayout.addView(lockButton, lockLp);
        rotateButton = new TextView(MainActivity.this);
        rotateButton.setGravity(Gravity.CENTER);
        rotateButton.setText("↻\nXoay");
        rotateButton.setTextColor(Color.WHITE);
        rotateButton.setTextSize(14);
        rotateButton.setBackgroundColor(Color.parseColor("#99141721"));
        FrameLayout.LayoutParams rotateLp = new FrameLayout.LayoutParams(sizePx, sizePx);
        rotateLp.gravity = Gravity.CENTER_VERTICAL | Gravity.RIGHT;
        rotateLp.rightMargin = lockLp.leftMargin;
        overlayLayout.addView(rotateButton, rotateLp);
        rotateButton.setOnClickListener(v -> {
          if (isLocked) return;
          boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
          setRequestedOrientation(landscape ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        });

        // Ban đầu ở toàn màn hình: Cho phép xoay 4 hướng theo cảm biến (FULL_SENSOR)
        isLocked = false;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        getWindow().getDecorView().setSystemUiVisibility(
          View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        // Tự động mờ nút khóa sau 3.5 giây khi người dùng không chạm
        scheduleHideControls();

        // Chạm vào màn hình để hiện lại nút khóa
        touchBlocker.setOnClickListener(v -> {});

        // Bấm nút Khóa / Mở khóa
        lockButton.setOnClickListener(v -> toggleLockState());

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

  @Override public boolean dispatchTouchEvent(android.view.MotionEvent event) {
    if (fullscreenContainer != null) {
      if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
        touchStartX = event.getRawX();
        touchStartY = event.getRawY();
        touchingPlayerButton = hitsButton(lockButton, event) || hitsButton(rotateButton, event);
      } else if (event.getActionMasked() == android.view.MotionEvent.ACTION_UP && !touchingPlayerButton) {
        float slop = android.view.ViewConfiguration.get(this).getScaledTouchSlop();
        if (Math.abs(event.getRawX() - touchStartX) < slop && Math.abs(event.getRawY() - touchStartY) < slop) onScreenTouched();
      }
    }
    return super.dispatchTouchEvent(event);
  }

  private boolean hitsButton(View button, android.view.MotionEvent event) {
    if (button == null || button.getVisibility() != View.VISIBLE) return false;
    android.graphics.Rect bounds = new android.graphics.Rect();
    return button.getGlobalVisibleRect(bounds) && bounds.contains((int) event.getRawX(), (int) event.getRawY());
  }

  private void onScreenTouched() {
    if (lockButton == null || fullscreenContainer == null) return;
    if (lockButton.getVisibility() == View.VISIBLE) {
      if (hideControlsRunnable != null) lockButton.removeCallbacks(hideControlsRunnable);
      lockButton.setVisibility(View.GONE);
      if (rotateButton != null) rotateButton.setVisibility(View.GONE);
      return;
    }
    lockButton.setVisibility(View.VISIBLE);
    lockButton.setAlpha(1f);
    lockButton.setText(isLocked ? "🔓\nMở khóa" : "🔒\nKhóa");
    if (rotateButton != null) rotateButton.setVisibility(isLocked ? View.GONE : View.VISIBLE);
    scheduleHideControls();
  }
  private void toggleLockState() {
    if (lockButton == null) return;
    isLocked = !isLocked;
    if (rotateButton != null) rotateButton.setVisibility(isLocked ? View.GONE : View.VISIBLE);
    if (isLocked) {
      // 1. CỐ ĐỊNH HƯỚNG MÀN HÌNH HIỆN TẠI (Dù xoay điện thoại theo hướng nào)
      setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

      // 2. Chặn chạm nhầm vào các điều khiển phát phim
      touchBlocker.setVisibility(View.VISIBLE);

      // 3. Đổi giao diện nút sang trạng thái đang khóa
      GradientDrawable lockedBg = new GradientDrawable();
      lockedBg.setColor(Color.parseColor("#D9E50914")); // Đỏ nổi bật bán trong suốt
      lockedBg.setCornerRadius(28f);
      lockedBg.setStroke(3, Color.parseColor("#FFFF6B72"));
      lockButton.setBackground(lockedBg);
      lockButton.setText("🔓\nMở khóa");
      lockButton.setAlpha(1.0f);
    } else {
      // 1. MỞ KHÓA: Cho phép xoay theo cảm biến ở CẢ BỐN HƯỚNG (FULL_SENSOR)
      setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);

      // 2. Khôi phục các điều khiển phát phim
      touchBlocker.setVisibility(View.GONE);

      // 3. Đổi giao diện nút sang trạng thái sẵn sàng khóa
      GradientDrawable normalBg = new GradientDrawable();
      normalBg.setColor(Color.parseColor("#99141721"));
      normalBg.setCornerRadius(28f);
      normalBg.setStroke(3, Color.parseColor("#44FFFFFF"));
      lockButton.setBackground(normalBg);
      lockButton.setText("🔒\nKhóa");
      lockButton.setAlpha(1.0f);
    }
    scheduleHideControls();
  }

  private void scheduleHideControls() {
    if (lockButton == null) return;
    if (hideControlsRunnable != null) lockButton.removeCallbacks(hideControlsRunnable);
    hideControlsRunnable = () -> {
      lockButton.setVisibility(View.GONE);
      if (rotateButton != null) rotateButton.setVisibility(View.GONE);
    };
    lockButton.postDelayed(hideControlsRunnable, 5000);
  }
  private void exitFullscreenMode() {
    if (fullscreenContainer == null && customView == null) return;
    isLocked = false;
    if (lockButton != null && hideControlsRunnable != null) lockButton.removeCallbacks(hideControlsRunnable);
    if (fullscreenContainer != null) {
      root.removeView(fullscreenContainer);
      fullscreenContainer = null;
    }
    customView = null;
    web.setVisibility(View.VISIBLE);
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
        toggleLockState();
        return;
      }
      exitFullscreenMode();
      return;
    }

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
