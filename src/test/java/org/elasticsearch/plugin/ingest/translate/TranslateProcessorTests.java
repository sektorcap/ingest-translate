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
import java.util.List;
import java.util.Arrays;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.Charset;



import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;



// Da testare
// le varie combinazioni di paremetri (target_field non presente, ignoreMissing, addToRoot,ecc)
// il thread con la modifica del file
// stop thread



public class TranslateProcessorTests extends ESTestCase {

    private static Path translateConfigDirectory;

    public void testThatProcessorWorks() throws Exception {
        Map<String, Object> document = new HashMap<>();
        String source_field_value = "100.0.111.185";
        document.put("source_field", source_field_value);
        String dictionary = "test.yaml";
        IngestDocument ingestDocument = RandomDocumentPicks.randomIngestDocument(random(), document);

        Path configDir = createTempDir();
        translateConfigDirectory = configDir.resolve("ingest-translate");
        Files.createDirectories(translateConfigDirectory);

        List<String> lines = Arrays.asList(
          "\"100.0.111.185\": \"known attacker\"",
          "\"100.11.12.193\": \"tor exit node\"",
          "\"100.0.111.199\": \"bad reputation\"",
          "\"100.0.111.126\": \"bot, crawler\""
        );
        Files.write(translateConfigDirectory.resolve(dictionary), lines, Charset.forName("UTF-8"));

        Translator translator = new Translator(translateConfigDirectory, dictionary, 10L);

        String tag = randomAlphaOfLength(10);
        TranslateProcessor processor = new TranslateProcessor(tag, "source_field", "target_field", dictionary,
                                                              false, false, translator);
        processor.execute(ingestDocument);
        Map<String, Object> data = ingestDocument.getSourceAndMetadata();

        assertThat(data, hasKey("target_field"));
        assertThat(data.get("target_field"), is("known attacker"));
    }
}
