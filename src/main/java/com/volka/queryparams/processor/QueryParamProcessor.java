package com.volka.queryparams.processor;

import com.squareup.javapoet.*;
import com.volka.queryparams.annotations.QueryParams;
import com.volka.queryparams.annotations.constant.Case;
import com.volka.queryparams.exception.CaseConvertException;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.util.ElementFilter;
import java.io.IOException;
import java.util.*;

@SupportedSourceVersion(SourceVersion.RELEASE_21)
@SupportedAnnotationTypes("com.volka.queryparams.QueryParams")
public class QueryParamProcessor extends AbstractProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            for (TypeElement typeElement : ElementFilter.typesIn(roundEnv.getElementsAnnotatedWith(annotation))) {
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
        
        for (Element field : collectFieldsByType(type)) {
            String fieldName = field.getSimpleName().toString();
            String key = CaseConverter.convertCase(fieldName, caseType);
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
            throw new CaseConvertException("Case converting Exception", e);
        }
    }

    private List<? extends Element> collectFieldsByType(TypeElement type) {
        if (type.getKind() == ElementKind.RECORD) return ElementFilter.recordComponentsIn(type.getEnclosedElements());
        return ElementFilter.fieldsIn(type.getEnclosedElements());
    }

}
