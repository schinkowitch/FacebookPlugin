
var Facebook = (function () {
    return {
        init: function (appId, callback) {
            console.log("In init");

            cordova.exec(function () {
                    console.log("FB initialized");
                    callback();
                },
                function (error) {
                    console.error(error);
                    callback(error);
                },
                "Facebook",
                "init",
                [appId]
            );
        },
        query: function (path, params, permissions, callback) {
            console.log("Making query: " + path);

            cordova.exec(function (response) {
                    console.log("query success");
                    callback(null, response);
                },
                function (error) {
                    console.log("query error");
                    console.error(error);
                    if (error === "login_required") {
                        callback({loginRequired: true});
                    } else {
                        callback(error);
                    }
                },
                "Facebook",
                "query",
                [path, params, permissions]);
        },
        login: function (permissions, callback) {
            console.log("In login");

            cordova.exec(function (response) {
                    console.log("login success");
                    callback(null, response);
                },
                function (error) {
                    console.log("login error");
                    console.error(error);
                    callback({status: "error", error: error});
                },
                "Facebook",
                "login",
                [permissions]);
        },
        logout: function (callback) {
            cordova.exec(function () {
                    console.log("FB logout success");
                    callback(null, {status: "success"});
                },
                function (error) {
                    console.log("FB logout error");
                    console.error(error);
                    callback(error);
                },
                "Facebook",
                "logout",
                []
            );
        }
    }
}());

module.exports = Facebook;