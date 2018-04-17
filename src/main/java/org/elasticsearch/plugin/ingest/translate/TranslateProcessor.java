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

import org.elasticsearch.ingest.AbstractProcessor;
import org.elasticsearch.ingest.IngestDocument;
import org.elasticsearch.ingest.Processor;

import java.io.IOException;
import java.util.Map;

import static org.elasticsearch.ingest.ConfigurationUtils.readStringProperty;

public class TranslateProcessor extends AbstractProcessor {

    public static final String TYPE = "translate";

    private final String field;
    private final String targetField;

    public TranslateProcessor(String tag, String field, String targetField) throws IOException {
        super(tag);
        this.field = field;
        this.targetField = targetField;
    }

    @Override
    public void execute(IngestDocument ingestDocument) throws Exception {
        String content = ingestDocument.getFieldValue(field, String.class);
        // TODO implement me!
        ingestDocument.setFieldValue(targetField, content);
    }

    @Override
    public String getType() {
        return TYPE;
    }

    public static final class Factory implements Processor.Factory {

        @Override
        public TranslateProcessor create(Map<String, Processor.Factory> factories, String tag, Map<String, Object> config) 
            throws Exception {
            String field = readStringProperty(TYPE, tag, config, "field");
            String targetField = readStringProperty(TYPE, tag, config, "target_field", "default_field_name");

            return new TranslateProcessor(tag, field, targetField);
        }
    }
}
