package com.wv.flutter_zoom;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import us.zoom.sdk.CustomizedNotificationData;
import us.zoom.sdk.InMeetingNotificationHandle;
import us.zoom.sdk.InMeetingService;
import us.zoom.sdk.JoinMeetingOptions;
import us.zoom.sdk.JoinMeetingParams;
import us.zoom.sdk.MeetingService;
import us.zoom.sdk.MeetingStatus;
import us.zoom.sdk.MeetingViewsOptions;
import us.zoom.sdk.StartMeetingOptions;
import us.zoom.sdk.StartMeetingParams4NormalUser;
import us.zoom.sdk.StartMeetingParamsWithoutLogin;
import us.zoom.sdk.ZoomAuthenticationError;
import us.zoom.sdk.ZoomError;
import us.zoom.sdk.ZoomSDK;
import us.zoom.sdk.ZoomSDKAuthenticationListener;
import us.zoom.sdk.ZoomSDKInitParams;
import us.zoom.sdk.ZoomSDKInitializeListener;
import io.flutter.plugin.common.MethodChannel.Result;

/**
 * FlutterZoomPlugin
 */
public class FlutterZoomPlugin implements FlutterPlugin, MethodChannel.MethodCallHandler, ActivityAware {
    Activity activity;
    private Result pendingResult;

