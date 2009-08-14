/*
 * Copyright (C) 2009 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.providers.contacts;

import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.provider.ContactsContract;

import java.util.ArrayList;

/**
 * General purpose {@link ContentProvider} base class that uses SQLiteDatabase for storage.
 */
public abstract class SQLiteContentProvider extends ContentProvider {

    private static final String TAG = "SQLiteContentProvider";

    private SQLiteOpenHelper mOpenHelper;
    private volatile boolean mNotifyChange;
    protected SQLiteDatabase mDb;

    private final ThreadLocal<Boolean> mApplyingBatch = new ThreadLocal<Boolean>();

    @Override
    public boolean onCreate() {
        Context context = getContext();
        mOpenHelper = getOpenHelper(context);
        return true;
    }

    protected abstract SQLiteOpenHelper getOpenHelper(Context context);

    /**
     * The equivalent of the {@link #insert} method, but invoked within a transaction.
     */
    protected abstract Uri insertInTransaction(Uri uri, ContentValues values);

    /**
     * The equivalent of the {@link #update} method, but invoked within a transaction.
     */
    protected abstract int updateInTransaction(Uri uri, ContentValues values, String selection,
            String[] selectionArgs);

    /**
     * The equivalent of the {@link #delete} method, but invoked within a transaction.
     */
    protected abstract int deleteInTransaction(Uri uri, String selection, String[] selectionArgs);

    protected SQLiteOpenHelper getOpenHelper() {
        return mOpenHelper;
    }

    private boolean applyingBatch() {
        return mApplyingBatch.get() != null && mApplyingBatch.get();
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        Uri result = null;
        boolean applyingBatch = applyingBatch();
        if (!applyingBatch) {
            mDb = mOpenHelper.getWritableDatabase();
            mDb.beginTransaction();
            try {
                result = insertInTransaction(uri, values);
                if (result != null) {
                    mNotifyChange = true;
                }
                mDb.setTransactionSuccessful();
            } finally {
                mDb.endTransaction();
            }

            onTransactionComplete();
        } else {
            result = insertInTransaction(uri, values);
            if (result != null) {
                mNotifyChange = true;
            }
        }
        return result;
    }

    @Override
    public int bulkInsert(Uri uri, ContentValues[] values) {
        int numValues = values.length;
        mDb = mOpenHelper.getWritableDatabase();
        mDb.beginTransaction();
        try {
            for (int i = 0; i < numValues; i++) {
                Uri result = insertInTransaction(uri, values[i]);
                if (result != null) {
                    mNotifyChange = true;
                }
            }
            mDb.setTransactionSuccessful();
        } finally {
            mDb.endTransaction();
        }

        onTransactionComplete();
        return numValues;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        int count = 0;
        boolean applyingBatch = applyingBatch();
        if (!applyingBatch) {
            mDb = mOpenHelper.getWritableDatabase();
            mDb.beginTransaction();
            try {
                count = updateInTransaction(uri, values, selection, selectionArgs);
                if (count > 0) {
                    mNotifyChange = true;
                }
                mDb.setTransactionSuccessful();
            } finally {
                mDb.endTransaction();
            }

            onTransactionComplete();
        } else {
            count = updateInTransaction(uri, values, selection, selectionArgs);
            if (count > 0) {
                mNotifyChange = true;
            }
        }

        return count;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int count = 0;
        boolean applyingBatch = applyingBatch();
        if (!applyingBatch) {
            mDb = mOpenHelper.getWritableDatabase();
            mDb.beginTransaction();
            try {
                count = deleteInTransaction(uri, selection, selectionArgs);
                if (count > 0) {
                    mNotifyChange = true;
                }
                mDb.setTransactionSuccessful();
            } finally {
                mDb.endTransaction();
            }

            onTransactionComplete();
        } else {
            count = deleteInTransaction(uri, selection, selectionArgs);
            if (count > 0) {
                mNotifyChange = true;
            }
        }
        return count;
    }

    @Override
    public ContentProviderResult[] applyBatch(ArrayList<ContentProviderOperation> operations)
            throws OperationApplicationException {
        ContentProviderResult[] results = null;
        mDb = mOpenHelper.getWritableDatabase();
        mDb.beginTransaction();
        try {
            mApplyingBatch.set(true);
            results = super.applyBatch(operations);
            mDb.setTransactionSuccessful();
        } finally {
            mApplyingBatch.set(false);
            mDb.endTransaction();
        }

        onTransactionComplete();
        return results;
    }

    protected void onTransactionComplete() {
        if (mNotifyChange) {
            mNotifyChange = false;
            getContext().getContentResolver().notifyChange(ContactsContract.AUTHORITY_URI, null);
        }
    }
}