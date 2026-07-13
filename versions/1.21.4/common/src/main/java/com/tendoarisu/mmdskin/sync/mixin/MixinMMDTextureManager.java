package com.tendoarisu.mmdskin.sync.mixin;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.renderer.runtime.texture.MMDTextureManager;
import com.opdent.mmdskin.sync.MMDSyncMod;
import com.tendoarisu.mmdskin.sync.util.CryptoUtils;
import com.tendoarisu.mmdskin.sync.util.MMDSyncNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.File;

@Mixin(value = MMDTextureManager.class, remap = false)
public class MixinMMDTextureManager {

    @Redirect(method = "preloadTexture(Ljava/lang/String;)V", at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;LoadTexture(Ljava/lang/String;)J"), remap = false)
    private static long redirectPreloadLoadTexture(NativeFunc instance, String filename) {
        File file = new File(filename);
        if (CryptoUtils.isEncrypted(file)) {
            long handle = MMDSyncNativeBridge.loadEncryptedTextureFromFile(filename);
            if (handle == 0L && !CryptoUtils.hasSessionMaterial()) {
                MMDSyncMod.LOGGER.warn("加载加密贴图时 Native SessionKey 尚未就绪，本次跳过阻塞等待: {}", filename);
            }
            if (handle == 0L) {
                MMDSyncMod.LOGGER.error("加载加密贴图失败: Native 直接文件解密/加载返回空句柄: {}", filename);
            }
            return MMDSyncNativeBridge.tagBridgeHandle(handle);
        }
        return instance.LoadTexture(filename);
    }

    @Redirect(method = "GetTexture(Ljava/lang/String;)Lcom/shiroha/mmdskin/renderer/runtime/texture/MMDTextureManager$Texture;", at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;LoadTexture(Ljava/lang/String;)J"), remap = false)
    private static long redirectGetTextureLoadTexture(NativeFunc instance, String filename) {
        File file = new File(filename);
        if (CryptoUtils.isEncrypted(file)) {
            long handle = MMDSyncNativeBridge.loadEncryptedTextureFromFile(filename);
            if (handle == 0L && !CryptoUtils.hasSessionMaterial()) {
                MMDSyncMod.LOGGER.warn("同步加载加密贴图时 Native SessionKey 尚未就绪，本次跳过阻塞等待: {}", filename);
            }
            if (handle == 0L) {
                MMDSyncMod.LOGGER.error("同步加载加密贴图失败: Native 直接文件解密/加载返回空句柄: {}", filename);
            }
            return MMDSyncNativeBridge.tagBridgeHandle(handle);
        }
        return instance.LoadTexture(filename);
    }

    @Redirect(method = {"preloadTexture(Ljava/lang/String;)V", "GetTexture(Ljava/lang/String;)Lcom/shiroha/mmdskin/renderer/runtime/texture/MMDTextureManager$Texture;"}, at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetTextureX(J)I"), remap = false)
    private static int redirectGetTextureX(NativeFunc instance, long tex) {
        if (!MMDSyncNativeBridge.isBridgeHandle(tex)) {
            return instance.GetTextureX(tex);
        }
        if (!MMDSyncNativeBridge.isTextureHandleValid(tex)) {
            MMDSyncMod.LOGGER.warn("阻止读取过期加密贴图宽度: tex={}", tex);
            return 0;
        }
        return MMDSyncNativeBridge.getTextureX(tex);
    }

    @Redirect(method = {"preloadTexture(Ljava/lang/String;)V", "GetTexture(Ljava/lang/String;)Lcom/shiroha/mmdskin/renderer/runtime/texture/MMDTextureManager$Texture;"}, at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetTextureY(J)I"), remap = false)
    private static int redirectGetTextureY(NativeFunc instance, long tex) {
        if (!MMDSyncNativeBridge.isBridgeHandle(tex)) {
            return instance.GetTextureY(tex);
        }
        if (!MMDSyncNativeBridge.isTextureHandleValid(tex)) {
            MMDSyncMod.LOGGER.warn("阻止读取过期加密贴图高度: tex={}", tex);
            return 0;
        }
        return MMDSyncNativeBridge.getTextureY(tex);
    }

    @Redirect(method = {"preloadTexture(Ljava/lang/String;)V", "GetTexture(Ljava/lang/String;)Lcom/shiroha/mmdskin/renderer/runtime/texture/MMDTextureManager$Texture;"}, at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;GetTextureData(J)J"), remap = false)
    private static long redirectGetTextureData(NativeFunc instance, long tex) {
        if (!MMDSyncNativeBridge.isBridgeHandle(tex)) {
            return instance.GetTextureData(tex);
        }
        if (!MMDSyncNativeBridge.isTextureHandleValid(tex)) {
            MMDSyncMod.LOGGER.warn("阻止读取过期加密贴图数据: tex={}", tex);
            return 0L;
        }
        return MMDSyncNativeBridge.getTextureData(tex);
    }

    @Redirect(method = {"preloadTexture(Ljava/lang/String;)V", "GetTexture(Ljava/lang/String;)Lcom/shiroha/mmdskin/renderer/runtime/texture/MMDTextureManager$Texture;"}, at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;TextureHasAlpha(J)Z"), remap = false)
    private static boolean redirectTextureHasAlpha(NativeFunc instance, long tex) {
        if (!MMDSyncNativeBridge.isBridgeHandle(tex)) {
            return instance.TextureHasAlpha(tex);
        }
        if (!MMDSyncNativeBridge.isTextureHandleValid(tex)) {
            MMDSyncMod.LOGGER.warn("阻止读取过期加密贴图透明通道标记: tex={}", tex);
            return false;
        }
        return MMDSyncNativeBridge.textureHasAlpha(tex);
    }

    @Redirect(method = {"preloadTexture(Ljava/lang/String;)V", "GetTexture(Ljava/lang/String;)Lcom/shiroha/mmdskin/renderer/runtime/texture/MMDTextureManager$Texture;"}, at = @At(value = "INVOKE", target = "Lcom/shiroha/mmdskin/NativeFunc;DeleteTexture(J)V"), remap = false)
    private static void redirectDeleteTexture(NativeFunc instance, long tex) {
        if (!MMDSyncNativeBridge.isBridgeHandle(tex)) {
            instance.DeleteTexture(tex);
            return;
        }
        if (!MMDSyncNativeBridge.isTextureHandleValid(tex)) {
            MMDSyncMod.LOGGER.warn("阻止删除过期加密贴图句柄: tex={}", tex);
            return;
        }
        MMDSyncNativeBridge.deleteTexture(tex);
    }
}
