package app.dpadmouse.shell;

// Chạy trong process quyền shell do Shizuku tạo.
interface IHidService {
    // Mã 16777114 do Shizuku quy định
    void destroy() = 16777114;

    void startHid() = 1;            // chạy `hid -`
    void write(String data) = 2;    // ghi vào stdin của hid
    String exec(String command) = 3;
    String drainLog() = 4;          // stdout/stderr của hid từ lần gọi trước
    void stopHid() = 5;
    boolean isHidAlive() = 6;
}
