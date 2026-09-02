package com.gwen.hideoemunlock;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

public class ConfigProvider extends ContentProvider {

    public static final String AUTHORITY = "com.gwen.hideoemunlock.provider";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);
    public static final String METHOD_GET_CONFIG = "get_config";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (METHOD_GET_CONFIG.equals(method)) {
            Context context = getContext();
            if (context != null) {
                SharedPreferences sp = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE);
                Bundle bundle = new Bundle();
                bundle.putBoolean(MainActivity.KEY_HIDE_OEM, sp.getBoolean(MainActivity.KEY_HIDE_OEM, true));
                bundle.putBoolean(MainActivity.KEY_HIDE_MI_UNLOCK, sp.getBoolean(MainActivity.KEY_HIDE_MI_UNLOCK, true));
                return bundle;
            }
        }
        return super.call(method, arg, extras);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
