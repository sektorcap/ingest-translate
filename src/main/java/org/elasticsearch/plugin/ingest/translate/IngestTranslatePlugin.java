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
import java.util.function.Function;

import com.cronutils.model.Cron;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;
import static com.cronutils.model.CronType.QUARTZ;



public class IngestTranslatePlugin extends Plugin implements IngestPlugin {

  private final Setting<String> CRON_CHECK = new Setting<>("ingest.translate.cron_check", "* 0 * * * ?",
                                                           Function.identity(), Setting.Property.NodeScope);

  @Override
  public List<Setting<?>> getSettings() {
      return Arrays.asList(CRON_CHECK);
  }
  @Override
  public Map<String, Processor.Factory> getProcessors(Processor.Parameters parameters) {
    Path translateConfigDirectory = parameters.env.configFile().resolve("ingest-translate");
    String cronCheck = CRON_CHECK.get(parameters.env.settings());


    CronParser unixCronParser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(QUARTZ));
    Cron parsedUnixCronExpression = unixCronParser.parse(cronCheck);

    return MapBuilder.<String, Processor.Factory>newMapBuilder()
            .put(TranslateProcessor.TYPE, new TranslateProcessor.Factory(translateConfigDirectory, parsedUnixCronExpression))
            .immutableMap();
  }

}
