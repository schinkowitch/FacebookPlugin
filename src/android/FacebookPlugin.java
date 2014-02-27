package com.schinkowitch.cordova.facebook;

import java.util.Arrays;
import java.util.Iterator;
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
import com.facebook.RequestBatch;
import com.facebook.Response;
import com.facebook.Session;
import com.facebook.Session.OpenRequest;
import com.facebook.Session.StatusCallback;
import com.facebook.SessionDefaultAudience;
import com.facebook.SessionState;
import com.facebook.model.OpenGraphAction;
import com.facebook.model.OpenGraphObject;

public class FacebookPlugin extends CordovaPlugin {
	private static final String TAG = FacebookPlugin.class.getSimpleName();
	private static final List<String> ACTIONS
		= Arrays.asList("init", "login", "getPermissions", "requestReadPermissions", "requestPublishPermissions", 
				"query", "publishAction", "getAccessToken", "logout");
	
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
					} else if ("getAccessToken".equals(action)) {
						getAccessToken(callbackContext);
					} else if ("getPermissions".equals(action)) {
						getPermissions(callbackContext);
					} else if ("requestReadPermissions".equals(action)) {
						requestReadPermissions(args, callbackContext);
					} else if ("requestPublishPermissions".equals(action)) {
						requestPublishPermissions(args, callbackContext);
					} else if ("query".equals(action)) {
						query(args, callbackContext);
					} else if ("publishAction".equals(action)) {
						publish(args, callbackContext);
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

	private void requestReadPermissions(JSONArray args, CallbackContext callbackContext) throws JSONException {
		Session.NewPermissionsRequest permissionsRequest = buildPermissionsRequest(args, callbackContext);
		        	
		Session.getActiveSession().requestNewReadPermissions(permissionsRequest);			
	}
	
	private void requestPublishPermissions(JSONArray args, final CallbackContext callbackContext) throws JSONException {
		Session.NewPermissionsRequest permissionsRequest = buildPermissionsRequest(args, callbackContext);
		
		String audienceArg = args.getString(1);
		SessionDefaultAudience audience = SessionDefaultAudience.NONE;
		
		if ("friends".equals(audienceArg)) {
			audience = SessionDefaultAudience.FRIENDS;
		} else if ("only_me".equals(audienceArg)) {
			audience = SessionDefaultAudience.ONLY_ME;
		}  else if ("everyone".equals(audienceArg)) {
			audience = SessionDefaultAudience.EVERYONE;
		}
		
		permissionsRequest.setDefaultAudience(audience);
    	
		Session.getActiveSession().requestNewPublishPermissions(permissionsRequest);			
	}
	
	private Session.NewPermissionsRequest buildPermissionsRequest(JSONArray args, final CallbackContext callbackContext) throws JSONException {
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
		
		return permissionsRequest;
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
		Log.d(TAG, "executing query: " + args.getString(0));

		Request request = new Request(Session.getActiveSession(), args.getString(0));
		
		Response response = request.executeAndWait();
		
		if (response.getError() != null) {
			callbackContext.error(response.getError().toString());
		} else {
			callbackContext.success(response.getGraphObject().getInnerJSONObject());
		}
	}
	
	private void publish(JSONArray args, CallbackContext callbackContext) throws JSONException {
		JSONObject actionParams = args.getJSONObject(0);
		
		if (actionParams.has("object")) {
			RequestBatch requestBatch = new RequestBatch();
			
			OpenGraphObject object = buildOpenGraphObject(actionParams);
			
			Request objectRequest = Request.newPostOpenGraphObjectRequest(Session.getActiveSession(), object, null);
			objectRequest.setBatchEntryName("objectCreate");
			
	        OpenGraphAction action = OpenGraphAction.Factory.createForPost(actionParams.getString("action"));
	        
	        action.setMessage(actionParams.getString("message"));
	        action.setProperty("place", actionParams.getLong("place"));
	        action.setExplicitlyShared(actionParams.getBoolean("explicitlyShared"));
	        action.setProperty(actionParams.getString("objectType"), "{result=objectCreate:$.id}");
	        
			Request request = Request.newPostOpenGraphActionRequest(Session.getActiveSession(), action, null);
			request.setBatchEntryDependsOn("objectCreate");
			
			requestBatch.add(objectRequest);
			requestBatch.add(request);
			
			List<Response> responses = requestBatch.executeAndWait();
			Response lastResponse = null;
			
			for (Response response : responses) {
				
				if (response.getError() != null) {
					callbackContext.error(response.getError().toString());
					return;
				}			
				
				lastResponse = response;
			}
			
			callbackContext.success(lastResponse.getGraphObject().getInnerJSONObject());
		} else {
	        OpenGraphAction action = OpenGraphAction.Factory.createForPost(actionParams.getString("action"));
	        
	        action.setMessage(actionParams.getString("message"));
	        action.setProperty("place", actionParams.getLong("place"));
	        action.setExplicitlyShared(actionParams.getBoolean("explicitlyShared"));
	        action.setProperty(actionParams.getString("objectType"), actionParams.getString("objectId"));
	        
			Request request = Request.newPostOpenGraphActionRequest(Session.getActiveSession(), action, null);
			
			Response response = request.executeAndWait();
			
			if (response.getError() != null) {
				callbackContext.error(response.getError().toString());
			} else {
				callbackContext.success(response.getGraphObject().getInnerJSONObject());
			}			
		}
	}

	private OpenGraphObject buildOpenGraphObject(JSONObject actionParams)
			throws JSONException {
		JSONObject jsonObject = actionParams.getJSONObject("object");
		
		OpenGraphObject object = OpenGraphObject.Factory.createForPost(jsonObject.getString("type"));
		object.setTitle(jsonObject.getString("title"));
		object.setDescription(jsonObject.getString("description"));
		
		if (jsonObject.has("url")) {
			object.setUrl(jsonObject.getString("url"));
		}
		
		object.setImageUrls(Arrays.asList(jsonObject.getString("image")));
		
		JSONObject dataObject = jsonObject.getJSONObject("data");
		
		@SuppressWarnings("unchecked")
		Iterator<String> keys = (Iterator<String>) dataObject.keys();
		
		while (keys.hasNext()) {
			String key = keys.next();
			
			object.getData().setProperty(key, dataObject.get(key));
		}
		
		return object;
	}
	
	private void getAccessToken(CallbackContext callbackContext) throws JSONException {
		Session session = Session.getActiveSession();
		JSONObject response = new JSONObject();
		
		if (session == null || !session.isOpened()) {
			response.put("error", "not_authorized");			
		} else {
			response.put("accessToken", session.getAccessToken());
		}
		
		callbackContext.success(response);
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