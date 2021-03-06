package com.pichillilorenzo.flutter_inappwebview.in_app_browser;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.SearchView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.pichillilorenzo.flutter_inappwebview.in_app_webview.InAppWebView;
import com.pichillilorenzo.flutter_inappwebview.in_app_webview.InAppWebViewChromeClient;
import com.pichillilorenzo.flutter_inappwebview.in_app_webview.InAppWebViewOptions;
import com.pichillilorenzo.flutter_inappwebview.InAppWebViewMethodHandler;
import com.pichillilorenzo.flutter_inappwebview.R;
import com.pichillilorenzo.flutter_inappwebview.Shared;
import com.pichillilorenzo.flutter_inappwebview.types.URLRequest;
import com.pichillilorenzo.flutter_inappwebview.types.UserScript;
import com.pichillilorenzo.flutter_inappwebview.Util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.flutter.plugin.common.MethodChannel;

public class InAppBrowserActivity extends AppCompatActivity implements InAppBrowserDelegate {

  static final String LOG_TAG = "InAppBrowserActivity";
  public MethodChannel channel;
  public Integer windowId;
  public String id;
  public InAppWebView webView;
  public ActionBar actionBar;
  public Menu menu;
  public SearchView searchView;
  public InAppBrowserOptions options;
  public ProgressBar progressBar;
  public boolean isHidden = false;
  public String fromActivity;
  private List<ActivityResultListener> activityResultListeners = new ArrayList<>();
  public InAppWebViewMethodHandler methodCallDelegate;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    if (savedInstanceState != null) {
      return;
    }

    Bundle b = getIntent().getExtras();
    id = b.getString("id");
    windowId = b.getInt("windowId");

    channel = new MethodChannel(Shared.messenger, "com.pichillilorenzo/flutter_inappbrowser_" + id);

    setContentView(R.layout.activity_web_view);

    webView = findViewById(R.id.webView);
    webView.windowId = windowId;
    webView.inAppBrowserDelegate = this;
    webView.channel = channel;

    methodCallDelegate = new InAppWebViewMethodHandler(webView);
    channel.setMethodCallHandler(methodCallDelegate);

    fromActivity = b.getString("fromActivity");

    Map<String, Object> optionsMap = (Map<String, Object>) b.getSerializable("options");
    Map<String, Object> contextMenu = (Map<String, Object>) b.getSerializable("contextMenu");
    List<Map<String, Object>> initialUserScripts = (List<Map<String, Object>>) b.getSerializable("initialUserScripts");

    options = new InAppBrowserOptions();
    options.parse(optionsMap);

    InAppWebViewOptions webViewOptions = new InAppWebViewOptions();
    webViewOptions.parse(optionsMap);
    webView.options = webViewOptions;
    webView.contextMenu = contextMenu;

    List<UserScript> userScripts = new ArrayList<>();
    if (initialUserScripts != null) {
      for (Map<String, Object> initialUserScript : initialUserScripts) {
        userScripts.add(UserScript.fromMap(initialUserScript));
      }
    }
    webView.userContentController.addUserOnlyScripts(userScripts);

    actionBar = getSupportActionBar();

    prepareView();

    if (windowId != -1) {
      Message resultMsg = InAppWebViewChromeClient.windowWebViewMessages.get(windowId);
      if (resultMsg != null) {
        ((WebView.WebViewTransport) resultMsg.obj).setWebView(webView);
        resultMsg.sendToTarget();
      }
    } else {
      String initialFile = b.getString("initialFile");
      Map<String, Object> initialUrlRequest = (Map<String, Object>) b.getSerializable("initialUrlRequest");
      String initialData = b.getString("initialData");
      if (initialFile != null) {
        try {
          webView.loadFile(initialFile);
        } catch (IOException e) {
          e.printStackTrace();
          Log.e(LOG_TAG, initialFile + " asset file cannot be found!", e);
          return;
        }
      }
      else if (initialData != null) {
        String mimeType = b.getString("mimeType");
        String encoding = b.getString("encoding");
        String baseUrl = b.getString("baseUrl");
        String historyUrl = b.getString("historyUrl");
        webView.loadDataWithBaseURL(baseUrl, initialData, mimeType, encoding, historyUrl);
      }
      else if (initialUrlRequest != null) {
        URLRequest urlRequest = URLRequest.fromMap(initialUrlRequest);
        webView.loadUrl(urlRequest);
      }
    }

