package com.taobao.arthas.core.command.monitor200;

import com.taobao.arthas.core.advisor.ThreadPoolEnhancer;
import com.taobao.arthas.core.command.Constants;
import com.taobao.arthas.core.shell.command.AnnotatedCommand;
import com.taobao.arthas.core.shell.command.CommandProcess;
import com.taobao.arthas.core.util.LogUtil;
import com.taobao.arthas.core.util.SearchUtils;
import com.taobao.middleware.cli.annotations.Description;
import com.taobao.middleware.cli.annotations.Name;
import com.taobao.middleware.cli.annotations.Option;
import com.taobao.middleware.cli.annotations.Summary;
import com.taobao.text.Decoration;
import com.taobao.text.ui.Element;
import com.taobao.text.ui.TableElement;
import com.taobao.text.util.RenderUtil;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.taobao.text.ui.Element.label;

/**
 * Date: 2019/4/9
 *
 * @author xuzhiyi
 */
@Name("threadpoll")
@Summary("Display thread info, thread stack")
@Description(Constants.EXAMPLE)
public class ThreadPoolCommand extends AnnotatedCommand {

    private Integer expand = 1;

    @Option(shortName = "x", longName = "expand")
    @Description("Expand level of object (1 by default)")
    public void setExpand(Integer expand) {
        this.expand = expand;
    }

    private static volatile boolean enhanced = false;

    @Override
    public void process(CommandProcess process) {
        try {
            if (!enhanced) {
                enhance(process);
            }
            Class<?> hooksClass = this.getClass().getClassLoader().loadClass("java.arthas.Hooks");
            Field field = hooksClass.getField("THREAD_POOLS");
            Set<ThreadPoolExecutor> pools = (Set<ThreadPoolExecutor>) field.get(null);
            Iterator<ThreadPoolExecutor> it = pools.iterator();
            //todo 线程安全
            while (it.hasNext()) {
                ThreadPoolExecutor poolExecutor = it.next();
                if (poolExecutor.isShutdown() || poolExecutor.isTerminated()) {
                    it.remove();
                }
            }
            process.write(RenderUtil.render(drawShowTable(pools), process.width()));
        } catch (Throwable e) {
            LogUtil.getArthasLogger().warn("ThreadPoolCommand fail", e);
        } finally {
            process.end();
        }
    }

    private void enhance(CommandProcess process) {
        Instrumentation instrumentation = process.session().getInstrumentation();
        ThreadPoolEnhancer threadPoolEnhancer = new ThreadPoolEnhancer();
        try {
            Set<Class<?>> classes = SearchUtils.searchClassOnly(instrumentation, "java.util.concurrent.ThreadPoolExecutor", false);
            instrumentation.addTransformer(threadPoolEnhancer, true);
            for (Class clazz : classes) {
                instrumentation.retransformClasses(clazz);
            }
            enhanced = true;
        } catch (Throwable e) {
            LogUtil.getArthasLogger().warn("ThreadPoolCommand enhance fail", e);
        } finally {
            instrumentation.removeTransformer(threadPoolEnhancer);
        }
    }

    private Element drawShowTable(Collection<ThreadPoolExecutor> pools) {
        TableElement table = new TableElement(1, 1, 2, 1, 2, 2, 1, 2, 8, 3)
            .leftCellPadding(1).rightCellPadding(1);
        table.row(true, label("core").style(Decoration.bold.bold()),
                  label("cur").style(Decoration.bold.bold()),
                  label("max").style(Decoration.bold.bold()),
                  label("active").style(Decoration.bold.bold()),
                  label("completed").style(Decoration.bold.bold()),
                  label("queued").style(Decoration.bold.bold()),
                  label("keepAlive").style(Decoration.bold.bold()),
                  label("threadFactory").style(Decoration.bold.bold()),
                  label("rejectedHandler").style(Decoration.bold.bold()));

        for (final ThreadPoolExecutor pool : pools) {
            table.row("" + pool.getCorePoolSize(),
                      "" + pool.getPoolSize(),
                      "" + pool.getMaximumPoolSize(),
                      "" + pool.getActiveCount(),
                      "" + pool.getCompletedTaskCount(),
                      "" + pool.getQueue().size(),
                      "" + pool.getKeepAliveTime(TimeUnit.SECONDS),
                      "" + pool.getThreadFactory().getClass().getName(),
                      "" + pool.getRejectedExecutionHandler().getClass().getSimpleName());
        }
        return table;
    }
}
