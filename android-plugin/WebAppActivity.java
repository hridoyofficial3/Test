package com.hridoy.heartorbit;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

/**
 * Shows one website full-screen with no browser bar, like a normal app.
 * The site address comes in as the intent's data; one recents entry per site.
 */
public class WebAppActivity extends Activity {
  public static final String EXTRA_TITLE = "title";
  private static final int FILE_REQ = 7711;

  private FrameLayout root;
  private WebView web;
  private View customView;
  private WebChromeClient.CustomViewCallback customCb;
  private ValueCallback<Uri[]> fileCb;

  @Override
  protected void onCreate(Bundle saved) {
    super.onCreate(saved);
    Intent in = getIntent();
    Uri data = in == null ? null : in.getData();
    String url = data == null ? null : data.toString();
    if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) {
      finish();
      return;
    }
    String title = in.getStringExtra(EXTRA_TITLE);
    if (title != null && !title.isEmpty()) {
      setTaskDescription(new ActivityManager.TaskDescription(title));
    }

    root = new FrameLayout(this);
    root.setBackgroundColor(0xFF000000);
    web = new WebView(this);
    root.addView(web, new FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    setContentView(root);

    WebSettings s = web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setDatabaseEnabled(true);
    s.setMediaPlaybackRequiresUserGesture(false);
    s.setUseWideViewPort(true);
    s.setLoadWithOverviewMode(true);
    s.setBuiltInZoomControls(true);
    s.setDisplayZoomControls(false);
    s.setAllowFileAccess(false);
    s.setAllowContentAccess(false);
    s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
    // look like normal mobile Chrome (many sites send a worse page to "wv")
    String ua = s.getUserAgentString();
    if (ua != null) s.setUserAgentString(ua.replace("; wv", "").replace("Version/4.0 ", ""));

    CookieManager cm = CookieManager.getInstance();
    cm.setAcceptCookie(true);
    cm.setAcceptThirdPartyCookies(web, true);

    web.setWebViewClient(new WebViewClient() {
      @Override
      public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
        return handleLink(r.getUrl());
      }
    });
    web.setWebChromeClient(new WebChromeClient() {
      @Override
      public void onShowCustomView(View v, CustomViewCallback cb) {
        if (customView != null) {
          cb.onCustomViewHidden();
          return;
        }
        customView = v;
        customCb = cb;
        web.setVisibility(View.GONE);
        root.addView(v, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        immersive(true);
      }

      @Override
      public void onHideCustomView() {
        hideCustom();
      }

      @Override
      public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams p) {
        if (fileCb != null) fileCb.onReceiveValue(null);
        fileCb = cb;
        try {
          startActivityForResult(p.createIntent(), FILE_REQ);
        } catch (Exception e) {
          fileCb = null;
          return false;
        }
        return true;
      }
    });
    web.setDownloadListener((u, agent, disp, mime, len) -> {
      try {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)));
      } catch (Exception ignored) {
      }
    });

    web.loadUrl(url);
  }

  /** http(s) stays inside this window; other schemes (mailto, tel, app links) go to the phone. */
  private boolean handleLink(Uri u) {
    String sc = u.getScheme();
    if (sc == null || sc.equals("http") || sc.equals("https")) return false;
    try {
      if (sc.equals("intent")) {
        Intent it = Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME);
        String fb = it.getStringExtra("browser_fallback_url");
        it.addCategory(Intent.CATEGORY_BROWSABLE);
        it.setComponent(null);
        it.setSelector(null);
        if (it.resolveActivity(getPackageManager()) != null) {
          startActivity(it);
        } else if (fb != null && (fb.startsWith("https://") || fb.startsWith("http://"))) {
          web.loadUrl(fb);
        }
        return true;
      }
      startActivity(new Intent(Intent.ACTION_VIEW, u));
    } catch (Exception ignored) {
    }
    return true;
  }

  private void immersive(boolean on) {
    View d = getWindow().getDecorView();
    if (on) {
      d.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
          | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
          | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
          | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
          | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
          | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    } else {
      d.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }
  }

  private void hideCustom() {
    if (customView == null) return;
    root.removeView(customView);
    customView = null;
    if (customCb != null) {
      customCb.onCustomViewHidden();
      customCb = null;
    }
    web.setVisibility(View.VISIBLE);
    immersive(false);
  }

  @Override
  protected void onActivityResult(int req, int res, Intent data) {
    if (req == FILE_REQ) {
      if (fileCb != null) {
        fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
        fileCb = null;
      }
      return;
    }
    super.onActivityResult(req, res, data);
  }

  @Override
  public void onBackPressed() {
    if (customView != null) {
      hideCustom();
    } else if (web != null && web.canGoBack()) {
      web.goBack();
    } else {
      finish();
    }
  }

  @Override
  protected void onDestroy() {
    if (web != null) {
      root.removeAllViews();
      web.destroy();
      web = null;
    }
    super.onDestroy();
  }
}
