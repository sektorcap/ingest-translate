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

import org.elasticsearch.common.collect.MapBuilder;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.ingest.Processor;
import org.elasticsearch.plugins.IngestPlugin;
import org.elasticsearch.plugins.Plugin;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.nio.file.Path;




public class IngestTranslatePlugin extends Plugin implements IngestPlugin {

  private final Setting<Long> CHECK_INTERVAL_SECONDS = Setting.longSetting("ingest.translate.check_interval", 3600, 0,
                                                                            Setting.Property.NodeScope);

  // al posto dei secondi usare una cron expression per definire quando effettuare il check
  // vedi https://github.com/jmrozanec/cron-utils
  @Override
  public List<Setting<?>> getSettings() {
      return Arrays.asList(CHECK_INTERVAL_SECONDS);
  }
  @Override
  public Map<String, Processor.Factory> getProcessors(Processor.Parameters parameters) {
    Path translateConfigDirectory = parameters.env.configFile().resolve("ingest-translate");
    Long checkInterval = CHECK_INTERVAL_SECONDS.get(parameters.env.settings());

    return MapBuilder.<String, Processor.Factory>newMapBuilder()
            .put(TranslateProcessor.TYPE, new TranslateProcessor.Factory(translateConfigDirectory, checkInterval))
            .immutableMap();
  }

}
