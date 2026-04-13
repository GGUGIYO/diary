package com.diary.myapp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private WebView webView;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        // Android 브릿지 등록 - 파일 저장 / 공유 기능
        webView.addJavascriptInterface(new AndroidBridge(this), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }
        });

        webView.loadUrl("https://ggugiyo.github.io/diary/diary.html");
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    // ── 파일 저장 & 공유 브릿지 ──
    class AndroidBridge {
        private final Activity activity;

        AndroidBridge(Activity a) {
            this.activity = a;
        }

        /**
         * 파일을 다운로드 폴더에 저장
         * JS: AndroidBridge.saveFile("파일명.doc", "내용")
         */
        @JavascriptInterface
        public void saveFile(final String fileName, final String content) {
            try {
                byte[] bytes = content.getBytes("UTF-8");
                String mime = fileName.endsWith(".doc")
                        ? "application/msword"
                        : "application/json";

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10 이상: MediaStore API 사용
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                    cv.put(MediaStore.Downloads.MIME_TYPE, mime);
                    cv.put(MediaStore.Downloads.IS_PENDING, 1);
                    Uri uri = activity.getContentResolver().insert(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    if (uri != null) {
                        OutputStream os = activity.getContentResolver()
                                .openOutputStream(uri);
                        if (os != null) {
                            os.write(bytes);
                            os.close();
                        }
                        cv.clear();
                        cv.put(MediaStore.Downloads.IS_PENDING, 0);
                        activity.getContentResolver().update(uri, cv, null, null);
                    }
                } else {
                    // Android 9 이하: 직접 파일 접근
                    java.io.File dir = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS);
                    if (!dir.exists()) dir.mkdirs();
                    java.io.File file = new java.io.File(dir, fileName);
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
                    fos.write(bytes);
                    fos.close();
                }

                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(activity,
                                "✅ 저장 완료 — 다운로드 폴더를 확인하세요",
                                Toast.LENGTH_LONG).show();
                    }
                });

            } catch (final Exception e) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(activity,
                                "저장 실패: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        }

        /**
         * 텍스트를 공유 시트로 내보내기 (백업 공유용)
         * JS: AndroidBridge.shareText("내용", "제목")
         */
        @JavascriptInterface
        public void shareText(final String text, final String title) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("text/plain");
                    intent.putExtra(Intent.EXTRA_TEXT, text);
                    activity.startActivity(
                            Intent.createChooser(intent, title));
                }
            });
        }
    }
}
