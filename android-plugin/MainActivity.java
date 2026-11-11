package com.hridoy.heartorbit;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    registerPlugin(BrowserPickerPlugin.class);
    registerPlugin(WebAppPlugin.class);
    super.onCreate(savedInstanceState);
  }
}
