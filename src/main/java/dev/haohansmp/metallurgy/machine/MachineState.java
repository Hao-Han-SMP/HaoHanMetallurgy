package dev.haohansmp.metallurgy.machine;

/**
 * Trạng thái hoạt động của một máy.
 */
public enum MachineState {

    /** Không có recipe đang chạy. */
    IDLE,

    /** Đang xử lý recipe. */
    WORKING,

    /** Tạm dừng (hết fuel hoặc nhiệt độ quá thấp). */
    PAUSED,

    /** Lỗi (thiếu nguyên liệu, output đầy, ...). */
    ERROR
}
