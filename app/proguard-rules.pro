# Giữ số dòng để stack trace của bản release đọc được (dùng với build/outputs/mapping/release/mapping.txt)
-keepattributes SourceFile,LineNumberTable,Signature,*Annotation*
-renamesourcefileattribute SourceFile

# Bản release: KHÔNG có log nào. Xoá toàn bộ lời gọi DLog và android.util.Log.
-assumenosideeffects class app.dpadmouse.DLog {
    public *** v(...);
    public *** d(...);
    public *** i(...);
    public *** w(...);
    public *** e(...);
}
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}
