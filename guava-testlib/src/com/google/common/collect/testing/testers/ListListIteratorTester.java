/*
 * Copyright (C) 2007 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.common.collect.testing.testers;

import static com.google.common.collect.testing.IteratorFeature.MODIFIABLE;
import static com.google.common.collect.testing.IteratorFeature.UNMODIFIABLE;
import static com.google.common.collect.testing.features.CollectionFeature.SUPPORTS_REMOVE;
import static com.google.common.collect.testing.features.ListFeature.SUPPORTS_ADD_WITH_INDEX;
import static com.google.common.collect.testing.features.ListFeature.SUPPORTS_SET;
import static com.google.common.collect.testing.testers.Platform.listListIteratorTesterNumIterations;
import static java.util.Collections.singleton;

import com.google.common.annotations.GwtCompatible;
import com.google.common.collect.testing.Helpers;
import com.google.common.collect.testing.IteratorFeature;
import com.google.common.collect.testing.ListIteratorTester;
import com.google.common.collect.testing.features.CollectionFeature;
import com.google.common.collect.testing.features.ListFeature;

import java.lang.reflect.Method;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * A generic JUnit test which tests {@code listIterator} operations on a list.
 * Can't be invoked directly; please see
 * {@link com.google.common.collect.testing.ListTestSuiteBuilder}.
 *
 * <p>This class is GWT compatible.
 *
 * @author Chris Povirk
 * @author Kevin Bourrillion
 */
@GwtCompatible
public class ListListIteratorTester<E> extends AbstractListTester<E> {
  // TODO: switch to DerivedIteratorTestSuiteBuilder

  @CollectionFeature.Require(absent = SUPPORTS_REMOVE)
  @ListFeature.Require(absent = {SUPPORTS_SET, SUPPORTS_ADD_WITH_INDEX})
  public void testListIterator_unmodifiable() {
    runListIteratorTest(UNMODIFIABLE);
  }

  /*
   * For now, we don't cope with testing this when the list supports only some
   * modification operations.
   */
  @CollectionFeature.Require(SUPPORTS_REMOVE)
  @ListFeature.Require({SUPPORTS_SET, SUPPORTS_ADD_WITH_INDEX})
  public void testListIterator_fullyModifiable() {
    runListIteratorTest(MODIFIABLE);
  }

  private void runListIteratorTest(Set<IteratorFeature> features) {
    new ListIteratorTester<E>(
        listListIteratorTesterNumIterations(), singleton(samples.e4), features,
        Helpers.copyToList(getSampleElements()), 0) {
      {
        // TODO: don't set this universally
        stopTestingWhenAddThrowsException();
      }

      @Override protected ListIterator<E> newTargetIterator() {
        resetCollection();
        return getList().listIterator();
      }

      @Override protected void verify(List<E> elements) {
        expectContents(elements);
      }
    }.test();
  }

  public void testListIterator_tooLow() {
    try {
      getList().listIterator(-1);
      fail();
    } catch (IndexOutOfBoundsException expected) {
    }
  }

  public void testListIterator_tooHigh() {
    try {
      getList().listIterator(getNumElements() + 1);
      fail();
    } catch (IndexOutOfBoundsException expected) {
    }
  }

  public void testListIterator_atSize() {
    getList().listIterator(getNumElements());
    // TODO: run the iterator through ListIteratorTester
  }

  /**
   * Returns the {@link Method} instance for
   * {@link #testListIterator_fullyModifiable()} so that tests of
   * {@link CopyOnWriteArraySet} can suppress it with
   * {@code FeatureSpecificTestSuiteBuilder.suppressing()} until <a
   * href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6570575">Sun bug
   * 6570575</a> is fixed.
   */
  public static Method getListIteratorFullyModifiableMethod() {
    return Platform.getMethod(
        ListListIteratorTester.class, "testListIterator_fullyModifiable");
  }

  /**
   * Returns the {@link Method} instance for
   * {@link #testListIterator_unmodifiable()} so that it can be suppressed in
   * GWT tests.
   */
  public static Method getListIteratorUnmodifiableMethod() {
    return Platform.getMethod(
        ListListIteratorTester.class, "testListIterator_unmodifiable");
  }
}
