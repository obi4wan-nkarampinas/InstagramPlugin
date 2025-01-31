/*
    The MIT License (MIT)
    Copyright (c) 2013 Vlad Stirbu

    Permission is hereby granted, free of charge, to any person obtaining
    a copy of this software and associated documentation files (the
    "Software"), to deal in the Software without restriction, including
    without limitation the rights to use, copy, modify, merge, publish,
    distribute, sublicense, and/or sell copies of the Software, and to
    permit persons to whom the Software is furnished to do so, subject to
    the following conditions:

    The above copyright notice and this permission notice shall be
    included in all copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
    EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
    MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
    NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
    LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
    OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
    WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package com.vladstirbu.cordova;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;
import android.util.Log;

import androidx.core.content.FileProvider;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@TargetApi(Build.VERSION_CODES.FROYO)
public class CDVInstagramPlugin extends CordovaPlugin {

    private static final FilenameFilter OLD_IMAGE_FILTER = new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
            return name.startsWith("instagram");
        }
    };
    public static final String INSTAGRAM_ANDROID = "com.instagram.android";
    public static final String TAG_INSTAGRAM_PLUGIN = "Instagram";

    CallbackContext cbContext;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

        this.cbContext = callbackContext;

        if (action.equals("share")) {
            String imageString = args.getString(0);
            String captionString = args.getString(1);

            PluginResult result = new PluginResult(Status.NO_RESULT);
            result.setKeepCallback(true);

            this.share(imageString, captionString);
            return true;
        } else if (action.equals("shareAsset")) {
            JSONArray assetsPaths = args.getJSONArray(0);

            PluginResult result = new PluginResult(Status.NO_RESULT);
            result.setKeepCallback(true);

            this.shareAsset(assetsPaths);
            return true;
        } else if (action.equals("isInstalled")) {
            this.isInstalled();
        } else {
            callbackContext.error("Invalid Action");
        }
        return false;
    }

    private void isInstalled() {
        try {
            this.webView.getContext().getPackageManager().getApplicationInfo(INSTAGRAM_ANDROID, 0);
            this.cbContext.success(this.webView.getContext().getPackageManager().getPackageInfo(INSTAGRAM_ANDROID, 0).versionName);
        } catch (PackageManager.NameNotFoundException e) {
            this.cbContext.error("Application not installed");
        }
    }

    private void share(String imageString, String captionString) {
        if (imageString != null && imageString.length() > 0) {
            byte[] imageData = Base64.decode(imageString, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);

            File file = null;
            File parentDir = this.webView.getContext().getExternalFilesDir(null);

            File[] oldImages = parentDir.listFiles(OLD_IMAGE_FILTER);
            for (File oldImage : oldImages) {
                oldImage.delete();
            }

            try {
                file = File.createTempFile("instagram", ".png", parentDir);
                FileOutputStream out = new FileOutputStream(file, true);
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out);
                out.flush();
                out.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/*");
            Uri uri;

            if (Build.VERSION.SDK_INT < 26) {
                // Handle the file uri with pre Oreo method
                uri = Uri.fromFile(file);
                shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            } else {
                // Handle the file URI using Android Oreo file provider
                FileProvider FileProvider = new FileProvider();

                uri = FileProvider.getUriForFile(
                        this.cordova.getActivity().getApplicationContext(),
                        this.cordova.getActivity().getPackageName() + ".provider",
                        file);

                shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                shareIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            shareIntent.putExtra(Intent.EXTRA_TEXT, captionString);
            shareToInstagram(shareIntent, new ArrayList<Uri>() {{add(uri);}});
        } else {
            this.cbContext.error("Expected one non-empty string argument.");
        }
    }

    private void shareAsset(JSONArray files) {
        if (files.length() > 0) {
            Intent shareIntent = new Intent();
            boolean hasMultipleAttachments = files.length() > 1;
            shareIntent.setAction(hasMultipleAttachments
                    ? Intent.ACTION_SEND_MULTIPLE
                    : Intent.ACTION_SEND);

            Uri fileUri;
            ArrayList<Uri> fileUris = new ArrayList<>();
            for (int i = 0; i < files.length(); i++) {
                try {
                    File parentDir = this.webView.getContext().getExternalFilesDir(null);
                    String filename = "file://" + parentDir + "/" + files.getString(i);
                    fileUri = Uri.parse(filename);
                    fileUri = FileProvider.getUriForFile(
                            this.cordova.getActivity().getApplicationContext(),
                            this.cordova.getActivity().getPackageName() + ".provider",
                            new File(fileUri.getPath()));
                    fileUris.add(fileUri);
                } catch (Exception e) {
                    Log.e(TAG_INSTAGRAM_PLUGIN, "Cannot parse asset #" + i);
                }
            }

            if (hasMultipleAttachments) {
                shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, fileUris);
            } else {
                shareIntent.putExtra(Intent.EXTRA_STREAM, fileUris.get(0));
            }

            shareIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            shareIntent.setType("*/*");

            shareToInstagram(shareIntent, fileUris);
        } else {
            this.cbContext.error("Expected one non-empty array argument.");
        }
    }

    private void shareToInstagram(Intent shareIntent, ArrayList<Uri> fileUris) {
        shareIntent.setPackage(INSTAGRAM_ANDROID);
        Intent chooser = Intent.createChooser(shareIntent, "Share to");
        setUriPermissionsForTheIntent(fileUris, chooser);
        this.cordova.startActivityForResult((CordovaPlugin) this, chooser, 12345);
    }

    private void setUriPermissionsForTheIntent(ArrayList<Uri> fileUris, Intent chooser) {
        List<ResolveInfo> resInfoList = this.cordova.getActivity().getPackageManager()
                .queryIntentActivities(chooser, PackageManager.MATCH_DEFAULT_ONLY);

        for (ResolveInfo resolveInfo : resInfoList) {
            String packageName = resolveInfo.activityInfo.packageName;
            for (Uri uri: fileUris) {
                this.cordova.getActivity().grantUriPermission(packageName, uri,
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            Log.v(TAG_INSTAGRAM_PLUGIN, "shared ok");
            if(this.cbContext != null) {
                this.cbContext.success();
            }
        } else if (resultCode == Activity.RESULT_CANCELED) {
            Log.v(TAG_INSTAGRAM_PLUGIN, "share cancelled");
            if(this.cbContext != null) {
                this.cbContext.error("Share Cancelled");
            }
        }
    }
}
