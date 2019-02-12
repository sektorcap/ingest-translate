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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.InputStream;
import java.io.IOException;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.Lock;

import java.security.MessageDigest;
import java.security.DigestInputStream;
import java.security.NoSuchAlgorithmException;
import java.math.BigInteger;

import com.cronutils.model.Cron;
import com.cronutils.model.time.ExecutionTime;
import java.time.Duration;
import java.time.ZonedDateTime;




public abstract class Translator {
  protected static final Logger LOGGER = LogManager.getLogger(Translator.class);

  // Dictionary file attributes
  protected final Path dictionaryPath;
  private final Cron cron;
  private String md5;

  // Monitoring Thread attributes
  private volatile boolean monitoringStarted;
  private Thread monitoringThread;
  private final ReentrantReadWriteLock rwlock = new ReentrantReadWriteLock();
  protected final Lock rlock = rwlock.readLock();
  protected final Lock wlock = rwlock.writeLock();


  Translator(Path dictionaryPath, Cron cron)  throws IOException, NoSuchAlgorithmException {
    LOGGER.info("Creating Translator for [{}]", dictionaryPath.getFileName().toString());

    // Initialize dictionary file attributes
    this.dictionaryPath = dictionaryPath;
    this.cron = cron;
    this.md5 = "";

    // Initialize Monitoring Thread attributes
    this.monitoringStarted = false;
    this.monitoringThread = null;

    // Loading dictionary
    try {
      checkMD5AndLoadDictionary();
    } catch(Exception e) {
      LOGGER.error(() -> new ParameterizedMessage("Failed to create Translator for [{}] with exception",
                                                  dictionaryPath.getFileName().toString()), e);
      throw e;
    }
  }

  public Object lookup(String item) {
    return lookup(item, false);
  }
  public abstract Object lookup(String item, boolean retMultipleValue);
  protected abstract void loadDictionary() throws IOException;

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
            ZonedDateTime now = ZonedDateTime.now();
            ExecutionTime executionTime = ExecutionTime.forCron(cron);
            Duration timeToNextExecution = executionTime.timeToNextExecution(now).get();
            Thread.sleep(timeToNextExecution.toMillis());
            checkMD5AndLoadDictionary();
          } catch(InterruptedException e) {
              Thread.currentThread().interrupt();
          } catch(Exception e) {
            LOGGER.error(() -> new ParameterizedMessage("Monitoring Thread [{}] exception",
                                                        Thread.currentThread().getName()), e);
          }
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

  public static final class Factory {
    public static Translator create(String type, Path dictionaryPath, Cron cron) throws IOException, NoSuchAlgorithmException {
      if ("string".equalsIgnoreCase(type)) return new StringTranslator(dictionaryPath, cron);
      if ("ip".equalsIgnoreCase(type))     return new IpTranslator(dictionaryPath, cron);

      throw new IllegalStateException("Invalid translator type: [" + type + "]");
    }
  }

}
