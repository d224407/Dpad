# Giữ số dòng để stack trace của bản release đọc được (dùng với build/outputs/mapping/release/mapping.txt)
-keepattributes SourceFile,LineNumberTable,Signature,*Annotation*
-renamesourcefileattribute SourceFile

# Shizuku tạo UserService bằng reflection theo tên lớp -> không được đổi tên / xoá
-keep class app.dpadmouse.shell.HidUserService { <init>(...); }
-keep class app.dpadmouse.shell.IHidService { *; }
-keep class app.dpadmouse.shell.IHidService$Stub { *; }
-dontwarn rikka.**

# Bản release: bỏ Log.v / Log.d hoàn toàn (DLog.i/w/e vẫn giữ để chẩn đoán)
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
