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
    
    NSLog(@"Initialized with app ID %@", appId);
    
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)getPermissions:(CDVInvokedUrlCommand*)command
{
    CDVPluginResult* pluginResult = nil;
    
    if (!FBSession.activeSession.isOpen) {
        [FBSession openActiveSessionWithAllowLoginUI:NO];
    }
    
    if (!FBSession.activeSession.isOpen) {
        NSLog(@"Login required");
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"login_required"];
    } else {
        NSMutableDictionary* permissions = [[NSMutableDictionary alloc] init];
        
        for (id sessionPermission in FBSession.activeSession.permissions) {
            [permissions setObject:[NSNumber numberWithInt:1] forKey:sessionPermission];
        }
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:permissions];
    }
    
    
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)sendLoginStatus:(CDVInvokedUrlCommand*)command session:(FBSession*)session
{
    CDVPluginResult* pluginResult = nil;
    NSMutableDictionary* dictionary = [[NSMutableDictionary alloc] init];
    
    if (FBSession.activeSession.state == FBSessionStateOpen) {
        [dictionary setObject:@"connected" forKey:@"status"];
    } else if (FBSession.activeSession.state == FBSessionStateClosedLoginFailed) {
        [dictionary setObject:@"not_authorized" forKey:@"status"];
    } else {
        [dictionary setObject:@"unknown" forKey:@"status"];
    }
    
    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:dictionary];
    
    
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    
}

- (void)sendError:(CDVInvokedUrlCommand*)command error:(NSError*) error info:(NSString*) info
{
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:error.description];
    
    NSLog(@"error %@: %@", info, error.description);
    
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];

}

- (void)getLoginStatus:(CDVInvokedUrlCommand*)command
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

- (BOOL) missingPermission:(NSArray*)permissions
{
    NSArray* sessionPermmisions = FBSession.activeSession.permissions;
    
    for (NSString* permission in permissions) {
        if (![sessionPermmisions containsObject:permission]) {
            return YES;
        }
    }
    
    return NO;
}

- (void)requestReadPermissions:(CDVInvokedUrlCommand*)command
{
    NSArray* permissions = [command.arguments objectAtIndex:0];
    
    [FBSession.activeSession requestNewReadPermissions:permissions completionHandler:^(FBSession *session, NSError *error) {
        NSString* status = nil;
        
        if (error) {
            [self sendError:command error:error info:@"query"];
            return;
        }
        
        if ([self missingPermission:permissions]) {
            status = @"not_authorized";
        } else {
            status = @"authorized";
        }
        
        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus: CDVCommandStatus_OK messageAsString:status]
                                    callbackId:command.callbackId];
    }];
}

- (void)requestPublishPermissions:(CDVInvokedUrlCommand*)command
{
    NSArray* permissions = [command.arguments objectAtIndex:0];
    NSString* audienceArg = [command.arguments objectAtIndex:1];
    int audience = FBSessionDefaultAudienceNone;
    
    if ([@"friends" isEqualToString:audienceArg]) {
        audience = FBSessionDefaultAudienceFriends;
    } else if ([@"only_me" isEqualToString:audienceArg]) {
        audience = FBSessionDefaultAudienceOnlyMe;
    } else if ([@"everyone" isEqualToString:audienceArg]) {
        audience = FBSessionDefaultAudienceEveryone;
    }
    
    [FBSession.activeSession requestNewPublishPermissions:permissions defaultAudience:audience
                                        completionHandler:^(FBSession *session, NSError *error) {
        NSString* status = nil;
        
        if (error) {
            [self sendError:command error:error info:@"query"];
            return;
        }
        
        if ([self missingPermission:permissions]) {
            status = @"not_authorized";
        } else {
            status = @"authorized";
        }
        
        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus: CDVCommandStatus_OK messageAsString:status]
                                    callbackId:command.callbackId];
    }];
}

- (void) query:(CDVInvokedUrlCommand*)command;
{
    NSLog(@"Will execute query %@", [command.arguments objectAtIndex:0]);
    
    [FBRequestConnection startWithGraphPath:[command.arguments objectAtIndex:0]
                          completionHandler:^(FBRequestConnection *connection, id result, NSError *error) {
                              CDVPluginResult* pluginResult = nil;
                              
                              if (error) {
                                  [self sendError:command error:error info:@"query"];
                                  return;
                              }
                              
                              pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:result];
                              [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
                          }];
}

