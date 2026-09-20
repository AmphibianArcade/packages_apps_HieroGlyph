/*
 * Copyright (C) 2023-2024 Paranoid Android
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

package org.nukisystems.hieroglyph.Utils;

import static org.nukisystems.hieroglyph.Data.toyCache;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.ContactsContract;
import android.provider.OpenableColumns;
import android.util.Log;

import com.android.internal.util.ArrayUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.nukisystems.hieroglyph.Data;
import org.nukisystems.hieroglyph.R;
import org.nukisystems.hieroglyph.Constants.Constants;

public final class ResourceUtils {

    private static final String TAG = "GlyphResourceUtils";
    private static final boolean DEBUG = true;

    private static Context context;
    private static AssetManager assetManager;
    private static Resources resources;

    private static List<String> bundledCallAnimations = null;
    private static List<String> bundledNotificationAnimations = null;

    private static Context getContext() {
        if (context == null) {
            context = Constants.CONTEXT;
            if (context == null) {
                throw new IllegalStateException("Constants.CONTEXT is not initialized");
            }
        }
        return context;
    }

    private static AssetManager getAssetManager() {
        if (assetManager == null) {
            assetManager = getContext().getAssets();
        }
        return assetManager;
    }

    private static Resources getResources() {
        if (resources == null) {
            resources = getContext().getResources();
        }
        return resources;
    }

    public static int getIdentifier(String id, String type) {
        return getResources().getIdentifier(id, type, getContext().getPackageName());
    }

    public static Boolean getBoolean(String id) {
        return getResources().getBoolean(getIdentifier(id, "bool"));
    }

    public static String getString(String id) {
        return getResources().getString(getIdentifier(id, "string"));
    }

    public static int getInteger(String id) {
        return getResources().getInteger(getIdentifier(id, "integer"));
    }

    public static String[] getStringArray(String id) {
        return getResources().getStringArray(getIdentifier(id, "array"));
    }

    public static int[] getIntArray(String id) {
        return getResources().getIntArray(getIdentifier(id, "array"));
    }

    public static boolean hasFlipCsv() {
        boolean hasFlipCsv = false;
        try {
            getAnimation("flip");
            hasFlipCsv = true;
        } catch (IOException ignored) {

        }
        return hasFlipCsv;
    }

    public static List<String> getUserCallAnimations() {

        List<String> animations = null;

        File dir = new File(Environment.getExternalStorageDirectory(),
                Constants.GLYPH_USER_CALL_CSV_PATH);

        File[] matchingFiles = dir.listFiles(file ->
                file.isFile() && file.getName().toLowerCase().endsWith(".csv")
                        && !file.getName().toLowerCase().startsWith(".")
        );

        if (matchingFiles != null) {
            animations = Arrays.stream(matchingFiles)
                    .map(File::getName)
                    .map(name -> name.substring(0, name.lastIndexOf('.')))
                    .toList();
        } else {
            animations = Collections.emptyList();
        }

        return animations;
    }

    public static List<String> getUserNotificationAnimations() {

        List<String> animations = null;

        File dir = new File(Environment.getExternalStorageDirectory(),
                Constants.GLYPH_USER_NOTIF_CSV_PATH);

        File[] matchingFiles = dir.listFiles(file ->
                file.isFile() && file.getName().toLowerCase().endsWith(".csv")
                        && !file.getName().toLowerCase().startsWith(".")
        );

        if (matchingFiles != null) {
            animations = Arrays.stream(matchingFiles)
                    .map(File::getName)
                    .map(name -> name.substring(0, name.lastIndexOf('.')))
                    .toList();
        } else {
            animations = Collections.emptyList();
        }

        return animations;
    }

    public static List<String> getBundledCallAnimations() {
        if (bundledCallAnimations == null) {
            try {
                String[] assets = getAssetManager().list("ring");
                for (int i=0; i < assets.length; i++) {
                    assets[i] = assets[i].replaceAll(".csv", "");
                }
                bundledCallAnimations = Arrays.asList(assets);
            } catch (IOException e) { }
        }
        return bundledCallAnimations;
    }

    public static List<String> getBundledNotificationAnimations() {
        if (bundledNotificationAnimations == null) {
            try {
                String[] assets = getAssetManager().list("notif");
                for (int i=0; i < assets.length; i++) {
                    assets[i] = assets[i].replaceAll(".csv", "");
                }
                bundledNotificationAnimations = Arrays.asList(assets);
            } catch (IOException e) { }
        }
        return bundledNotificationAnimations;
    }

    public static String getFileName(Context context, Uri uri) {
        String name = null;
        Cursor cursor = context.getContentResolver()
                .query(uri, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if (index != -1) name = cursor.getString(index);
            cursor.close();
        }
        return name;
    }

    public static String getContactName(Context context, String contactId) {
        Cursor cursor = context.getContentResolver().query(
                ContactsContract.Contacts.CONTENT_URI,
                new String[]{ContactsContract.Contacts.DISPLAY_NAME},
                ContactsContract.Contacts._ID + " = ?",
                new String[]{contactId},
                null);

        if (cursor != null && cursor.moveToFirst()) {
            String name = cursor.getString(0);
            cursor.close();
            return name;
        }
        if (cursor != null) cursor.close();

        return null;
    }

    public static String getContactIdForNumber(String number) {
        Uri uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number));

        Cursor cursor = context.getContentResolver().query(
                uri,
                new String[]{ContactsContract.PhoneLookup.CONTACT_ID},
                null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            String contactId = cursor.getString(0);
            cursor.close();
            return contactId;
        }
        if (cursor != null) cursor.close();
        return null;
    }

    public static InputStream getCallAnimation(String name) throws IOException {
        if (bundledCallAnimations == null) getBundledCallAnimations();

        InputStream stream = null;

        if (name.startsWith(Constants.GLYPH_USER_CALL_CSV_PREFIX)) {
            File csv = new File(Environment.getExternalStorageDirectory(),
                    Constants.GLYPH_USER_CALL_CSV_PATH
                            + "/" + name.replaceFirst(Constants.GLYPH_USER_CALL_CSV_PREFIX, "") + ".csv");
            stream = new FileInputStream(csv);
            return stream;
        }
        if (bundledCallAnimations.contains(name))
            return getAssetManager().open("ring/" + name + ".csv");

        if (stream == null) {
            stream = getAssetManager().open("ring/"
                    + ResourceUtils.getString("glyph_settings_call_animations_default") + ".csv");
        }
        return stream;
    }

    public static InputStream getNotificationAnimation(String name) throws IOException {
        if (bundledNotificationAnimations == null) getBundledCallAnimations();

        InputStream stream = null;

        if (name.startsWith(Constants.GLYPH_USER_NOTIF_CSV_PREFIX)) {
            File csv = new File(Environment.getExternalStorageDirectory(),
                    Constants.GLYPH_USER_NOTIF_CSV_PATH
                            + "/" + name.replaceFirst(Constants.GLYPH_USER_NOTIF_CSV_PREFIX,"") + ".csv");
            stream = new FileInputStream(csv);
            return stream;
        }

        if (bundledNotificationAnimations.contains(name)) {
            stream = getAssetManager().open("notif/" + name + ".csv");
            return stream;
        }

        if (stream == null) {
            stream = getAssetManager().open("notif/"
                    + ResourceUtils.getString("glyph_settings_notifs_animations_default")
                        + ".csv");
        }

        return stream;
    }

    public static InputStream getAnimation(String name) throws IOException {
        if (bundledCallAnimations == null) getBundledCallAnimations();
        if (bundledNotificationAnimations == null) getBundledNotificationAnimations();

        InputStream stream = null;

        if (bundledCallAnimations.contains(name)) {
           stream = getCallAnimation(name);
        }

        if (bundledNotificationAnimations.contains(name)) {
            stream = getNotificationAnimation(name);
        }

        if (name.startsWith(Constants.GLYPH_USER_CALL_CSV_PREFIX)) {
            File csv = new File(Environment.getExternalStorageDirectory(),
                    Constants.GLYPH_USER_CALL_CSV_PATH
                            + "/" + name.replaceFirst(Constants.GLYPH_USER_CALL_CSV_PREFIX, "") + ".csv");
            stream = new FileInputStream(csv);
            return stream;
        }

        if (name.startsWith(Constants.GLYPH_USER_NOTIF_CSV_PREFIX)) {
            File csv = new File(Environment.getExternalStorageDirectory(),
                    Constants.GLYPH_USER_NOTIF_CSV_PATH
                            + "/" + name.replaceFirst(Constants.GLYPH_USER_NOTIF_CSV_PREFIX,"") + ".csv" );
            stream = new FileInputStream(csv);
            return stream;
        }

        if (stream == null) {
            stream = getAssetManager().open(name + ".csv");
        }

        return stream;
    }

    public static String[] getApplicationsWithPermission(boolean resolveLabel, String[] permissionList) {
        List<ApplicationInfo> matched = new ArrayList<>();
        PackageManager pm = getContext().getPackageManager();
        List<PackageInfo> allApps = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);
        for (PackageInfo pkg : allApps) {
            int pkgFlags = pkg.applicationInfo.flags;
            if (pkg.requestedPermissions == null) continue;
            if (pm.getLaunchIntentForPackage(pkg.packageName) == null) continue;
            if ((pkgFlags & ApplicationInfo.FLAG_INSTALLED) == 0
                    || (pkgFlags & ApplicationInfo.FLAG_PERSISTENT) != 0) continue;
            for (String perm : pkg.requestedPermissions) {
                for (String requiredPerm : permissionList) {
                    if (perm.equals(requiredPerm)) {
                        matched.add(pkg.applicationInfo);
                    }
                }
            }
        }

        matched.sort((a, b) -> pm.getApplicationLabel(a).toString()
                .compareToIgnoreCase(pm.getApplicationLabel(b).toString()));

        List<String> result = new ArrayList<>();
        for (ApplicationInfo app : matched) {
            result.add(resolveLabel
                    ? pm.getApplicationLabel(app).toString()
                    : app.packageName);
        }
        return result.toArray(new String[0]);
    }

        public static class External {

            public static Resources getResourcesForPackage(Context context, String packageName) {
                try {
                    PackageManager pm = context.getPackageManager();
                    return pm.getResourcesForApplication(packageName);
                } catch (PackageManager.NameNotFoundException e) {
                    Log.e("ExternalResourceLoader", "Package not found: " + packageName, e);
                    return null;
                }
            }

            public static Drawable getDrawable(Context context, String packageName, String resName) {
                Resources res = getResourcesForPackage(context, packageName);
                if (res == null) return null;

                int resId = res.getIdentifier(resName, "drawable", packageName);
                if (resId == 0) {
                    Log.e("ExternalResourceLoader", "Drawable not found: " + resName);
                    return null;
                }
                return res.getDrawable(resId, null);
            }

            public static Drawable getDrawable(Context context, String packageName, int resId) {
                Resources res = getResourcesForPackage(context, packageName);
                if (res == null) return null;
                try {
                    return res.getDrawable(resId, null);
                } catch (Resources.NotFoundException e) {
                    Log.e("ExternalResourceLoader", "Drawable ID not found: " + resId, e);
                    return null;
                }
            }

            public static String getString(Context context, String packageName, String resName) {
                Resources res = getResourcesForPackage(context, packageName);
                if (res == null) return null;

                int resId = res.getIdentifier(resName, "string", packageName);
                if (resId == 0) {
                    Log.e("ExternalResourceLoader", "String not found: " + resName);
                    return null;
                }
                return res.getString(resId);
            }


            public static String getString(Context context, String packageName, int resId) {
                Resources res = getResourcesForPackage(context, packageName);
                if (res == null) return null;
                try {
                    return res.getString(resId);
                } catch (Resources.NotFoundException e) {
                    Log.e("ExternalResourceLoader", "String ID not found: " + resId, e);
                    return null;
                }
            }
        }

        public static class Toys {

            public static void reload(Context ctx) {
                toyCache.clear();
                PackageManager pm = ctx.getPackageManager();
                Intent intent = new Intent(Constants.External.TOY_INTENT);
                List<ResolveInfo> resolveInfos =
                        pm.queryIntentServices(intent, PackageManager.GET_META_DATA);

                for (ResolveInfo info : resolveInfos) {
                    ServiceInfo serviceInfo = info.serviceInfo;
                    if (serviceInfo == null) continue;
                    Bundle serviceMeta = serviceInfo.metaData;
                    String pkg = serviceInfo.packageName;
                    Log.w(TAG, "resolved pkg " + pkg);

                    if (serviceMeta == null || serviceMeta.isEmpty()) {
                        Log.w(TAG, "Unable to get Toy service metadata for package: "
                                + pkg);
                        continue;
                    }

                    ComponentName serviceComponent = new ComponentName(serviceInfo.packageName, serviceInfo.name);

                    int nameResId = serviceMeta.getInt(Constants.External.STRING_TOY_NAME, 0);
                    int iconResId = serviceMeta.getInt(Constants.External.DRAWABLE_TOY_IMAGE, 0);
                    if (nameResId == 0 || iconResId == 0) {
                        Log.w(TAG, "Missing required data for glyph toy: "
                                + pkg + "/" + serviceInfo.name + ", skipping!");
                        continue;
                    }

                    int summaryResId = serviceMeta.getInt(Constants.External.STRING_TOY_SUMMARY, 0);

                    boolean supportsAOD = Integer.parseInt(
                            serviceMeta.getString(Constants.External.META_SUPPORTS_AOD, "0")) == 1;
                    boolean supportsLongPress = Integer.parseInt(
                            serviceMeta.getString(Constants.External.META_SUPPORTS_LONGPRESS, "0")) == 1;

                    String introActivity =
                            serviceMeta.getString(Constants.External.META_TOY_INTRO_ACTIVITY, null);
                    ComponentName introComponent =
                            introActivity == null ? null : new ComponentName(serviceInfo.packageName, introActivity);

                    String toyName = External.getString(ctx, pkg, nameResId);
                    Drawable toyDrawable = External.getDrawable(ctx, pkg, iconResId);
                    String toySummary = External.getString(ctx, pkg, summaryResId);


                    Data.GlyphToy toyData =
                            new Data.GlyphToy(toyName, toyDrawable, toySummary, introComponent,
                                    supportsAOD, supportsLongPress);

                    toyCache.put(serviceComponent, toyData);
                }

                if (toyCache == null || toyCache.isEmpty()) {
                    Log.w(TAG, "No valid toys found");
                }

            }

            public static void delete(ComponentName component) {
                if (toyCache == null || toyCache.isEmpty()) {
                    Log.w(TAG, "No glyph toys in cache to delete?");
                    return;
                }

                if (!toyCache.containsKey(component)) {
                    Log.w(TAG, "Toy package: " + component.toShortString() + " not found in cache");
                    return;
                }
                toyCache.remove(component);
        }
    }
}
