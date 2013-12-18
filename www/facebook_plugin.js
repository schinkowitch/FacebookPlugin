
var FB = (function () {
    var that = this,
        initialized = false;

    return {
        init: function (params) {
            console.log("In init");

            cordova.exec(function () {
                    console.log("FB initialized");
                    that.initialized = true;
                },
                function (error) {
                    console.error(error);
                },
                "Facebook",
                "init",
                [params.appId, params.status === true]
            );
        },
        getLoginStatus: function (callback, force) {
            cordova.exec(function (response) {
                    console.log("FB getLoginStatus success");
                    callback(response)
                },
                function (error) {
                    console.log("getLoginStatus error");
                    console.error(error);
                    callback({status: "error", error: error});
                },
                "Facebook",
                "getLoginStatus",
                []
            );
        },
        login: function (callback, options) {
            cordova.exec(function (response) {
                    console.log("FB login success");
                    callback(response)
                },
                function (error) {
                    console.log("login error");
                    console.error(error);
                    callback({status: "error", error: error});
                },
                "Facebook",
                "login",
                []
            );
        },
        api: function (path, method, params, callback) {
            console.log("Making api call: " + path);

            if (typeof method === "function") {
                callback = method;
                method = "GET";
            }

            cordova.exec(function (response) {
                    console.log("api success");
                    callback(response);
                },
                function (error) {
                    console.log("api error");
                    console.error(error);
                    callback({status: "error", error: error});
                },
                "Facebook",
                "api",
                [path, method, params]);
        },
        logout: function (callback) {
            cordova.exec(function () {
                    console.log("FB logout success");
                    callback({status: "success"});
                },
                function (error) {
                    console.log("FB logout error");
                    console.error(error);
                    callback({status: "error", error: error});
                },
                "Facebook",
                "logout",
                []
            );
        }
    }
}());

module.exports = FB;