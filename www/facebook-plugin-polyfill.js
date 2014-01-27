var Facebook = (function () {
    var my = {},
        _appId = null,
        initCallback,
        initFacebookUsingAppId = function () {
            if (typeof(FB) === "undefined") {
                console.debug("FB javascript SDK not yet loaded");
                return;
            }

            if (!_appId) {
                console.debug("Facebook app ID not yet defined");
                return;
            }

            FB.init({
                appId      : _appId,
                status     : false, // do not check login status
                cookie     : true, // enable cookies to allow the server to access the session
                xfbml      : false
            });

            initCallback();
        },
        missingPermission = function (requestedPermissions, grantedPermissionsObject) {
            var missingPermission = false;

            requestedPermissions.forEach(function(permission) {
                missingPermission = missingPermission || (grantedPermissionsObject[permission] !== 1);
            });

            return missingPermission;
        },
        postObjectAndAction = function (action, callback) {
            var objectType = action.object.type;

            delete action.object.type;

            console.log("Posting object", action.object);

            FB.api("/me/objects/" + objectType,
                "post",
                {object: action.object},
                function (response) {
                    console.log("response post object", response);

                    if (response.error) {
                        callback(response.error);
                        return;
                    }

                    action.objectId = response.id.toString();
                    delete action.object;

                    postAction(action, callback);
                });
        },
        postAction = function (action, callback) {
            var graphAction = {
                message: action.message,
                place: action.place,
                "fb:explicitly_shared": action.explicitlyShared
            };

            if (action.object) {
                postObjectAndAction(action, callback);
                return;
            }

            graphAction[action.objectType] = action.objectId.toString();

            console.log(graphAction);

            FB.api("/me/" + action.action,
                "post",
                graphAction,
                function (response) {
                    console.log("response post action", response);

                    if (response.error) {
                        callback(response.error);
                        return;
                    }

                    callback(null, response);
                });
        };

    if (typeof(cordova) !== "undefined") {
        console.log("cordova is defined");
        return undefined;
    }

    console.log("Cordova not defined. Using javascript fallback for Facebook plugin.");

    window.fbAsyncInit = function() {
        initFacebookUsingAppId();
    };

    console.log("Will load Facebook SDK");

    // Load the SDK asynchronously
    (function(d){
        var js, id = 'facebook-jssdk', ref = d.getElementsByTagName('script')[0];
        if (d.getElementById(id)) {return;}
        js = d.createElement('script'); js.id = id; js.async = true;
        js.src = "//connect.facebook.net/en_US/all/debug.js";
        ref.parentNode.insertBefore(js, ref);
    }(document));

    my.init = function (appId, callback) {
        _appId = appId;
        initCallback = callback;

        initFacebookUsingAppId();
    };

    my.query = function (path, params, permissions, callback) {
        FB.getLoginStatus(function (response) {
            if (response.status === "connected") {
                FB.api(path, function (response) {
                    callback(null, response);
                });
            } else {
                callback({loginRequired: true});
            }
        });
    };

    my.publishAction = function (action, audience, callback) {
        var permissions = ["publish_actions"];

        FB.getLoginStatus(function (response) {
            if (response.status !== "connected") {
                callback({loginRequired: true});
                return;
            }

            FB.api("/me/permissions", function (response) {
                    console.log("permissions: ", response.data[0]);
                    if (missingPermission(permissions, response.data[0])) {
                        my.login(permissions, function (error, response) {
                            if (error) {
                                callback(error);
                                return;
                            }

                            if (response.status !== "connected") {
                                callback({permissionDenied: true});
                                return;
                            }
                            postAction(action, callback);
                        });
                        return;
                    }

                    postAction(action, callback);
                }
            );
        });
    };

    my.login = function (permissions, callback) {
        var scope = "";
        permissions.forEach(function (permission) {
            if (scope.length > 0) {
                scope += ",";
            }
            scope += permission;
        });

        FB.login(function (response) {
            if (response.error) {
                callback(response.error);
                return;
            }

            callback(null, response);
        }, {scope: scope});
    };

    my.logout = function (callback) {
        FB.logout(function (response) {
            console.debug(response);
            callback(null, response);
        });
    };

    return my;
}());