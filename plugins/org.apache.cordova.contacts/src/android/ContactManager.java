/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
*/
package org.apache.cordova.contacts;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Intent;
import android.provider.ContactsContract.Contacts;
import android.util.Log;

public class ContactManager extends CordovaPlugin {

    private ContactAccessor contactAccessor;
    private CallbackContext callbackContext;        // The callback context from which we were invoked.
    private JSONArray executeArgs;
    
    private static final String LOG_TAG = "Contact Query";

    public static final int UNKNOWN_ERROR = 0;
    public static final int INVALID_ARGUMENT_ERROR = 1;
    public static final int TIMEOUT_ERROR = 2;
    public static final int PENDING_OPERATION_ERROR = 3;
    public static final int IO_ERROR = 4;
    public static final int NOT_SUPPORTED_ERROR = 5;
    public static final int PERMISSION_DENIED_ERROR = 20;
    private static final int CONTACT_PICKER_RESULT = 1000;

    /**
     * Constructor.
     */
    public ContactManager() {
    }

    /**
     * Executes the request and returns PluginResult.
     *
     * @param action            The action to execute.
     * @param args              JSONArray of arguments for the plugin.
     * @param callbackContext   The callback context used when calling back into JavaScript.
     * @return                  True if the action was valid, false otherwise.
     */
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
        
        this.callbackContext = callbackContext;
        this.executeArgs = args; 
        
        /**
         * Check to see if we are on an Android 1.X device.  If we are return an error as we
         * do not support this as of Cordova 1.0.
         */
        if (android.os.Build.VERSION.RELEASE.startsWith("1.")) {
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, ContactManager.NOT_SUPPORTED_ERROR));
            return true;
        }

        /**
         * Only create the contactAccessor after we check the Android version or the program will crash
         * older phones.
         */
        if (this.contactAccessor == null) {
            this.contactAccessor = new ContactAccessorSdk5(this.cordova);
        }

        if (action.equals("search")) {
            final JSONArray filter = args.getJSONArray(0);
            final JSONObject options = args.get(1) == null ? null : args.getJSONObject(1);
            this.cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    JSONArray res = contactAccessor.search(filter, options);
                    callbackContext.success(res);
                }
            });
        }
        else if (action.equals("save")) {
            final JSONObject contact = args.getJSONObject(0);
            this.cordova.getThreadPool().execute(new Runnable(){
                public void run() {
                    JSONObject res = null;
                    String id = contactAccessor.save(contact);
                    if (id != null) {
                        try {
                            res = contactAccessor.getContactById(id);
                        } catch (JSONException e) {
                            Log.e(LOG_TAG, "JSON fail.", e);
                        }
                    }
                    if (res != null) {
                        callbackContext.success(res);
                    } else {
                        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, UNKNOWN_ERROR));
                    }
                }
            });
        }
        else if (action.equals("remove")) {
            final String contactId = args.getString(0);
            this.cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    if (contactAccessor.remove(contactId)) {
                        callbackContext.success();
                    } else {
                        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, UNKNOWN_ERROR));
                    }
                }
            });
        }
        else if (action.equals("pickContact")) {
            pickContactAsync();
        }
        else {
            return false;
        }
        return true;
    }
    
    /**
     * Launches the Contact Picker to select a single contact.
     */
    private void pickContactAsync() {
        final CordovaPlugin plugin = (CordovaPlugin) this;
        Runnable worker = new Runnable() {
            public void run() {
                Intent contactPickerIntent = new Intent(Intent.ACTION_PICK, Contacts.CONTENT_URI);
                plugin.cordova.startActivityForResult(plugin, contactPickerIntent, CONTACT_PICKER_RESULT);
            }
        };
        this.cordova.getThreadPool().execute(worker);
    }
    
    /**
     * Called when user picks contact.
     * @param requestCode       The request code originally supplied to startActivityForResult(),
     *                          allowing you to identify who this result came from.
     * @param resultCode        The integer result code returned by the child activity through its setResult().
     * @param intent            An Intent, which can return result data to the caller (various data can be attached to Intent "extras").
     * @throws JSONException
     */
    public void onActivityResult(int requestCode, int resultCode, final Intent intent) {
        if (requestCode == CONTACT_PICKER_RESULT) {
            if (resultCode == Activity.RESULT_OK) {
                String contactId = intent.getData().getLastPathSegment();
                try {
                    JSONObject contact = contactAccessor.getContactById(contactId);
                    this.callbackContext.success(contact);
                    return;
                } catch (JSONException e) {
                    Log.e(LOG_TAG, "JSON fail.", e);
                }
            } else if (resultCode == Activity.RESULT_CANCELED){
                this.callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.NO_RESULT, UNKNOWN_ERROR));
                return;
            }
            this.callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, UNKNOWN_ERROR));
        }
    }
}
