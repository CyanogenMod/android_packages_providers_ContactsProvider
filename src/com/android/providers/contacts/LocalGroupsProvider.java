/*
 * Copyright (C) 2013, The Linux Foundation. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.providers.contacts;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.provider.LocalGroups;
import android.provider.LocalGroups.GroupColumns;

public class LocalGroupsProvider extends ContentProvider {

    private static final String TAG = "LocalGroupsProvider";

    private static final boolean DEBUG = false;

    private static final String DATABASES = "local_groups.db";

    private static final String TABLE = "local_groups";

    private static final int VERSION = 1;

    private static final int GROUPS = 0;

    private static final int GROUPS_ID = 1;

    private DatabaseHelper mOpenHelper;

    private static final UriMatcher urlMatcher = new UriMatcher(
            UriMatcher.NO_MATCH);
    static {
        urlMatcher.addURI(LocalGroups.AUTHORITY, "local-groups", GROUPS);
        urlMatcher.addURI(LocalGroups.AUTHORITY, "local-groups/#", GROUPS_ID);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int count = 0;
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int match = urlMatcher.match(uri);
        switch (match) {
            case GROUPS:
                count = db.delete(TABLE, selection, selectionArgs);
                break;
        }
        if (count > 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        Uri result = null;
        long rowId = -1;
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int match = urlMatcher.match(uri);
        switch (match) {
            case GROUPS:
                rowId = db.insert(TABLE, null, values);
                break;
        }
        if (rowId != -1) {
            result = ContentUris.withAppendedId(uri, rowId);
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return result;
    }

    @Override
    public boolean onCreate() {
        mOpenHelper = new DatabaseHelper(getContext());
        return false;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        int match = urlMatcher.match(uri);
        Cursor ret = null;
        switch (match) {
            case GROUPS: {
                ret = db.query(TABLE, projection, selection,
                        selectionArgs, null, null, sortOrder);
                break;
            }
            case GROUPS_ID: {
                ret = db.query(TABLE, projection, GroupColumns._ID + "=?",
                        new String[] {
                            uri.getLastPathSegment()
                        }, null, null,
                        sortOrder);
                break;
            }
        }
        return ret;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        int count = 0;
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int match = urlMatcher.match(uri);
        switch (match) {
            case GROUPS: {
                count = db.update(TABLE, values, selection, selectionArgs);
                break;
            }
        }
        if (count > 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    private static class DatabaseHelper extends SQLiteOpenHelper {

        private Context mContext;

        public DatabaseHelper(Context context) {
            this(context, DATABASES, null, VERSION);
            mContext = context;
        }

        public DatabaseHelper(Context context, String name,
                CursorFactory factory, int version) {
            super(context, name, factory, version);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {

            db.execSQL("CREATE TABLE local_groups ("
                    + LocalGroups.GroupColumns._ID + " INTEGER PRIMARY KEY,"
                    + LocalGroups.GroupColumns.TITLE + " text,"
                    + LocalGroups.GroupColumns.COUNT + " INTEGER);");

            db.execSQL("insert into local_groups (" + LocalGroups.GroupColumns.TITLE + ") values ("
                    + DatabaseUtils.sqlEscapeString(
                            mContext.getString(R.string.group_family)) + ")");
            db.execSQL("insert into local_groups (" + LocalGroups.GroupColumns.TITLE + ") values ("
                    + DatabaseUtils.sqlEscapeString(
                            mContext.getString(R.string.group_friend)) + ")");
            db.execSQL("insert into local_groups (" + LocalGroups.GroupColumns.TITLE + ") values ("
                    + DatabaseUtils.sqlEscapeString(
                            mContext.getString(R.string.group_work)) + ")");

        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        }

    }

}
