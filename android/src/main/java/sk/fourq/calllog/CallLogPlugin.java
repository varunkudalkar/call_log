package sk.fourq.calllog;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.provider.CallLog;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@TargetApi(Build.VERSION_CODES.M)
public class CallLogPlugin implements MethodCallHandler, PluginRegistry.RequestPermissionsResultListener {

    private static final String ALREADY_RUNNING = "ALREADY_RUNNING";
    private static final String PERMISSION_NOT_GRANTED = "PERMISSION_NOT_GRANTED";
    private static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private final Registrar registrar;
    private MethodCall request;
    private Result result;


    private CallLogPlugin(Registrar registrar) {
        this.registrar = registrar;
    }

    public static void registerWith(Registrar registrar) {
        final MethodChannel channel = new MethodChannel(registrar.messenger(), "sk.fourq.call_log");
        final CallLogPlugin callLogPlugin = new CallLogPlugin(registrar);
        channel.setMethodCallHandler(callLogPlugin);
        registrar.addRequestPermissionsResultListener(callLogPlugin);
    }

    @Override
    public void onMethodCall(MethodCall c, Result r) {
        if (request != null) {
            r.error(ALREADY_RUNNING, "Method call was cancelled. One method call is already running", null);
        }

        request = c;
        result = r;

        // String[] perm = {Manifest.permission.READ_PHONE_STATE};
        // if (hasPermissions(perm)) {
            handleCall();
        // } else {
        //     if(registrar.activity() != null){
        //         ActivityCompat.requestPermissions(
        //         registrar.activity(), perm, 0);
        //     } else {
        //         r.error("MISSING_PERMISSIONS", "Permission READ_CALL_LOG or READ_PHONE_STATE is required for plugin. Hovewer, plugin is unable to request permission because of background execution.", null);
        //     }
        // }
    }
/*

    private boolean hasPermissions(String[] permissions) {
        for(String perm : permissions) {
            if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(registrar.activeContext(), perm)) {
                return false;
            }
        }
        return true;
    }
    @Override
    public boolean onRequestPermissionsResult(int requestCode, String[] strings, int[] grantResults) {
        if (requestCode == 0) {
            //CHECK IF ALL REQUESTED PERMISSIONS ARE GRANTED
            for (int grantResult : grantResults) {
                if(grantResults[0] == PackageManager.PERMISSION_DENIED ){
                    return false;
                }
            }
            if (request != null) {
                handleCall();
            }
            return true;
        } else {
            if (result != null) {
                result.error(PERMISSION_NOT_GRANTED, null, null);
                cleanup();
            }
            return false;
        }
    }
*/
    private void handleCall() {
        switch (request.method) {
            case "get":
                queryLogs(null);
                break;
            case "query":
                String dateFrom = request.argument("dateFrom");
                String dateTo = request.argument("dateTo");
                String durationFrom = request.argument("durationFrom");
                String durationTo = request.argument("durationTo");
                String name = request.argument("name");
                String number = request.argument("number");
                String type = request.argument("type");
                String cachedMatchedNumber = request.argument("cachedMatchedNumber");
                String phoneAccountId = request.argument("phoneAccountId");

                List<String> predicates = new ArrayList<>();
                if (dateFrom != null) {
                    predicates.add(CallLog.Calls.DATE + " > " + dateFrom);
                }
                if (dateTo != null) {
                    predicates.add(CallLog.Calls.DATE + " < " + dateTo);
                }
                if (durationFrom != null) {
                    predicates.add(CallLog.Calls.DURATION + " > " + durationFrom);
                }
                if (durationTo != null) {
                    predicates.add(CallLog.Calls.DURATION + " < " + durationTo);
                }
                if (name != null) {
                    predicates.add(CallLog.Calls.CACHED_NAME + " LIKE '%" + name + "%'");
                }
                if (number != null) {
                    predicates.add(CallLog.Calls.NUMBER + " LIKE '%" + number + "%'");
                }
                if (cachedMatchedNumber != null) {
                    predicates.add(CallLog.Calls.CACHED_MATCHED_NUMBER + " LIKE '%" + number + "%'");
                }
                if (phoneAccountId != null) {
                    predicates.add(CallLog.Calls.PHONE_ACCOUNT_ID + " LIKE '%" + number + "%'");
                }
                if (type != null) {
                    predicates.add(CallLog.Calls.TYPE + " = " + type);
                }

                if (predicates.size() == 0) {
                    queryLogs(null);
                } else {
                    StringBuilder whereCondition = new StringBuilder();
                    for (String predicate : predicates) {
                        whereCondition.append((whereCondition.length() == 0) ? "" : " AND ").append(predicate);
                    }
                    queryLogs(whereCondition.toString());
                }
                break;
            default:
                result.notImplemented();
                cleanup();
        }
    }

    private static final String[] PROJECTION = {
            CallLog.Calls.CACHED_FORMATTED_NUMBER,
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.CACHED_NUMBER_TYPE,
            CallLog.Calls.CACHED_NUMBER_LABEL,
            CallLog.Calls.CACHED_MATCHED_NUMBER,
            CallLog.Calls.PHONE_ACCOUNT_ID
    };

    private void queryLogs(String query) {

        SubscriptionManager subscriptionManager = ContextCompat.getSystemService(registrar.context(), SubscriptionManager.class);
        List<SubscriptionInfo> subscriptions = subscriptionManager.getActiveSubscriptionInfoList();

        try (Cursor cursor = registrar.context().getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                PROJECTION,
                query,
                null,
                CallLog.Calls.DATE + " DESC"
        )) {
            List<HashMap<String, Object>> entries = new ArrayList<>();
            while (cursor != null && cursor.moveToNext()) {
                HashMap<String, Object> map = new HashMap<>();
                map.put("formattedNumber", cursor.getString(0));
                map.put("number", cursor.getString(1));
                map.put("callType", cursor.getInt(2));
                map.put("timestamp", cursor.getLong(3));
                map.put("duration", cursor.getInt(4));
                map.put("name", cursor.getString(5));
                map.put("cachedNumberType", cursor.getInt(6));
                map.put("cachedNumberLabel", cursor.getString(7));
                map.put("cachedMatchedNumber", cursor.getString(8));
                map.put("simDisplayName", getSimDisplayName(subscriptions, cursor.getString(9)));
                map.put("phoneAccountId", cursor.getString(9));
                entries.add(map);
            }
            result.success(entries);
            cleanup();
        } catch (Exception e) {
            result.error(INTERNAL_ERROR, e.getMessage(), null);
            cleanup();
        }
    }

    private String getSimDisplayName(List<SubscriptionInfo> subscriptions, String accountId) {
        if (accountId != null) {
            for(SubscriptionInfo info : subscriptions) {
                if (Integer.toString(info.getSubscriptionId()).equals(accountId) ||
                        accountId.contains(info.getIccId())) {
                    return String.valueOf(info.getDisplayName());
                }
            }
        }
        return null;
    }

    private void cleanup() {
        request = null;
        result = null;
    }
}
