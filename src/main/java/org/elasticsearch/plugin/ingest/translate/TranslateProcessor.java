/*
 * Copyright [2018] [Ettore Caprella]
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

import org.elasticsearch.ingest.AbstractProcessor;
import org.elasticsearch.ingest.IngestDocument;
import org.elasticsearch.ingest.Processor;

import java.nio.file.Path;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;

import static org.elasticsearch.ingest.ConfigurationUtils.readBooleanProperty;
import static org.elasticsearch.ingest.ConfigurationUtils.readOptionalStringProperty;
import static org.elasticsearch.ingest.ConfigurationUtils.readStringProperty;
import static org.elasticsearch.ingest.ConfigurationUtils.newConfigurationException;

import com.cronutils.model.Cron;

public class TranslateProcessor extends AbstractProcessor {

  public static final String TYPE = "translate";

  private final String field;
  private final String targetField;
  private final String dictionary;
  private final boolean addToRoot;
  private final boolean ignoreMissing;
  private final boolean multipleMatch;
  private final Translator translator;

  public TranslateProcessor(String tag, String field, String targetField, String dictionary,
                            boolean addToRoot, boolean ignoreMissing, boolean multipleMatch,
                            Translator translator) throws IOException {
    super(tag);
    this.field = field;
    this.targetField = targetField;
    this.translator = translator;
    this.addToRoot = addToRoot;
    this.ignoreMissing = ignoreMissing;
    this.dictionary = dictionary;
    this.multipleMatch = multipleMatch;
  }

  boolean isIgnoreMissing() {
    return ignoreMissing;
  }

  @Override
  public void execute(IngestDocument ingestDocument) throws Exception {
    String content = ingestDocument.getFieldValue(field, String.class, ignoreMissing);

    if (content == null && ignoreMissing) {
      return;
    } else if (content == null) {
      throw new IllegalArgumentException("field [" + field + "] is null, cannot extract information from the dictionary.");
    }

    Object value = translator.lookup(content, multipleMatch);
    if (value == null)
      return;

    if (addToRoot && (value instanceof Map)) {
      for (Map.Entry<String, Object> entry : ((Map<String, Object>) value).entrySet()) {
        ingestDocument.setFieldValue(entry.getKey(), entry.getValue());
      }
    } else if (addToRoot) {
      throw new IllegalArgumentException("cannot add non-map fields to root of document");
    } else {
      ingestDocument.setFieldValue(targetField, value);
    }
  }

  @Override
  public String getType() {
      return TYPE;
  }

  public static final class Factory implements Processor.Factory {

    private final HashMap<String, Translator> translators;
    private final Path translateConfigDirectory;
    private final Cron cron;

    public Factory(Path translateConfigDirectory, Cron cron) {
      this.translators = new HashMap<String, Translator>();
      this.translateConfigDirectory = translateConfigDirectory;
      this.cron = cron;
    }

    @Override
    public TranslateProcessor create(Map<String, Processor.Factory> factories, String tag, Map<String, Object> config)
      throws Exception {
      String field = readStringProperty(TYPE, tag, config, "field");
      String targetField = readOptionalStringProperty(TYPE, tag, config, "target_field");
      String dictionary = readStringProperty(TYPE, tag, config, "dictionary");
      boolean ignoreMissing = readBooleanProperty(TYPE, tag, config, "ignore_missing", false);
      boolean addToRoot = readBooleanProperty(TYPE, tag, config, "add_to_root", false);
      boolean multipleMatch = readBooleanProperty(TYPE, tag, config, "multiple_match", false);
      String translatorType = readStringProperty(TYPE, tag, config, "type", "string");

      if (addToRoot && targetField != null) {
          throw newConfigurationException(TYPE, tag, "target_field",
              "Cannot set a target field while also setting `add_to_root` to true");
      }
      if (targetField == null) {
          targetField = field;
      }

      Translator translator = null;
      synchronized(this) {
        translator = translators.get(dictionary);
        if (translator == null) {
          translator = Translator.Factory.create(translatorType, translateConfigDirectory.resolve(dictionary), cron);
          translators.put(dictionary, translator);
          translator.startMonitoring();
        }
      }
      return new TranslateProcessor(tag, field, targetField, dictionary, addToRoot, ignoreMissing, multipleMatch, translator);
    }
  }
}
