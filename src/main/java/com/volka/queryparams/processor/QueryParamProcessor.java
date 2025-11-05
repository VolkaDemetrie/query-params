package com.volka.queryparams.processor;

import com.squareup.javapoet.*;
import com.volka.queryparams.annotations.QueryParams;
import com.volka.queryparams.annotations.constant.Case;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import java.io.IOException;
import java.util.*;

@SupportedSourceVersion(SourceVersion.RELEASE_21)
@SupportedAnnotationTypes("com.volka.queryparams.QueryParams")
public class QueryParamProcessor extends AbstractProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            for (TypeElement typeElement :
                    ElementFilter.typesIn(roundEnv.getElementsAnnotatedWith(annotation))) {
                generateToQueryMethods(typeElement);
            }
        }
        return true;
    }

    private void generateToQueryMethods(TypeElement type) {
        String className = type.getSimpleName() + "QueryParams";
        String pkg = processingEnv.getElementUtils()
                .getPackageOf(type)
                .getQualifiedName()
                .toString();

        QueryParams ann = type.getAnnotation(QueryParams.class);
        Case caseType = ann.value();

        // Map<String, List<String>>
        ClassName stringCls = ClassName.get(String.class);
        ClassName listCls = ClassName.get(List.class);
        ClassName mapCls = ClassName.get(Map.class);
        ClassName linkedHashMapCls = ClassName.get(LinkedHashMap.class);
        ClassName collectorsCls = ClassName.get(java.util.stream.Collectors.class);

        TypeName listOfString = ParameterizedTypeName.get(listCls, stringCls);
        TypeName mapStringList = ParameterizedTypeName.get(mapCls, stringCls, listOfString);

        // toQueryParams(...)
        MethodSpec.Builder toMap = MethodSpec.methodBuilder("toQueryParams")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(mapStringList)
                .addParameter(TypeName.get(type.asType()), "src")
                .addStatement("$T map = new $T<>()", mapStringList, linkedHashMapCls);

        for (RecordComponentElement field : ElementFilter.recordComponentsIn(type.getEnclosedElements())) {
            String fieldName = field.getSimpleName().toString();
            String key = convertCase(fieldName, caseType);
            // 단일 값만 있다고 가정하고 List<String> 한 칸짜리로 감쌈
            toMap.addStatement(
                    "map.put($S, $T.of(String.valueOf(src.$L())))",
                    key, listCls, fieldName
            );
        }
        toMap.addStatement("return map");

        // toQueryString(...)
        MethodSpec toString = MethodSpec.methodBuilder("toQueryString")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(String.class)
                .addParameter(TypeName.get(type.asType()), "src")
                .addStatement("$T params = toQueryParams(src)", mapStringList)
                .addStatement(
                        "return params.entrySet().stream()" +
                                ".flatMap(e -> e.getValue().stream().map(v -> e.getKey() + \"=\" + v))" +
                                ".collect($T.joining(\"&\"))",
                        collectorsCls
                )
                .build();

        TypeSpec generated = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addMethod(toMap.build())
                .addMethod(toString)
                .build();

        try {
            JavaFile.builder(pkg, generated).build().writeTo(processingEnv.getFiler());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String convertCase(String name, Case targetCase) {
        List<String> words = splitIntoWords(name);

        return switch (targetCase) {
            case CAMEL -> toCamel(words);
            case PASCAL -> toPascal(words);
            case SNAKE -> String.join("_", words);
            case KEBAB -> String.join("-", words);
            case UPPER_SNAKE -> String.join("_", words).toUpperCase();
            case UPPER_KEBAB -> String.join("-", words).toUpperCase();
        };
    }

    private List<String> splitIntoWords(String name) {
        if (name == null || name.isEmpty()) {
            return List.of();
        }

        // snake
        if (name.contains("_")) {
            String[] arr = name.split("_");
            List<String> list = new ArrayList<>(arr.length);
            for (String s : arr) {
                if (!s.isEmpty()) list.add(s.toLowerCase());
            }
            return list;
        }

        // kebab
        if (name.contains("-")) {
            String[] arr = name.split("-");
            List<String> list = new ArrayList<>(arr.length);
            for (String s : arr) {
                if (!s.isEmpty()) list.add(s.toLowerCase());
            }
            return list;
        }

        // camel / Pascal
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char[] chars = name.toCharArray();

        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (i > 0 && Character.isUpperCase(c) && !current.isEmpty()) {
                // 새 토큰 경계
                result.add(current.toString().toLowerCase());
                current.setLength(0);
            }
            current.append(c);
        }
        if (!current.isEmpty()) {
            result.add(current.toString().toLowerCase());
        }
        return result;
    }

    private String toCamel(List<String> words) {
        if (words.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(words.get(0));
        for (int i = 1; i < words.size(); i++) {
            sb.append(cap(words.get(i)));
        }
        return sb.toString();
    }

    private String toPascal(List<String> words) {
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            sb.append(cap(w));
        }
        return sb.toString();
    }

    private String cap(String w) {
        if (w == null || w.isEmpty()) return "";
        if (w.length() == 1) return w.toUpperCase();
        return Character.toUpperCase(w.charAt(0)) + w.substring(1);
    }
}
