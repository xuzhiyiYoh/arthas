package com.taobao.arthas.core.command.klass100;

import com.taobao.arthas.core.command.express.ExpressException;
import com.taobao.arthas.core.command.express.ExpressFactory;
import com.taobao.arthas.core.shell.command.AnnotatedCommand;
import com.taobao.arthas.core.shell.command.CommandProcess;
import com.taobao.arthas.core.util.SearchUtils;
import com.taobao.arthas.core.util.StringUtils;
import com.taobao.arthas.core.util.TypeRenderUtils;
import com.taobao.arthas.core.util.affect.RowAffect;
import com.taobao.arthas.core.util.matcher.EqualsMatcher;
import com.taobao.arthas.core.util.matcher.Matcher;
import com.taobao.arthas.core.view.ObjectView;
import com.taobao.middleware.cli.annotations.Argument;
import com.taobao.middleware.cli.annotations.Description;
import com.taobao.middleware.cli.annotations.Name;
import com.taobao.middleware.cli.annotations.Option;
import com.taobao.middleware.cli.annotations.Summary;
import com.taobao.text.Color;
import com.taobao.text.Decoration;
import com.taobao.text.ui.Element;
import com.taobao.text.ui.LabelElement;
import com.taobao.text.ui.TableElement;
import com.taobao.text.util.RenderUtil;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Set;

import static com.taobao.text.ui.Element.label;

/**
 * Date: 2019/4/11
 *
 * @author xuzhiyi
 */
@Name("setstatic")
@Summary("Set the static field of a class")
public class SetStaticCommand extends AnnotatedCommand {

    private String classPattern;
    private String fieldPattern;
    private String value;
    private String hashCode;

    @Argument(argName = "class-pattern", index = 0)
    @Description("Class name pattern, use either '.' or '/' as separator")
    public void setClassPattern(String classPattern) {
        this.classPattern = classPattern;
    }

    @Argument(argName = "field-pattern", index = 1)
    @Description("Field name pattern")
    public void setFieldPattern(String fieldPattern) {
        this.fieldPattern = fieldPattern;
    }

    @Argument(argName = "value", index = 2)
    @Description("Field value")
    public void setValue(String value) {
        this.value = value;
    }

    @Option(shortName = "c", longName = "classloader")
    @Description("The hash code of the special class's classLoader")
    public void setHashCode(String hashCode) {
        this.hashCode = hashCode;
    }

    @Override
    public void process(CommandProcess process) {
        RowAffect affect = new RowAffect();
        Instrumentation inst = process.session().getInstrumentation();
        Set<Class<?>> matchedClasses = SearchUtils.searchClassOnly(inst, getClassMatcher(), hashCode);

        try {
            if (matchedClasses == null || matchedClasses.isEmpty()) {
                process.write("No class found for: " + classPattern + "\n");
            } else if (matchedClasses.size() > 1) {
                processMatches(process, matchedClasses);
            } else {
                processExactMatch(process, affect, inst, matchedClasses);
            }
        } finally {
            process.write(affect + "\n");
            process.end();
        }
    }

    private void processExactMatch(CommandProcess process, RowAffect affect, Instrumentation inst,
                                   Set<Class<?>> matchedClasses) {
        Matcher<String> fieldNameMatcher = getClassMatcher();
        Class<?> clazz = matchedClasses.iterator().next();
        boolean found = false;

        for (Field field : clazz.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !fieldNameMatcher.matching(field.getName())) {
                continue;
            }
            if (!field.isAccessible()) {
                field.setAccessible(true);
            }
            try {
                Object value = field.get(null);

                process.write("field: " + field.getName() + "\n" + result + "\n");

                affect.rCnt(1);
            } catch (IllegalAccessException e) {
                logger.warn("getstatic: failed to get static value, class: " + clazz + ", field: " + field.getName(),
                            e);
                process.write("Failed to get static, exception message: " + e.getMessage()
                              + ", please check $HOME/logs/arthas/arthas.log for more details. \n");
            } catch (ExpressException e) {
                logger.warn("getstatic: failed to get express value, class: " + clazz + ", field: " + field.getName()
                            + ", express: " + express, e);
                process.write("Failed to get static, exception message: " + e.getMessage()
                              + ", please check $HOME/logs/arthas/arthas.log for more details. \n");
            } finally {
                found = true;
            }
        }

        if (!found) {
            process.write("getstatic: no matched static field was found\n");
        }
    }

    private void processMatches(CommandProcess process, Set<Class<?>> matchedClasses) {
        Element usage = new LabelElement("setstatic -c <hashcode> " + classPattern + " " + fieldPattern).style(
            Decoration.bold.fg(Color.blue));
        process.write("\n Found more than one class for: " + classPattern + ", Please use " + RenderUtil.render(usage,
                                                                                                                process.width()));

        TableElement table = new TableElement().leftCellPadding(1).rightCellPadding(1);
        table.row(new LabelElement("HASHCODE").style(Decoration.bold.bold()),
                  new LabelElement("CLASSLOADER").style(Decoration.bold.bold()));

        for (Class<?> c : matchedClasses) {
            ClassLoader classLoader = c.getClassLoader();
            table.row(label(Integer.toHexString(classLoader.hashCode())).style(Decoration.bold.fg(Color.red)),
                      TypeRenderUtils.drawClassLoader(c));
        }

        process.write(RenderUtil.render(table, process.width()) + "\n");
    }

    private Matcher<String> getClassMatcher() {
        return new EqualsMatcher<String>(classPattern);
    }
}
