#import "HRBridgeModule.h"

#import "KuiklyRenderViewController.h"
#import <OpenKuiklyIOSRender/NSObject+KR.h>
#import <UIKit/UIKit.h>

#define REQ_PARAM_KEY @"reqParam"
#define CMD_KEY @"cmd"
#define FROM_HIPPY_RENDER @"from_hippy_render"
// 扩展桥接接口
/*
 * @brief Native暴露接口到kotlin侧，提供kotlin侧调用native能力
 */

@implementation HRBridgeModule

@synthesize hr_rootView;

- (void)copyToPasteboard:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary];
    NSString *content = params[@"content"];
    UIPasteboard *pasteboard = [UIPasteboard generalPasteboard];
    pasteboard.string = content;
}

- (void)log:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary];
    NSString *content = params[@"content"];
    NSLog(@"KuiklyRender:%@", content);
}

/*
 * 触觉反馈:短震。与 commonMain 的 BridgeModule.vibrateShort(type) 对齐,
 * type 取 heavy / medium / light(默认 heavy)。此前 iOS 侧缺失 → 点击卡片无反馈。
 */
- (void)vibrateShort:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary];
    NSString *type = params[@"type"];
    UIImpactFeedbackStyle style = UIImpactFeedbackStyleHeavy;
    if ([type isEqualToString:@"light"]) {
        style = UIImpactFeedbackStyleLight;
    } else if ([type isEqualToString:@"medium"]) {
        style = UIImpactFeedbackStyleMedium;
    }
    // 桥接回调未必在主线程,UIKit 操作统一切回主线程
    dispatch_async(dispatch_get_main_queue(), ^{
        UIImpactFeedbackGenerator *generator = [[UIImpactFeedbackGenerator alloc] initWithStyle:style];
        [generator prepare];
        [generator impactOccurred];
    });
}

/*
 * 轻提示:黑底白字浮层,2 秒后淡出。与 Android 侧的 Toast 语义一致;
 * 此前 iOS 侧缺失 → RouterPage 的调试提示在 iOS 上无任何表现。
 */
- (void)toast:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary];
    NSString *content = params[@"content"];
    if (![content isKindOfClass:[NSString class]] || content.length == 0) {
        return;
    }
    dispatch_async(dispatch_get_main_queue(), ^{
        UIView *host = [self toastHostView];
        if (host == nil) {
            return;
        }
        UILabel *label = [[UILabel alloc] init];
        label.text = content;
        label.textColor = UIColor.whiteColor;
        label.font = [UIFont systemFontOfSize:14];
        label.numberOfLines = 0;
        label.textAlignment = NSTextAlignmentCenter;
        label.backgroundColor = [UIColor colorWithWhite:0 alpha:0.8];
        label.layer.cornerRadius = 8;
        label.layer.masksToBounds = YES;
        [label sizeToFit];

        CGRect frame = label.frame;
        frame.size.width += 24;
        frame.size.height += 16;
        frame.origin.x = (host.bounds.size.width - frame.size.width) / 2;
        frame.origin.y = host.bounds.size.height * 0.75;
        label.frame = frame;
        label.alpha = 0;
        [host addSubview:label];

        [UIView animateWithDuration:0.2 animations:^{
            label.alpha = 1;
        } completion:^(BOOL finished) {
            [UIView animateWithDuration:0.2 delay:1.6 options:0 animations:^{
                label.alpha = 0;
            } completion:^(BOOL finished) {
                [label removeFromSuperview];
            }];
        }];
    });
}

/** 取当前前台 window 作为 toast 宿主;iOS 13+ 用 UIWindowScene,取不到再退回旧 API。 */
- (UIView *)toastHostView {
    UIWindow *host = nil;
    for (UIScene *scene in UIApplication.sharedApplication.connectedScenes) {
        if (![scene isKindOfClass:[UIWindowScene class]] ||
            scene.activationState != UISceneActivationStateForegroundActive) {
            continue;
        }
        for (UIWindow *window in ((UIWindowScene *)scene).windows) {
            if (window.isKeyWindow) {
                host = window;
                break;
            }
        }
    }
    if (host == nil) {
        host = UIApplication.sharedApplication.windows.firstObject;
    }
    return host;
}

@end
