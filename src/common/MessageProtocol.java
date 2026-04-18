package common;

public class MessageProtocol {
    // Message type codes (4-byte int sent first on every message)
    public static final int CONNECT       = 0x00;
    public static final int TASK_ASSIGN   = 0x01;
    public static final int RESULT_RETURN = 0x02;
    public static final int SHUTDOWN      = 0x03;
    public static final int REQUEST_TASK  = 0x04; // for dynamic queue mode

    // Default networking
    public static final int    DEFAULT_PORT    = 5001;
    public static final String DEFAULT_HOST    = "localhost";
    public static final int    SOCKET_TIMEOUT_MS = 120_000; // 2 min

    private MessageProtocol() {} // utility class, no instances
}