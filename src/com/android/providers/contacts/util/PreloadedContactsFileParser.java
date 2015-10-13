package com.android.providers.contacts.util;

import android.content.ContentProviderOperation;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;
import com.android.providers.contacts.Constants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Responsible for parsing the preloaded_contacts.json file and helping generate database commands
 * to persist that information.
 *
 * Details about the json schema and encoding specification for the properties can be found in
 * the preloaded_contacts.schema file
 */
public class PreloadedContactsFileParser {

    private static final String TAG = "PreloadContacts";

    private static String TOKEN_AT = "@";
    private static String TOKEN_AT_SUB = "android.provider.ContactsContract$CommonDataKinds";
    private static String TOKEN_CONTACTS_ROOT = "contacts";
    private static String TOKEN_CONTACT_DATA = "data";
    private static String TOKEN_MIMETYPE = "@mimetype";
    private static String TOKEN_MIMETYPE_SUB = "android.provider.ContactsContract$Data.MIMETYPE";

    private static Character CLASS_NAME_DELIMITER = '.';

    private static Pattern mExpressionPattern = Pattern.compile("\\{\\{(.+?)\\}\\}");

    private boolean mDebug;
    private JSONObject mJsonRoot;
    private HashMap<String,String> mResolvedNameCache;

    public PreloadedContactsFileParser(InputStream inputStream) throws JSONException {
        mDebug = Log.isLoggable(Constants.TAG_DEBUG_PRELOAD_CONTACTS, Log.DEBUG);
        String jsonString = convertInputStreamToString(inputStream);
        mJsonRoot = new JSONObject(jsonString);
        mResolvedNameCache = new HashMap<String, String>();
    }

    private String convertInputStreamToString(InputStream is) {
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();

        String line = null;
        try {
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return sb.toString();
    }

    /**
     * Parses the json object and creates the necessary {@link ContentProviderOperation}s to
     * construct the contacts specified
     */
    public ArrayList<ContentProviderOperation> parseForContacts() {
        try {
            ArrayList<ContentProviderOperation> cpOps = new ArrayList<ContentProviderOperation>();
            JSONArray contacts = mJsonRoot.getJSONArray(TOKEN_CONTACTS_ROOT);
            int numContacts = contacts.length();

            for (int i = 0; i < numContacts; ++i) {
                JSONArray contactData = contacts.getJSONObject(i).getJSONArray(TOKEN_CONTACT_DATA);
                int rawEntries = contactData.length();

                // create a new raw contact entry
                cpOps.add(
                        ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                        .build() );
                int cvBackRef = cpOps.size() - 1;

                for (int j = 0; j < rawEntries; ++j) {
                    JSONObject rawEntry = contactData.getJSONObject(j);
                    Iterator<String> keys = rawEntry.keys();

                    // build a ContentProviderOperation to add the contact's raw entry
                    ContentProviderOperation.Builder cpoBuilder =
                            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
                    cpoBuilder.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID,
                            cvBackRef);

                    while (keys.hasNext()) {
                        String key = keys.next();
                        String value = rawEntry.getString(key);

                        if (mDebug) {
                            Log.d(TAG, "parsing property : " + key);
                            Log.d(TAG, "parsing property value : " + value);
                        }

                        String resolvedKey = null;
                        // keys always need interpolation
                        resolvedKey = resolvePropertyName(key);
                        // determine if the property is an expression that need to be evaluated
                        String resolvedValue = value;
                        Matcher matcher = mExpressionPattern.matcher(value);
                        if (matcher.matches()) {
                            matcher.reset();
                            matcher.find();
                            resolvedValue = resolvePropertyName(matcher.group(1));
                        }

                        if (mDebug) {
                            Log.d(TAG, "resolved property name : " + resolvedKey);
                            Log.d(TAG, "resolved property value : " + resolvedValue);
                        }

                        if (TextUtils.isEmpty(resolvedKey) || TextUtils.isEmpty(resolvedValue)) {
                            // don't persist this raw_contact value
                            continue;
                        } else {
                            cpoBuilder.withValue(resolvedKey, resolvedValue);
                        }

                    }

                    cpOps.add(cpoBuilder.build());
                }

            }
            return cpOps;

        } catch (JSONException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * parses an object's property name to determine its codified value
     */
    private String resolvePropertyName(String encodedName) {
        if (TextUtils.isEmpty(encodedName)) {
            return null;
        }

        if (mResolvedNameCache.containsKey(encodedName)) {
            return mResolvedNameCache.get(encodedName);
        }

        String unwrappedName = encodedName;
        // check if any substitution rules apply
        if (TextUtils.equals(TOKEN_MIMETYPE, encodedName)) {
            unwrappedName = TOKEN_MIMETYPE_SUB;
        } else if (encodedName.startsWith(TOKEN_AT)) {
            unwrappedName = encodedName.replace(TOKEN_AT, TOKEN_AT_SUB);
        }

        if (mDebug) {
            Log.d(TAG, "encoded property name : " + encodedName);
            Log.d(TAG, "resolved property name : " + unwrappedName);
        }

        String resolvedName = resolveCodifiedName(unwrappedName);
        mResolvedNameCache.put(encodedName, resolvedName);
        return resolvedName;
    }

    /**
     * returns the string-ified value of the Java field the property name points to
     */
    private String resolveCodifiedName(String absoluteName) {
        int delimiterIndex = TextUtils.lastIndexOf(absoluteName, CLASS_NAME_DELIMITER);
        // ensure there is a field identifier to read
        if (delimiterIndex == -1 || delimiterIndex >= absoluteName.length() - 1) {
            return null;
        }

        String className = TextUtils.substring(absoluteName, 0, delimiterIndex);
        String fieldName = absoluteName.substring(delimiterIndex + 1);

        if (mDebug) {
            Log.d(TAG, "property's class : " + className);
            Log.d(TAG, "property's field : " + fieldName);
        }

        try {
            Class clazz = Class.forName(className);
            Field field = clazz.getField(fieldName);
            String fieldValue = field.get(clazz).toString();
            if (mDebug) {
                Log.d(TAG, "fully resolved property name : " + fieldValue);
            }
            return fieldValue;

        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        return null;
    }

}
