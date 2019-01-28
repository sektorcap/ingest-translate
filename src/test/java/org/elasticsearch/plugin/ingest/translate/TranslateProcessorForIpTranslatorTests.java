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

import java.util.Map;
import java.util.Collections;
import java.util.List;
import java.util.Arrays;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.charset.Charset;

import com.cronutils.model.Cron;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;
import static com.cronutils.model.CronType.QUARTZ;

import static org.elasticsearch.ingest.IngestDocumentMatcher.assertIngestDocument;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.hasKey;

public class TranslateProcessorForIpTranslatorTests extends ESTestCase {

  private static List<String> dictionary_lines = Arrays.asList(
    "13.113.128.0/17: NET 1",
    "13.115.128.0/17: NET 2",
    "13.117.0.0/17: NET 3",
    "13.119.0.0/17: NET 4"
  );
  private static List<String> new_dictionary_lines = Arrays.asList(
    "13.120.128.0/18: WI-FI",
    "13.121.0.0/16: LAN",
    "15.140.100.0/22: VPN"
  );
  private static List<String> complex_dictionary_lines = Arrays.asList(
    "10.10.22.0/24: Be-Secure",
    "10.11.28.0/24:",
    "  gateway: gw.lab2.it",
    "  label: Ingest Lab 2"
  );
  private static List<String> multiple_match_complex_dictionary_lines = Arrays.asList(
    "10.10.0.0/16:",
    "  label: Internal Net",
    "10.10.22.0/24:",
    "  label: Ingest Lab 1",
    "10.10.22.1/32:",
    "  host: gw.lab1.it",
    "  label: GW for Ingest Lab 1"
  );

  private Cron cron1sec;

  public TranslateProcessorForIpTranslatorTests() {
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
      RandomDocumentPicks.randomIngestDocument(random(), Collections.singletonMap("source_field", "13.115.128.5"));

    String dictionary = "test.yml";
    Path dictionaryPath = setupDictionary(dictionary, dictionary_lines);
    Translator translator = new IpTranslator(dictionaryPath, cron1sec);
    translator.startMonitoring();

    String tag = randomAlphaOfLength(10);
    TranslateProcessor processor = new TranslateProcessor(tag, "source_field", "target_field", dictionary,
                                                          false, false, false, translator);
    Map<String, Object> data = processor.execute(ingestDocument).getSourceAndMetadata();
    assertThat(data, hasKey("target_field"));
    assertThat(data.get("target_field"), is("NET 2"));

    appendLinesToDictionary(dictionaryPath, new_dictionary_lines);
    Thread.sleep(2000L);

    ingestDocument = RandomDocumentPicks.randomIngestDocument(random(), Collections.singletonMap("source_field", "13.121.1.1"));
    data = processor.execute(ingestDocument).getSourceAndMetadata();
    assertThat(data, hasKey("target_field"));
    assertThat(data.get("target_field"), is("LAN"));

    translator.stopMonitoring();
    Thread.sleep(3000L);

    assertThat(translator.isMonitoringStarted(), is(false));
  }

  public void testNoMatch() throws Exception {
    IngestDocument originalIngestDocument =
      RandomDocumentPicks.randomIngestDocument(random(), Collections.singletonMap("source_field", "192.168.1.1"));

    String dictionary = "test.yml";
    Path dictionaryPath = setupDictionary(dictionary, dictionary_lines);
    Translator translator = new StringTranslator(dictionaryPath, cron1sec);

    TranslateProcessor processor = new TranslateProcessor(randomAlphaOfLength(10), "source_field", "target_field", dictionary,
                                                          false, false, false, translator);
    IngestDocument ingestDocument = new IngestDocument(originalIngestDocument);
    processor.execute(ingestDocument);
    assertIngestDocument(originalIngestDocument, ingestDocument);
  }

  public void testNullValueWithIgnoreMissing() throws Exception {
    IngestDocument originalIngestDocument =
      RandomDocumentPicks.randomIngestDocument(random(), Collections.singletonMap("source_field", null));

    String dictionary = "test.yml";
    Path dictionaryPath = setupDictionary(dictionary, dictionary_lines);
    Translator translator = new IpTranslator(dictionaryPath, cron1sec);

    TranslateProcessor processor = new TranslateProcessor(randomAlphaOfLength(10), "source_field", "target_field", dictionary,
                                                          false, true, false, translator);
    IngestDocument ingestDocument = new IngestDocument(originalIngestDocument);
    processor.execute(ingestDocument);
    assertIngestDocument(originalIngestDocument, ingestDocument);
  }

  public void testNonExistentWithIgnoreMissing() throws Exception {
    IngestDocument originalIngestDocument =
      RandomDocumentPicks.randomIngestDocument(random(), Collections.emptyMap());

    String dictionary = "test.yml";
    Path dictionaryPath = setupDictionary(dictionary, dictionary_lines);
    Translator translator = new IpTranslator(dictionaryPath, cron1sec);

    TranslateProcessor processor = new TranslateProcessor(randomAlphaOfLength(10), "source_field", "target_field", dictionary,
                                                          false, true, false, translator);
    IngestDocument ingestDocument = new IngestDocument(originalIngestDocument);
    processor.execute(ingestDocument);
    assertIngestDocument(originalIngestDocument, ingestDocument);
  }

