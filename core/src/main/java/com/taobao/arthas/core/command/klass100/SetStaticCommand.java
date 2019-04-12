package com.taobao.arthas.core.command.klass100;

import com.alibaba.fastjson.JSONObject;
import com.taobao.arthas.core.command.Constants;
import com.taobao.arthas.core.shell.cli.Completion;
import com.taobao.arthas.core.shell.cli.CompletionUtils;
import com.taobao.arthas.core.shell.command.AnnotatedCommand;
import com.taobao.arthas.core.shell.command.CommandProcess;
import com.taobao.arthas.core.util.LogUtil;
import com.taobao.arthas.core.util.SearchUtils;
import com.taobao.arthas.core.util.StringUtils;
import com.taobao.arthas.core.util.TypeRenderUtils;
import com.taobao.arthas.core.util.affect.RowAffect;
import com.taobao.arthas.core.util.matcher.EqualsMatcher;
import com.taobao.arthas.core.util.matcher.Matcher;
import com.taobao.arthas.core.util.reflect.FieldUtils;
import com.taobao.middleware.cli.annotations.Argument;
import com.taobao.middleware.cli.annotations.Description;
import com.taobao.middleware.cli.annotations.Name;
import com.taobao.middleware.cli.annotations.Option;
import com.taobao.middleware.cli.annotations.Summary;
import com.taobao.middleware.logger.Logger;
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

import static com.taobao.arthas.core.util.ArthasCheckUtils.isIn;
import static com.taobao.text.ui.Element.label;

/**
 * Date: 2019/4/11
 *
 * @author xuzhiyi
 */
@Name("setstatic")
@Summary("Set the static field of a class")
@Description(Constants.EXAMPLE +
             "  setstatic com.taobao.arthas.core.GlobalOptions isDump true \n" +
             Constants.WIKI + Constants.WIKI_HOME + "setstatic")
public class SetStaticCommand extends AnnotatedCommand {

    private static final Logger logger = LogUtil.getArthasLogger();

    private String className;
    private String fieldName;
    private String value;
    private String hashCode;

    @Argument(argName = "class-pattern", index = 0)
    @Description("Class name pattern, use either '.' or '/' as separator")
    public void setClassName(String className) {
        this.className = className;
    }

    @Argument(argName = "field-pattern", index = 1)
    @Description("Field name pattern")
    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
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
                process.write("No class found for: " + className + "\n");
            } else if (matchedClasses.size() > 1) {
                processMatches(process, matchedClasses);
            } else {
                processExactMatch(process, affect, matchedClasses);
            }
        } finally {
            process.write(affect + "\n");
            process.end();
        }
    }

    @Override
    public void complete(Completion completion) {
        int argumentIndex = CompletionUtils.detectArgumentIndex(completion);
        if (argumentIndex == 1) {
            if (!CompletionUtils.completeClassName(completion)) {
                super.complete(completion);
            }
            return;
        } else if (argumentIndex == 2) {
            if (!CompletionUtils.completeStaticFieldName(completion)) {
                super.complete(completion);
            }
            return;
        }

        super.complete(completion);
    }

    private void processExactMatch(CommandProcess process, RowAffect affect, Set<Class<?>> matchedClasses) {
        Matcher<String> fieldNameMatcher = getFieldNameMatcher();
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
                Object beforeValue = field.get(null);
                Class<?> fieldType = field.getType();
                Object afterValue;
                if (isIn(fieldType, int.class, Integer.class)) {
                    FieldUtils.writeStaticField(field, afterValue = Integer.valueOf(value));
                } else if (isIn(fieldType, long.class, Long.class)) {
                    FieldUtils.writeStaticField(field, afterValue = Long.valueOf(value));
                } else if (isIn(fieldType, boolean.class, Boolean.class)) {
                    FieldUtils.writeStaticField(field, afterValue = Boolean.valueOf(value));
                } else if (isIn(fieldType, double.class, Double.class)) {
                    FieldUtils.writeStaticField(field, afterValue = Double.valueOf(value));
                } else if (isIn(fieldType, float.class, Float.class)) {
                    FieldUtils.writeStaticField(field, afterValue = Float.valueOf(value));
                } else if (isIn(fieldType, byte.class, Byte.class)) {
                    FieldUtils.writeStaticField(field, afterValue = Byte.valueOf(value));
                } else if (isIn(fieldType, short.class, Short.class)) {
                    FieldUtils.writeStaticField(field, afterValue = Short.valueOf(value));
                } else if (isIn(fieldType, short.class, String.class)) {
                    FieldUtils.writeStaticField(field, afterValue = value);
                } else {
                    FieldUtils.writeStaticField(field, afterValue = JSONObject.parseObject(value, field.getType()));
                }
                TableElement table = new TableElement().leftCellPadding(1).rightCellPadding(1);
                table.row(true, label("NAME").style(Decoration.bold.bold()),
                          label("BEFORE-VALUE").style(Decoration.bold.bold()),
                          label("AFTER-VALUE").style(Decoration.bold.bold()));
                table.row(fieldName, StringUtils.objectToString(beforeValue),
                          StringUtils.objectToString(afterValue));
                process.write(RenderUtil.render(table, process.width()));
                affect.rCnt(1);
            } catch (Throwable e) {
                logger.warn("setstatic: failed to get static value, class: " + clazz + ", field: " + field.getName(),
                            e);
                process.write("Failed to set static, exception message: " + e.getMessage()
                              + ", please check $HOME/logs/arthas/arthas.log for more details. \n");
            } finally {
                found = true;
            }
        }

        if (!found) {
            process.write("setstatic: no matched static field was found\n");
        }
    }

    private void processMatches(CommandProcess process, Set<Class<?>> matchedClasses) {
        Element usage = new LabelElement("setstatic -c <hashcode> " + className + " " + fieldName).style(
            Decoration.bold.fg(Color.blue));
        process.write("\n Found more than one class for: " + className + ", Please use " + RenderUtil.render(usage,
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
        return new EqualsMatcher<String>(className);
    }

    private Matcher<String> getFieldNameMatcher() {
        return new EqualsMatcher<String>(fieldName);
    }
}
