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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.elasticsearch.common.logging.Loggers;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.io.InputStream;
import java.io.IOException;
import java.lang.Thread;
import java.lang.StackTraceElement;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.Lock;

import org.elasticsearch.SpecialPermission;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.security.PrivilegedActionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Map;
import java.util.HashMap;

import java.security.MessageDigest;
import java.security.DigestInputStream;
import java.security.NoSuchAlgorithmException;
import java.math.BigInteger;



final class Translator {
  private static final Logger LOGGER = Loggers.getLogger(Translator.class);

  // Dictionary file attributes
  private final Path dictionaryPath;
  private final Long checkInterval;
  private String md5;

  // The dictionary for the Translator
  private Map<String, Object> dictionary;

  // Monitoring Thread attributes
  private volatile boolean monitoringStarted;
  private Thread monitoringThread;
  private final ReentrantReadWriteLock rwlock = new ReentrantReadWriteLock();
  private final Lock rlock = rwlock.readLock();
  private final Lock wlock = rwlock.writeLock();


  Translator(Path dictionaryPath, Long checkInterval)  throws IOException, NoSuchAlgorithmException {
    LOGGER.info("Creating Translator for [{}]", dictionaryPath.getFileName().toString());

    // Initialize dictionary file attributes
    this.dictionaryPath = dictionaryPath;
    this.checkInterval = checkInterval;
    this.md5 = "";

    // Initialize Monitoring Thread attributes
    this.monitoringStarted = false;
    this.monitoringThread = null;

    // Loading dictionary
    dictionary = null;
    try {
      checkMD5AndLoadDictionary();
    } catch(Exception e) {
      LOGGER.error(() -> new ParameterizedMessage("Failed to create Translator for [{}] with exception",
                                                  dictionaryPath.getFileName().toString()), e);
      throw e;
    }
    LOGGER.info("Translator for [{}] created with {} entries", dictionaryPath.getFileName().toString(), dictionary.size());
  }

  public Object lookup(String item) {
    rlock.lock();
    try {
      if (dictionary == null)
        return null;
      return dictionary.get(item);
    } finally {
      rlock.unlock();
    }
  }

  public void finalize() {
    LOGGER.info("Finalize Translator for [{}]", dictionaryPath.getFileName().toString());
  }

  private void checkMD5AndLoadDictionary() throws IOException, NoSuchAlgorithmException {
    if (Files.exists(dictionaryPath) == false) {
      throw new IllegalStateException(
          "the file [" + dictionaryPath + "] doesn't exist");
    }

    String newmd5 = calculateMD5();
    LOGGER.debug("Check MD5 for [{}]. Current MD5: {}, Checked MD5: {}",
                 dictionaryPath.getFileName().toString(), md5, newmd5);

    if (newmd5.equals(this.md5))
      return;
    loadDictionary();
    this.md5 = newmd5;
  }

  private String calculateMD5() throws IOException, NoSuchAlgorithmException {
    MessageDigest messageDigest = MessageDigest.getInstance("MD5");
    InputStream inputStream = Files.newInputStream(dictionaryPath);
    DigestInputStream digestInputStream = new DigestInputStream(inputStream, messageDigest);
    byte[] buffer = new byte[8192];
    while (digestInputStream.read(buffer) > -1) {}
    MessageDigest digest = digestInputStream.getMessageDigest();
    digestInputStream.close();
    byte[] md5 = digest.digest();
    return new BigInteger(1, md5).toString(16);
  }

  private void loadDictionary() throws IOException {
    wlock.lock();
    try {
      SpecialPermission.check();
      try {
        dictionary = AccessController.doPrivileged((PrivilegedExceptionAction< Map<String, Object> >) () -> {
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

  // Thread methods
  public void startMonitoring() {
    if (monitoringStarted)
      return;

    // This is the Monitoring Thread
    monitoringThread = new Thread(dictionaryPath.getFileName().toString()) {
      public void run() {
        LOGGER.info("Monitoring Thread [{}] started", Thread.currentThread().getName());
        while(!isInterrupted()) {
          try {
            Thread.sleep(checkInterval*1000);
            checkMD5AndLoadDictionary();
          } catch(InterruptedException e) {
              Thread.currentThread().interrupt();
          } catch(Exception e) {}
        }
        LOGGER.info("Monitoring Thread [{}] stopped", Thread.currentThread().getName());
      }
    };

    monitoringThread.start();
    monitoringStarted = true;
  }

  public void stopMonitoring() {
    if ( monitoringStarted && (monitoringThread != null) ) {
      monitoringThread.interrupt();
      try {
        monitoringThread.join();
      } catch(InterruptedException e) { }
      monitoringStarted = false;
      monitoringThread = null;
    }
  }

  public boolean isMonitoringStarted() {
    return monitoringStarted;
  }

}
