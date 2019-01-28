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
import java.util.Map;
import java.util.TreeMap;
import java.security.NoSuchAlgorithmException;
import com.cronutils.model.Cron;




final class StringTranslator extends Translator {
  // The dictionary for the Translator
  private Map<String, Object> dictionary;

  StringTranslator(Path dictionaryPath, Cron cron)  throws IOException, NoSuchAlgorithmException {
    super(dictionaryPath, cron);
    if (dictionary == null) {
      throw new IllegalStateException(
          "Unable to create StringTranslator for [" + dictionaryPath + "]");
    } else
      LOGGER.info("Translator for [{}] created with {} entries", dictionaryPath.getFileName().toString(), dictionary.size());
  }


  @Override
  public Object lookup(String item, boolean retMultipleValue) {
    rlock.lock();
    try {
      if (dictionary == null)
        return null;
      return dictionary.get(item);
    } finally {
      rlock.unlock();
    }
  }

  @Override
  protected void loadDictionary() throws IOException {
    wlock.lock();
    try {
      SpecialPermission.check();
      Map<String, Object> tmp_dictionary;
      try {
        tmp_dictionary = AccessController.doPrivileged((PrivilegedExceptionAction< Map<String, Object> >) () -> {
          InputStream fileStream = Files.newInputStream(dictionaryPath, StandardOpenOption.READ);
          ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
          return yamlReader.readValue(fileStream, new TypeReference<Map<String,Object>>(){});
        });
      } catch (PrivilegedActionException e) {
        // e.getException() should be an instance of IOException
        // as only checked exceptions will be wrapped in a
        // PrivilegedActionException.
        throw (IOException) e.getException();
      }

      dictionary = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
      dictionary.putAll(tmp_dictionary);

      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Entries for [{}] are:",dictionaryPath.getFileName().toString());
        for (Map.Entry<String, Object> entry : dictionary.entrySet()) {
          LOGGER.debug("  - {}: {}", entry.getKey(), entry.getValue());
        }
      }
    } finally {
      wlock.unlock();
    }
  }

}
