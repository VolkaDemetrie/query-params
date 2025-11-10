package io.github.volkayun.queryparams.processor;

import com.squareup.javapoet.*;
import io.github.volkayun.queryparams.annotations.QueryParams;
import io.github.volkayun.queryparams.annotations.constant.Case;
import io.github.volkayun.queryparams.exception.CaseConvertException;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.ElementFilter;
import java.io.IOException;
import java.util.*;

@SupportedSourceVersion(SourceVersion.RELEASE_17)
@SupportedAnnotationTypes("io.github.volkayun.queryparams.annotations.QueryParams")
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

        // 공통으로 쓸 프로퍼티 정보 (필드명, 접근식)
        record Prop(String name, String accessorExpr) {}

        List<Prop> props = new ArrayList<>();

        if (type.getKind() == ElementKind.RECORD) {
            // record: src.fieldName()
            for (RecordComponentElement comp
                    : ElementFilter.recordComponentsIn(type.getEnclosedElements())) {
                String fieldName = comp.getSimpleName().toString();
                String accessor = "src." + fieldName + "()";
                props.add(new Prop(fieldName, accessor));
            }
        } else {
            // class: public getter 기준으로 프로퍼티 추출
            Map<String, String> getters = findBeanGetters(type);
            for (Map.Entry<String, String> e : getters.entrySet()) {
                String fieldName = e.getKey();       // name
                String getterName = e.getValue();    // getName
                String accessor = "src." + getterName + "()";
                props.add(new Prop(fieldName, accessor));
            }
        }

        MethodSpec.Builder toMap = MethodSpec.methodBuilder("toQueryParams")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(mapStringList)
                .addParameter(TypeName.get(type.asType()), "src")
                .addStatement("$T map = new $T<>()", mapStringList, linkedHashMapCls);

        for (Prop p : props) {
            String key = CaseConverter.convertCase(p.name(), caseType);
            toMap.addStatement(
                    "map.put($S, $T.of(String.valueOf($L)))",
                    key, listCls, p.accessorExpr()
            );
        }
        toMap.addStatement("return map");

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

    private Map<String, String> findBeanGetters(TypeElement type) {
        Map<String, String> result = new LinkedHashMap<>();

        for (ExecutableElement method
                : ElementFilter.methodsIn(type.getEnclosedElements())) {

            Set<Modifier> mods = method.getModifiers();
            if (!mods.contains(Modifier.PUBLIC) || mods.contains(Modifier.STATIC)) continue;
            if (!method.getParameters().isEmpty()) continue;

            String name = method.getSimpleName().toString();
            String propName = null;

            if (name.startsWith("get") && name.length() > 3) {
                propName = decap(name.substring(3));
            } else if (name.startsWith("is") && name.length() > 2 &&
                    method.getReturnType().getKind() == TypeKind.BOOLEAN) {
                propName = decap(name.substring(2));
            }

            if (propName != null && !propName.isEmpty()) {
                result.put(propName, name); // propName -> getterName
            }
        }
        return result;
    }

    private String decap(String s) {
        if (s.isEmpty()) return s;
        if (s.length() == 1) return s.toLowerCase();
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }


    private List<? extends Element> collectFieldsByType(TypeElement type) {
        if (type.getKind() == ElementKind.RECORD) return ElementFilter.recordComponentsIn(type.getEnclosedElements());
        return ElementFilter.fieldsIn(type.getEnclosedElements());
    }

}
