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

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.io.InputStream;
import java.io.IOException;

import org.elasticsearch.SpecialPermission;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.security.PrivilegedActionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.net.util.SubnetUtils;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.security.NoSuchAlgorithmException;
import com.cronutils.model.Cron;



final class IpTranslator extends Translator {
  // The dictionary for the Translator
  private Map<SubnetUtils, Object> dictionary;

  IpTranslator(Path dictionaryPath, Cron cron)  throws IOException, NoSuchAlgorithmException {
    super(dictionaryPath, cron);
    if (dictionary == null) {
      throw new IllegalStateException(
          "Unable to create IpTranslator for [" + dictionaryPath + "]");
    } else
      LOGGER.info("Translator for [{}] created with {} entries", dictionaryPath.getFileName().toString(), dictionary.size());
  }


  @Override
  public Object lookup(String item, boolean retMultipleValue) {
    List<Object> multipleValue = new ArrayList<Object>();
    rlock.lock();
    try {
      if (dictionary == null)
        return null;

      for (Map.Entry<SubnetUtils, Object> entry : dictionary.entrySet()) {
        if (entry.getKey().getInfo().isInRange(item))
          if (!retMultipleValue)
            return entry.getValue();
          else
            multipleValue.add(entry.getValue());
      }

      if (!retMultipleValue || multipleValue.size() == 0)
        return null;
      return multipleValue;
    } catch (IllegalArgumentException e) {
      // in case item is not an ip
      return null;
    } finally {
      rlock.unlock();
    }
  }

  @Override
  protected void loadDictionary() throws IOException {
    wlock.lock();
    try {
      SpecialPermission.check();
      Map<SubnetUtils, Object> tmp_dictionary;
      try {
        tmp_dictionary = AccessController.doPrivileged((PrivilegedExceptionAction< Map<SubnetUtils, Object> >) () -> {
          InputStream fileStream = Files.newInputStream(dictionaryPath, StandardOpenOption.READ);
          ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
          return yamlReader.readValue(fileStream, new TypeReference<Map<SubnetUtils,Object>>(){});
        });
      } catch (PrivilegedActionException e) {
        // e.getException() should be an instance of IOException
        // as only checked exceptions will be wrapped in a
        // PrivilegedActionException.
        throw (IOException) e.getException();
      }

      for (Map.Entry<SubnetUtils, Object> entry : tmp_dictionary.entrySet())
        entry.getKey().setInclusiveHostCount(true);
      dictionary = tmp_dictionary;

      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Entries for [{}] are:",dictionaryPath.getFileName().toString());
        for (Map.Entry<SubnetUtils, Object> entry : dictionary.entrySet()) {
          LOGGER.debug("  - {}: {}", entry.getKey().getInfo().getCidrSignature(), entry.getValue());
        }
      }
    } finally {
      wlock.unlock();
    }
  }

}