- (void)publishAction:(CDVInvokedUrlCommand*)command
{
    NSDictionary* actionParams = [command.arguments objectAtIndex:0];
    NSString* path = [NSString stringWithFormat:@"/me/%@", [actionParams objectForKey:@"action"]];
    NSMutableDictionary<FBOpenGraphAction>* action = [FBGraphObject openGraphActionForPost];
    
    if (actionParams[@"object"] != nil) {
        [self publishObjectAndAction:command actionParams:actionParams];
        return;
    }

    action[@"message"] = actionParams[@"message"];
    action[@"place"] = actionParams[@"place"];
    action[@"fb:explicitly_shared"] = actionParams[@"explicitlyShared"];
    action[[actionParams objectForKey:@"objectType"]] = actionParams[@"objectId"];

    [FBRequestConnection startForPostWithGraphPath:path graphObject:action
                                 completionHandler:^(FBRequestConnection *connection, id result, NSError *error) {
                                     CDVPluginResult* pluginResult = nil;
                                     
                                     if (error) {
                                         [self sendError:command error:error info:@"publishAction"];
                                         return;
                                     }
                                     
                                     pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:result];
                                     [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
                                 }];
}

- (void)publishObjectAndAction:(CDVInvokedUrlCommand*)command actionParams:(NSDictionary*) actionParams
{
    NSLog(@"Will publish action and object");
    FBRequestConnection* requestConnection = [[FBRequestConnection alloc] init];
    
    NSDictionary* object = actionParams[@"object"];
    NSString* path = [NSString stringWithFormat:@"/me/%@", [actionParams objectForKey:@"action"]];
    FBRequest* objectRequest = [FBRequest requestForPostOpenGraphObjectWithType: object[@"type"]
                                                                          title: object[@"title"]
                                                                          image: object[@"image"]
                                                                            url: object[@"url"]
                                                                    description: object[@"description"]
                                                               objectProperties: object[@"data"]];
    
    
    NSMutableDictionary<FBOpenGraphAction>* action = [FBGraphObject openGraphActionForPost];
    action[@"message"] = actionParams[@"message"];
    action[@"place"] = actionParams[@"place"];
    action[@"fb:explicitly_shared"] = actionParams[@"explicitlyShared"];
    action[[actionParams objectForKey:@"objectType"]] = @"{result=objectCreate:$.id}";
    
    FBRequest* actionRequest = [FBRequest requestForPostWithGraphPath:path graphObject:action];
    NSMutableDictionary* batchParameters = [[NSMutableDictionary alloc] init];
    batchParameters[@"depends_upon"] = @"objectCreate";
    
    [requestConnection addRequest:objectRequest
                completionHandler:^(FBRequestConnection *connection, id result, NSError *error) {
                                      if (error) {
                                          [self sendError:command error:error info:@"publishObjectAndAction - object"];
                                      }
                                  }
                   batchEntryName:@"objectCreate"
    ];
    
    [requestConnection addRequest:actionRequest
                completionHandler:^(FBRequestConnection *connection, id result, NSError *error) {
                    CDVPluginResult* pluginResult = nil;
                    
                    if (error) {
                        [self sendError:command error:error info:@"publishObjectAndAction - action"];
                        return;
                    }
                    
                    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:result];
                    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
                }
                   batchParameters:batchParameters
     ];
    
    [requestConnection start];
}

- (void)login:(CDVInvokedUrlCommand*)command;
{
    BOOL opened = NO;

    if (FBSession.activeSession.isOpen) {
        NSLog(@"Session is open %d", FBSession.activeSession.state);

        [self sendLoginStatus:command session:FBSession.activeSession];
        return;
    }
    
    NSLog(@"Opening session %d", FBSession.activeSession.state);
    
    NSMutableArray* permissions = [[NSMutableArray alloc] init];
    [permissions addObject:@"basic_info"];
    [permissions addObjectsFromArray:[command.arguments objectAtIndex:0]];
    
    opened = [FBSession openActiveSessionWithReadPermissions: permissions allowLoginUI:YES completionHandler:
                    ^(FBSession *session, FBSessionState status, NSError *error) {
                        if (error) {
                            [self sendError:command error:error info:@"login"];
                            return;
                        }
                        
                        NSLog(@"Session now has state %d", FBSession.activeSession.state);
                        
                        
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
