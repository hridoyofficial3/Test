package com.hridoy.heartorbit;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.util.Base64;

import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** open(): show a site as a full-screen app window. addShortcut(): pin it to the home screen. */
@CapacitorPlugin(name = "WebApp")
public class WebAppPlugin extends Plugin {

  private static boolean okUrl(String u) {
    return u != null && u.length() <= 2000 && (u.startsWith("https://") || u.startsWith("http://"));
  }

  private static String cleanTitle(String t, String url) {
    if (t == null) t = "";
    t = t.trim();
    if (t.length() > 60) t = t.substring(0, 60);
    if (t.isEmpty()) t = Uri.parse(url).getHost() == null ? "Site" : Uri.parse(url).getHost();
    return t;
  }

  private Intent siteIntent(Context ctx, String url, String title) {
    Intent i = new Intent(ctx, WebAppActivity.class);
    i.setAction(Intent.ACTION_VIEW);
    i.setData(Uri.parse(url));
    i.putExtra(WebAppActivity.EXTRA_TITLE, title);
    return i;
  }

  @PluginMethod
  public void open(PluginCall call) {
    String url = call.getString("url");
    if (!okUrl(url)) {
      call.reject("bad url");
      return;
    }
    String title = cleanTitle(call.getString("title"), url);
    Intent i = siteIntent(getContext(), url, title);
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
    try {
      getActivity().startActivity(i);
      call.resolve();
    } catch (Exception e) {
      call.reject("cannot open");
    }
  }

  @PluginMethod
  public void addShortcut(final PluginCall call) {
    final String url = call.getString("url");
    if (!okUrl(url)) {
      call.reject("bad url");
      return;
    }
    final String title = cleanTitle(call.getString("title"), url);
    final String iconData = call.getString("iconData");
    final String iconUrl = call.getString("iconUrl");
    final Context ctx = getContext();
    if (!ShortcutManagerCompat.isRequestPinShortcutSupported(ctx)) {
      call.reject("unsupported");
      return;
    }
    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          Bitmap src = null;
          try {
            if (iconData != null && iconData.startsWith("data:image/") && iconData.contains(",")) {
              byte[] raw = Base64.decode(iconData.substring(iconData.indexOf(',') + 1), Base64.DEFAULT);
              src = BitmapFactory.decodeByteArray(raw, 0, raw.length);
            } else if (iconUrl != null && iconUrl.startsWith("https://")) {
              src = download(iconUrl);
            }
          } catch (Exception ignored) {
          }
          Bitmap icon = makeIcon(src, title);
          String id = "webapp-" + Integer.toHexString(url.hashCode());
          String shortLabel = title.length() > 24 ? title.substring(0, 24) : title;
          ShortcutInfoCompat info = new ShortcutInfoCompat.Builder(ctx, id)
              .setShortLabel(shortLabel)
              .setLongLabel(title)
              .setIcon(IconCompat.createWithAdaptiveBitmap(icon))
              .setIntent(siteIntent(ctx, url, title))
              .build();
          boolean ok = ShortcutManagerCompat.requestPinShortcut(ctx, info, null);
          JSObject res = new JSObject();
          res.put("requested", ok);
          if (ok) call.resolve(res);
          else call.reject("not added");
        } catch (Exception e) {
          call.reject("failed");
        }
      }
    }).start();
  }

  private static Bitmap download(String u) throws Exception {
    HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
    c.setConnectTimeout(6000);
    c.setReadTimeout(6000);
    try (InputStream in = c.getInputStream()) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buf = new byte[8192];
      int n;
      while ((n = in.read(buf)) > 0) {
        out.write(buf, 0, n);
        if (out.size() > 1000000) return null;
      }
      byte[] b = out.toByteArray();
      return BitmapFactory.decodeByteArray(b, 0, b.length);
    } finally {
      c.disconnect();
    }
  }

  /** 432px adaptive-icon canvas; artwork kept inside the central safe zone. */
  private static Bitmap makeIcon(Bitmap src, String title) {
    final int S = 432;
    Bitmap out = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(out);
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    if (src != null) {
      c.drawColor(Color.WHITE);
      int s = 250;
      c.drawBitmap(src, null, new Rect((S - s) / 2, (S - s) / 2, (S + s) / 2, (S + s) / 2), p);
    } else {
      int h = Math.abs(title.hashCode()) % 360;
      c.drawColor(Color.HSVToColor(new float[] {h, 0.55f, 0.62f}));
      p.setColor(Color.WHITE);
      p.setTypeface(Typeface.DEFAULT_BOLD);
      p.setTextSize(170f);
      p.setTextAlign(Paint.Align.CENTER);
      String letter = new String(Character.toChars(title.codePointAt(0))).toUpperCase();
      float y = S / 2f - (p.descent() + p.ascent()) / 2f;
      c.drawText(letter, S / 2f, y, p);
    }
    return out;
  }
}
