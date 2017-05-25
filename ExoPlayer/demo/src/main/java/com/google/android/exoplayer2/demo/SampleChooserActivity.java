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
import android.util.JsonReader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSourceInputStream;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * An activity for selecting from a list of samples.
 * Read JSON locally and from 10.12.96.25
 */
public class SampleChooserActivity extends Activity {

  private static final String TAG = "SampleChooserActivity";
  private boolean loadData = true;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.sample_chooser_activity);
    final SampleChooserActivity self = this;

    try {
      new HTTPListener(this, this.getApplicationContext(),new WSListener.Reader() {
        public void read(String txt) throws Exception {
          parseJSON(self,txt);
        }
      });
    } catch(Exception e) {
    }

    loadInfo();

    new WSListener(new WSListener.Reader() {
      public void read(String txt) throws Exception {
        parseJSON(self,txt);
      }
    });

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
                      Toast.makeText(getApplicationContext(), ((UriSample)sample).uri, Toast.LENGTH_LONG).show();
                    }
                  }
                },
                1000);
      }
    });


    startingActivity = true;
    startActivity(sample.buildIntent(self));
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

  private void loadInfo() {
    if(!loadData)
    {
      return;
    }
    setTitle(getLocalIpAddress(true, false)+" | 0 - Reload JSON");
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
    SampleListLoader loaderTask = new SampleListLoader();
    loaderTask.execute(uris);
  }

  @Override
  public boolean onKeyDown(int keyCode, android.view.KeyEvent event)
  {
    if (keyCode == android.view.KeyEvent.KEYCODE_0) {
      loadData = true;
      loadInfo();
      return false;
    }
    return super.onKeyDown(keyCode, event);
  }

  @Override
  protected void onResume()
  {
    super.onResume();
    loadInfo();
  }

  @Override
  protected void onStop()
  {
    super.onStop();
    // Next time we'll load data
    if(!startingActivity) {
      loadData = true;
    }
  }

  @Override
  protected void onDestroy()
  {
    super.onDestroy();
    loadData = true;
  }


  private void onSampleGroups(final List<SampleGroup> groups, boolean sawError) {
    if (sawError) {
      Toast.makeText(getApplicationContext(), R.string.sample_list_load_error, Toast.LENGTH_LONG)
          .show();
    }
    ExpandableListView sampleList = (ExpandableListView) findViewById(R.id.sample_list);
    sampleList.setAdapter(new SampleAdapter(this, groups));
    sampleList.setOnChildClickListener(new OnChildClickListener() {
      @Override
      public boolean onChildClick(ExpandableListView parent, View view, int groupPosition,
          int childPosition, long id) {
        onSampleSelected(groups.get(groupPosition).samples.get(childPosition));
        return true;
      }
    });
  }

  private boolean startingActivity = false;
  private void onSampleSelected(Sample sample) {
    startingActivity = true;
    startActivity(sample.buildIntent(this));
  }

  private final class SampleListLoader extends AsyncTask<String, Void, List<SampleGroup>> {

    private boolean sawError;

    @Override
    protected List<SampleGroup> doInBackground(String... uris) {
      List<SampleGroup> result = new ArrayList<>();
      Context context = getApplicationContext();
      String userAgent = Util.getUserAgent(context, "ExoPlayerDemo");
      DataSource dataSource = new DefaultDataSource(context, null, userAgent, false);
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
      String uri = null;
      String extension = null;
      UUID drmUuid = null;
      String drmLicenseUrl = null;
      String[] drmKeyRequestProperties = null;
      boolean preferExtensionDecoders = false;
      ArrayList<UriSample> playlistSamples = null;

      reader.beginObject();
      while (reader.hasNext()) {
        String name = reader.nextName();
        switch (name) {
          case "name":
            sampleName = reader.nextString();
            break;
          case "uri":
            uri = reader.nextString();
            break;
          case "extension":
            extension = reader.nextString();
            break;
          case "drm_scheme":
            Assertions.checkState(!insidePlaylist, "Invalid attribute on nested item: drm_scheme");
            drmUuid = getDrmUuid(reader.nextString());
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
          case "prefer_extension_decoders":
            Assertions.checkState(!insidePlaylist,
                "Invalid attribute on nested item: prefer_extension_decoders");
            preferExtensionDecoders = reader.nextBoolean();
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
          default:
            throw new ParserException("Unsupported attribute name: " + name);
        }
      }
      reader.endObject();

      if (playlistSamples != null) {
        UriSample[] playlistSamplesArray = playlistSamples.toArray(
            new UriSample[playlistSamples.size()]);
        return new PlaylistSample(sampleName, drmUuid, drmLicenseUrl, drmKeyRequestProperties,
            preferExtensionDecoders, playlistSamplesArray);
      } else {
        return new UriSample(sampleName, drmUuid, drmLicenseUrl, drmKeyRequestProperties,
            preferExtensionDecoders, uri, extension);
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

    private UUID getDrmUuid(String typeString) throws ParserException {
      switch (Util.toLowerInvariant(typeString)) {
        case "widevine":
          return C.WIDEVINE_UUID;
        case "playready":
          return C.PLAYREADY_UUID;
        case "cenc":
          return C.CLEARKEY_UUID;
        default:
          try {
            return UUID.fromString(typeString);
          } catch (RuntimeException e) {
            throw new ParserException("Unsupported drm type: " + typeString);
          }
      }
    }

  }

  private static final class SampleAdapter extends BaseExpandableListAdapter {

    private final Context context;
    private final List<SampleGroup> sampleGroups;

    public SampleAdapter(Context context, List<SampleGroup> sampleGroups) {
      this.context = context;
      this.sampleGroups = sampleGroups;
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
        view = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_1, parent,
            false);
      }
      ((TextView) view).setText(getChild(groupPosition, childPosition).name);
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
        view = LayoutInflater.from(context).inflate(android.R.layout.simple_expandable_list_item_1,
            parent, false);
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

  }

  private static final class SampleGroup {

    public final String title;
    public final List<Sample> samples;

    public SampleGroup(String title) {
      this.title = title;
      this.samples = new ArrayList<>();
    }

  }

  private abstract static class Sample {

    public final String name;
    public final boolean preferExtensionDecoders;
    public final UUID drmSchemeUuid;
    public final String drmLicenseUrl;
    public final String[] drmKeyRequestProperties;

    public Sample(String name, UUID drmSchemeUuid, String drmLicenseUrl,
        String[] drmKeyRequestProperties, boolean preferExtensionDecoders) {
      this.name = name;
      this.drmSchemeUuid = drmSchemeUuid;
      this.drmLicenseUrl = drmLicenseUrl;
      this.drmKeyRequestProperties = drmKeyRequestProperties;
      this.preferExtensionDecoders = preferExtensionDecoders;
    }

    public Intent buildIntent(Context context) {
      Intent intent = new Intent(context, PlayerActivity.class);
      intent.putExtra(PlayerActivity.PREFER_EXTENSION_DECODERS, preferExtensionDecoders);
      if (drmSchemeUuid != null) {
        intent.putExtra(PlayerActivity.DRM_SCHEME_UUID_EXTRA, drmSchemeUuid.toString());
        intent.putExtra(PlayerActivity.DRM_LICENSE_URL, drmLicenseUrl);
        intent.putExtra(PlayerActivity.DRM_KEY_REQUEST_PROPERTIES, drmKeyRequestProperties);
      }
      return intent;
    }

  }

  private static final class UriSample extends Sample {

    public final String uri;
    public final String extension;

    public UriSample(String name, UUID drmSchemeUuid, String drmLicenseUrl,
        String[] drmKeyRequestProperties, boolean preferExtensionDecoders, String uri,
        String extension) {
      super(name, drmSchemeUuid, drmLicenseUrl, drmKeyRequestProperties, preferExtensionDecoders);
      this.uri = uri;
      this.extension = extension;
    }

    @Override
    public Intent buildIntent(Context context) {
      return super.buildIntent(context)
          .setData(Uri.parse(uri))
          .putExtra(PlayerActivity.EXTENSION_EXTRA, extension)
          .setAction(PlayerActivity.ACTION_VIEW);
    }

  }

  private static final class PlaylistSample extends Sample {

    public final UriSample[] children;

    public PlaylistSample(String name, UUID drmSchemeUuid, String drmLicenseUrl,
        String[] drmKeyRequestProperties, boolean preferExtensionDecoders,
        UriSample... children) {
      super(name, drmSchemeUuid, drmLicenseUrl, drmKeyRequestProperties, preferExtensionDecoders);
      this.children = children;
    }

    @Override
    public Intent buildIntent(Context context) {
      String[] uris = new String[children.length];
      String[] extensions = new String[children.length];
      for (int i = 0; i < children.length; i++) {
        uris[i] = children[i].uri;
        extensions[i] = children[i].extension;
      }
      return super.buildIntent(context)
          .putExtra(PlayerActivity.URI_LIST_EXTRA, uris)
          .putExtra(PlayerActivity.EXTENSION_LIST_EXTRA, extensions)
          .setAction(PlayerActivity.ACTION_VIEW_LIST);
    }

  }

}
