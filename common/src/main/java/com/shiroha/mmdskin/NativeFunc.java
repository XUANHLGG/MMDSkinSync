package com.shiroha.mmdskin;

/**
 * MMDSync Stub - 仅用于编译 Mixin，不参与运行
 * 必须包含所有 Native 方法，否则 Mixin 在验证类时可能会报错
 */
public class NativeFunc {
    // 基础功能
    public native long LoadModelPMX(String filename, String dir, long layerCount);
    public native long LoadModelPMD(String filename, String dir, long layerCount);
    public native long LoadModelVRM(String filename, String dir, long layerCount);
    public native long LoadTexture(String filename);
    public native long LoadAnimation(long model, String filename);
    
    // 渲染相关
    public native long GetVertexCount(long model);
    public native long GetMaterialCount(long model);
    public native String GetMaterialTex(long model, int index);
    public native long GetSubMeshCount(long model);
    public native int GetSubMeshVertexCount(long model, int index);
    public native int BuildMCVertexBuffer(long model, int subMeshIndex, java.nio.ByteBuffer vertexBuf, java.nio.ByteBuffer poseBuf, java.nio.ByteBuffer normalBuf);
    public native int GetMaterialMorphResultCount(long model);
    public native boolean GetMaterialBothFace(long model, int materialID);
    public native void SetAutoBlinkEnabled(long model, boolean enabled);
    public native void SetEyeTrackingEnabled(long model, boolean enabled);
    public native void SetEyeMaxAngle(long model, float angle);
    
    // 动画相关
    public native void UpdateAnimation(long model, float deltaSec);
    public native void SetAnimation(long model, long animHandle, boolean loop);
    public native void ChangeModelAnim(long model, long anim, long layer);
    public native void TransitionLayerTo(long model, long layer, long anim, float transitionTime);
    public native int ApplyVpdMorph(long model, String filename);
    public native void ResetAllMorphs(long model);
    
    // VR / 视角相关
    public native int CopyRealtimeUVsToBuffer(long model, java.nio.ByteBuffer buffer);
    public native long GetModelMemoryUsage(long model);
    public native void SetVRTrackingData(long model, float[] trackingData);
    public native void SetVREnabled(long model, boolean enabled);
    public native void SetVRArmIKStrength(long model, float strength);
    public native void SetVRIKParams(long model, float armIKStrength);
    public native void SetFirstPersonMode(long model, boolean enabled);
    public native void GetEyeBonePosition(long model, float[] out);
    
    // 获取实例
    public static NativeFunc GetInst() { return null; }
}
