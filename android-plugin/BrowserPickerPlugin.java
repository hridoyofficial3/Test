package com.hridoy.heartorbit;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lets the web UI (a) list the browsers installed on the phone and
 * (b) open a link in one specific browser by package name.
 * If that browser is missing, the link opens normally and {fallback:true} is returned.
 */
@CapacitorPlugin(name = "BrowserPicker")
public class BrowserPickerPlugin extends Plugin {

  @PluginMethod
  public void getBrowsers(PluginCall call) {
    JSArray arr = new JSArray();
    try {
      PackageManager pm = getContext().getPackageManager();
      Intent probe = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"));
      probe.addCategory(Intent.CATEGORY_BROWSABLE);
      List<ResolveInfo> list = pm.queryIntentActivities(probe, PackageManager.MATCH_ALL);
      Set<String> seen = new HashSet<>();
      String me = getContext().getPackageName();
      for (ResolveInfo ri : list) {
        if (ri.activityInfo == null) continue;
        String pkg = ri.activityInfo.packageName;
        if (pkg == null || pkg.equals(me) || !seen.add(pkg)) continue;
        JSObject o = new JSObject();
        o.put("pkg", pkg);
        o.put("label", String.valueOf(ri.loadLabel(pm)));
        arr.put(o);
      }
    } catch (Exception ignored) {
    }
    JSObject res = new JSObject();
    res.put("browsers", arr);
    call.resolve(res);
  }

  @PluginMethod
  public void open(PluginCall call) {
    String url = call.getString("url");
    String pkg = call.getString("pkg");
    if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
      call.reject("bad url");
      return;
    }
    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
    i.addCategory(Intent.CATEGORY_BROWSABLE);
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

    boolean opened = false;
    boolean fallback = false;
    if (pkg != null && !pkg.isEmpty()) {
      try {
        i.setPackage(pkg);
        getContext().startActivity(i);
        opened = true;
      } catch (ActivityNotFoundException | SecurityException e) {
        fallback = true;
      }
    }
    if (!opened) {
      try {
        i.setPackage(null);
        getContext().startActivity(i);
      } catch (Exception e) {
        call.reject("no browser");
        return;
      }
    }
    JSObject res = new JSObject();
    res.put("fallback", fallback);
    call.resolve(res);
  }
}
