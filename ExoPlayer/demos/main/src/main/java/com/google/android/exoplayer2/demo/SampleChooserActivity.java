/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.demo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.JsonReader;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.offline.DownloadService;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSourceInputStream;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** An activity for selecting from a list of media samples. */
public class SampleChooserActivity extends Activity
    implements DownloadTracker.Listener, OnChildClickListener {

  private static final String TAG = "SampleChooserActivity";

  private boolean loadData = true;
  private boolean useExtensionRenderers;
  private DownloadTracker downloadTracker;
  private SampleAdapter sampleAdapter;
  private MenuItem preferExtensionDecodersMenuItem;
  private MenuItem randomAbrMenuItem;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.sample_chooser_activity);
    final SampleChooserActivity self = this;
    sampleAdapter = new SampleAdapter();
    ExpandableListView sampleListView = findViewById(R.id.sample_list);
    sampleListView.setAdapter(sampleAdapter);
    sampleListView.setOnChildClickListener(this);

    try {
      new HTTPListener(this, this.getApplicationContext(),new WSListener.Reader() {
        public void read(String txt) throws Exception {
          parseJSON(self,txt);
        }
      },true);
    } catch(Exception e) {
    }

    loadInfo();

    new WSListener(new WSListener.Reader() {
      public void read(String txt) throws Exception {
        parseJSON(self,txt);
      }
    });

  }

  @Override
  public boolean onKeyDown(int keyCode, android.view.KeyEvent event)
  {
    if (keyCode == KeyEvent.KEYCODE_0 || keyCode == KeyEvent.KEYCODE_MENU) {
      loadData = true;
      loadInfo();
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }


  private String castAPIResponses(String txt) throws Exception {

    org.json.JSONObject jsonObj = null;
    try {

      try {
          jsonObj = new org.json.JSONObject(txt);
      } catch(Exception e) {
          // Avoid some socket server situations
          if(txt.startsWith("\"") && txt.endsWith("\"")) {
              txt = txt.substring(1,txt.length()-1);
              txt = txt.replaceAll("\\\\", "");
              jsonObj = new org.json.JSONObject(txt);
          }
          else {
              // Rethrow the error
              throw e;
          }
      }

      org.json.JSONObject parsedJson = new org.json.JSONObject();
      parsedJson.putOpt("name",jsonObj.optString("key",jsonObj.optString("wrid",jsonObj.optString("title",null))));
      parsedJson.putOpt("uri",jsonObj.optString("url",jsonObj.optString("uri",null)));

      if(parsedJson.optString("uri",null)==null || parsedJson.optString("uri",null).equals("null")) {
        throw new Exception("NO URI found");
      }

      String systemEnc = jsonObj.optString("type");
      org.json.JSONObject licenseParams = jsonObj.optJSONObject("license");
      if(licenseParams!=null) {
        String drm = licenseParams.optString("drm",null);
        if(drm!=null && drm.length()>0) {
          systemEnc = drm;
        }
      }
      else {
        String player = jsonObj.optString("player",null);
        if(player!=null && player.length()>0) {
          String[] sp = player.split(":");
          systemEnc = sp[sp.length-1];
        }
      }

      if(systemEnc!=null) {
        systemEnc = systemEnc.toLowerCase();
      }

      switch(systemEnc) {
        case "wvm":
        case "widevine":
          parsedJson.putOpt("drm_scheme","widevine");
          break;
        case "ss-pr":
        case "pr":
        case "playready":
        case "mss":
          parsedJson.putOpt("drm_scheme","playready");
          break;
        case "wvc":
        case "wvn":
          throw new Exception("DRM scheme ["+systemEnc+"] not handled");
      }

      String licenseUrl = null;
      String customData = null;
      // Kami
      if(licenseParams!=null) {
        licenseUrl = licenseParams.optString("url",null);
        customData = licenseParams.optString("custom_data",null);
        if(licenseUrl!=null && licenseUrl.equals("null")) licenseUrl = null;
        if(customData!=null && customData.equals("null")) customData = null;
      }
      // Gizmo ( check parameters )
      if(licenseUrl==null ||licenseUrl.length()==0) {
        licenseUrl = jsonObj.optString("license_url",null);
        if(licenseUrl!=null && licenseUrl.equals("null")) licenseUrl = null;
      }
      if(customData==null ||customData.length()==0) {
        customData = jsonObj.optString("custom_data",null);
        if(customData!=null && customData.equals("null")) customData = null;
      }
      // JS ( check parameters )
      if(licenseUrl==null ||licenseUrl.length()==0) {
        licenseUrl = jsonObj.optString("license",null);
        if(licenseUrl!=null && licenseUrl.equals("null")) licenseUrl = null;
      }
      if(customData==null ||customData.length()==0) {
        JSONObject params = jsonObj.optJSONObject("params");
        if(params!=null) {
          customData = params.optString("customData",null);
          if(customData!=null && customData.equals("null")) customData = null;
        }
      }
      // ExoFormat ( check parameters )
      if(licenseUrl==null ||licenseUrl.length()==0) {
        licenseUrl = jsonObj.optString("drm_license_url",null);
        if(licenseUrl!=null && licenseUrl.equals("null")) licenseUrl = null;
      }
      if(customData==null ||customData.length()==0) {
        JSONObject params = jsonObj.optJSONObject("drm_key_request_properties");
        if(params!=null) {
          customData = params.optString("PRCustomData",null);
          if(customData!=null && customData.equals("null")) customData = null;
        }
      }

      if(licenseUrl!=null && licenseUrl.length()>0) {
        parsedJson.putOpt("drm_license_url",licenseUrl);
      }
      if(customData!=null && customData.length()>0) {
        // customDataObj
        org.json.JSONObject customDataObj = new org.json.JSONObject();
        customDataObj.putOpt("PRCustomData",customData);
        parsedJson.putOpt("drm_key_request_properties",customDataObj);
      }

      if(parsedJson.optString("uri",null).indexOf(".mpd")>0) {
        parsedJson.putOpt("extension","mpd");
      }

      JSONArray subtitlesArr = jsonObj.optJSONArray("all_"+PlayerActivity.SUBTITLES_URL+"s");
      if(subtitlesArr==null) subtitlesArr = jsonObj.optJSONArray(PlayerActivity.SUBTITLES_URL+"s");
      if(subtitlesArr==null) {
        JSONObject subtitle = jsonObj.optJSONObject(PlayerActivity.SUBTITLES_URL);
        if(subtitle==null) subtitle = jsonObj.optJSONObject(PlayerActivity.SUBTITLES_URL+"s");
        if(subtitle!=null) {
          String subtitleUrl = subtitle.optString("url",null);
          if(subtitleUrl!=null && subtitleUrl.length()>0 && !subtitleUrl.equals("null")) {
            parsedJson.putOpt(PlayerActivity.SUBTITLES_URL,subtitleUrl);
          }
          else {
            parsedJson.putOpt(PlayerActivity.SUBTITLES_URL,subtitle);
          }
        }
      }
      else {
        parsedJson.putOpt(PlayerActivity.SUBTITLES_URL,subtitlesArr);
      }

      JSONObject audio = jsonObj.optJSONObject("all_"+PlayerActivity.AUDIO_URL+"s");
      if(audio==null) audio = jsonObj.optJSONObject(PlayerActivity.AUDIO_URL+"s");
      if(audio==null) audio = jsonObj.optJSONObject(PlayerActivity.AUDIO_URL);
      if(audio!=null) {
        String audioUrl = audio.optString("url",null);
        if(audioUrl!=null && audioUrl.length()>0 && !audioUrl.equals("null")) {
          parsedJson.putOpt(PlayerActivity.AUDIO_URL, audioUrl);
        }
        else {
          parsedJson.putOpt(PlayerActivity.AUDIO_URL,audio);
        }
      }

      txt = parsedJson.toString();

    } catch(Exception e) {

      // If the object was previously casted we'll NOT throw the exception and we'll try to play
      if(jsonObj==null || jsonObj.optString("uri",null)==null) {
        Log.e(TAG,txt);
        throw e;
      }

    }

    return txt;
  }

  private void parseJSON(SampleChooserActivity self, String txt) throws Exception {
    String formattedTxt = castAPIResponses(txt);
    JsonReader reader = new JsonReader(new java.io.StringReader(formattedTxt));
    SampleListLoader loaderTask = new SampleListLoader();
    final Sample sample = loaderTask.readEntry(reader, false);

    self.runOnUiThread(new Runnable() {
      public void run() {
        new android.os.Handler().postDelayed(
                new Runnable() {
                  public void run() {
                    if (UriSample.class.isInstance(sample)) {
                      Toast.makeText(getApplicationContext(), ((UriSample)sample).uri.toString().split("\\?")[0], Toast.LENGTH_LONG).show();
                    }
                  }
                },
                1000);
      }
    });


    startActivity(sample.buildIntent(self,
            isNonNullAndChecked(preferExtensionDecodersMenuItem),
            isNonNullAndChecked(randomAbrMenuItem)
                    ? PlayerActivity.ABR_ALGORITHM_RANDOM
                    : PlayerActivity.ABR_ALGORITHM_DEFAULT));
  }

  public String getLocalIpAddress(boolean checkIsUp, boolean onlyIP){
    String ret = "";
    String suffix = "";
    try {

      for (java.util.Enumeration<java.net.NetworkInterface> en = java.net.NetworkInterface.getNetworkInterfaces();
           en.hasMoreElements(); ) {
        java.net.NetworkInterface intf = en.nextElement();
        Boolean isUp;
        try {
          isUp = !intf.isUp();
        } catch(SocketException e) {
          isUp = false;
        }
        if (checkIsUp && !isUp) {
          continue;
        }
        for (java.util.Enumeration<java.net.InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
          java.net.InetAddress inetAddress = enumIpAddr.nextElement();
          if (!inetAddress.isLoopbackAddress()) {
            String str = inetAddress.getHostAddress();
            if (str == null || str.length() <= 0 || str.contains(":") || !str.contains(".")) {
              // It's a IPV6 address
              continue;
            }
            if (ret.length() > 0) {
              ret = ret + " " + str;
            } else {
              ret = str;
              // Only 1 suffix
              if(onlyIP == false) {
                suffix = " "+HTTPListener.getBaseURL(str);
              }
            }
          }
        }
      }
    } catch (Exception ex) {
      Log.e("IP Address", ex.toString());
    }

    if(checkIsUp==true) {
      android.net.ConnectivityManager connectivityManager
              = (android.net.ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
      android.net.NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
      if (activeNetworkInfo != null && activeNetworkInfo.isConnectedOrConnecting()) {
        // Check again but without using isUp
        return getLocalIpAddress(false, onlyIP);
      }
    }

    return ret+suffix;
  }

  private String getVersionInfo() {
      String info = "";
      try {
        android.content.pm.ApplicationInfo appInfo = getApplicationContext().getPackageManager().getApplicationInfo(getApplicationContext().getPackageName(), 0);
        long time = new java.io.File(appInfo.sourceDir).lastModified();
        java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat("yyMMddHHmm");
        info = formatter.format(time) + " ";
      } catch (Exception e) {
      }
      return "["+info+BuildConfig.VERSION_NAME+"]";
  }
  private void loadInfo() {
    if(!loadData)
    {
      return;
    }
    setTitle(getLocalIpAddress(true, false)+" | 0-Refresh "+getVersionInfo());
    loadData = false;
    Intent intent = getIntent();
    String dataUri = intent.getDataString();
    String[] uris;
    if (dataUri != null) {
      uris = new String[] {dataUri};
    } else {
      ArrayList<String> uriList = new ArrayList<>();
      AssetManager assetManager = getAssets();
      try {
        for (String asset : assetManager.list("")) {
          if (asset.endsWith(".exolist.json")) {
            uriList.add("asset:///" + asset);
          }
        }
      } catch (IOException e) {
        Toast.makeText(getApplicationContext(), R.string.sample_list_load_error, Toast.LENGTH_LONG)
            .show();
      }
      uriList.add("http://10.12.96.25/smarttv/json/load.php?f=exoplayer.json&fmt=exo");
      uris = new String[uriList.size()];
      uriList.toArray(uris);
      Arrays.sort(uris,java.util.Collections.reverseOrder());
    }

    DemoApplication application = (DemoApplication) getApplication();
    useExtensionRenderers = application.useExtensionRenderers();
    downloadTracker = application.getDownloadTracker();
    SampleListLoader loaderTask = new SampleListLoader();
    loaderTask.execute(uris);

    // Start the download service if it should be running but it's not currently.
    // Starting the service in the foreground causes notification flicker if there is no scheduled
    // action. Starting it in the background throws an exception if the app is in the background too
    // (e.g. if device screen is locked).
    try {
      DownloadService.start(this, DemoDownloadService.class);
    } catch (IllegalStateException e) {
      DownloadService.startForeground(this, DemoDownloadService.class);
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.sample_chooser_menu, menu);
    preferExtensionDecodersMenuItem = menu.findItem(R.id.prefer_extension_decoders);
    preferExtensionDecodersMenuItem.setVisible(useExtensionRenderers);
    randomAbrMenuItem = menu.findItem(R.id.random_abr);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    item.setChecked(!item.isChecked());
    return true;
  }

  @Override
  public void onStart() {
    super.onStart();
    downloadTracker.addListener(this);
    sampleAdapter.notifyDataSetChanged();
  }

  @Override
  public void onStop() {
    downloadTracker.removeListener(this);
    super.onStop();
  }

  @Override
  public void onDownloadsChanged() {
    sampleAdapter.notifyDataSetChanged();
  }

  private void onSampleGroups(final List<SampleGroup> groups, boolean sawError) {
    if (sawError) {
      Toast.makeText(getApplicationContext(), R.string.sample_list_load_error, Toast.LENGTH_LONG)
          .show();
    }
    sampleAdapter.setSampleGroups(groups);
  }

  @Override
  public boolean onChildClick(
      ExpandableListView parent, View view, int groupPosition, int childPosition, long id) {
    Sample sample = (Sample) view.getTag();
    startActivity(
        sample.buildIntent(
            /* context= */ this,
            isNonNullAndChecked(preferExtensionDecodersMenuItem),
            isNonNullAndChecked(randomAbrMenuItem)
                ? PlayerActivity.ABR_ALGORITHM_RANDOM
                : PlayerActivity.ABR_ALGORITHM_DEFAULT));
    return true;
  }

  private void onSampleDownloadButtonClicked(Sample sample) {
    int downloadUnsupportedStringId = getDownloadUnsupportedStringId(sample);
    if (downloadUnsupportedStringId != 0) {
      Toast.makeText(getApplicationContext(), downloadUnsupportedStringId, Toast.LENGTH_LONG)
          .show();
    } else {
      UriSample uriSample = (UriSample) sample;
      downloadTracker.toggleDownload(this, sample.name, uriSample.uri, uriSample.extension);
    }
  }

  private int getDownloadUnsupportedStringId(Sample sample) {
    if (sample instanceof PlaylistSample) {
      return R.string.download_playlist_unsupported;
    }
    UriSample uriSample = (UriSample) sample;
    if (uriSample.drmInfo != null) {
      return R.string.download_drm_unsupported;
    }
    if (uriSample.adTagUri != null) {
      return R.string.download_ads_unsupported;
    }
    String scheme = uriSample.uri.getScheme();
    if (!("http".equals(scheme) || "https".equals(scheme))) {
      return R.string.download_scheme_unsupported;
    }
    return 0;
  }

  private static boolean isNonNullAndChecked(@Nullable MenuItem menuItem) {
    // Temporary workaround for layouts that do not inflate the options menu.
    return menuItem != null && menuItem.isChecked();
  }

  private final class SampleListLoader extends AsyncTask<String, Void, List<SampleGroup>> {

    private boolean sawError;

    @Override
    protected List<SampleGroup> doInBackground(String... uris) {
      List<SampleGroup> result = new ArrayList<>();
      Context context = getApplicationContext();
      String userAgent = Util.getUserAgent(context, "ExoPlayerDemo");
      DataSource dataSource =
          new DefaultDataSource(context, userAgent, /* allowCrossProtocolRedirects= */ false);
      for (String uri : uris) {
        DataSpec dataSpec = new DataSpec(Uri.parse(uri));
        InputStream inputStream = new DataSourceInputStream(dataSource, dataSpec);
        try {
          readSampleGroups(new JsonReader(new InputStreamReader(inputStream, "UTF-8")), result);
        } catch (Exception e) {
          Log.e(TAG, "Error loading sample list: " + uri, e);
          sawError = true;
        } finally {
          Util.closeQuietly(dataSource);
        }
      }
      return result;
    }

    @Override
    protected void onPostExecute(List<SampleGroup> result) {
      onSampleGroups(result, sawError);
    }

    private void readSampleGroups(JsonReader reader, List<SampleGroup> groups) throws IOException {
      reader.beginArray();
      while (reader.hasNext()) {
        readSampleGroup(reader, groups);
      }
      reader.endArray();
    }

    private void readSampleGroup(JsonReader reader, List<SampleGroup> groups) throws IOException {
      String groupName = "";
      ArrayList<Sample> samples = new ArrayList<>();

      reader.beginObject();
      while (reader.hasNext()) {
        String name = reader.nextName();
        switch (name) {
          case "name":
            groupName = reader.nextString();
            break;
          case "samples":
            reader.beginArray();
            while (reader.hasNext()) {
              samples.add(readEntry(reader, false));
            }
            reader.endArray();
            break;
          case "_comment":
            reader.nextString(); // Ignore.
            break;
          default:
            throw new ParserException("Unsupported name: " + name);
        }
      }
      reader.endObject();

      SampleGroup group = getGroup(groupName, groups);
      group.samples.addAll(samples);
    }

    private Sample readEntry(JsonReader reader, boolean insidePlaylist) throws IOException {
      String sampleName = null;
      Uri uri = null;
      String extension = null;
      HashMap<String, String> audioMap = new HashMap<String, String>();
      HashMap<String, String> subtitleMap = new HashMap<String, String>();
      String drmScheme = null;
      String drmLicenseUrl = null;
      String[] drmKeyRequestProperties = null;
      boolean drmMultiSession = false;
      ArrayList<UriSample> playlistSamples = null;
      String adTagUri = null;
      String sphericalStereoMode = null;

      reader.beginObject();
      while (reader.hasNext()) {
        String name = reader.nextName();
        switch (name) {
          case "name":
            sampleName = reader.nextString();
            break;
          case "uri":
            uri = Uri.parse(reader.nextString());
            break;
          case "subtitle":
          case "subtitles":
          case "all_subtitles":
            parseAudioOrSubtitle(reader, subtitleMap);
            break;
          case "audio":
          case "audios":
          case "all_audios":
            parseAudioOrSubtitle(reader, audioMap);
            break;
          case "extension":
            extension = reader.nextString();
            break;
          case "drm_scheme":
            Assertions.checkState(!insidePlaylist, "Invalid attribute on nested item: drm_scheme");
            drmScheme = reader.nextString();
            switch(drmScheme) {
              case "wvm":
              case "widevine":
                drmScheme = "widevine";
                break;
              case "ss-pr":
              case "pr":
              case "playready":
              case "mss":
                drmScheme = "playready";
                break;
            }
            break;
          case "drm_license_url":
            Assertions.checkState(!insidePlaylist,
                "Invalid attribute on nested item: drm_license_url");
            drmLicenseUrl = reader.nextString();
            break;
          case "drm_key_request_properties":
            Assertions.checkState(!insidePlaylist,
                "Invalid attribute on nested item: drm_key_request_properties");
            ArrayList<String> drmKeyRequestPropertiesList = new ArrayList<>();
            reader.beginObject();
            while (reader.hasNext()) {
              drmKeyRequestPropertiesList.add(reader.nextName());
              drmKeyRequestPropertiesList.add(reader.nextString());
            }
            reader.endObject();
            drmKeyRequestProperties = drmKeyRequestPropertiesList.toArray(new String[0]);
            break;
          case "drm_multi_session":
            drmMultiSession = reader.nextBoolean();
            break;
          case "playlist":
            Assertions.checkState(!insidePlaylist, "Invalid nesting of playlists");
            playlistSamples = new ArrayList<>();
            reader.beginArray();
            while (reader.hasNext()) {
              playlistSamples.add((UriSample) readEntry(reader, true));
            }
            reader.endArray();
            break;
          case "ad_tag_uri":
            adTagUri = reader.nextString();
            break;
          case "spherical_stereo_mode":
            Assertions.checkState(
                !insidePlaylist, "Invalid attribute on nested item: spherical_stereo_mode");
            sphericalStereoMode = reader.nextString();
            break;
          default:
            throw new ParserException("Unsupported attribute name: " + name);
        }
      }
      reader.endObject();
      DrmInfo drmInfo =
          drmScheme == null
              ? null
              : new DrmInfo(drmScheme, drmLicenseUrl, drmKeyRequestProperties, drmMultiSession);
      if (playlistSamples != null) {
        UriSample[] playlistSamplesArray = playlistSamples.toArray(
            new UriSample[playlistSamples.size()]);
        return new PlaylistSample(sampleName, drmInfo, playlistSamplesArray);
      } else {
        return new UriSample(
            sampleName,
            drmInfo,
            uri,
            extension,
            audioMap,
            subtitleMap,
            adTagUri,
            sphericalStereoMode);
      }
    }

    private void parseAudioOrSubtitle(JsonReader reader, Map<String, String> theMap) {
      try {
        String k = reader.nextString();
        theMap.put("und", k);
      } catch(Exception e1) {
        parseAudioOrSubtitleFromObject(reader, theMap);
      }
    }
    private void parseAudioOrSubtitleFromObject(JsonReader reader, Map<String, String> theMap) {
      try {
        reader.beginObject();
        while (reader.hasNext()) {
          theMap.put(reader.nextName(), reader.nextString());
        }
        reader.endObject();
      } catch (Exception e) {
        parseAudioOrSubtitleFromArray(reader, theMap);
      }
    }

    private void parseAudioOrSubtitleFromArray(JsonReader reader, Map<String, String> theMap) {
      try {
        reader.beginArray();
        int i = 0;
        while (reader.hasNext()) {
          // Get string or object
          String name = String.valueOf(++i);
          getUrlFromReader(reader, name, theMap);
        }
        reader.endArray();
      } catch (Exception e) {
      }
    }

    private void getUrlFromReader(JsonReader reader, String name, Map<String, String> theMap) {
      String value = "";
      try {
        value = reader.nextString();
        theMap.put(name, value);
      } catch (Exception e1) {
        try {
          name = "";
          reader.beginObject();
          while (reader.hasNext()) {
            String k = reader.nextName();
            switch (k) {
              case "language":
              case "locale":
                if (name.length() > 0) name += " ";
                name += reader.nextString();
                break;
              case "forced":
                if (reader.nextBoolean() == true) {
                  if (name.length() > 0) name += " ";
                  name += "forced";
                }
                break;
              case "url":
                value = reader.nextString();
                break;
              default:
                reader.skipValue();
                break;
            }
          }
          if (value != null && value.length() > 0) {
            theMap.put(name, value);
          }
          reader.endObject();
        } catch (Exception e2) {
        }
      }
    }
    private SampleGroup getGroup(String groupName, List<SampleGroup> groups) {
      for (int i = 0; i < groups.size(); i++) {
        if (Util.areEqual(groupName, groups.get(i).title)) {
          return groups.get(i);
        }
      }
      SampleGroup group = new SampleGroup(groupName);
      groups.add(group);
      return group;
    }

  }

  private final class SampleAdapter extends BaseExpandableListAdapter implements OnClickListener {

    private List<SampleGroup> sampleGroups;

    public SampleAdapter() {
      sampleGroups = Collections.emptyList();
    }

    public void setSampleGroups(List<SampleGroup> sampleGroups) {
      this.sampleGroups = sampleGroups;
      notifyDataSetChanged();
    }

    @Override
    public Sample getChild(int groupPosition, int childPosition) {
      return getGroup(groupPosition).samples.get(childPosition);
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
      return childPosition;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
        View convertView, ViewGroup parent) {
      View view = convertView;
      if (view == null) {
        view = getLayoutInflater().inflate(R.layout.sample_list_item, parent, false);
        View downloadButton = view.findViewById(R.id.download_button);
        downloadButton.setOnClickListener(this);
        downloadButton.setFocusable(false);
      }
      initializeChildView(view, getChild(groupPosition, childPosition));
      return view;
    }

    @Override
    public int getChildrenCount(int groupPosition) {
      return getGroup(groupPosition).samples.size();
    }

    @Override
    public SampleGroup getGroup(int groupPosition) {
      return sampleGroups.get(groupPosition);
    }

    @Override
    public long getGroupId(int groupPosition) {
      return groupPosition;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView,
        ViewGroup parent) {
      View view = convertView;
      if (view == null) {
        view =
            getLayoutInflater()
                .inflate(android.R.layout.simple_expandable_list_item_1, parent, false);
      }
      ((TextView) view).setText(getGroup(groupPosition).title);
      return view;
    }

    @Override
    public int getGroupCount() {
      return sampleGroups.size();
    }

    @Override
    public boolean hasStableIds() {
      return false;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
      return true;
    }

    @Override
    public void onClick(View view) {
      onSampleDownloadButtonClicked((Sample) view.getTag());
    }

    private void initializeChildView(View view, Sample sample) {
      view.setTag(sample);
      TextView sampleTitle = view.findViewById(R.id.sample_title);
      sampleTitle.setText(sample.name);

      boolean canDownload = getDownloadUnsupportedStringId(sample) == 0;
      boolean isDownloaded = canDownload && downloadTracker.isDownloaded(((UriSample) sample).uri);
      ImageButton downloadButton = view.findViewById(R.id.download_button);
      downloadButton.setTag(sample);
      downloadButton.setColorFilter(
          canDownload ? (isDownloaded ? 0xFF42A5F5 : 0xFFBDBDBD) : 0xFFEEEEEE);
      downloadButton.setImageResource(
          isDownloaded ? R.drawable.ic_download_done : R.drawable.ic_download);
    }
  }

  private static final class SampleGroup {

    public final String title;
    public final List<Sample> samples;

    public SampleGroup(String title) {
      this.title = title;
      this.samples = new ArrayList<>();
    }

  }

  private static final class DrmInfo {
    public final String drmScheme;
    public final String drmLicenseUrl;
    public final String[] drmKeyRequestProperties;
    public final boolean drmMultiSession;

    public DrmInfo(
        String drmScheme,
        String drmLicenseUrl,
        String[] drmKeyRequestProperties,
        boolean drmMultiSession) {
      this.drmScheme = drmScheme;
      this.drmLicenseUrl = drmLicenseUrl;
      this.drmKeyRequestProperties = drmKeyRequestProperties;
      this.drmMultiSession = drmMultiSession;
    }

    public void updateIntent(Intent intent) {
      Assertions.checkNotNull(intent);
      intent.putExtra(PlayerActivity.DRM_SCHEME_EXTRA, drmScheme);
      intent.putExtra(PlayerActivity.DRM_LICENSE_URL_EXTRA, drmLicenseUrl);
      intent.putExtra(PlayerActivity.DRM_KEY_REQUEST_PROPERTIES_EXTRA, drmKeyRequestProperties);
      intent.putExtra(PlayerActivity.DRM_MULTI_SESSION_EXTRA, drmMultiSession);
    }
  }

  private abstract static class Sample {
    public final String name;
    public final DrmInfo drmInfo;

    public Sample(String name, DrmInfo drmInfo) {
      this.name = name;
      this.drmInfo = drmInfo;
    }

    public Intent buildIntent(
        Context context, boolean preferExtensionDecoders, String abrAlgorithm) {
      Intent intent = new Intent(context, PlayerActivity.class);
      intent.putExtra(PlayerActivity.PREFER_EXTENSION_DECODERS_EXTRA, preferExtensionDecoders);
      intent.putExtra(PlayerActivity.ABR_ALGORITHM_EXTRA, abrAlgorithm);
      if (drmInfo != null) {
        drmInfo.updateIntent(intent);
      }
      return intent;
    }

  }

  private static final class UriSample extends Sample {

    public final Uri uri;
    public final String extension;
    public final HashMap<String, String> audioMap;
    public final HashMap<String, String> subtitleMap;
    public final String adTagUri;
    public final String sphericalStereoMode;

    public UriSample(
        String name,
        DrmInfo drmInfo,
        Uri uri,
        String extension,
        HashMap<String, String> audioMap,
        HashMap<String, String> subtitleMap,
        String adTagUri,
        String sphericalStereoMode) {
      super(name, drmInfo);
      this.uri = uri;
      this.extension = extension;
      this.audioMap = audioMap;
      this.subtitleMap = subtitleMap;
      this.adTagUri = adTagUri;
      this.sphericalStereoMode = sphericalStereoMode;
    }

    @Override
    public Intent buildIntent(
        Context context, boolean preferExtensionDecoders, String abrAlgorithm) {
      return super.buildIntent(context, preferExtensionDecoders, abrAlgorithm)
          .setData(uri)
          .putExtra(PlayerActivity.EXTENSION_EXTRA, extension)
          .putExtra(PlayerActivity.AUDIO_URL, audioMap)
          .putExtra(PlayerActivity.SUBTITLES_URL, subtitleMap)
          .putExtra(PlayerActivity.AD_TAG_URI_EXTRA, adTagUri)
          .putExtra(PlayerActivity.SPHERICAL_STEREO_MODE_EXTRA, sphericalStereoMode)
          .setAction(PlayerActivity.ACTION_VIEW);
    }

  }

  private static final class PlaylistSample extends Sample {

    public final UriSample[] children;

    public PlaylistSample(
        String name,
        DrmInfo drmInfo,
        UriSample... children) {
      super(name, drmInfo);
      this.children = children;
    }

    @Override
    public Intent buildIntent(
        Context context, boolean preferExtensionDecoders, String abrAlgorithm) {
      String[] uris = new String[children.length];
      String[] extensions = new String[children.length];
      for (int i = 0; i < children.length; i++) {
        uris[i] = children[i].uri.toString();
        extensions[i] = children[i].extension;
      }
      return super.buildIntent(context, preferExtensionDecoders, abrAlgorithm)
          .putExtra(PlayerActivity.URI_LIST_EXTRA, uris)
          .putExtra(PlayerActivity.EXTENSION_LIST_EXTRA, extensions)
          .setAction(PlayerActivity.ACTION_VIEW_LIST);
    }

  }

}
