package com.lody.virtual.helper.utils;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.os.Build;
import android.os.Process;

import com.lody.virtual.GmsSupport;
import com.lody.virtual.client.core.VirtualCore;
import com.lody.virtual.client.env.SpecialComponentList;
import com.lody.virtual.client.stub.ShadowPendingActivity;
import com.lody.virtual.client.stub.ShadowPendingReceiver;
import com.lody.virtual.client.stub.ShadowPendingService;
import com.lody.virtual.helper.compat.ActivityManagerCompat;
import com.lody.virtual.helper.compat.IntentCompat;
import com.lody.virtual.helper.compat.ObjectsCompat;
import com.lody.virtual.os.VUserHandle;
import com.lody.virtual.remote.BroadcastIntentData;

import java.util.Set;

import static android.content.pm.ActivityInfo.LAUNCH_SINGLE_INSTANCE;
import static com.lody.virtual.client.env.SpecialComponentList.protectAction;

/**
 * @author Lody
 */
public class ComponentUtils {

    public static String getTaskAffinity(ActivityInfo info) {
        if (info.launchMode == LAUNCH_SINGLE_INSTANCE) {
            return "-SingleInstance-" + info.packageName + "/" + info.name;
        } else if (info.taskAffinity == null && info.applicationInfo.taskAffinity == null) {
            return info.packageName;
        } else if (info.taskAffinity != null) {
            return info.taskAffinity;
        }
        return info.applicationInfo.taskAffinity;
    }

    public static boolean intentFilterEquals(Intent a, Intent b) {
        if (a != null && b != null) {
            if (!ObjectsCompat.equals(a.getAction(), b.getAction())) {
                return false;
            }
            if (!ObjectsCompat.equals(a.getData(), b.getData())) {
                return false;
            }
            if (!ObjectsCompat.equals(a.getType(), b.getType())) {
                return false;
            }
            Object pkgA = a.getPackage();
            if (pkgA == null && a.getComponent() != null) {
                pkgA = a.getComponent().getPackageName();
            }
            String pkgB = b.getPackage();
            if (pkgB == null && b.getComponent() != null) {
                pkgB = b.getComponent().getPackageName();
            }
            if (!ObjectsCompat.equals(pkgA, pkgB)) {
                return false;
            }
            if (!ObjectsCompat.equals(a.getComponent(), b.getComponent())) {
                return false;
            }
            if (!ObjectsCompat.equals(a.getCategories(), b.getCategories())) {
                return false;
            }
        }
        return true;
    }

    public static String getProcessName(ComponentInfo componentInfo) {
        String processName = componentInfo.processName;
        if (processName == null) {
            processName = componentInfo.packageName;
            componentInfo.processName = processName;
        }
        return processName;
    }

    public static boolean isSameComponent(ComponentInfo first, ComponentInfo second) {

        if (first != null && second != null) {
            String pkg1 = first.packageName + "";
            String pkg2 = second.packageName + "";
            String name1 = first.name + "";
            String name2 = second.name + "";
            return pkg1.equals(pkg2) && name1.equals(name2);
        }
        return false;
    }

    public static ComponentName toComponentName(ComponentInfo componentInfo) {
        return new ComponentName(componentInfo.packageName, componentInfo.name);
    }

    public static boolean isSystemApp(ApplicationInfo applicationInfo) {
        if (applicationInfo == null) {
            return false;
        }
        if (GmsSupport.isGoogleAppOrService(applicationInfo.packageName)) {
            return false;
        } else if (SpecialComponentList.isSpecSystemPackage(applicationInfo.packageName)) {
            return true;
        } else if (applicationInfo.uid >= Process.FIRST_APPLICATION_UID && (applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
            return false;
        } else if (SpecialComponentList.isSpecSystemPackage(applicationInfo.packageName)) {
            return true;
        } else if (applicationInfo.uid >= Process.FIRST_APPLICATION_UID) {
            return (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        }
        return true;
    }

    public static boolean isStubComponent(Intent intent) {
        return intent != null
                && intent.getComponent() != null
                && VirtualCore.get().getHostPkg().equals(intent.getComponent().getPackageName());
    }

    public static String getComponentAction(ActivityInfo info) {
        return getComponentAction(info.packageName, info.name);
    }

    public static String getComponentAction(ComponentName component) {
        return getComponentAction(component.getPackageName(), component.getClassName());
    }

    public static String getComponentAction(String packageName, String name) {
        return String.format("_VA_%s_%s", packageName, name);
    }

    public static Intent redirectBroadcastIntent(Intent intent, int userId) {
        Intent newIntent = new Intent();
        newIntent.setDataAndType(intent.getData(), intent.getType());
        Set<String> categories = intent.getCategories();
        if (categories != null) {
            for (String category : categories) {
                newIntent.addCategory(category);
            }
        }
        ComponentName component = intent.getComponent();
        String targetPackage = intent.getPackage();
        // for static Receiver
        if (component != null) {
            String componentAction = getComponentAction(component);
            newIntent.setAction(componentAction);
            if (targetPackage == null) {
                targetPackage = component.getPackageName();
            }
        } else {
            newIntent.setAction(protectAction(intent.getAction()));
        }
        BroadcastIntentData data = new BroadcastIntentData(userId, intent, targetPackage);
        newIntent.putExtra("_VA_|_data_", data);
        return newIntent;
    }

    public static Intent redirectIntentSender(int type, String creator, Intent intent) {
        if (type == ActivityManagerCompat.INTENT_SENDER_ACTIVITY_RESULT) {
            return null;
        }
        Intent newIntent = new Intent();
        newIntent.setSourceBounds(intent.getSourceBounds());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            newIntent.setClipData(intent.getClipData());
        }
        newIntent.addFlags(intent.getFlags() & IntentCompat.IMMUTABLE_FLAGS);
        String originType = newIntent.getType();
        ComponentName component = newIntent.getComponent();
        String newType = originType != null ? originType + ":" + creator : creator;
        if (component != null) {
            newType = newType + ":" + component.flattenToString();
        }
        newIntent.setDataAndType(newIntent.getData(), newType);

        String packageName32bit = VirtualCore.getConfig().getHostPackageName();
        switch (type) {
            case ActivityManagerCompat.INTENT_SENDER_ACTIVITY: {
                newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                newIntent.setClassName(packageName32bit, ShadowPendingActivity.class.getName());
                break;
            }
            case ActivityManagerCompat.INTENT_SENDER_SERVICE: {
                newIntent.setClassName(packageName32bit, ShadowPendingService.class.getName());
                break;
            }
            case ActivityManagerCompat.INTENT_SENDER_BROADCAST: {
                newIntent.setClassName(packageName32bit, ShadowPendingReceiver.class.getName());
                break;
            }
            default:
                return null;
        }
        Intent selector = new Intent();
        selector.putExtra("_VA_|_intent_", intent);
        selector.putExtra("_VA_|_userId_", VUserHandle.myUserId());
        newIntent.setSelector(selector);
        return newIntent;
    }

}
