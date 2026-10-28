package com.vietstream.mobile;
import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
public class MainActivity extends Activity {
 private WebView web; private String server;
 public void onCreate(Bundle b){ super.onCreate(b); web=new WebView(this); web.getSettings().setJavaScriptEnabled(true); web.getSettings().setDomStorageEnabled(true); web.getSettings().setMediaPlaybackRequiresUserGesture(false); web.setWebViewClient(new WebViewClient(){ public void onReceivedError(WebView v,WebResourceRequest req,android.webkit.WebResourceError err){if(req.isForMainFrame())showServerDialog();}}); FrameLayout layout=new FrameLayout(this);layout.addView(web);TextView settings=new TextView(this);settings.setText("⚙");settings.setTextSize(19);settings.setTextColor(0xffffffff);settings.setBackgroundColor(0xff292d36);settings.setPadding(12,8,12,8);FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-2,-2);p.gravity=android.view.Gravity.BOTTOM|android.view.Gravity.RIGHT;p.setMargins(0,0,12,82);layout.addView(settings,p);settings.setOnClickListener(v->showServerDialog());setContentView(layout);server=getPreferences(0).getString("server","");if(server.isEmpty())showServerDialog();else open();}
 private void showServerDialog(){EditText input=new EditText(this);input.setSingleLine(true);input.setText(server==null||server.isEmpty()?"http://10.0.2.2:7001":server);input.setSelectAllOnFocus(true);new AlertDialog.Builder(this).setTitle("Địa chỉ máy chủ VietStream").setMessage("Máy giả lập dùng 10.0.2.2. Điện thoại thật dùng IP máy tính cùng Wi-Fi, ví dụ http://192.168.1.10:7001").setView(input).setPositiveButton("Kết nối",(dialog,which)->{server=input.getText().toString().trim().replaceAll("/+$","");getPreferences(0).edit().putString("server",server).apply();open();}).setNegativeButton("Hủy",null).show();}
 private void open(){web.loadUrl(server+"/app/1");}
 public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
}
