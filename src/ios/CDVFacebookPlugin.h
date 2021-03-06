//
//  CDVFacebookPlugin.h
//  Golfer Alley
//
//  Created by Aaron Schinkowitch on 18/12/13.
//
//

#import <Cordova/CDVPlugin.h>
#import <FacebookSDK/FacebookSDK.h>

@interface CDVFacebookPlugin : CDVPlugin

- (void)init:(CDVInvokedUrlCommand*)command;

- (void)getPermissions:(CDVInvokedUrlCommand*)command;

- (void)requestReadPermissions:(CDVInvokedUrlCommand*)command;

- (void)requestPublishPermissions:(CDVInvokedUrlCommand*)command;

- (void)query:(CDVInvokedUrlCommand*)command;

- (void)publishAction:(CDVInvokedUrlCommand*)command;

- (void)login:(CDVInvokedUrlCommand*)command;

- (void)getAccessToken:(CDVInvokedUrlCommand*)command;

- (void)logout:(CDVInvokedUrlCommand*)command;
@end
