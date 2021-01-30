package cn.objectspace.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.io.IOException;

/**
 * @author xiejiajun
 */
@Slf4j
public class IOUtil {

    public static void closeQuietly(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException var2) {
            log.error(var2.getMessage());
        }
    }
}
