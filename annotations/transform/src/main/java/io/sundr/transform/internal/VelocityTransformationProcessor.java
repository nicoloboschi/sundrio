/*
 *      Copyright 2018 The original authors.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 */

package io.sundr.transform.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import io.sundr.adapter.api.Adapters;
import io.sundr.adapter.apt.AptContext;
import io.sundr.adapter.apt.utils.Apt;
import io.sundr.codegen.annotations.AnnotationSelector;
import io.sundr.codegen.annotations.PackageSelector;
import io.sundr.codegen.annotations.ResourceSelector;
import io.sundr.codegen.processor.JavaGeneratingProcessor;
import io.sundr.model.TypeDef;
import io.sundr.model.TypeDefBuilder;
import io.sundr.model.repo.DefinitionRepository;
import io.sundr.transform.annotations.VelocityTransformation;
import io.sundr.transform.annotations.VelocityTransformations;
import io.sundr.utils.Strings;

@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes({ "io.sundr.transform.annotations.VelocityTransformation",
    "io.sundr.transform.annotations.VelocityTransformations" })
public class VelocityTransformationProcessor extends JavaGeneratingProcessor {

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {
    Elements elements = processingEnv.getElementUtils();
    Types types = processingEnv.getTypeUtils();
    Filer filer = processingEnv.getFiler();
    AptContext aptContext = AptContext.create(elements, types, DefinitionRepository.getRepository());

    Map<VelocityTransformation, Map<String, TypeDef>> annotatedTypes = new HashMap<>();
    for (TypeElement typeElement : annotations) {
      for (Element element : env.getElementsAnnotatedWith(typeElement)) {
        VelocityTransformations transformations = element.getAnnotation(VelocityTransformations.class);
        VelocityTransformation transformation = element.getAnnotation(VelocityTransformation.class);

        List<VelocityTransformation> all = new ArrayList<>();
        if (transformation != null) {
          all.add(transformation);
        }

        if (transformations != null) {
          for (VelocityTransformation t : transformations.value()) {
            all.add(t);
          }
        }

        TypeDef def = new TypeDefBuilder(Adapters.adaptType(Apt.getClassElement(element), aptContext))
            .build();

        for (VelocityTransformation t : all) {
          if (!annotatedTypes.containsKey(t)) {
            annotatedTypes.put(t, new HashMap<>());
          }

          //If there are no selectors processes the annotated class
          if (transformations == null) {
            annotatedTypes.get(t).put(def.getFullyQualifiedName(), def);
          } else if (transformations.annotations().length > 0) {
            for (AnnotationSelector selector : transformations.annotations()) {
              selectAnnotated(env, types, selector, annotatedTypes.get(t));
            }
          } else if (transformations.packages().length > 0) {

            for (PackageSelector selector : transformations.packages()) {
              selectPackages(elements, selector, annotatedTypes.get(t));
            }
          } else if (transformations.resources().length > 0) {
            for (ResourceSelector selector : transformations.resources()) {
              selectFromResource(elements, filer, selector, annotatedTypes.get(t));
            }

          } else {
            annotatedTypes.get(t).put(def.getFullyQualifiedName(), def);
          }
        }
      }

      for (Map.Entry<VelocityTransformation, Map<String, TypeDef>> entry : annotatedTypes.entrySet()) {
        VelocityTransformation transformation = entry.getKey();
        Map<String, TypeDef> annotated = entry.getValue();
        try {
          if (transformation.gather()) {
            generateFromStringTemplate(annotated, transformation.outputPath(),
                readTemplate(filer, null, transformation.value()));
          } else {
            for (TypeDef typeDef : annotated.values()) {
              generateFromStringTemplate(typeDef, transformation.paremters(), transformation.outputPath(),
                  readTemplate(filer, typeDef.getPackageName(), transformation.value()));
            }
          }
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }
    return false;
  }

  public void selectFromResource(Elements elements, Filer filer, ResourceSelector selector, Map<String, TypeDef> definitions) {
    try {
      FileObject fileObject = filer.getResource(StandardLocation.CLASS_PATH, "", selector.value());
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(fileObject.openInputStream()))) {
        List<String> lines = reader.lines().map(String::trim).filter(l -> !Strings.isNullOrEmpty(l))
            .collect(Collectors.toList());
        AptContext aptContext = AptContext.getContext();
        Map<String, TypeDef> map = lines.stream()
            .map(l -> elements.getTypeElement(l))
            .filter(e -> e instanceof TypeElement)
            .map(e -> Adapters.adaptType(e, aptContext))
            .collect(Collectors.toMap(e -> e.getFullyQualifiedName(), e -> e));

        definitions.putAll(map);
      }

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void selectAnnotated(RoundEnvironment env, Types types, AnnotationSelector selector,
      Map<String, TypeDef> definitions) {
    for (Object o : env.getElementsAnnotatedWith((TypeElement) types.asElement(annotationMirror(selector)))) {
      if (o instanceof TypeElement) {
        TypeDef typeDef = new TypeDefBuilder(Adapters.adaptType(Apt.getClassElement((Element) o), AptContext.getContext()))
            .build();
        definitions.put(typeDef.getFullyQualifiedName(), typeDef);
      }
    }
  }

  public void selectPackages(Elements elements, PackageSelector selector, Map<String, TypeDef> definitions) {
    Pattern pattern = Pattern.compile(selector.pattern());
    PackageElement packageElement = elements.getPackageElement(selector.value());
    List<TypeElement> typeElements = new ArrayList<>();

    if (packageElement != null) {
      for (Element e : packageElement.getEnclosedElements()) {
        if (e instanceof TypeElement) {
          typeElements.add((TypeElement) e);
        }
      }
    } else {
      TypeElement e = elements.getTypeElement(selector.value());
      if (e != null) {
        typeElements.add(e);
      }
    }

    for (TypeElement typeElement : typeElements) {
      TypeDef typeDef = new TypeDefBuilder(Adapters.adaptType(Apt.getClassElement(typeElement), AptContext.getContext()))
          .build();

      Matcher m = pattern.matcher(typeDef.getName());
      if (m.matches()) {
        definitions.put(typeDef.getFullyQualifiedName(), typeDef);
      }
    }
  }

  private TypeMirror annotationMirror(AnnotationSelector selector) {
    try {
      selector.value();
      return null;
    } catch (MirroredTypeException m) {
      return m.getTypeMirror();
    }
  }

  private String readTemplate(Filer filer, String pkg, String template) throws IOException {
    FileObject o;
    if (template == null) {
      throw new IllegalArgumentException("Template in:" + VelocityTransformation.class.getName() + " cannot be null.");
    }

    String targetPkg = pkg == null || (template != null && template.startsWith("/"))
        ? ""
        : pkg;

    String targetTemplate = template.startsWith("/") ? template.substring(1) : template;

    try {
      o = filer.getResource(StandardLocation.SOURCE_PATH, targetPkg, targetTemplate);
    } catch (IOException e) {
      try {
        o = filer.getResource(StandardLocation.CLASS_PATH, targetPkg, targetTemplate);
      } catch (IOException ex) {
        throw e;
      }
    }
    if (o == null) {
      throw new IOException("Template resource: " + template + " couldn't be found in sources or classpath.");
    }
    return o.getCharContent(false).toString();
  }

}
