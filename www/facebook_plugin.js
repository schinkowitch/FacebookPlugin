cordova.define("com.schinkowitch.cordova.facebook.Facebook", function(require, exports, module) {
var Facebook = (function () {
    var getMissingPermissions = function (requestedPermissions, grantedPermissionsObject) {
        var missingPermissions = [];

        requestedPermissions.forEach(function(permission) {
            if (grantedPermissionsObject[permission] !== 1) {
                missingPermissions.push(permission);
            } else {
                console.log("Has permission " + permission);
            }
        });

        return missingPermissions;
    },
    handleError = function (error, info, callback) {
        console.error("Error in " + info);
        console.error(error);

        if (callback) {
            callback(error);
        }
    },
    executeQuery = function (path, params, callback) {
        cordova.exec(function (response) {
                console.log("query success");
                callback(null, response);
            },
            function (error) {
                handleError(error, "query", callback);
            },
            "Facebook",
            "query",
            [path, params]);
    };

    return {
        init: function (appId, callback) {
            cordova.exec(function () {
                    callback();
                },
                function (error) {
                    handleError(error, "init", callback);
                },
                "Facebook", "init", [appId]);
        },
        query: function (path, params, permissions, callback) {
            cordova.exec(function (response) {
                    var missingPermissions;

                    if (response === "login_required") {
                        callback({loginRequired: true});
                        return;
                    }

                    missingPermissions = getMissingPermissions(permissions, response);

                    if (missingPermissions.length > 0) {
                        cordova.exec(function (response) {
                                if (response === "not_authorized") {
                                    callback({notAuthorized: true});
                                    return;
                                }

                                executeQuery(path, params, callback);
                            },
                            function (error) {
                                handleError(error, "requestReadPermissions", callback);
                            },
                            "Facebook", "requestReadPermissions", [permissions]);
                        return;
                    }

                    executeQuery(path, params, callback);
                },
                function (error) {
                    handleError(error, "getPermissions", callback);
                },
                "Facebook", "getPermissions", [permissions]);
        },
        login: function (permissions, callback) {
            cordova.exec(function (response) {
                    callback(null, response);
                },
                function (error) {
                    handleError(error, "logout", callback);
                },
                "Facebook", "login", [permissions]);
        },
        logout: function (callback) {
            cordova.exec(function () {
                    console.log("FB logout success");
                    callback(null, {status: "success"});
                },
                function (error) {
                    handleError(error, "logout", callback);
                },
                "Facebook", "logout", []);
        }
    }
}());

module.exports = Facebook;});
