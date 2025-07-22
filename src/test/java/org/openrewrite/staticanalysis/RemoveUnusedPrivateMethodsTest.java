/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.staticanalysis;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RemoveUnusedPrivateMethodsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveUnusedPrivateMethods())
          .parser(JavaParser.fromJavaVersion().classpath("junit-jupiter-params"));
    }

    @DocumentExample
    @Test
    void removeUnusedPrivateMethods() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  private void unused() {
                  }

                  public void dontRemove() {
                      dontRemove2();
                  }

                  private void dontRemove2() {
                  }
              }
              """,
            """
              class Test {

                  public void dontRemove() {
                      dontRemove2();
                  }

                  private void dontRemove2() {
                  }
              }
              """
          )
        );
    }

    @Test
    void removeUnusedPrivateMethodsChainedUsage() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  private void unused() {
                      unused2();
                  }

                  private void unused2() {
                  }

                  public void dontRemove() {
                      dontRemove2();
                  }

                  private void dontRemove2() {
                  }
              }
              """,
            """
              class Test {

                  public void dontRemove() {
                      dontRemove2();
                  }

                  private void dontRemove2() {
                  }
              }
              """
          )
        );
    }

    @SuppressWarnings("MissingSerialAnnotation")
    @Test
    void doNotRemoveCustomizedSerialization() {
        rewriteRun(
          //language=java
          java(
            """
              class Test implements java.io.Serializable {
                  private void writeObject(java.io.ObjectOutputStream out) {}
                  private void readObject(java.io.ObjectInputStream in) {}
                  private void readObjectNoData() {}
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveMethodsWithAnnotations() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.params.provider.MethodSource;
              import java.util.stream.Stream;

              class Test {
                  @MethodSource("sourceExample")
                  void test(String input) {
                  }
                  private Stream<Object> sourceExample() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1536")
    @Test
    void privateMethodWithBoundedGenericTypes() {
        rewriteRun(
          //language=java
          java(
            """
              public class TestClass {
                  void method() {
                      checkMethodInUse("String", "String");
                  }

                  private static void checkMethodInUse(String arg0, String arg1) {
                  }

                  private static <T> void checkMethodInUse(String arg0, T arg1) {
                  }
              }
              """
          )
        );
    }

    @Test
    void removeMethodsOnNestedClass() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.stream.Stream;

              class Test {
                  void test(String input) {
                  }
                  private Stream<Object> unused() {
                      return null;
                  }

                  class InnerTest {
                      void test(String input) {
                      }
                      private Stream<Object> unused() {
                          return null;
                      }
                  }
              }
              """,
            """
              class Test {
                  void test(String input) {
                  }

                  class InnerTest {
                      void test(String input) {
                      }
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/4076")
    @Test
    void doNotRemoveMethodsWithUnusedSuppressWarningsOnClass() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.stream.Stream;

              @SuppressWarnings("unused")
              class Test {
                  void test(String input) {
                  }
                  private Stream<Object> unused() {
                      return null;
                  }
                  private Stream<Object> anotherUnused() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/4076")
    @Test
    void doNotRemoveMethodsWithUnusedSuppressWarningsOnClassNestedClass() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.stream.Stream;

              @SuppressWarnings("unused")
              class Test {
                  void test(String input) {
                  }
                  private Stream<Object> unused() {
                      return null;
                  }

                  class InnerTest {
                      void test(String input) {
                      }
                      private Stream<Object> unused() {
                          return null;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void falsePositiveOnSearchAfterIT() {
        rewriteRun(
          //language=java
          java(
            """
              /*
               * SPDX-License-Identifier: Apache-2.0
               *
               * The OpenSearch Contributors require contributions made to
               * this file be licensed under the Apache-2.0 license or a
               * compatible open source license.
               */

              /*
               * Licensed to Elasticsearch under one or more contributor
               * license agreements. See the NOTICE file distributed with
               * this work for additional information regarding copyright
               * ownership. Elasticsearch licenses this file to you under
               * the Apache License, Version 2.0 (the "License"); you may
               * not use this file except in compliance with the License.
               * You may obtain a copy of the License at
               *
               *    http://www.apache.org/licenses/LICENSE-2.0
               *
               * Unless required by applicable law or agreed to in writing,
               * software distributed under the License is distributed on an
               * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
               * KIND, either express or implied.  See the License for the
               * specific language governing permissions and limitations
               * under the License.
               */

              /*
               * Modifications Copyright OpenSearch Contributors. See
               * GitHub history for details.
               */

              package org.opensearch.search.searchafter;

              import com.carrotsearch.randomizedtesting.annotations.ParametersFactory;

              import org.opensearch.action.admin.indices.create.CreateIndexRequestBuilder;
              import org.opensearch.action.index.IndexRequestBuilder;
              import org.opensearch.action.search.CreatePitAction;
              import org.opensearch.action.search.CreatePitRequest;
              import org.opensearch.action.search.CreatePitResponse;
              import org.opensearch.action.search.SearchPhaseExecutionException;
              import org.opensearch.action.search.SearchRequestBuilder;
              import org.opensearch.action.search.SearchResponse;
              import org.opensearch.action.search.ShardSearchFailure;
              import org.opensearch.common.UUIDs;
              import org.opensearch.common.action.ActionFuture;
              import org.opensearch.common.settings.Settings;
              import org.opensearch.common.unit.TimeValue;
              import org.opensearch.core.xcontent.XContentBuilder;
              import org.opensearch.search.SearchHit;
              import org.opensearch.search.builder.PointInTimeBuilder;
              import org.opensearch.search.sort.SortOrder;
              import org.opensearch.test.ParameterizedStaticSettingsOpenSearchIntegTestCase;
              import org.hamcrest.Matchers;

              import java.util.ArrayList;
              import java.util.Arrays;
              import java.util.Collection;
              import java.util.Collections;
              import java.util.Comparator;
              import java.util.List;

              import static org.opensearch.common.xcontent.XContentFactory.jsonBuilder;
              import static org.opensearch.index.query.QueryBuilders.matchAllQuery;
              import static org.opensearch.search.SearchService.CLUSTER_CONCURRENT_SEGMENT_SEARCH_SETTING;
              import static org.opensearch.test.hamcrest.OpenSearchAssertions.assertAcked;
              import static org.hamcrest.Matchers.containsString;
              import static org.hamcrest.Matchers.equalTo;

              public class SearchAfterIT extends ParameterizedStaticSettingsOpenSearchIntegTestCase {
                  private static final String INDEX_NAME = "test";
                  private static final int NUM_DOCS = 100;

                  public SearchAfterIT(Settings settings) {
                      super(settings);
                  }

                  @ParametersFactory
                  public static Collection<Object[]> parameters() {
                      return Arrays.asList(
                          new Object[] { Settings.builder().put(CLUSTER_CONCURRENT_SEGMENT_SEARCH_SETTING.getKey(), false).build() },
                          new Object[] { Settings.builder().put(CLUSTER_CONCURRENT_SEGMENT_SEARCH_SETTING.getKey(), true).build() }
                      );
                  }

                  public void testsShouldFail() throws Exception {
                      assertAcked(client().admin().indices().prepareCreate("test").setMapping("field1", "type=long", "field2", "type=keyword").get());
                      ensureGreen();
                      indexRandom(true, client().prepareIndex("test").setId("0").setSource("field1", 0, "field2", "toto"));
                      {
                          SearchPhaseExecutionException e = expectThrows(
                              SearchPhaseExecutionException.class,
                              () -> client().prepareSearch("test")
                                  .addSort("field1", SortOrder.ASC)
                                  .setQuery(matchAllQuery())
                                  .searchAfter(new Object[] { 0 })
                                  .setScroll("1m")
                                  .get()
                          );
                          assertTrue(e.shardFailures().length > 0);
                          for (ShardSearchFailure failure : e.shardFailures()) {
                              assertThat(failure.toString(), containsString("`search_after` cannot be used in a scroll context."));
                          }
                      }

                      {
                          SearchPhaseExecutionException e = expectThrows(
                              SearchPhaseExecutionException.class,
                              () -> client().prepareSearch("test")
                                  .addSort("field1", SortOrder.ASC)
                                  .setQuery(matchAllQuery())
                                  .searchAfter(new Object[] { 0 })
                                  .setFrom(10)
                                  .get()
                          );
                          assertTrue(e.shardFailures().length > 0);
                          for (ShardSearchFailure failure : e.shardFailures()) {
                              assertThat(failure.toString(), containsString("`from` parameter must be set to 0 when `search_after` is used."));
                          }
                      }

                      {
                          SearchPhaseExecutionException e = expectThrows(
                              SearchPhaseExecutionException.class,
                              () -> client().prepareSearch("test").setQuery(matchAllQuery()).searchAfter(new Object[] { 0.75f }).get()
                          );
                          assertTrue(e.shardFailures().length > 0);
                          for (ShardSearchFailure failure : e.shardFailures()) {
                              assertThat(failure.toString(), containsString("Sort must contain at least one field."));
                          }
                      }

                      {
                          SearchPhaseExecutionException e = expectThrows(
                              SearchPhaseExecutionException.class,
                              () -> client().prepareSearch("test")
                                  .addSort("field2", SortOrder.DESC)
                                  .addSort("field1", SortOrder.ASC)
                                  .setQuery(matchAllQuery())
                                  .searchAfter(new Object[] { 1 })
                                  .get()
                          );
                          assertTrue(e.shardFailures().length > 0);
                          for (ShardSearchFailure failure : e.shardFailures()) {
                              assertThat(failure.toString(), containsString("search_after has 1 value(s) but sort has 2."));
                          }
                      }

                      {
                          SearchPhaseExecutionException e = expectThrows(
                              SearchPhaseExecutionException.class,
                              () -> client().prepareSearch("test")
                                  .setQuery(matchAllQuery())
                                  .addSort("field1", SortOrder.ASC)
                                  .searchAfter(new Object[] { 1, 2 })
                                  .get()
                          );
                          for (ShardSearchFailure failure : e.shardFailures()) {
                              assertTrue(e.shardFailures().length > 0);
                              assertThat(failure.toString(), containsString("search_after has 2 value(s) but sort has 1."));
                          }
                      }

                      {
                          SearchPhaseExecutionException e = expectThrows(
                              SearchPhaseExecutionException.class,
                              () -> client().prepareSearch("test")
                                  .setQuery(matchAllQuery())
                                  .addSort("field1", SortOrder.ASC)
                                  .searchAfter(new Object[] { "toto" })
                                  .get()
                          );
                          assertTrue(e.shardFailures().length > 0);
                          for (ShardSearchFailure failure : e.shardFailures()) {
                              assertThat(failure.toString(), containsString("Failed to parse search_after value for field [field1]."));
                          }
                      }
                  }

                  public void testPitWithSearchAfter() throws Exception {
                      assertAcked(client().admin().indices().prepareCreate("test").setMapping("field1", "type=long", "field2", "type=keyword").get());
                      ensureGreen();
                      indexRandom(
                          true,
                          client().prepareIndex("test").setId("0").setSource("field1", 0),
                          client().prepareIndex("test").setId("1").setSource("field1", 100, "field2", "toto"),
                          client().prepareIndex("test").setId("2").setSource("field1", 101),
                          client().prepareIndex("test").setId("3").setSource("field1", 99)
                      );

                      CreatePitRequest request = new CreatePitRequest(TimeValue.timeValueDays(1), true);
                      request.setIndices(new String[] { "test" });
                      ActionFuture<CreatePitResponse> execute = client().execute(CreatePitAction.INSTANCE, request);
                      CreatePitResponse pitResponse = execute.get();
                      SearchResponse sr = client().prepareSearch()
                          .addSort("field1", SortOrder.ASC)
                          .setQuery(matchAllQuery())
                          .searchAfter(new Object[] { 99 })
                          .setPointInTime(new PointInTimeBuilder(pitResponse.getId()))
                          .get();
                      assertEquals(2, sr.getHits().getHits().length);
                      sr = client().prepareSearch()
                          .addSort("field1", SortOrder.ASC)
                          .setQuery(matchAllQuery())
                          .searchAfter(new Object[] { 100 })
                          .setPointInTime(new PointInTimeBuilder(pitResponse.getId()))
                          .get();
                      assertEquals(1, sr.getHits().getHits().length);
                      sr = client().prepareSearch()
                          .addSort("field1", SortOrder.ASC)
                          .setQuery(matchAllQuery())
                          .searchAfter(new Object[] { 0 })
                          .setPointInTime(new PointInTimeBuilder(pitResponse.getId()))
                          .get();
                      assertEquals(3, sr.getHits().getHits().length);
                      /*
                        Add new data and assert PIT results remain the same and normal search results gets refreshed
                       */
                      indexRandom(true, client().prepareIndex("test").setId("4").setSource("field1", 102));
                      sr = client().prepareSearch()
                          .addSort("field1", SortOrder.ASC)
                          .setQuery(matchAllQuery())
                          .searchAfter(new Object[] { 0 })
                          .setPointInTime(new PointInTimeBuilder(pitResponse.getId()))
                          .get();
                      assertEquals(3, sr.getHits().getHits().length);
                      sr = client().prepareSearch().addSort("field1", SortOrder.ASC).setQuery(matchAllQuery()).searchAfter(new Object[] { 0 }).get();
                      assertEquals(4, sr.getHits().getHits().length);
                      client().admin().indices().prepareDelete("test").get();
                  }

                  public void testWithNullStrings() throws InterruptedException {
                      assertAcked(client().admin().indices().prepareCreate("test").setMapping("field2", "type=keyword").get());
                      ensureGreen();
                      indexRandom(
                          true,
                          client().prepareIndex("test").setId("0").setSource("field1", 0),
                          client().prepareIndex("test").setId("1").setSource("field1", 100, "field2", "toto")
                      );
                      SearchResponse searchResponse = client().prepareSearch("test")
                          .addSort("field1", SortOrder.ASC)
                          .addSort("field2", SortOrder.ASC)
                          .setQuery(matchAllQuery())
                          .searchAfter(new Object[] { 0, null })
                          .get();
                      assertThat(searchResponse.getHits().getTotalHits().value(), Matchers.equalTo(2L));
                      assertThat(searchResponse.getHits().getHits().length, Matchers.equalTo(1));
                      assertThat(searchResponse.getHits().getHits()[0].getSourceAsMap().get("field1"), Matchers.equalTo(100));
                      assertThat(searchResponse.getHits().getHits()[0].getSourceAsMap().get("field2"), Matchers.equalTo("toto"));
                  }

                  public void testWithSimpleTypes() throws Exception {
                      int numFields = randomInt(20) + 1;
                      int[] types = new int[numFields - 1];
                      for (int i = 0; i < numFields - 1; i++) {
                          types[i] = randomInt(6);
                      }
                      List<List> documents = new ArrayList<>();
                      for (int i = 0; i < NUM_DOCS; i++) {
                          List values = new ArrayList<>();
                          for (int type : types) {
                              switch (type) {
                                  case 0:
                                      values.add(randomBoolean());
                                      break;
                                  case 1:
                                      values.add(randomByte());
                                      break;
                                  case 2:
                                      values.add(randomShort());
                                      break;
                                  case 3:
                                      values.add(randomInt());
                                      break;
                                  case 4:
                                      values.add(randomFloat());
                                      break;
                                  case 5:
                                      values.add(randomDouble());
                                      break;
                                  case 6:
                                      values.add(randomAlphaOfLengthBetween(5, 20));
                                      break;
                              }
                          }
                          values.add(UUIDs.randomBase64UUID());
                          documents.add(values);
                      }
                      int reqSize = randomInt(NUM_DOCS - 1);
                      if (reqSize == 0) {
                          reqSize = 1;
                      }
                      assertSearchFromWithSortValues(INDEX_NAME, documents, reqSize);
                  }

                  private static class ListComparator implements Comparator<List> {
                      @Override
                      public int compare(List o1, List o2) {
                          if (o1.size() > o2.size()) {
                              return 1;
                          }

                          if (o2.size() > o1.size()) {
                              return -1;
                          }

                          for (int i = 0; i < o1.size(); i++) {
                              if (!(o1.get(i) instanceof Comparable)) {
                                  throw new RuntimeException(o1.get(i).getClass() + " is not comparable");
                              }
                              Object cmp1 = o1.get(i);
                              Object cmp2 = o2.get(i);
                              int cmp = ((Comparable) cmp1).compareTo(cmp2);
                              if (cmp != 0) {
                                  return cmp;
                              }
                          }
                          return 0;
                      }
                  }

                  private ListComparator LST_COMPARATOR = new ListComparator();

                  private void assertSearchFromWithSortValues(String indexName, List<List> documents, int reqSize) throws Exception {
                      int numFields = documents.get(0).size();
                      {
                          createIndexMappingsFromObjectType(indexName, documents.get(0));
                          List<IndexRequestBuilder> requests = new ArrayList<>();
                          for (int i = 0; i < documents.size(); i++) {
                              XContentBuilder builder = jsonBuilder();
                              assertThat(documents.get(i).size(), Matchers.equalTo(numFields));
                              builder.startObject();
                              for (int j = 0; j < numFields; j++) {
                                  builder.field("field" + Integer.toString(j), documents.get(i).get(j));
                              }
                              builder.endObject();
                              requests.add(client().prepareIndex(INDEX_NAME).setId(Integer.toString(i)).setSource(builder));
                          }
                          indexRandom(true, requests);
                      }

                      Collections.sort(documents, LST_COMPARATOR);
                      int offset = 0;
                      Object[] sortValues = null;
                      while (offset < documents.size()) {
                          SearchRequestBuilder req = client().prepareSearch(indexName);
                          for (int i = 0; i < documents.get(0).size(); i++) {
                              req.addSort("field" + Integer.toString(i), SortOrder.ASC);
                          }
                          req.setQuery(matchAllQuery()).setSize(reqSize);
                          if (sortValues != null) {
                              req.searchAfter(sortValues);
                          }
                          SearchResponse searchResponse = req.get();
                          for (SearchHit hit : searchResponse.getHits()) {
                              List toCompare = convertSortValues(documents.get(offset++));
                              assertThat(LST_COMPARATOR.compare(toCompare, Arrays.asList(hit.getSortValues())), equalTo(0));
                          }
                          sortValues = searchResponse.getHits().getHits()[searchResponse.getHits().getHits().length - 1].getSortValues();
                      }
                  }

                  private void createIndexMappingsFromObjectType(String indexName, List<Object> types) {
                      CreateIndexRequestBuilder indexRequestBuilder = client().admin().indices().prepareCreate(indexName);
                      List<String> mappings = new ArrayList<>();
                      int numFields = types.size();
                      for (int i = 0; i < numFields; i++) {
                          Class type = types.get(i).getClass();
                          if (type == Integer.class) {
                              mappings.add("field" + Integer.toString(i));
                              mappings.add("type=integer");
                          } else if (type == Long.class) {
                              mappings.add("field" + Integer.toString(i));
                              mappings.add("type=long");
                          } else if (type == Float.class) {
                              mappings.add("field" + Integer.toString(i));
                              mappings.add("type=float");
                          } else if (type == Double.class) {
                              mappings.add("field" + Integer.toString(i));
                              mappings.add("type=double");
                          } else if (type == Byte.class) {
                              mappings.add("field" + Integer.toString(i));
                              mappings.add("type=byte");
                          } else if (type == Short.class) {
                              mappings.add("field" + Integer.toString(i));
                              mappings.add("type=short");
                          } else if (type == Boolean.class) {
                              mappings.add("field" + Integer.toString(i));
                              mappings.add("type=boolean");
                          } else if (types.get(i) instanceof String) {
                              mappings.add("field" + Integer.toString(i));
                              mappings.add("type=keyword");
                          } else {
                              fail("Can't match type [" + type + "]");
                          }
                      }
                      indexRequestBuilder.setMapping(mappings.toArray(new String[0])).get();
                      ensureGreen();
                  }

                  // Convert Integer, Short, Byte and Boolean to Int in order to match the conversion done
                  // by the internal hits when populating the sort values.
                  private List<Object> convertSortValues(List<Object> sortValues) {
                      List<Object> converted = new ArrayList<>();
                      for (int i = 0; i < sortValues.size(); i++) {
                          Object from = sortValues.get(i);
                          if (from instanceof Short) {
                              converted.add(((Short) from).intValue());
                          } else if (from instanceof Byte) {
                              converted.add(((Byte) from).intValue());
                          } else if (from instanceof Boolean) {
                              boolean b = (boolean) from;
                              if (b) {
                                  converted.add(1);
                              } else {
                                  converted.add(0);
                              }
                          } else {
                              converted.add(from);
                          }
                      }
                      return converted;
                  }
              }
              """
          )
        );
    }

    @Test
    void falsePositiveOnStarTreeMapper() {
        rewriteRun(
          //language=java
          java(
            """
              /*
               * SPDX-License-Identifier: Apache-2.0
               *
               * The OpenSearch Contributors require contributions made to
               * this file be licensed under the Apache-2.0 license or a
               * compatible open source license.
               */

              package org.opensearch.index.mapper;

              import org.apache.lucene.search.Query;
              import org.opensearch.common.annotation.ExperimentalApi;
              import org.opensearch.common.xcontent.support.XContentMapValues;
              import org.opensearch.index.compositeindex.datacube.DateDimension;
              import org.opensearch.index.compositeindex.datacube.Dimension;
              import org.opensearch.index.compositeindex.datacube.DimensionFactory;
              import org.opensearch.index.compositeindex.datacube.Metric;
              import org.opensearch.index.compositeindex.datacube.MetricStat;
              import org.opensearch.index.compositeindex.datacube.startree.StarTreeField;
              import org.opensearch.index.compositeindex.datacube.startree.StarTreeFieldConfiguration;
              import org.opensearch.index.compositeindex.datacube.startree.StarTreeIndexSettings;
              import org.opensearch.index.query.QueryShardContext;
              import org.opensearch.search.lookup.SearchLookup;

              import java.util.ArrayList;
              import java.util.Collections;
              import java.util.HashSet;
              import java.util.LinkedHashSet;
              import java.util.LinkedList;
              import java.util.List;
              import java.util.Locale;
              import java.util.Map;
              import java.util.Optional;
              import java.util.Queue;
              import java.util.Set;
              import java.util.stream.Collectors;

              /**
               * A field mapper for star tree fields
               *
               * @opensearch.experimental
               */
              @ExperimentalApi
              public class StarTreeMapper extends ParametrizedFieldMapper {
                  public static final String CONTENT_TYPE = "star_tree";
                  public static final String CONFIG = "config";
                  public static final String MAX_LEAF_DOCS = "max_leaf_docs";
                  public static final String SKIP_STAR_NODE_IN_DIMS = "skip_star_node_creation_for_dimensions";
                  public static final String ORDERED_DIMENSIONS = "ordered_dimensions";
                  public static final String DATE_DIMENSION = "date_dimension";
                  public static final String METRICS = "metrics";
                  public static final String STATS = "stats";

                  @Override
                  public ParametrizedFieldMapper.Builder getMergeBuilder() {
                      return new Builder(simpleName(), objBuilder).init(this);

                  }

                  /**
                   * Builder for the star tree field mapper
                   *
                   * @opensearch.internal
                   */
                  public static class Builder extends ParametrizedFieldMapper.Builder {
                      private ObjectMapper.Builder objbuilder;

                      @SuppressWarnings("unchecked")
                      private final Parameter<StarTreeField> config = new Parameter<>(CONFIG, false, () -> null, (name, context, nodeObj) -> {
                          if (nodeObj instanceof Map<?, ?>) {
                              Map<String, Object> paramMap = (Map<String, Object>) nodeObj;
                              int maxLeafDocs = XContentMapValues.nodeIntegerValue(
                                  paramMap.get(MAX_LEAF_DOCS),
                                  StarTreeIndexSettings.STAR_TREE_DEFAULT_MAX_LEAF_DOCS.get(context.getSettings())
                              );
                              if (maxLeafDocs < 1) {
                                  throw new IllegalArgumentException(
                                      String.format(Locale.ROOT, "%s [%s] must be greater than 0", MAX_LEAF_DOCS, maxLeafDocs)
                                  );
                              }
                              paramMap.remove(MAX_LEAF_DOCS);
                              Set<String> skipStarInDims = new LinkedHashSet<>(
                                  List.of(XContentMapValues.nodeStringArrayValue(paramMap.getOrDefault(SKIP_STAR_NODE_IN_DIMS, new ArrayList<String>())))
                              );
                              paramMap.remove(SKIP_STAR_NODE_IN_DIMS);
                              StarTreeFieldConfiguration.StarTreeBuildMode buildMode = StarTreeFieldConfiguration.StarTreeBuildMode.OFF_HEAP;

                              List<Dimension> dimensions = buildDimensions(name, paramMap, context);
                              paramMap.remove(DATE_DIMENSION);
                              paramMap.remove(ORDERED_DIMENSIONS);
                              List<Metric> metrics = buildMetrics(name, paramMap, context);
                              paramMap.remove(METRICS);
                              paramMap.remove(CompositeDataCubeFieldType.NAME);
                              for (String dim : skipStarInDims) {
                                  if (dimensions.stream().filter(d -> d.getField().equals(dim)).findAny().isEmpty()) {
                                      throw new IllegalArgumentException(
                                          String.format(
                                              Locale.ROOT,
                                              "[%s] in skip_star_node_creation_for_dimensions should be part of ordered_dimensions",
                                              dim
                                          )
                                      );
                                  }
                              }
                              StarTreeFieldConfiguration spec = new StarTreeFieldConfiguration(maxLeafDocs, skipStarInDims, buildMode);
                              DocumentMapperParser.checkNoRemainingFields(
                                  paramMap,
                                  context.indexVersionCreated(),
                                  "Star tree mapping definition has unsupported parameters: "
                              );
                              return new StarTreeField(this.name, dimensions, metrics, spec);

                          } else {
                              throw new IllegalArgumentException(
                                  String.format(Locale.ROOT, "unable to parse config for star tree field [%s]", this.name)
                              );
                          }
                      }, m -> toType(m).starTreeField);

                      /**
                       * Build dimensions from mapping
                       */
                      @SuppressWarnings("unchecked")
                      private List<Dimension> buildDimensions(String fieldName, Map<String, Object> map, Mapper.TypeParser.ParserContext context) {
                          List<Dimension> dimensions = new LinkedList<>();
                          DateDimension dateDim = buildDateDimension(fieldName, map, context);
                          if (dateDim != null) {
                              dimensions.add(dateDim);
                          }
                          Object dims = XContentMapValues.extractValue("ordered_dimensions", map);
                          if (dims == null) {
                              throw new IllegalArgumentException(
                                  String.format(Locale.ROOT, "ordered_dimensions is required for star tree field [%s]", fieldName)
                              );
                          }

                          if (dims instanceof List<?>) {
                              List<Object> orderedDimensionsList = (List<Object>) dims;
                              if (orderedDimensionsList.size() + dimensions.size() > context.getSettings()
                                  .getAsInt(
                                      StarTreeIndexSettings.STAR_TREE_MAX_DIMENSIONS_SETTING.getKey(),
                                      StarTreeIndexSettings.STAR_TREE_MAX_DIMENSIONS_DEFAULT
                                  )) {
                                  throw new IllegalArgumentException(
                                      String.format(
                                          Locale.ROOT,
                                          "ordered_dimensions cannot have more than %s dimensions for star tree field [%s]",
                                          context.getSettings()
                                              .getAsInt(
                                                  StarTreeIndexSettings.STAR_TREE_MAX_DIMENSIONS_SETTING.getKey(),
                                                  StarTreeIndexSettings.STAR_TREE_MAX_DIMENSIONS_DEFAULT
                                              ),
                                          fieldName
                                      )
                                  );
                              }
                              if (dimensions.size() + orderedDimensionsList.size() < 2) {
                                  throw new IllegalArgumentException(
                                      String.format(Locale.ROOT, "Atleast two dimensions are required to build star tree index field [%s]", fieldName)
                                  );
                              }
                              Set<String> dimensionFieldNames = new HashSet<>();
                              for (Object dim : orderedDimensionsList) {
                                  Dimension dimension = getDimension(fieldName, dim, context);
                                  if (dimensionFieldNames.add(dimension.getField()) == false) {
                                      throw new IllegalArgumentException(
                                          String.format(
                                              Locale.ROOT,
                                              "Duplicate dimension [%s] present as part star tree index field [%s]",
                                              dimension.getField(),
                                              fieldName
                                          )
                                      );
                                  }
                                  dimensions.add(dimension);
                              }
                          } else {
                              throw new MapperParsingException(
                                  String.format(Locale.ROOT, "unable to parse ordered_dimensions for star tree field [%s]", fieldName)
                              );
                          }
                          return dimensions;
                      }

                      private DateDimension buildDateDimension(String fieldName, Map<String, Object> map, Mapper.TypeParser.ParserContext context) {
                          Object dims = XContentMapValues.extractValue("date_dimension", map);
                          if (dims == null) {
                              return null;
                          }
                          return getDateDimension(fieldName, dims, context);
                      }

                      /**
                       * Get dimension based on mapping
                       */
                      @SuppressWarnings("unchecked")
                      private DateDimension getDateDimension(String fieldName, Object dimensionMapping, Mapper.TypeParser.ParserContext context) {
                          DateDimension dimension;
                          Map<String, Object> dimensionMap = (Map<String, Object>) dimensionMapping;
                          String name = (String) XContentMapValues.extractValue(CompositeDataCubeFieldType.NAME, dimensionMap);
                          dimensionMap.remove(CompositeDataCubeFieldType.NAME);
                          if (this.objbuilder == null || this.objbuilder.mappersBuilders == null) {
                              String type = (String) XContentMapValues.extractValue(CompositeDataCubeFieldType.TYPE, dimensionMap);
                              dimensionMap.remove(CompositeDataCubeFieldType.TYPE);
                              if (type == null || type.equals(DateDimension.DATE) == false) {
                                  throw new MapperParsingException(
                                      String.format(Locale.ROOT, "unable to parse date dimension for star tree field [%s]", fieldName)
                                  );
                              }
                              return (DateDimension) DimensionFactory.parseAndCreateDimension(name, type, dimensionMap, context);
                          } else {
                              Optional<Mapper.Builder> dimBuilder = findMapperBuilderByName(name, this.objbuilder.mappersBuilders);
                              if (dimBuilder.isEmpty()) {
                                  throw new IllegalArgumentException(String.format(Locale.ROOT, "unknown date dimension field [%s]", name));
                              }
                              if (dimBuilder.get() instanceof DateFieldMapper.Builder == false) {
                                  throw new IllegalArgumentException(
                                      String.format(Locale.ROOT, "date_dimension [%s] should be of type date for star tree field [%s]", name, fieldName)
                                  );
                              }
                              dimension = (DateDimension) DimensionFactory.parseAndCreateDimension(name, dimBuilder.get(), dimensionMap, context);
                          }
                          DocumentMapperParser.checkNoRemainingFields(
                              dimensionMap,
                              context.indexVersionCreated(),
                              "Star tree mapping definition has unsupported parameters: "
                          );
                          return dimension;
                      }

                      /**
                       * Get dimension based on mapping
                       */
                      @SuppressWarnings("unchecked")
                      private Dimension getDimension(String fieldName, Object dimensionMapping, Mapper.TypeParser.ParserContext context) {
                          Dimension dimension;
                          Map<String, Object> dimensionMap = (Map<String, Object>) dimensionMapping;
                          String name = (String) XContentMapValues.extractValue(CompositeDataCubeFieldType.NAME, dimensionMap);
                          dimensionMap.remove(CompositeDataCubeFieldType.NAME);
                          if (this.objbuilder == null || this.objbuilder.mappersBuilders == null) {
                              String type = (String) XContentMapValues.extractValue(CompositeDataCubeFieldType.TYPE, dimensionMap);
                              dimensionMap.remove(CompositeDataCubeFieldType.TYPE);
                              if (type == null) {
                                  throw new MapperParsingException(
                                      String.format(Locale.ROOT, "unable to parse ordered_dimensions for star tree field [%s]", fieldName)
                                  );
                              }
                              return DimensionFactory.parseAndCreateDimension(name, type, dimensionMap, context);
                          } else {
                              Optional<Mapper.Builder> dimBuilder = findMapperBuilderByName(name, this.objbuilder.mappersBuilders);
                              if (dimBuilder.isEmpty()) {
                                  throw new IllegalArgumentException(String.format(Locale.ROOT, "unknown dimension field [%s]", name));
                              }
                              if (!isBuilderAllowedForDimension(dimBuilder.get())) {
                                  throw new IllegalArgumentException(
                                      String.format(
                                          Locale.ROOT,
                                          "unsupported field type associated with dimension [%s] as part of star tree field [%s]",
                                          name,
                                          fieldName
                                      )
                                  );
                              }
                              dimension = DimensionFactory.parseAndCreateDimension(name, dimBuilder.get(), dimensionMap, context);
                          }
                          DocumentMapperParser.checkNoRemainingFields(
                              dimensionMap,
                              context.indexVersionCreated(),
                              "Star tree mapping definition has unsupported parameters: "
                          );
                          return dimension;
                      }

                      /**
                       * Build metrics from mapping
                       */
                      @SuppressWarnings("unchecked")
                      private List<Metric> buildMetrics(String fieldName, Map<String, Object> map, Mapper.TypeParser.ParserContext context) {
                          List<Metric> metrics = new LinkedList<>();
                          Object metricsFromInput = XContentMapValues.extractValue(METRICS, map);
                          if (metricsFromInput == null) {
                              throw new IllegalArgumentException(
                                  String.format(Locale.ROOT, "metrics section is required for star tree field [%s]", fieldName)
                              );
                          }
                          if (metricsFromInput instanceof List<?>) {
                              List<?> metricsList = (List<?>) metricsFromInput;
                              Set<String> metricFieldNames = new HashSet<>();
                              for (Object metric : metricsList) {
                                  Map<String, Object> metricMap = (Map<String, Object>) metric;
                                  String name = (String) XContentMapValues.extractValue(CompositeDataCubeFieldType.NAME, metricMap);
                                  // Handle _doc_count metric separately at the end
                                  if (name.equals(DocCountFieldMapper.NAME)) {
                                      continue;
                                  }
                                  metricMap.remove(CompositeDataCubeFieldType.NAME);
                                  if (objbuilder == null || objbuilder.mappersBuilders == null) {
                                      Metric metricFromParser = getMetric(name, metricMap, context);
                                      if (metricFieldNames.add(metricFromParser.getField()) == false) {
                                          throw new IllegalArgumentException(
                                              String.format(
                                                  Locale.ROOT,
                                                  "Duplicate metrics [%s] present as part star tree index field [%s]",
                                                  metricFromParser.getField(),
                                                  fieldName
                                              )
                                          );
                                      }
                                      metrics.add(metricFromParser);
                                  } else {
                                      Optional<Mapper.Builder> meticBuilder = findMapperBuilderByName(name, this.objbuilder.mappersBuilders);
                                      if (meticBuilder.isEmpty()) {
                                          throw new IllegalArgumentException(String.format(Locale.ROOT, "unknown metric field [%s]", name));
                                      }
                                      if (!isBuilderAllowedForMetric(meticBuilder.get())) {
                                          throw new IllegalArgumentException(
                                              String.format(Locale.ROOT, "non-numeric field type is associated with star tree metric [%s]", this.name)
                                          );
                                      }
                                      Metric metricFromParser = getMetric(name, metricMap, context);
                                      if (metricFieldNames.add(metricFromParser.getField()) == false) {
                                          throw new IllegalArgumentException(
                                              String.format(
                                                  Locale.ROOT,
                                                  "Duplicate metrics [%s] present as part star tree index field [%s]",
                                                  metricFromParser.getField(),
                                                  fieldName
                                              )
                                          );
                                      }
                                      metrics.add(metricFromParser);
                                      DocumentMapperParser.checkNoRemainingFields(
                                          metricMap,
                                          context.indexVersionCreated(),
                                          "Star tree mapping definition has unsupported parameters: "
                                      );
                                  }
                              }
                          } else {
                              throw new MapperParsingException(String.format(Locale.ROOT, "unable to parse metrics for star tree field [%s]", this.name));
                          }
                          int numBaseMetrics = 0;
                          for (Metric metric : metrics) {
                              numBaseMetrics += metric.getBaseMetrics().size();
                          }
                          if (numBaseMetrics > context.getSettings()
                              .getAsInt(
                                  StarTreeIndexSettings.STAR_TREE_MAX_BASE_METRICS_SETTING.getKey(),
                                  StarTreeIndexSettings.STAR_TREE_MAX_BASE_METRICS_DEFAULT
                              )) {
                              throw new IllegalArgumentException(
                                  String.format(
                                      Locale.ROOT,
                                      "There cannot be more than [%s] base metrics for star tree field [%s]",
                                      context.getSettings()
                                          .getAsInt(
                                              StarTreeIndexSettings.STAR_TREE_MAX_BASE_METRICS_SETTING.getKey(),
                                              StarTreeIndexSettings.STAR_TREE_MAX_BASE_METRICS_DEFAULT
                                          ),
                                      fieldName
                                  )
                              );
                          }
                          Metric docCountMetric = new Metric(DocCountFieldMapper.NAME, List.of(MetricStat.DOC_COUNT));
                          metrics.add(docCountMetric);
                          return metrics;
                      }

                      @SuppressWarnings("unchecked")
                      private Metric getMetric(String name, Map<String, Object> metric, Mapper.TypeParser.ParserContext context) {
                          List<MetricStat> metricTypes;
                          List<String> metricStrings = XContentMapValues.extractRawValues(STATS, metric)
                              .stream()
                              .map(Object::toString)
                              .collect(Collectors.toList());
                          metric.remove(STATS);
                          if (metricStrings.isEmpty()) {
                              metricStrings = new ArrayList<>(StarTreeIndexSettings.DEFAULT_METRICS_LIST.get(context.getSettings()));
                          }
                          // Add all required metrics initially
                          Set<MetricStat> metricSet = new LinkedHashSet<>();
                          for (String metricString : metricStrings) {
                              MetricStat metricStat = MetricStat.fromTypeName(metricString);
                              metricSet.add(metricStat);
                              addBaseMetrics(metricStat, metricSet);
                          }
                          addEligibleDerivedMetrics(metricSet);
                          metricTypes = new ArrayList<>(metricSet);
                          return new Metric(name, metricTypes);
                      }

                      /**
                       * Add base metrics of derived metric to metric set
                       */
                      private void addBaseMetrics(MetricStat metricStat, Set<MetricStat> metricSet) {
                          if (metricStat.isDerivedMetric()) {
                              Queue<MetricStat> metricQueue = new LinkedList<>(metricStat.getBaseMetrics());
                              while (metricQueue.isEmpty() == false) {
                                  MetricStat metric = metricQueue.poll();
                                  if (metric.isDerivedMetric() && !metricSet.contains(metric)) {
                                      metricQueue.addAll(metric.getBaseMetrics());
                                  }
                                  metricSet.add(metric);
                              }
                          }
                      }

                      /**
                       * Add derived metrics if all associated base metrics are present
                       */
                      private void addEligibleDerivedMetrics(Set<MetricStat> metricStats) {
                          for (MetricStat metric : MetricStat.values()) {
                              if (metric.isDerivedMetric() && !metricStats.contains(metric)) {
                                  List<MetricStat> sourceMetrics = metric.getBaseMetrics();
                                  if (metricStats.containsAll(sourceMetrics)) {
                                      metricStats.add(metric);
                                  }
                              }
                          }
                      }

                      @Override
                      protected List<Parameter<?>> getParameters() {
                          return List.of(config);
                      }

                      private static boolean isBuilderAllowedForDimension(Mapper.Builder builder) {
                          return builder.getSupportedDataCubeDimensionType().isPresent();
                      }

                      private static boolean isBuilderAllowedForMetric(Mapper.Builder builder) {
                          return builder.isDataCubeMetricSupported();
                      }

                      private Optional<Mapper.Builder> findMapperBuilderByName(String name, List<Mapper.Builder> mappersBuilders) {
                          String[] parts = name.split("\\\\.");

                          // Start with the top-level builders
                          Optional<Mapper.Builder> currentBuilder = mappersBuilders.stream()
                              .filter(builder -> builder.name().equals(parts[0]))
                              .findFirst();

                          // If we can't find the first part, or if there's only one part, return the result
                          if (currentBuilder.isEmpty() || parts.length == 1) {
                              return currentBuilder;
                          }
                          // Navigate through the nested structure
                          try {
                              Mapper.Builder builder = currentBuilder.get();
                              for (int i = 1; i < parts.length; i++) {
                                  List<Mapper.Builder> childBuilders = getChildBuilders(builder); // fixme gets removed
                                  int finalI = i;

                                  // First try to find in regular child builders
                                  Optional<Mapper.Builder> nextBuilder = childBuilders.stream().filter(b -> b.name().equals(parts[finalI])).findFirst();

                                  if (nextBuilder.isPresent()) {
                                      builder = nextBuilder.get();
                                  } else {
                                      MultiFields.Builder multiFieldsBuilder = null;
                                      // If not found in regular children, check for multi-fields
                                      if (builder instanceof FieldMapper.Builder<?> fieldBuilder) {
                                          multiFieldsBuilder = fieldBuilder.multiFieldsBuilder;
                                      } else if (builder instanceof ParametrizedFieldMapper.Builder parameterizedFieldBuilder) {
                                          multiFieldsBuilder = parameterizedFieldBuilder.multiFieldsBuilder;
                                      }
                                      if (multiFieldsBuilder != null) {
                                          Map<String, Mapper.Builder> multiFields = multiFieldsBuilder.getMapperBuilders();
                                          Mapper.Builder multiFieldBuilder = multiFields.get(parts[finalI]);
                                          if (multiFieldBuilder != null) {
                                              builder = multiFieldBuilder;
                                              continue;
                                          }
                                      }
                                      throw new IllegalArgumentException(
                                          String.format(Locale.ROOT, "Could not find nested field [%s] in path [%s]", parts[finalI], name)
                                      );
                                  }
                              }
                              return Optional.of(builder);
                          } catch (Exception e) {
                              return Optional.empty();
                          }
                      }

                      // Helper method to get child builders from a parent builder
                      private List<Mapper.Builder> getChildBuilders(Mapper.Builder builder) {
                          if (builder instanceof ObjectMapper.Builder) {
                              return ((ObjectMapper.Builder) builder).mappersBuilders;
                          }
                          return Collections.emptyList();
                      }

                      public Builder(String name, ObjectMapper.Builder objBuilder) {
                          super(name);
                          this.objbuilder = objBuilder;
                      }

                      @Override
                      public ParametrizedFieldMapper build(BuilderContext context) {
                          StarTreeFieldType type = new StarTreeFieldType(name, this.config.get());
                          return new StarTreeMapper(name, type, this, objbuilder);
                      }
                  }

                  private static StarTreeMapper toType(FieldMapper in) {
                      return (StarTreeMapper) in;
                  }

                  /**
                   * Concrete parse for star tree type
                   *
                   * @opensearch.internal
                   */
                  public static class TypeParser implements Mapper.TypeParser {

                      /**
                       * default constructor of VectorFieldMapper.TypeParser
                       */
                      public TypeParser() {}

                      @Override
                      public Mapper.Builder<?> parse(String name, Map<String, Object> node, ParserContext context) throws MapperParsingException {
                          Builder builder = new StarTreeMapper.Builder(name, null);
                          builder.parse(name, context, node);
                          return builder;
                      }

                      @Override
                      public Mapper.Builder<?> parse(String name, Map<String, Object> node, ParserContext context, ObjectMapper.Builder objBuilder)
                          throws MapperParsingException {
                          Builder builder = new StarTreeMapper.Builder(name, objBuilder);
                          builder.parse(name, context, node);
                          return builder;
                      }
                  }

                  private final StarTreeField starTreeField;

                  private final ObjectMapper.Builder objBuilder;

                  protected StarTreeMapper(String simpleName, StarTreeFieldType type, Builder builder, ObjectMapper.Builder objbuilder) {
                      super(simpleName, type, MultiFields.empty(), CopyTo.empty());
                      this.starTreeField = builder.config.get();
                      this.objBuilder = objbuilder;
                  }

                  @Override
                  public StarTreeFieldType fieldType() {
                      return (StarTreeFieldType) super.fieldType();
                  }

                  @Override
                  protected String contentType() {
                      return CONTENT_TYPE;
                  }

                  @Override
                  protected void parseCreateField(ParseContext context) {
                      throw new MapperParsingException(
                          String.format(
                              Locale.ROOT,
                              "Field [%s] is a star tree field and cannot be added inside a document. Use the index API request parameters.",
                              name()
                          )
                      );
                  }

                  /**
                   * Star tree mapped field type containing dimensions, metrics, star tree specs
                   *
                   * @opensearch.experimental
                   */
                  @ExperimentalApi
                  public static final class StarTreeFieldType extends CompositeDataCubeFieldType {

                      private final StarTreeFieldConfiguration starTreeConfig;

                      public StarTreeFieldType(String name, StarTreeField starTreeField) {
                          super(name, starTreeField.getDimensionsOrder(), starTreeField.getMetrics(), CompositeFieldType.STAR_TREE);
                          this.starTreeConfig = starTreeField.getStarTreeConfig();
                      }

                      public StarTreeFieldConfiguration getStarTreeConfig() {
                          return starTreeConfig;
                      }

                      @Override
                      public ValueFetcher valueFetcher(QueryShardContext context, SearchLookup searchLookup, String format) {
                          // TODO : evaluate later
                          throw new UnsupportedOperationException("Cannot fetch values for star tree field [" + name() + "].");
                      }

                      @Override
                      public String typeName() {
                          return CONTENT_TYPE;
                      }

                      @Override
                      public Query termQuery(Object value, QueryShardContext context) {
                          // TODO : evaluate later
                          throw new UnsupportedOperationException("Cannot perform terms query on star tree field [" + name() + "].");
                      }
                  }

              }
              """
          )
        );
    }
}
