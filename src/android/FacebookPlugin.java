package com.schinkowitch.cordova.facebook;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

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
	private static final List<String> ACTIONS = Arrays.asList("init", "login", "getPermissions", "requestReadPermissions", "query", "logout");
	
	private String appId;
	
	@Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
		if (!ACTIONS.contains(action)) {
			Log.e(TAG, "Invalid action: " + action);
			return false;
		}
		
		Runnable task = getTask(action, args, callbackContext);
		
		super.cordova.getThreadPool().execute(task);
		
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
					} else if ("getPermissions".equals(action)) {
						getPermissions(callbackContext);
					} else if ("requestReadPermissions".equals(action)) {
						requestReadPermissions(args, callbackContext);
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
		try {
			this.appId = args.getString(0);
		} catch (JSONException e) {
			throw new RuntimeException("Error getting appId", e);
		}
		
		super.cordova.setActivityResultCallback(this);

		Log.d(TAG, "Initialized with appId: " + this.appId);

		callbackContext.success();
	}

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
		
		List<String> permissions = new ArrayList<String>();
		
		permissions.add("basic_info");
		JSONArray argumentPermissions = args.getJSONArray(0);
		
		for (int i = 0; i < argumentPermissions.length(); i++) {
			permissions.add(argumentPermissions.getString(i));
		}
		
		OpenRequest openRequest = new OpenRequest(cordova.getActivity())
			.setPermissions(permissions)
			.setCallback(new StatusCallback() {
				@Override
				public void call(Session session, SessionState state,
						Exception exception) {
					Log.d(TAG, "In status callback open for read " + state);				
					
					if (state == SessionState.OPENING) {
						return;
					}
					
					session.removeCallback(this);
					
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
		JSONObject response = new JSONObject();
		
		if (session.getState().isOpened()) {
			response.put("status", "connected");
		} else if (session.getState() == SessionState.CLOSED_LOGIN_FAILED) {
			response.put("status", "not_authorized");
		} else {
			response.put("status", "unknown");
		}
		
		return response;
	}
	
	private void getPermissions(CallbackContext callbackContext) throws JSONException {
		Session session = getSession();
		
		if (!session.getState().isOpened()) {
			callbackContext.success("login_required");
			return;
		}
		Log.d(TAG, "Permissions: " + Arrays.toString(session.getPermissions().toArray()));
		JSONObject response = new JSONObject();
		
		for (String permission : session.getPermissions()) {
			response.put(permission, 1);
		}
		
		callbackContext.success(response);
	}
	
	private Session getSession() {
	    Session session = Session.getActiveSession();

        if (session == null || session.getState().isClosed()) {
            Log.d(TAG, "building session");
            session = new Session.Builder(cordova.getActivity())
                .setApplicationId(this.appId)
                .build();

            if (session.getState() == SessionState.CREATED_TOKEN_LOADED) {
                Log.d(TAG, "opening session");
                session.openForRead(null);
            }
            
            Session.setActiveSession(session);
        }
		
        return session;
	}

	private void requestReadPermissions(JSONArray args, final CallbackContext callbackContext) throws JSONException {
		final List<String> permissions = asStringList(args.getJSONArray(0));
		
		Session.NewPermissionsRequest permissionsRequest 
			= new Session.NewPermissionsRequest(cordova.getActivity(), permissions);
		
		permissionsRequest.setCallback(new StatusCallback() {
			@Override
			public void call(Session session, SessionState state,
					Exception exception) {
				try {
					session.removeCallback(this);
					
					if (missingPermission(permissions)) {
						callbackContext.success("not_authorized");
					} else {
						callbackContext.success("authorized");
					}
				} catch (Exception e) {
					Log.e(TAG, "Error executing action", e);
					callbackContext.error(e.toString());
				}				
			}			
		});
		        	
		Session.getActiveSession().requestNewReadPermissions(permissionsRequest);			
	}
	
	private List<String> asStringList(JSONArray array) throws JSONException {
		List<String> list = new ArrayList<String>(array.length());
		
		for (int i = 0; i < array.length(); i++) {
			list.add(array.getString(i));
		}
		
		return list;
	}
	
	private boolean missingPermission(List<String> permissions) {
		List<String> sessionPermissions = getSession().getPermissions();
		for (String permission : permissions) {
			if (!sessionPermissions.contains(permission)) {
				return true;
			}
		}
		
		return false;
	}
	
	private void query(JSONArray args, CallbackContext callbackContext) throws JSONException {
		Session session = getSession();
		
        if (session == null || !session.getState().isOpened()) {
            callbackContext.error("login_required");
            return;
        }
        
        executeQuery(args, callbackContext);
	}	
	
	private void executeQuery(JSONArray args, CallbackContext callbackContext) throws JSONException {
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
 
