/*
 * Copyright 2015 The original authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.sundr.dsl.internal.element.functions;

import java.util.Set;

import javax.lang.model.util.Elements;

import io.sundr.dsl.annotations.All;
import io.sundr.dsl.internal.element.functions.filter.RequiresAllFilter;
import io.sundr.dsl.internal.element.functions.filter.TransitionFilter;

public class ToRequiresAll extends KeywordsAndMethodsToFilter {

  public ToRequiresAll(Elements elements) {
    super(elements, All.class.getCanonicalName());
  }

  @Override
  public TransitionFilter create(Set<String> classes, Set<String> keywords, Set<String> methods) {
    return new RequiresAllFilter(classes, keywords, methods);
  }
}
