package java.arthas;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Date: 2019/4/9
 *
 * @author xuzhiyi
 */
public class Hooks {

    public static Set<ThreadPoolExecutor> THREAD_POOLS = new HashSet<ThreadPoolExecutor>();

    public static void init() {

    }

    public static void destroy() {
        THREAD_POOLS.clear();
    }
}
