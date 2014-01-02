package com.schinkowitch.cordova.facebook;

import java.util.Arrays;
import java.util.List;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Intent;
import android.util.Log;

import com.facebook.Request;
import com.facebook.Response;
import com.facebook.Session;
import com.facebook.Session.OpenRequest;
import com.facebook.Session.StatusCallback;
import com.facebook.SessionState;

public class FacebookPlugin extends CordovaPlugin {
	private static final String TAG = FacebookPlugin.class.getSimpleName();
	private static final List<String> ACTIONS = Arrays.asList("init", "login", "query", "logout");
	
	private String appId;
	
	@Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
		if (!ACTIONS.contains(action)) {
			return false;
		}
		
		Runnable task = getTask(action, args, callbackContext);
		
		cordova.getThreadPool().execute(task);
		
		return true;
	}	

	private Runnable getTask(final String action, final JSONArray args, final CallbackContext callbackContext) {
		return new Runnable() {
			public void run() {
				try {			
					if ("init".equals(action)) {
						init(args, callbackContext);
					} else if ("login".equals(action)) {
						login(args, callbackContext);
					} else if ("query".equals(action)) {
						query(args, callbackContext);
					} else if ("logout".equals(action)) {
						logout(callbackContext);
					} else {
						throw new IllegalArgumentException(action + " not implemented");
					}
				} catch (Exception e) {
					Log.e(TAG, "Error executing action", e);
					callbackContext.error(e.toString());
				}				
			}
		};
	}

	private void init(JSONArray args, CallbackContext callbackContext) {
	    Log.d(TAG, "In init");
		try {
			this.appId = args.getString(0);
		} catch (JSONException e) {
			throw new RuntimeException("Error getting appId", e);
		}

		Log.d(TAG, "Initialized with appId: " + this.appId);

		callbackContext.success();
	}

/*
	private void getLoginStatus(JSONArray args, CallbackContext callbackContext) throws JSONException {
		Session session = Session.getActiveSession();
		
		if (session == null) {	
			Session tempSession = new Session.Builder(cordova.getActivity())
				.setApplicationId(this.appId)
				.build();
			
			if (tempSession.getState() == SessionState.CREATED_TOKEN_LOADED) {
				session = tempSession;
				Session.setActiveSession(session);
				session.openForRead(null);
			}
		}
		
		if (session == null) {
			callbackContext.success(new JSONObject().put("status", "unknown"));
		} else {		
			callbackContext.success(buildAuthorizationResponse(session));
		}
	}
*/
	private void login(JSONArray args, CallbackContext callbackContext) throws JSONException {
		Session session = Session.getActiveSession();		
		
		if (session == null || session.isClosed()) {
			Log.d(TAG, "Building new session");
			session = new Session.Builder(cordova.getActivity())
				.setApplicationId(this.appId)
				.build();
			Session.setActiveSession(session);
		} else {
			Log.d(TAG, "Existing session " + session.getState());
		}
		
		if (session.isOpened()) {
			Log.d(TAG, "Session already open");
			callbackContext.success(buildAuthorizationResponse(session));
			return;
		}
		
		final CallbackContext callback = callbackContext;
		
		OpenRequest openRequest = new OpenRequest(cordova.getActivity());
		openRequest.setPermissions("email"); // TODO add permissions from args
		openRequest.setCallback(new StatusCallback() {
			@Override
			public void call(Session session, SessionState state,
					Exception exception) {
				Log.d(TAG, "In status callback open for read " + state);				
				
				if (state == SessionState.OPENING) {
					return;
				}
				
				try {
					JSONObject response = buildAuthorizationResponse(session);
					
					callback.success(response);
				} catch (JSONException e) {
					Log.e(TAG, "JSONException", e);
					callback.error(e.toString());
				}
			}			
		});
		
		cordova.setActivityResultCallback(this);
		
		session.openForRead(openRequest);
	}
	
	private JSONObject buildAuthorizationResponse(Session session) throws JSONException {
		boolean connected = session.getPermissions().contains("email");
		
		JSONObject response = new JSONObject();
		response.put("status", connected ? "connected" : "not_authorized");
		
		if (connected) {
			response.put("authResponse", 
					new JSONObject().put("accessToken", session.getAccessToken())
						.put("expirationDate", session.getExpirationDate()));
		}
		
		return response;
	}
	

	private void query(JSONArray args, CallbackContext callbackContext) throws JSONException {
	    Log.d(TAG, "In query: " + args.getString(0));

	    Session session = Session.getActiveSession();

        if (session == null) {
            Log.d(TAG, "building session");
            Session tempSession = new Session.Builder(cordova.getActivity())
                .setApplicationId(this.appId)
                .build();

            if (tempSession.getState() == SessionState.CREATED_TOKEN_LOADED) {
                Log.d(TAG, "opening session");
                session = tempSession;
                Session.setActiveSession(session);
                session.openForRead(null);
            }
        }

        if (session == null || !session.getState().isOpened()) {
            callbackContext.error("login_required");
            return;
        }

        Log.d(TAG, "executing query: " + args.getString(0));

		Request request = new Request(Session.getActiveSession(), args.getString(0));
		
		Response response = request.executeAndWait();
		
		if (response.getError() != null) {
			callbackContext.error(response.getError().toString());
		} else {
			callbackContext.success(response.getGraphObject().getInnerJSONObject());
		}
	}
	
	protected void logout(CallbackContext callbackContext) {
		Session session = Session.getActiveSession();
		
		if (session == null || session.isClosed()) {
			callbackContext.error("User is not logged in");
			return;
		}
		
		session.closeAndClearTokenInformation();
		callbackContext.success();
	}
	
	@Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Session.getActiveSession().onActivityResult(cordova.getActivity(), requestCode, resultCode, data);
    }
}
 
