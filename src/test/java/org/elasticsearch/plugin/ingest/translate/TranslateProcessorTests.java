/*
 * Copyright [2017] [Ettore Caprella]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.elasticsearch.plugin.ingest.translate;

import org.elasticsearch.ingest.IngestDocument;
import org.elasticsearch.ingest.RandomDocumentPicks;
import org.elasticsearch.test.ESTestCase;

import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.List;
import java.util.Arrays;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.charset.Charset;

import com.cronutils.model.Cron;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;
import static com.cronutils.model.CronType.QUARTZ;

import static org.elasticsearch.ingest.IngestDocumentMatcher.assertIngestDocument;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.hasKey;

public class TranslateProcessorTests extends ESTestCase {

  private static List<String> dictionary_lines = Arrays.asList(
    "\"100.0.111.185\": \"known attacker\"",
    "\"100.11.12.193\": \"tor exit node\"",
    "\"100.0.111.199\": \"bad reputation\"",
    "\"100.0.111.126\": \"bot, crawler\""
  );
  private static List<String> new_dictionary_lines = Arrays.asList(
    "\"1.1.1.1\": \"known attacker\"",
    "\"2.2.2.2\": \"tor exit node\"",
    "\"3.3.3.3\": \"bad reputation\"",
    "\"4.4.4.4\": \"bot, crawler\""
  );
  private static List<String> complex_dictionary_lines = Arrays.asList(
    "test: pippo",
    "ldap:",
    "  host: server1",
    "  port: 636",
    "  base: dc=example,dc=local",
    "  attribute: uid",
    "  ssl: true",
    "  allowed_groups:",
    "    - group1",
    "    - group2",
    "    - group3"
  );

  private Cron cron1sec;

  public TranslateProcessorTests() {
    String strCron1sec = "*/1 * * * * ?";
    CronParser unixCronParser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(QUARTZ));
    cron1sec = unixCronParser.parse(strCron1sec);
  }

  private Path setupDictionary(String dictionary, List<String> lines) throws Exception {
    Path translateConfigDirectory = createTempDir().resolve("ingest-translate");
    Files.createDirectories(translateConfigDirectory);
    Path dictionaryPath = translateConfigDirectory.resolve(dictionary);
    Files.write(dictionaryPath, lines, Charset.forName("UTF-8"));
    return dictionaryPath;
  }

  private void appendLinesToDictionary(Path dictionaryPath, List<String> lines) throws Exception {
    Files.write(dictionaryPath, lines, Charset.forName("UTF-8"), StandardOpenOption.APPEND);
  }

  public void testThatProcessorWorks() throws Exception {
    IngestDocument ingestDocument =
      RandomDocumentPicks.randomIngestDocument(random(), Collections.singletonMap("source_field", "100.0.111.185"));

    String dictionary = "test.yml";
    Path dictionaryPath = setupDictionary(dictionary, dictionary_lines);
    Translator translator = new Translator(dictionaryPath, cron1sec);
    translator.startMonitoring();

    String tag = randomAlphaOfLength(10);
    TranslateProcessor processor = new TranslateProcessor(tag, "source_field", "target_field", dictionary,
                                                          false, false, translator);
    processor.execute(ingestDocument);
    Map<String, Object> data = ingestDocument.getSourceAndMetadata();
    assertThat(data, hasKey("target_field"));
    assertThat(data.get("target_field"), is("known attacker"));

    appendLinesToDictionary(dictionaryPath, new_dictionary_lines);
    Thread.sleep(2000L);

    ingestDocument = RandomDocumentPicks.randomIngestDocument(random(), Collections.singletonMap("source_field", "2.2.2.2"));
    processor.execute(ingestDocument);
    data = ingestDocument.getSourceAndMetadata();
    assertThat(data, hasKey("target_field"));
    assertThat(data.get("target_field"), is("tor exit node"));

    translator.stopMonitoring();
    Thread.sleep(3000L);

    assertThat(translator.isMonitoringStarted(), is(false));
  }

  public void testNoMatch() throws Exception {
    IngestDocument originalIngestDocument =
      RandomDocumentPicks.randomIngestDocument(random(), Collections.singletonMap("source_field", "10.10.10.10"));

    String dictionary = "test.yml";
    Path dictionaryPath = setupDictionary(dictionary, dictionary_lines);
    Translator translator = new Translator(dictionaryPath, cron1sec);

    TranslateProcessor processor = new TranslateProcessor(randomAlphaOfLength(10), "source_field", "target_field", dictionary,
                                                          false, false, translator);
    IngestDocument ingestDocument = new IngestDocument(originalIngestDocument);
    processor.execute(ingestDocument);
    assertIngestDocument(originalIngestDocument, ingestDocument);
  }


  public void testNullValueWithIgnoreMissing() throws Exception {
    IngestDocument originalIngestDocument =
      RandomDocumentPicks.randomIngestDocument(random(), Collections.singletonMap("source_field", null));

    String dictionary = "test.yml";
    Path dictionaryPath = setupDictionary(dictionary, dictionary_lines);
    Translator translator = new Translator(dictionaryPath, cron1sec);

    TranslateProcessor processor = new TranslateProcessor(randomAlphaOfLength(10), "source_field", "target_field", dictionary,
                                                          false, true, translator);
    IngestDocument ingestDocument = new IngestDocument(originalIngestDocument);
    processor.execute(ingestDocument);
    assertIngestDocument(originalIngestDocument, ingestDocument);
  }

  public void testNonExistentWithIgnoreMissing() throws Exception {
    IngestDocument originalIngestDocument =
      RandomDocumentPicks.randomIngestDocument(random(), Collections.emptyMap());

    String dictionary = "test.yml";
    Path dictionaryPath = setupDictionary(dictionary, dictionary_lines);
    Translator translator = new Translator(dictionaryPath, cron1sec);

    TranslateProcessor processor = new TranslateProcessor(randomAlphaOfLength(10), "source_field", "target_field", dictionary,
                                                          false, true, translator);
    IngestDocument ingestDocument = new IngestDocument(originalIngestDocument);
    processor.execute(ingestDocument);
    assertIngestDocument(originalIngestDocument, ingestDocument);
  }

  public void testNullWithoutIgnoreMissing() throws Exception {
    IngestDocument originalIngestDocument =
      RandomDocumentPicks.randomIngestDocument(random(), Collections.singletonMap("source_field", null));

    String dictionary = "test.yml";
    Path dictionaryPath = setupDictionary(dictionary, dictionary_lines);
    Translator translator = new Translator(dictionaryPath, cron1sec);

    TranslateProcessor processor = new TranslateProcessor(randomAlphaOfLength(10), "source_field", "target_field", dictionary,
                                                          false, false, translator);
    IngestDocument ingestDocument = new IngestDocument(originalIngestDocument);
    Exception exception = expectThrows(Exception.class, () -> processor.execute(ingestDocument));
    assertThat(exception.getMessage(), equalTo("field [source_field] is null, cannot extract information from the dictionary."));
  }

  public void testNonExistentWithoutIgnoreMissing() throws Exception {
    IngestDocument ingestDocument =
      RandomDocumentPicks.randomIngestDocument(random(), Collections.emptyMap());

    String dictionary = "test.yml";
    Path dictionaryPath = setupDictionary(dictionary, dictionary_lines);
    Translator translator = new Translator(dictionaryPath, cron1sec);

    TranslateProcessor processor = new TranslateProcessor(randomAlphaOfLength(10), "source_field", "target_field", dictionary,
                                                          false, false, translator);
    Exception exception = expectThrows(Exception.class, () -> processor.execute(ingestDocument));
    assertThat(exception.getMessage(), equalTo("field [source_field] not present as part of path [source_field]"));
  }

  public void testComplexYaml() throws Exception {
    IngestDocument ingestDocument =
      RandomDocumentPicks.randomIngestDocument(random(), Collections.singletonMap("source_field", "ldap"));

    String dictionary = "test.yml";
    Path dictionaryPath = setupDictionary(dictionary, complex_dictionary_lines);
    Translator translator = new Translator(dictionaryPath, cron1sec);

    String tag = randomAlphaOfLength(10);
    TranslateProcessor processor = new TranslateProcessor(tag, "source_field", "target_field", dictionary,
                                                          false, false, translator);
    processor.execute(ingestDocument);
    Map<String, Object> data = ingestDocument.getSourceAndMetadata();
    assertThat(data, hasKey("target_field"));
    assertThat(data.get("target_field") instanceof Map, is(true));
    Map<String, Object> expetedTargetValue = (Map<String, Object>) data.get("target_field");
    assertThat(expetedTargetValue.get("port"), is(636));
  }

  public void testComplexYamlWithAddToRoot() throws Exception {
    IngestDocument ingestDocument =
      RandomDocumentPicks.randomIngestDocument(random(), Collections.singletonMap("source_field", "ldap"));

    String dictionary = "test.yml";
    Path dictionaryPath = setupDictionary(dictionary, complex_dictionary_lines);
    Translator translator = new Translator(dictionaryPath, cron1sec);

    String tag = randomAlphaOfLength(10);
    TranslateProcessor processor = new TranslateProcessor(tag, "source_field", "target_field", dictionary,
                                                          true, false, translator);
    processor.execute(ingestDocument);
    Map<String, Object> data = ingestDocument.getSourceAndMetadata();
    assertThat(data, hasKey("host"));
    assertThat(data.get("host"), is("server1"));
  }

  public void testAddToRootWithoutComplexYaml() throws Exception {
    IngestDocument ingestDocument =
      RandomDocumentPicks.randomIngestDocument(random(), Collections.singletonMap("source_field", "100.0.111.185"));

    String dictionary = "test.yml";
    Path dictionaryPath = setupDictionary(dictionary, dictionary_lines);
    Translator translator = new Translator(dictionaryPath, cron1sec);

    String tag = randomAlphaOfLength(10);
    TranslateProcessor processor = new TranslateProcessor(tag, "source_field", "target_field", dictionary,
                                                          true, false, translator);
    Exception exception = expectThrows(Exception.class, () -> processor.execute(ingestDocument));
    assertThat(exception.getMessage(), equalTo("cannot add non-map fields to root of document"));
  }
}