    private MethodChannel methodChannel;
    private Context context;
    private EventChannel meetingStatusChannel;
    private InMeetingService inMeetingService;


    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        this.activity = binding.getActivity();
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        this.activity = binding.getActivity();
    }

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        context = flutterPluginBinding.getApplicationContext();
        methodChannel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "com.wv/flutter_zoom");
        methodChannel.setMethodCallHandler(this);

        meetingStatusChannel = new EventChannel(flutterPluginBinding.getBinaryMessenger(), "com.wv/flutter_zoom_event_stream");
    }

    @Override
    public void onMethodCall(@NonNull MethodCall methodCall, @NonNull final Result result) {
        switch (methodCall.method) {
            case "init":
                init(methodCall, result);
                break;
            case "join":
                joinMeeting(methodCall, result);
                break;
            case "startNormal":
                startMeetingNormal(methodCall, result);
                break;
            case "meeting_status":
                meetingStatus(result);
                break;
            case "meeting_details":
                meetingDetails(result);
                break;
            default:
                result.notImplemented();
        }
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        methodChannel.setMethodCallHandler(null);
    }

    private void sendReply(List data) {
        if (this.pendingResult == null) {
            return;
        }
        this.pendingResult.success(data);
        this.clearPendingResult();
    }

    private void clearPendingResult() {
        this.pendingResult = null;
    }

    //Initializing the Zoom SDK for Android
    private void init(final MethodCall methodCall, final Result result) {
        Map<String, String> options = methodCall.arguments();

        ZoomSDK zoomSDK = ZoomSDK.getInstance();

        if (zoomSDK.isInitialized()) {
            List<Integer> response = Arrays.asList(0, 0);
            result.success(response);
            return;
        }
        String token = options.get("accessToken");
//        try{
//            String header = "{\"alg\": \"HS256\",\"typ\": \"JWT\"}";
//            Strq  ing body = "{\n" +
//                    "  \"appKey\": \"4j5zxMjeTgGYLTgqH4dx4g\",\n" +
//                    "  \"iat\":1703493480,\n" +
//                    "  \"exp\":1703666280,\n" +
//                    "  \"tokenExp\":1703666280\n" +
//                    "}";
//
//            Mac hmacSha256 = Mac.getInstance("HmacSHA256");
//            SecretKeySpec secretKey = new SecretKeySpec(header.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
//            hmacSha256.init(secretKey);
//            byte[] hash = hmacSha256.doFinal(body.getBytes(StandardCharsets.UTF_8));
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                token = Base64.getEncoder().encodeToString(hash);
//            }
//        } catch (Exception e) {
//            token = "";
//            e.printStackTrace();
//        }

        ZoomSDKInitParams initParams = new ZoomSDKInitParams();

        initParams.jwtToken = token;
        initParams.domain = options.get("domain");
        initParams.enableLog = true;

        final InMeetingNotificationHandle handle = (context, intent) -> {
            intent = new Intent(context, FlutterZoomPlugin.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            if (context == null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            intent.setAction(InMeetingNotificationHandle.ACTION_RETURN_TO_CONF);
            assert context != null;
            context.startActivity(intent);
            return true;
        };

        //Set custom Notification fro android
        final CustomizedNotificationData data = new CustomizedNotificationData();
        data.setContentTitleId(R.string.app_name_zoom_local);
        data.setLargeIconId(R.drawable.zm_mm_type_emoji);
        data.setSmallIconId(R.drawable.zm_mm_type_emoji);
        data.setSmallIconForLorLaterId(R.drawable.zm_mm_type_emoji);

        ZoomSDKInitializeListener listener = new ZoomSDKInitializeListener() {
            /**
             * @param errorCode {@link ZoomError#ZOOM_ERROR_SUCCESS} if the SDK has been initialized successfully.
             */
            @Override
            public void onZoomSDKInitializeResult(int errorCode, int internalErrorCode) {
                List<Integer> response = Arrays.asList(errorCode, internalErrorCode);

                if (errorCode != ZoomError.ZOOM_ERROR_SUCCESS) {
                    System.out.println("Failed to initialize Zoom SDK");
                    result.success(response);
                    return;
                }

                ZoomSDK zoomSDK = ZoomSDK.getInstance();
                ZoomSDK.getInstance().getMeetingSettingsHelper().enableShowMyMeetingElapseTime(true);
                ZoomSDK.getInstance().getMeetingSettingsHelper().setCustomizedNotificationData(data, handle);

                MeetingService meetingService = zoomSDK.getMeetingService();
                meetingStatusChannel.setStreamHandler(new StatusStreamHandler(meetingService));
                result.success(response);
            }

            @Override
            public void onZoomAuthIdentityExpired() {
            }
        };
        zoomSDK.initialize(context, listener, initParams);
    }

    //Join Meeting with passed Meeting ID and Passcode
    private void joinMeeting(MethodCall methodCall, Result result) {

        Map<String, String> options = methodCall.arguments();

        ZoomSDK zoomSDK = ZoomSDK.getInstance();
        if (!zoomSDK.isInitialized()) {
            System.out.println("Not initialized!!!!!!");

            result.success(false);
            return;
        } else {
            boolean hideMeetingInviteUrl = parseBoolean(options, "hideMeetingInviteUrl");
            ZoomSDK.getInstance().getZoomUIService().hideMeetingInviteUrl(hideMeetingInviteUrl);
        }

        MeetingService meetingService = zoomSDK.getMeetingService();

        JoinMeetingOptions opts = new JoinMeetingOptions();
        opts.no_invite = parseBoolean(options, "disableInvite");
        opts.no_share = parseBoolean(options, "disableShare");
        opts.no_titlebar = parseBoolean(options, "disableTitlebar");
        opts.no_driving_mode = parseBoolean(options, "disableDrive");
        opts.no_dial_in_via_phone = parseBoolean(options, "disableDialIn");
        opts.no_disconnect_audio = parseBoolean(options, "noDisconnectAudio");
        opts.no_audio = parseBoolean(options, "noAudio");
        opts.meeting_views_options = parseInt(options, "meetingViewOptions", 0);
        boolean view_options = parseBoolean(options, "viewOptions");
        if (view_options) {
            opts.meeting_views_options = MeetingViewsOptions.NO_TEXT_MEETING_ID + MeetingViewsOptions.NO_TEXT_PASSWORD + MeetingViewsOptions.NO_BUTTON_PARTICIPANTS;
        }

        JoinMeetingParams params = new JoinMeetingParams();

        params.displayName = options.get("userId");
        params.meetingNo = options.get("meetingId");
        params.password = options.get("meetingPassword");
        meetingService.joinMeetingWithParams(context, params, opts);
        result.success(true);
    }

    //Perform start meeting function with logging in to the zoom account (Only for passed meeting id)
    private void startMeetingNormal(final MethodCall methodCall, final Result result) {
        this.pendingResult = result;
        ZoomSDK zoomSDK = ZoomSDK.getInstance();

        if (!zoomSDK.isInitialized()) {
            System.out.println("Not initialized!!!!!!");
            sendReply(Arrays.asList("SDK ERROR", "001"));
            return;
        }
        startMeetingNormalInternal(methodCall);

    }

    // Meeting ID passed Start Meeting Function called on startMeetingNormal triggered via startMeetingNormal function
    private void startMeetingNormalInternal(MethodCall methodCall) {
        Map<String, String> options = methodCall.arguments();

        ZoomSDK zoomSDK = ZoomSDK.getInstance();

        if (!zoomSDK.isInitialized()) {
            System.out.println("Not initialized!!!!!!");
            sendReply(Arrays.asList("SDK ERROR", "001"));
            return;
        }

        MeetingService meetingService = zoomSDK.getMeetingService();
        if (meetingService == null) {
            return;
        }

        StartMeetingOptions opts = new StartMeetingOptions();
        opts.no_invite = parseBoolean(options, "disableInvite");
        opts.no_share = parseBoolean(options, "disableShare");
        opts.no_driving_mode = parseBoolean(options, "disableDrive");
        opts.no_dial_in_via_phone = parseBoolean(options, "disableDialIn");
        opts.no_disconnect_audio = parseBoolean(options, "noDisconnectAudio");
        opts.no_audio = parseBoolean(options, "noAudio");
        opts.no_titlebar = parseBoolean(options, "disableTitlebar");
        boolean view_options = parseBoolean(options, "viewOptions");
        if (view_options) {
            opts.meeting_views_options = MeetingViewsOptions.NO_TEXT_MEETING_ID + MeetingViewsOptions.NO_TEXT_PASSWORD + MeetingViewsOptions.NO_BUTTON_PARTICIPANTS;
        }

        StartMeetingParamsWithoutLogin params = new StartMeetingParamsWithoutLogin();
        params.meetingNo = options.get("meetingId");
//        params.userId = options.get("userId");
        params.displayName = options.get("displayName");
        params.userType = MeetingService.USER_TYPE_UNKNOWN;
        params.zoomAccessToken = options.get("zoomAccessToken");
        meetingService.startMeetingWithParams(context, params, opts);
        inMeetingService = zoomSDK.getInMeetingService();
        sendReply(Arrays.asList("MEETING SUCCESS", "200"));

    }

    //Helper Function for parsing string to boolean value
    private boolean parseBoolean(Map<String, String> options, String property) {
        return options.get(property) != null && Boolean.parseBoolean(options.get(property));
    }

    private int parseInt(Map<String, String> options, String property, int defaultValue) {
        return options.get(property) == null ? defaultValue : Integer.parseInt(options.get(property));
    }

    //Get Meeting Details Programmatically after Starting the Meeting
    private void meetingDetails(Result result) {
        ZoomSDK zoomSDK = ZoomSDK.getInstance();

        if (!zoomSDK.isInitialized()) {
            System.out.println("Not initialized!!!!!!");
            result.success(Arrays.asList("MEETING_STATUS_UNKNOWN", "SDK not initialized"));
            return;
        }
        MeetingService meetingService = zoomSDK.getMeetingService();

        if (meetingService == null) {
            result.success(Arrays.asList("MEETING_STATUS_UNKNOWN", "No status available"));
            return;
        }
        MeetingStatus status = meetingService.getMeetingStatus();

        result.success(status != null ? Arrays.asList(inMeetingService.getCurrentMeetingNumber(), inMeetingService.getMeetingPassword()) : Arrays.asList("MEETING_STATUS_UNKNOWN", "No status available"));
    }

    //Listen to meeting status on joinning and starting the mmeting
    private void meetingStatus(Result result) {

        ZoomSDK zoomSDK = ZoomSDK.getInstance();

        if (!zoomSDK.isInitialized()) {
            System.out.println("Not initialized!!!!!!");
            result.success(Arrays.asList("MEETING_STATUS_UNKNOWN", "SDK not initialized"));
            return;
        }
        MeetingService meetingService = zoomSDK.getMeetingService();

        if (meetingService == null) {
            result.success(Arrays.asList("MEETING_STATUS_UNKNOWN", "No status available"));
            return;
        }

        MeetingStatus status = meetingService.getMeetingStatus();
        result.success(status != null ? Arrays.asList(status.name(), "") : Arrays.asList("MEETING_STATUS_UNKNOWN", "No status available"));
    }


    @Override
    public void onDetachedFromActivityForConfigChanges() {
        this.activity = null;
    }

    @Override
    public void onDetachedFromActivity() {
        this.activity = null;
    }
}
