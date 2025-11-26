package io.github.volkayun.queryparams.processor;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.*;
import io.github.volkayun.queryparams.annotations.*;
import io.github.volkayun.queryparams.runtime.QueryParamConvertible;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Annotation processor for @QueryParams.
 * Generates toQueryParams() methods at compile-time.
 */
@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@SupportedAnnotationTypes("io.github.volkayun.queryparams.annotations.QueryParams")
@SupportedOptions({"lombok.addLombokGeneratedAnnotation"})
public class QueryParamsProcessor extends AbstractProcessor {

    private Elements elementUtils;
    private Types typeUtils;
    private Messager messager;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.elementUtils = processingEnv.getElementUtils();
        this.typeUtils = processingEnv.getTypeUtils();
        this.messager = processingEnv.getMessager();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);
            for (Element element : annotatedElements) {
                if (element.getKind() == ElementKind.CLASS || element.getKind() == ElementKind.RECORD) {
                    processType((TypeElement) element);
                }
            }
        }
        return true;
    }

    private void processType(TypeElement typeElement) {
        try {
            QueryParams annotation = typeElement.getAnnotation(QueryParams.class);
            if (annotation == null) return;

            String packageName = elementUtils.getPackageOf(typeElement).getQualifiedName().toString();
            String simpleClassName = typeElement.getSimpleName().toString();

            TypeSpec generatedClass = generateHelperClass(typeElement, annotation);

            JavaFile javaFile = JavaFile.builder(packageName, generatedClass)
                    .build();

            javaFile.writeTo(processingEnv.getFiler());

            // Also generate the interface implementation method directly in the source type
            // This requires modifying the original source, which is done via a separate generated class

        } catch (IOException e) {
            error(typeElement, "Failed to generate query params helper: " + e.getMessage());
        }
    }

    private TypeSpec generateHelperClass(TypeElement typeElement, QueryParams config) {
        String simpleClassName = typeElement.getSimpleName().toString();
        String helperClassName = simpleClassName + "__QueryParams";

        ClassName stringClass = ClassName.get(String.class);
        ClassName listClass = ClassName.get(List.class);
        ClassName mapClass = ClassName.get(Map.class);
        ClassName linkedHashMapClass = ClassName.get(LinkedHashMap.class);
        ClassName arrayListClass = ClassName.get(ArrayList.class);
        TypeName listOfString = ParameterizedTypeName.get(listClass, stringClass);
        TypeName mapType = ParameterizedTypeName.get(mapClass, stringClass, listOfString);

        // Collect fields
        List<FieldInfo> fields = collectFields(typeElement, config);

        // Generate toQueryParams method
        MethodSpec.Builder toMapMethod = MethodSpec.methodBuilder("toQueryParams")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(mapType)
                .addParameter(TypeName.get(typeElement.asType()), "source");

        toMapMethod.addStatement("$T map = new $T<>()", mapType, linkedHashMapClass);
        toMapMethod.addCode("\n");

        Set<String> usedKeys = new HashSet<>();

        for (FieldInfo field : fields) {
            if (field.ignored) continue;

            String key = determineKey(field, config);

            // Check for duplicate keys
            if (config.failOnDuplicateKeys() && usedKeys.contains(key)) {
                error(field.element, "Duplicate query parameter key detected: " + key);
            }
            usedKeys.add(key);

            generateFieldCode(toMapMethod, field, key, config);
        }

        toMapMethod.addStatement("return map");

        return TypeSpec.classBuilder(helperClassName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addMethod(toMapMethod.build())
                .build();
    }

    private List<FieldInfo> collectFields(TypeElement typeElement, QueryParams config) {
        List<FieldInfo> fields = new ArrayList<>();

        if (typeElement.getKind() == ElementKind.RECORD) {
            // Record components
            for (RecordComponentElement component : ElementFilter.recordComponentsIn(typeElement.getEnclosedElements())) {
                fields.add(new FieldInfo(
                        component,
                        component.getSimpleName().toString(),
                        component.asType(),
                        "source." + component.getSimpleName() + "()",
                        component.getAnnotation(ParamName.class),
                        component.getAnnotation(ParamIgnore.class) != null,
                        component.getAnnotation(ParamPrefix.class),
                        component.getAnnotation(ParamConverter.class),
                        component.getAnnotation(UseJsonProperty.class) != null
                ));
            }
        } else {
            // Regular class - look for getters
            Map<String, MethodInfo> getters = findGetters(typeElement);
            for (Map.Entry<String, MethodInfo> entry : getters.entrySet()) {
                String propName = entry.getKey();
                MethodInfo methodInfo = entry.getValue();

                // Check for field-level annotations
                VariableElement fieldElement = findField(typeElement, propName);

                ParamName paramName = fieldElement != null ? fieldElement.getAnnotation(ParamName.class) : null;
                boolean ignored = fieldElement != null && fieldElement.getAnnotation(ParamIgnore.class) != null;
                ParamPrefix prefix = fieldElement != null ? fieldElement.getAnnotation(ParamPrefix.class) : null;
                ParamConverter converter = fieldElement != null ? fieldElement.getAnnotation(ParamConverter.class) : null;
                boolean useJsonProp = fieldElement != null && fieldElement.getAnnotation(UseJsonProperty.class) != null;

                fields.add(new FieldInfo(
                        fieldElement != null ? fieldElement : methodInfo.method,
                        propName,
                        methodInfo.returnType,
                        "source." + methodInfo.methodName + "()",
                        paramName,
                        ignored,
                        prefix,
                        converter,
                        useJsonProp
                ));
            }
        }

        return fields;
    }

    private Map<String, MethodInfo> findGetters(TypeElement typeElement) {
        Map<String, MethodInfo> result = new LinkedHashMap<>();

        for (ExecutableElement method : ElementFilter.methodsIn(typeElement.getEnclosedElements())) {
            if (!method.getModifiers().contains(Modifier.PUBLIC)) continue;
            if (method.getModifiers().contains(Modifier.STATIC)) continue;
            if (!method.getParameters().isEmpty()) continue;

            String methodName = method.getSimpleName().toString();
            String propName = null;

            if (methodName.startsWith("get") && methodName.length() > 3) {
                propName = decapitalize(methodName.substring(3));
            } else if (methodName.startsWith("is") && methodName.length() > 2) {
                propName = decapitalize(methodName.substring(2));
            }

            if (propName != null && !propName.isEmpty()) {
                result.put(propName, new MethodInfo(methodName, method.getReturnType(), method));
            }
        }

        return result;
    }

    private VariableElement findField(TypeElement typeElement, String propName) {
        for (VariableElement field : ElementFilter.fieldsIn(typeElement.getEnclosedElements())) {
            if (field.getSimpleName().toString().equals(propName)) {
                return field;
            }
        }
        return null;
    }

    private String decapitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        if (name.length() == 1) return name.toLowerCase();
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    private String determineKey(FieldInfo field, QueryParams config) {
        // Priority: @ParamName > @UseJsonProperty > case conversion
        if (field.paramName != null) {
            return field.paramName.value();
        }

        if (field.useJsonProperty) {
            String jsonPropertyValue = extractJsonPropertyValue(field.element);
            if (jsonPropertyValue != null) {
                return jsonPropertyValue;
            }
        }

        String baseName = field.name;
        CaseStrategy strategy = config.caseStrategy();

        String convertedName = CaseConverter.convertCase(baseName, strategy);

        String prefix = config.prefix();
        if (field.paramPrefix != null) {
            prefix = field.paramPrefix.value();
        }

        return prefix + convertedName;
    }

    private String extractJsonPropertyValue(Element element) {
        // Try to extract @JsonProperty value
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            if (mirror.getAnnotationType().toString().equals("com.fasterxml.jackson.annotation.JsonProperty")) {
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : mirror.getElementValues().entrySet()) {
                    if (entry.getKey().getSimpleName().toString().equals("value")) {
                        return entry.getValue().getValue().toString();
                    }
                }
            }
        }
        return null;
    }

    private void generateFieldCode(MethodSpec.Builder method, FieldInfo field, String key, QueryParams config) {
        String accessor = field.accessor;
        TypeMirror fieldType = field.type;

        // Check if custom converter is specified
        if (field.paramConverter != null) {
            generateConverterCode(method, field, key, config);
            return;
        }

        // Check for nested object with @ParamPrefix
        if (field.paramPrefix != null && isComplexType(fieldType)) {
            generateNestedObjectCode(method, field, key, config);
            return;
        }

        // Handle by type
        if (isSimpleType(fieldType)) {
            generateSimpleFieldCode(method, accessor, key, fieldType, config);
        } else if (isCollectionType(fieldType)) {
            generateCollectionCode(method, accessor, key, fieldType, config);
        } else if (isArrayType(fieldType)) {
            generateArrayCode(method, accessor, key, fieldType, config);
        } else if (isOptionalType(fieldType)) {
            generateOptionalCode(method, accessor, key, fieldType, config);
        } else if (isMapType(fieldType)) {
            generateMapCode(method, accessor, key, config);
        } else if (isDateTimeType(fieldType)) {
            generateDateTimeCode(method, accessor, key, fieldType, config);
        } else {
            // Nested object
            generateNestedObjectCode(method, field, key, config);
        }
    }

    private void generateSimpleFieldCode(MethodSpec.Builder method, String accessor, String key, TypeMirror type, QueryParams config) {
        method.addCode("{\n");
        method.addCode("  $T value = $L;\n", TypeName.get(type), accessor);

        if (config.includeNulls()) {
            method.addCode("  String strValue = value == null ? \"\" : ");
        } else {
            method.addCode("  if (value != null) {\n");
            method.addCode("    String strValue = ");
        }

        // Special handling for BigDecimal
        if (isBigDecimal(type)) {
            method.addCode("((BigDecimal)value).toPlainString()");
        } else {
            method.addCode("String.valueOf(value)");
        }

        method.addCode(";\n");

        if (config.encoded()) {
            if (config.includeNulls()) {
                method.addCode("  map.put($S, $T.of(strValue));\n", key, List.class);
            } else {
                method.addCode("    map.put($S, $T.of(strValue));\n", key, List.class);
                method.addCode("  }\n");
            }
        } else {
            String encodedVar = config.includeNulls() ? "  " : "    ";
            method.addCode(encodedVar + "try {\n");
            method.addCode(encodedVar + "  String encoded = $T.encode(strValue, $T.UTF_8.name());\n", URLEncoder.class, StandardCharsets.class);
            method.addCode(encodedVar + "  map.put($S, $T.of(encoded));\n", key, List.class);
            method.addCode(encodedVar + "} catch ($T e) {\n", UnsupportedEncodingException.class);
            method.addCode(encodedVar + "  throw new RuntimeException(e);\n");
            method.addCode(encodedVar + "}\n");

            if (!config.includeNulls()) {
                method.addCode("  }\n");
            }
        }

        method.addCode("}\n");
    }

    private void generateCollectionCode(MethodSpec.Builder method, String accessor, String key, TypeMirror type, QueryParams config) {
        TypeMirror elementType = getCollectionElementType(type);

        method.addCode("{\n");
        method.addCode("  $T collection = $L;\n", TypeName.get(type), accessor);
        method.addCode("  if (collection != null && !collection.isEmpty()) {\n");

        if (config.explodeArrays()) {
            method.addCode("    $T<String> values = new $T<>();\n", List.class, ArrayList.class);
            method.addCode("    for (Object item : collection) {\n");
            method.addCode("      if (item != null) {\n");

            if (config.encoded()) {
                method.addCode("        values.add(String.valueOf(item));\n");
            } else {
                method.addCode("        try {\n");
                method.addCode("          values.add($T.encode(String.valueOf(item), $T.UTF_8.name()));\n", URLEncoder.class, StandardCharsets.class);
                method.addCode("        } catch ($T e) {\n", UnsupportedEncodingException.class);
                method.addCode("          throw new RuntimeException(e);\n");
                method.addCode("        }\n");
            }

            method.addCode("      }\n");
            method.addCode("    }\n");
            method.addCode("    map.put($S, values);\n", key);
        } else {
            method.addCode("    StringBuilder joined = new StringBuilder();\n");
            method.addCode("    boolean first = true;\n");
            method.addCode("    for (Object item : collection) {\n");
            method.addCode("      if (item != null) {\n");
            method.addCode("        if (!first) joined.append(\",\");\n");
            method.addCode("        joined.append(String.valueOf(item));\n");
            method.addCode("        first = false;\n");
            method.addCode("      }\n");
            method.addCode("    }\n");

            if (config.encoded()) {
                method.addCode("    map.put($S, $T.of(joined.toString()));\n", key, List.class);
            } else {
                method.addCode("    try {\n");
                method.addCode("      map.put($S, $T.of($T.encode(joined.toString(), $T.UTF_8.name())));\n",
                        key, List.class, URLEncoder.class, StandardCharsets.class);
                method.addCode("    } catch ($T e) {\n", UnsupportedEncodingException.class);
                method.addCode("      throw new RuntimeException(e);\n");
                method.addCode("    }\n");
            }
        }

        method.addCode("  }\n");
        method.addCode("}\n");
    }

    private void generateArrayCode(MethodSpec.Builder method, String accessor, String key, TypeMirror type, QueryParams config) {
        method.addCode("{\n");
        method.addCode("  Object[] array = $L;\n", accessor);
        method.addCode("  if (array != null && array.length > 0) {\n");

        if (config.explodeArrays()) {
            method.addCode("    $T<String> values = new $T<>();\n", List.class, ArrayList.class);
            method.addCode("    for (Object item : array) {\n");
            method.addCode("      if (item != null) {\n");

            if (config.encoded()) {
                method.addCode("        values.add(String.valueOf(item));\n");
            } else {
                method.addCode("        try {\n");
                method.addCode("          values.add($T.encode(String.valueOf(item), $T.UTF_8.name()));\n", URLEncoder.class, StandardCharsets.class);
                method.addCode("        } catch ($T e) {\n", UnsupportedEncodingException.class);
                method.addCode("          throw new RuntimeException(e);\n");
                method.addCode("        }\n");
            }

            method.addCode("      }\n");
            method.addCode("    }\n");
            method.addCode("    map.put($S, values);\n", key);
        } else {
            method.addCode("    StringBuilder joined = new StringBuilder();\n");
            method.addCode("    boolean first = true;\n");
            method.addCode("    for (Object item : array) {\n");
            method.addCode("      if (item != null) {\n");
            method.addCode("        if (!first) joined.append(\",\");\n");
            method.addCode("        joined.append(String.valueOf(item));\n");
            method.addCode("        first = false;\n");
            method.addCode("      }\n");
            method.addCode("    }\n");

            if (config.encoded()) {
                method.addCode("    map.put($S, $T.of(joined.toString()));\n", key, List.class);
            } else {
                method.addCode("    try {\n");
                method.addCode("      map.put($S, $T.of($T.encode(joined.toString(), $T.UTF_8.name())));\n",
                        key, List.class, URLEncoder.class, StandardCharsets.class);
                method.addCode("    } catch ($T e) {\n", UnsupportedEncodingException.class);
                method.addCode("      throw new RuntimeException(e);\n");
                method.addCode("    }\n");
            }
        }

        method.addCode("  }\n");
        method.addCode("}\n");
    }

    private void generateOptionalCode(MethodSpec.Builder method, String accessor, String key, TypeMirror type, QueryParams config) {
        TypeMirror elementType = getOptionalElementType(type);

        method.addCode("{\n");
        method.addCode("  $T optional = $L;\n", TypeName.get(type), accessor);
        method.addCode("  if (optional != null && optional.isPresent()) {\n");
        method.addCode("    Object value = optional.get();\n");

        if (config.encoded()) {
            method.addCode("    map.put($S, $T.of(String.valueOf(value)));\n", key, List.class);
        } else {
            method.addCode("    try {\n");
            method.addCode("      map.put($S, $T.of($T.encode(String.valueOf(value), $T.UTF_8.name())));\n",
                    key, List.class, URLEncoder.class, StandardCharsets.class);
            method.addCode("    } catch ($T e) {\n", UnsupportedEncodingException.class);
            method.addCode("      throw new RuntimeException(e);\n");
            method.addCode("    }\n");
        }

        method.addCode("  }\n");
        method.addCode("}\n");
    }

    private void generateMapCode(MethodSpec.Builder method, String accessor, String key, QueryParams config) {
        method.addCode("{\n");
        method.addCode("  $T<String, ?> mapValue = ($T<String, ?>) $L;\n", Map.class, Map.class, accessor);
        method.addCode("  if (mapValue != null) {\n");
        method.addCode("    for ($T.Entry<String, ?> entry : mapValue.entrySet()) {\n", Map.class);
        method.addCode("      if (entry.getValue() != null) {\n");

        if (config.encoded()) {
            method.addCode("        map.put(entry.getKey(), $T.of(String.valueOf(entry.getValue())));\n", List.class);
        } else {
            method.addCode("        try {\n");
            method.addCode("          map.put(entry.getKey(), $T.of($T.encode(String.valueOf(entry.getValue()), $T.UTF_8.name())));\n",
                    List.class, URLEncoder.class, StandardCharsets.class);
            method.addCode("        } catch ($T e) {\n", UnsupportedEncodingException.class);
            method.addCode("          throw new RuntimeException(e);\n");
            method.addCode("        }\n");
        }

        method.addCode("      }\n");
        method.addCode("    }\n");
        method.addCode("  }\n");
        method.addCode("}\n");
    }

    private void generateDateTimeCode(MethodSpec.Builder method, String accessor, String key, TypeMirror type, QueryParams config) {
        String typeName = type.toString();
        DateTimeFormat format = config.dateTimeFormat();

        method.addCode("{\n");
        method.addCode("  $T value = $L;\n", TypeName.get(type), accessor);
        method.addCode("  if (value != null) {\n");
        method.addCode("    String formatted;\n");

        if (format == DateTimeFormat.PATTERN && !config.pattern().isEmpty()) {
            method.addCode("    $T formatter = $T.ofPattern($S);\n", DateTimeFormatter.class, DateTimeFormatter.class, config.pattern());
            method.addCode("    formatted = value.format(formatter);\n");
        } else if (format == DateTimeFormat.ISO_LOCAL_DATE_TIME) {
            method.addCode("    formatted = value.format($T.ISO_LOCAL_DATE_TIME);\n", DateTimeFormatter.class);
        } else if (format == DateTimeFormat.ISO_LOCAL_DATE) {
            method.addCode("    formatted = value.format($T.ISO_LOCAL_DATE);\n", DateTimeFormatter.class);
        } else {
            method.addCode("    formatted = value.toString();\n");
        }

        if (config.encoded()) {
            method.addCode("    map.put($S, $T.of(formatted));\n", key, List.class);
        } else {
            method.addCode("    try {\n");
            method.addCode("      map.put($S, $T.of($T.encode(formatted, $T.UTF_8.name())));\n",
                    key, List.class, URLEncoder.class, StandardCharsets.class);
            method.addCode("    } catch ($T e) {\n", UnsupportedEncodingException.class);
            method.addCode("      throw new RuntimeException(e);\n");
            method.addCode("    }\n");
        }

        method.addCode("  }\n");
        method.addCode("}\n");
    }

    private void generateNestedObjectCode(MethodSpec.Builder method, FieldInfo field, String key, QueryParams config) {
        // For nested objects with @ParamPrefix, we recursively process its fields
        TypeMirror nestedType = field.type;

        if (nestedType.getKind() != TypeKind.DECLARED) {
            method.addCode("// Cannot process nested type: $L\n", nestedType);
            return;
        }

        TypeElement nestedElement = (TypeElement) ((DeclaredType) nestedType).asElement();

        method.addCode("{\n");
        method.addCode("  $T nested = $L;\n", TypeName.get(nestedType), field.accessor);
        method.addCode("  if (nested != null) {\n");

        // Get the prefix to use
        String prefix = field.paramPrefix != null ? field.paramPrefix.value() : "";

        // Collect fields from the nested object
        List<FieldInfo> nestedFields = collectFieldsForNestedObject(nestedElement);

        for (FieldInfo nestedField : nestedFields) {
            if (nestedField.ignored) continue;

            // Determine the key for the nested field
            String nestedKey = determineNestedKey(nestedField, prefix, config);

            // Generate code for this nested field
            String nestedAccessor = "nested." + getAccessorMethod(nestedElement, nestedField.name);

            generateNestedFieldCode(method, nestedField, nestedKey, nestedAccessor, config);
        }

        method.addCode("  }\n");
        method.addCode("}\n");
    }

    private List<FieldInfo> collectFieldsForNestedObject(TypeElement typeElement) {
        List<FieldInfo> fields = new ArrayList<>();

        if (typeElement.getKind() == ElementKind.RECORD) {
            for (RecordComponentElement component : ElementFilter.recordComponentsIn(typeElement.getEnclosedElements())) {
                fields.add(new FieldInfo(
                        component,
                        component.getSimpleName().toString(),
                        component.asType(),
                        component.getSimpleName() + "()",
                        component.getAnnotation(ParamName.class),
                        component.getAnnotation(ParamIgnore.class) != null,
                        null,
                        null,
                        component.getAnnotation(UseJsonProperty.class) != null
                ));
            }
        } else {
            Map<String, MethodInfo> getters = findGetters(typeElement);
            for (Map.Entry<String, MethodInfo> entry : getters.entrySet()) {
                String propName = entry.getKey();
                MethodInfo methodInfo = entry.getValue();
                VariableElement fieldElement = findField(typeElement, propName);

                ParamName paramName = fieldElement != null ? fieldElement.getAnnotation(ParamName.class) : null;
                boolean ignored = fieldElement != null && fieldElement.getAnnotation(ParamIgnore.class) != null;
                boolean useJsonProp = fieldElement != null && fieldElement.getAnnotation(UseJsonProperty.class) != null;

                fields.add(new FieldInfo(
                        fieldElement != null ? fieldElement : methodInfo.method,
                        propName,
                        methodInfo.returnType,
                        methodInfo.methodName + "()",
                        paramName,
                        ignored,
                        null,
                        null,
                        useJsonProp
                ));
            }
        }

        return fields;
    }

    private String getAccessorMethod(TypeElement typeElement, String fieldName) {
        if (typeElement.getKind() == ElementKind.RECORD) {
            return fieldName + "()";
        }

        // For regular classes, find the getter
        Map<String, MethodInfo> getters = findGetters(typeElement);
        MethodInfo methodInfo = getters.get(fieldName);
        if (methodInfo != null) {
            return methodInfo.methodName + "()";
        }

        return "get" + capitalize(fieldName) + "()";
    }

    private String capitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        if (name.length() == 1) return name.toUpperCase();
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private String determineNestedKey(FieldInfo field, String prefix, QueryParams config) {
        // Priority: @ParamName > @UseJsonProperty > case conversion
        if (field.paramName != null) {
            return prefix + field.paramName.value();
        }

        if (field.useJsonProperty) {
            String jsonPropertyValue = extractJsonPropertyValue(field.element);
            if (jsonPropertyValue != null) {
                return prefix + jsonPropertyValue;
            }
        }

        String baseName = field.name;
        CaseStrategy strategy = config.caseStrategy();

        String convertedName = CaseConverter.convertCase(baseName, strategy);

        return prefix + convertedName;
    }

    private void generateNestedFieldCode(MethodSpec.Builder method, FieldInfo field, String key, String accessor, QueryParams config) {
        TypeMirror fieldType = field.type;

        if (isSimpleType(fieldType)) {
            method.addCode("    {\n");
            method.addCode("      $T value = $L;\n", TypeName.get(fieldType), accessor);
            method.addCode("      if (value != null) {\n");
            method.addCode("        String strValue = String.valueOf(value);\n");

            if (config.encoded()) {
                method.addCode("        map.put($S, $T.of(strValue));\n", key, List.class);
            } else {
                method.addCode("        try {\n");
                method.addCode("          String encoded = $T.encode(strValue, $T.UTF_8.name());\n", URLEncoder.class, StandardCharsets.class);
                method.addCode("          map.put($S, $T.of(encoded));\n", key, List.class);
                method.addCode("        } catch ($T e) {\n", UnsupportedEncodingException.class);
                method.addCode("          throw new RuntimeException(e);\n");
                method.addCode("        }\n");
            }

            method.addCode("      }\n");
            method.addCode("    }\n");
        } else {
            method.addCode("    // Complex nested field: $L (type: $L)\n", field.name, fieldType);
        }
    }

    private void generateConverterCode(MethodSpec.Builder method, FieldInfo field, String key, QueryParams config) {
        TypeMirror converterType = null;
        try {
            field.paramConverter.value();
        } catch (MirroredTypeException mte) {
            converterType = mte.getTypeMirror();
        }

        if (converterType != null) {
            method.addCode("{\n");
            method.addCode("  $T value = $L;\n", TypeName.get(field.type), field.accessor);
            method.addCode("  $T converter = new $T();\n", TypeName.get(converterType), TypeName.get(converterType));
            method.addCode("  $T<String, $T<String>> result = converter.convert($S, value);\n",
                    Map.class, List.class, key);
            method.addCode("  if (result != null) {\n");
            method.addCode("    map.putAll(result);\n");
            method.addCode("  }\n");
            method.addCode("}\n");
        }
    }

    // Type checking utilities
    private boolean isSimpleType(TypeMirror type) {
        if (type.getKind().isPrimitive()) return true;

        String typeName = type.toString();
        return typeName.equals("java.lang.String")
                || typeName.equals("java.lang.Integer")
                || typeName.equals("java.lang.Long")
                || typeName.equals("java.lang.Double")
                || typeName.equals("java.lang.Float")
                || typeName.equals("java.lang.Boolean")
                || typeName.equals("java.lang.Byte")
                || typeName.equals("java.lang.Short")
                || typeName.equals("java.lang.Character")
                || typeName.equals("java.math.BigDecimal")
                || typeName.equals("java.math.BigInteger")
                || type.getKind() == TypeKind.DECLARED && ((DeclaredType) type).asElement().getKind() == ElementKind.ENUM;
    }

    private boolean isBigDecimal(TypeMirror type) {
        return type.toString().equals("java.math.BigDecimal");
    }

    private boolean isCollectionType(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) return false;

        TypeElement element = (TypeElement) ((DeclaredType) type).asElement();
        return isAssignable(element.asType(), "java.util.Collection");
    }

    private boolean isArrayType(TypeMirror type) {
        return type.getKind() == TypeKind.ARRAY;
    }

    private boolean isOptionalType(TypeMirror type) {
        return type.toString().startsWith("java.util.Optional");
    }

    private boolean isMapType(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) return false;

        TypeElement element = (TypeElement) ((DeclaredType) type).asElement();
        return isAssignable(element.asType(), "java.util.Map");
    }

    private boolean isDateTimeType(TypeMirror type) {
        String typeName = type.toString();
        return typeName.equals("java.time.LocalDate")
                || typeName.equals("java.time.LocalDateTime")
                || typeName.equals("java.time.LocalTime")
                || typeName.equals("java.time.ZonedDateTime")
                || typeName.equals("java.time.OffsetDateTime")
                || typeName.equals("java.time.Instant");
    }

    private boolean isComplexType(TypeMirror type) {
        return !isSimpleType(type)
                && !isCollectionType(type)
                && !isArrayType(type)
                && !isOptionalType(type)
                && !isMapType(type)
                && !isDateTimeType(type);
    }

    private boolean isAssignable(TypeMirror type, String targetClassName) {
        TypeElement targetElement = elementUtils.getTypeElement(targetClassName);
        if (targetElement == null) return false;
        return typeUtils.isAssignable(type, typeUtils.erasure(targetElement.asType()));
    }

    private TypeMirror getCollectionElementType(TypeMirror type) {
        if (type.getKind() == TypeKind.DECLARED) {
            DeclaredType declaredType = (DeclaredType) type;
            if (!declaredType.getTypeArguments().isEmpty()) {
                return declaredType.getTypeArguments().get(0);
            }
        }
        return elementUtils.getTypeElement("java.lang.Object").asType();
    }

    private TypeMirror getOptionalElementType(TypeMirror type) {
        if (type.getKind() == TypeKind.DECLARED) {
            DeclaredType declaredType = (DeclaredType) type;
            if (!declaredType.getTypeArguments().isEmpty()) {
                return declaredType.getTypeArguments().get(0);
            }
        }
        return elementUtils.getTypeElement("java.lang.Object").asType();
    }

    private void error(Element element, String message) {
        messager.printMessage(Diagnostic.Kind.ERROR, message, element);
    }

    // Helper classes
    private static class FieldInfo {
        final Element element;
        final String name;
        final TypeMirror type;
        final String accessor;
        final ParamName paramName;
        final boolean ignored;
        final ParamPrefix paramPrefix;
        final ParamConverter paramConverter;
        final boolean useJsonProperty;

        FieldInfo(Element element, String name, TypeMirror type, String accessor,
                  ParamName paramName, boolean ignored, ParamPrefix paramPrefix,
                  ParamConverter paramConverter, boolean useJsonProperty) {
            this.element = element;
            this.name = name;
            this.type = type;
            this.accessor = accessor;
            this.paramName = paramName;
            this.ignored = ignored;
            this.paramPrefix = paramPrefix;
            this.paramConverter = paramConverter;
            this.useJsonProperty = useJsonProperty;
        }
    }

    private static class MethodInfo {
        final String methodName;
        final TypeMirror returnType;
        final ExecutableElement method;

        MethodInfo(String methodName, TypeMirror returnType, ExecutableElement method) {
            this.methodName = methodName;
            this.returnType = returnType;
            this.method = method;
        }
    }
}