  public void testNullWithoutIgnoreMissing() throws Exception {
    IngestDocument originalIngestDocument =
      RandomDocumentPicks.randomIngestDocument(random(), Collections.singletonMap("source_field", null));

    String dictionary = "test.yml";
    Path dictionaryPath = setupDictionary(dictionary, dictionary_lines);
    Translator translator = new IpTranslator(dictionaryPath, cron1sec);

    TranslateProcessor processor = new TranslateProcessor(randomAlphaOfLength(10), "source_field", "target_field", dictionary,
                                                          false, false, false, translator);
    IngestDocument ingestDocument = new IngestDocument(originalIngestDocument);
    Exception exception = expectThrows(Exception.class, () -> processor.execute(ingestDocument));
    assertThat(exception.getMessage(), equalTo("field [source_field] is null, cannot extract information from the dictionary."));
  }

  public void testNonExistentWithoutIgnoreMissing() throws Exception {
    IngestDocument ingestDocument =
      RandomDocumentPicks.randomIngestDocument(random(), Collections.emptyMap());

    String dictionary = "test.yml";
    Path dictionaryPath = setupDictionary(dictionary, dictionary_lines);
    Translator translator = new IpTranslator(dictionaryPath, cron1sec);

    TranslateProcessor processor = new TranslateProcessor(randomAlphaOfLength(10), "source_field", "target_field", dictionary,
                                                          false, false, false, translator);
    Exception exception = expectThrows(Exception.class, () -> processor.execute(ingestDocument));
    assertThat(exception.getMessage(), equalTo("field [source_field] not present as part of path [source_field]"));
  }

  public void testComplexYaml() throws Exception {
    IngestDocument ingestDocument =
      RandomDocumentPicks.randomIngestDocument(random(), Collections.singletonMap("source_field", "10.11.28.1"));

    String dictionary = "test.yml";
    Path dictionaryPath = setupDictionary(dictionary, complex_dictionary_lines);
    Translator translator = new IpTranslator(dictionaryPath, cron1sec);

    String tag = randomAlphaOfLength(10);
    TranslateProcessor processor = new TranslateProcessor(tag, "source_field", "target_field", dictionary,
                                                          false, false, false, translator);
    Map<String, Object> data = processor.execute(ingestDocument).getSourceAndMetadata();
    assertThat(data, hasKey("target_field"));
    assertThat(data.get("target_field") instanceof Map, is(true));
    Map<String, Object> expetedTargetValue = (Map<String, Object>) data.get("target_field");
    assertThat(expetedTargetValue.get("gateway"), is("gw.lab2.it"));
    assertThat(expetedTargetValue.get("label"), is("Ingest Lab 2"));
  }

  public void testComplexYamlWithAddToRoot() throws Exception {
    IngestDocument ingestDocument =
      RandomDocumentPicks.randomIngestDocument(random(), Collections.singletonMap("source_field", "10.11.28.1"));

    String dictionary = "test.yml";
    Path dictionaryPath = setupDictionary(dictionary, complex_dictionary_lines);
    Translator translator = new IpTranslator(dictionaryPath, cron1sec);

    String tag = randomAlphaOfLength(10);
    TranslateProcessor processor = new TranslateProcessor(tag, "source_field", "target_field", dictionary,
                                                          true, false, false, translator);
    Map<String, Object> data = processor.execute(ingestDocument).getSourceAndMetadata();
    assertThat(data, hasKey("gateway"));

    assertThat(data, hasKey("gateway"));
    assertThat(data.get("gateway"), is("gw.lab2.it"));
  }

  public void testAddToRootWithoutComplexYaml() throws Exception {
    IngestDocument ingestDocument =
      RandomDocumentPicks.randomIngestDocument(random(), Collections.singletonMap("source_field", "10.10.22.5"));

    String dictionary = "test.yml";
    Path dictionaryPath = setupDictionary(dictionary, complex_dictionary_lines);
    Translator translator = new IpTranslator(dictionaryPath, cron1sec);

    String tag = randomAlphaOfLength(10);
    TranslateProcessor processor = new TranslateProcessor(tag, "source_field", "target_field", dictionary,
                                                          true, false, false, translator);
    Exception exception = expectThrows(Exception.class, () -> processor.execute(ingestDocument));
    assertThat(exception.getMessage(), equalTo("cannot add non-map fields to root of document"));
  }

  public void testMultipleMatch() throws Exception {
    IngestDocument ingestDocument =
      RandomDocumentPicks.randomIngestDocument(random(), Collections.singletonMap("source_field", "10.10.22.1"));

    String dictionary = "test.yml";
    Path dictionaryPath = setupDictionary(dictionary, multiple_match_complex_dictionary_lines);
    Translator translator = new IpTranslator(dictionaryPath, cron1sec);

    String tag = randomAlphaOfLength(10);
    TranslateProcessor processor = new TranslateProcessor(tag, "source_field", "target_field", dictionary,
                                                          false, false, true, translator);
    Map<String, Object> data = processor.execute(ingestDocument).getSourceAndMetadata();
    assertThat(data, hasKey("target_field"));
    assertThat(data.get("target_field") instanceof List, is(true));
    List<Map<String, Object>> expectedTargetValue = (List<Map<String, Object>>) data.get("target_field");
    assertThat(expectedTargetValue.get(0).get("label"), is("Internal Net"));
    assertThat(expectedTargetValue.get(1).get("label"), is("Ingest Lab 1"));
    assertThat(expectedTargetValue.get(2).get("label"), is("GW for Ingest Lab 1"));
    assertThat(expectedTargetValue.get(2).get("host"),  is("gw.lab1.it"));
  }
}
