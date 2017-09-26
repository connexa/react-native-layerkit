#import <React/RCTBridgeModule.h>
#import <LayerKit/LayerKit.h>
#import "JSONHelper.h"
#import "MessageParts.h"
#import "LayerAuthenticate.h"
#import "LayerQuery.h"
#import "RCTLog.h"
#import "RCTUtils.h"
#import <React/RCTEventDispatcher.h>
#import <React/RCTEventEmitter.h>

@interface RNLayerKit : RCTEventEmitter <RCTBridgeModule>
+ (nonnull instancetype)bridgeWithLayerAppID:(nonnull NSURL *)layerAppID bridge:(RCTBridge *)bridge apiUrl:(NSString *)apiUrl;
- (void)didReceiveTypingIndicator:(NSNotification *)notification;
- (void)updateRemoteNotificationDeviceToken:(nullable NSData *)deviceToken;
- (void)setPresenceStatusAway;
- (void)setPresenceStatusAvailable;
@property (nonatomic) LYRConversation *conversation;
@end