    onBrowserCreated();
  }

  public void onBrowserCreated() {
    Map<String, Object> obj = new HashMap<>();
    channel.invokeMethod("onBrowserCreated", obj);
  }

  private void prepareView() {

    webView.prepare();

    if (options.hidden)
      hide();
    else
      show();

    progressBar = findViewById(R.id.progressBar);

    if (options.hideProgressBar)
      progressBar.setMax(0);
    else
      progressBar.setMax(100);

    actionBar.setDisplayShowTitleEnabled(!options.hideTitleBar);

    if (options.hideToolbarTop)
      actionBar.hide();

    if (options.toolbarTopBackgroundColor != null && !options.toolbarTopBackgroundColor.isEmpty())
      actionBar.setBackgroundDrawable(new ColorDrawable(Color.parseColor(options.toolbarTopBackgroundColor)));

    if (options.toolbarTopFixedTitle != null && !options.toolbarTopFixedTitle.isEmpty())
      actionBar.setTitle(options.toolbarTopFixedTitle);

  }

  @Override
  public boolean onCreateOptionsMenu(Menu m) {
    menu = m;

    MenuInflater inflater = getMenuInflater();
    // Inflate menu to add items to action bar if it is present.
    inflater.inflate(R.menu.menu_main, menu);

    searchView = (SearchView) menu.findItem(R.id.menu_search).getActionView();
    searchView.setFocusable(true);

    if (options.hideUrlBar)
      menu.findItem(R.id.menu_search).setVisible(false);

    searchView.setQuery(webView.getUrl(), false);

    if (options.toolbarTopFixedTitle == null || options.toolbarTopFixedTitle.isEmpty())
      actionBar.setTitle(webView.getTitle());

    searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
      @Override
      public boolean onQueryTextSubmit(String query) {
        if (!query.isEmpty()) {
          webView.loadUrl(query);
          searchView.setQuery("", false);
          searchView.setIconified(true);
          return true;
        }
        return false;
      }

      @Override
      public boolean onQueryTextChange(String newText) {
        return false;
      }

    });

    searchView.setOnCloseListener(new SearchView.OnCloseListener() {
      @Override
      public boolean onClose() {
        if (searchView.getQuery().toString().isEmpty())
          searchView.setQuery(webView.getUrl(), false);
        return false;
      }
    });

    searchView.setOnQueryTextFocusChangeListener(new View.OnFocusChangeListener() {
      @Override
      public void onFocusChange(View view, boolean b) {
        if (!b) {
          searchView.setQuery("", false);
          searchView.setIconified(true);
        }
      }
    });

    return true;
  }

  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if ((keyCode == KeyEvent.KEYCODE_BACK)) {
      if (canGoBack())
        goBack();
      else if (options.closeOnCannotGoBack)
        close(null);
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  public void close(final MethodChannel.Result result) {
    Map<String, Object> obj = new HashMap<>();
    channel.invokeMethod("onExit", obj);

    dispose();

    if (result != null) {
      result.success(true);
    }
  }

  public void reload() {
    if (webView != null)
      webView.reload();
  }

  public void goBack() {
    if (webView != null && canGoBack())
      webView.goBack();
  }

  public boolean canGoBack() {
    if (webView != null)
      return webView.canGoBack();
    return false;
  }

  public void goForward() {
    if (webView != null && canGoForward())
      webView.goForward();
  }

  public boolean canGoForward() {
    if (webView != null)
      return webView.canGoForward();
    return false;
  }

  public void hide() {
    try {
      isHidden = true;
      Intent openActivity = new Intent(this, Class.forName(fromActivity));
      openActivity.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
      startActivityIfNeeded(openActivity, 0);
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
      Log.d(LOG_TAG, e.getMessage());
    }
  }

  public void show() {
    isHidden = false;
    Intent openActivity = new Intent(this, InAppBrowserActivity.class);
    openActivity.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
    startActivityIfNeeded(openActivity, 0);
  }

  public void goBackButtonClicked(MenuItem item) {
    goBack();
  }

  public void goForwardButtonClicked(MenuItem item) {
    goForward();
  }

  public void shareButtonClicked(MenuItem item) {
    Intent share = new Intent(Intent.ACTION_SEND);
    share.setType("text/plain");
    share.putExtra(Intent.EXTRA_TEXT, webView.getUrl());
    startActivity(Intent.createChooser(share, "Share"));
  }

  public void reloadButtonClicked(MenuItem item) {
    reload();
  }

  public void closeButtonClicked(MenuItem item) {
    close(null);
  }

  public void setOptions(InAppBrowserOptions newOptions, HashMap<String, Object> newOptionsMap) {

    InAppWebViewOptions newInAppWebViewOptions = new InAppWebViewOptions();
    newInAppWebViewOptions.parse(newOptionsMap);
    webView.setOptions(newInAppWebViewOptions, newOptionsMap);

    if (newOptionsMap.get("hidden") != null && options.hidden != newOptions.hidden) {
      if (newOptions.hidden)
        hide();
      else
        show();
    }

    if (newOptionsMap.get("hideProgressBar") != null && options.hideProgressBar != newOptions.hideProgressBar && progressBar != null) {
      if (newOptions.hideProgressBar)
        progressBar.setMax(0);
      else
        progressBar.setMax(100);
    }

    if (newOptionsMap.get("hideTitleBar") != null && options.hideTitleBar != newOptions.hideTitleBar)
      actionBar.setDisplayShowTitleEnabled(!newOptions.hideTitleBar);

    if (newOptionsMap.get("hideToolbarTop") != null && options.hideToolbarTop != newOptions.hideToolbarTop) {
      if (newOptions.hideToolbarTop)
        actionBar.hide();
      else
        actionBar.show();
    }

    if (newOptionsMap.get("toolbarTopBackgroundColor") != null && !Util.objEquals(options.toolbarTopBackgroundColor, newOptions.toolbarTopBackgroundColor) &&
            !newOptions.toolbarTopBackgroundColor.isEmpty())
      actionBar.setBackgroundDrawable(new ColorDrawable(Color.parseColor(newOptions.toolbarTopBackgroundColor)));

    if (newOptionsMap.get("toolbarTopFixedTitle") != null && !Util.objEquals(options.toolbarTopFixedTitle, newOptions.toolbarTopFixedTitle) && !newOptions.toolbarTopFixedTitle.isEmpty())
      actionBar.setTitle(newOptions.toolbarTopFixedTitle);

    if (newOptionsMap.get("hideUrlBar") != null && options.hideUrlBar != newOptions.hideUrlBar) {
      if (newOptions.hideUrlBar)
        menu.findItem(R.id.menu_search).setVisible(false);
      else
        menu.findItem(R.id.menu_search).setVisible(true);
    }

    options = newOptions;
  }

  public Map<String, Object> getOptions() {
    Map<String, Object> webViewOptionsMap = webView.getOptions();
    if (options == null || webViewOptionsMap == null)
      return null;

    Map<String, Object> optionsMap = options.getRealOptions(this);
    optionsMap.putAll(webViewOptionsMap);
    return optionsMap;
  }

  @Override
  public Activity getActivity() {
    return this;
  }

  @Override
  public void didChangeTitle(String title) {
    if (options.toolbarTopFixedTitle == null || options.toolbarTopFixedTitle.isEmpty()) {
      actionBar.setTitle(title);
    }
  }

  @Override
  public void didStartNavigation(String url) {
    progressBar.setProgress(0);
    searchView.setQuery(url, false);
  }

  @Override
  public void didUpdateVisitedHistory(String url) {
    searchView.setQuery(url, false);
  }

  @Override
  public void didFinishNavigation(String url) {
    searchView.setQuery(url, false);
    progressBar.setProgress(0);
  }

  @Override
  public void didFailNavigation(String url, int errorCode, String description) {
    progressBar.setProgress(0);
  }

  @Override
  public void didChangeProgress(int progress) {
    progressBar.setVisibility(View.VISIBLE);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      progressBar.setProgress(progress, true);
    } else {
      progressBar.setProgress(progress);
    }
    if (progress == 100) {
      progressBar.setVisibility(View.GONE);
    }
  }

  public List<ActivityResultListener> getActivityResultListeners() {
    return activityResultListeners;
  }

  @Override
  protected void onActivityResult (int requestCode,
                                   int resultCode,
                                   Intent data) {
    for (ActivityResultListener listener : activityResultListeners) {
      if (listener.onActivityResult(requestCode, resultCode, data)) {
        return;
      }
    }
    super.onActivityResult(requestCode, resultCode, data);
  }

  public void dispose() {
    channel.setMethodCallHandler(null);
    activityResultListeners.clear();
    if (methodCallDelegate != null) {
      methodCallDelegate.dispose();
      methodCallDelegate = null;
    }
    if (webView != null) {
      if (Shared.activityPluginBinding != null) {
        Shared.activityPluginBinding.removeActivityResultListener(webView.inAppWebViewChromeClient);
      }
      ViewGroup vg = (ViewGroup) (webView.getParent());
      if (vg != null) {
        vg.removeView(webView);
      }
      webView.setWebChromeClient(new WebChromeClient());
      webView.setWebViewClient(new WebViewClient() {
        public void onPageFinished(WebView view, String url) {
          webView.dispose();
          webView.destroy();
          webView = null;
        }
      });
      webView.loadUrl("about:blank");
      finish();
    }
  }

  @Override
  public void onDestroy() {
    dispose();
    super.onDestroy();
  }
}
