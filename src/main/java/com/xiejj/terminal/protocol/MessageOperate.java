package com.xiejj.terminal.protocol;

import com.xiejj.terminal.service.SessionHandle;

/**
 * @author xiejiajun
 */
public enum MessageOperate {
    COMMAND,
    ESTABLISH_CONNECT,
    RESIZE_WINDOW,
    HEARTBEAT {
        @Override
        public void postHandle(SessionHandle sessionHandle) {

        }
    };

    public void postHandle(SessionHandle sessionHandle) {
        sessionHandle.refreshLastAccessTime();
    }
}
