//
//  CDVFacebookPlugin.m
//  Golfer Alley
//
//  Created by Aaron Schinkowitch on 18/12/13.
//
//

#import "CDVFacebookPlugin.h"

@implementation CDVFacebookPlugin

/* This overrides CDVPlugin's method, which receives a notification when handleOpenURL is called on the main app delegate */
- (void) handleOpenURL:(NSNotification*)notification
{
    NSURL* url = [notification object];
    
    if (![url isKindOfClass:[NSURL class]]) {
        return;
    }
    
    [FBSession.activeSession handleOpenURL:url];
}



- (void)init:(CDVInvokedUrlCommand*)command
{
    CDVPluginResult* pluginResult = nil;
    
    NSString* appId = [command.arguments objectAtIndex:0];
    
    if (appId == nil || [appId length] == 0) {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Missing appId"];
    } else {
        [FBSettings setDefaultAppID:appId];
        
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    }
    
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)sendLoginStatus:(CDVInvokedUrlCommand*)command session:(FBSession*)session
{
    CDVPluginResult* pluginResult = nil;
    NSMutableDictionary* dictionary = [[NSMutableDictionary alloc] init];
    
    if (FBSession.activeSession.state == FBSessionStateOpen) {
        [dictionary setObject:@"connected" forKey:@"status"];
    } else {
        [dictionary setObject:@"unknown" forKey:@"status"];
    }
    
    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:dictionary];
    
    
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    
}

- (void)getLoginStatus:(CDVInvokedUrlCommand*)command;
{
    FBSession *session = FBSession.activeSession;
    
    
    if (session.state == FBSessionStateCreatedTokenLoaded) {
        [session openWithCompletionHandler:^(FBSession *openedSession, FBSessionState status, NSError *error) {
            if (status != FBSessionStateOpen) {
                return;
            }

            [self sendLoginStatus:command session:openedSession];
        }];
        
        return;
    }
    
    [self sendLoginStatus:command session:session];
}

- (void) query:(CDVInvokedUrlCommand*)command;
{
    if (!FBSession.activeSession.isOpen) {
        [FBSession openActiveSessionWithAllowLoginUI:NO];
    }
    
    if (!FBSession.activeSession.isOpen) {
        NSLog(@"Login required");
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"login_required"];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        return;
    }

    NSLog(@"Will execute query %@", [command.arguments objectAtIndex:0]);
    
    [FBRequestConnection startWithGraphPath:[command.arguments objectAtIndex:0]
                          completionHandler:^(FBRequestConnection *connection, id result, NSError *error) {
                              CDVPluginResult* pluginResult = nil;
                              
                              if (error) {
                                  NSLog(@"error executing query '%@': %@", [command.arguments objectAtIndex:0], error.description);
                                  pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:error.description];
                                  [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
                                  return;
                              }
                              
                              pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:result];
                              [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
                          }];
}

- (void)login:(CDVInvokedUrlCommand*)command;
{
    BOOL opened = NO;

    if (FBSession.activeSession.isOpen) {
        [self sendLoginStatus:command session:FBSession.activeSession];
        return;
    }
    
    NSLog(@"Opening session %d", FBSession.activeSession.state);
    
    NSMutableArray* permissions = [[NSMutableArray alloc] init];
    [permissions addObject:@"basic_info"];
    [permissions addObjectsFromArray:[command.arguments objectAtIndex:0]];
    
    opened = [FBSession openActiveSessionWithReadPermissions: permissions allowLoginUI:YES completionHandler:
                    ^(FBSession *session, FBSessionState status, NSError *error) {
                        if (!session.isOpen && status != FBSessionStateClosedLoginFailed) {
                            return;
                        }
                        
                        [self sendLoginStatus:command session:session];
                    }];
    
    if (opened) {
        NSLog(@"Session opened without UI: %d", FBSession.activeSession.state);
        [self sendLoginStatus:command session:FBSession.activeSession];
    }
}

- (void)logout:(CDVInvokedUrlCommand*)command;
{
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];

    [FBSession.activeSession closeAndClearTokenInformation];
    
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

@end
