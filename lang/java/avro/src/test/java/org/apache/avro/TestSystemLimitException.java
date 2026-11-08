/*
 * Copyright 2017 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.avro;

import static org.apache.avro.SystemLimitException.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Test;



public class TestSystemLimitException {

  /** Delegated here for package visibility. */
  public static final int MAX_ARRAY_VM_LIMIT = SystemLimitException.MAX_ARRAY_VM_LIMIT;

  public static final String ERROR_NEGATIVE = "Malformed data. Length is negative: -1";
  public static final String ERROR_VM_LIMIT_BYTES = "Cannot read arrays longer than " + MAX_ARRAY_VM_LIMIT
      + " bytes in Java library";
  public static final String ERROR_VM_LIMIT_COLLECTION = "Cannot read collections larger than " + MAX_ARRAY_VM_LIMIT
      + " items in Java library";
  public static final String ERROR_VM_LIMIT_STRING = "Cannot read strings longer than " + MAX_ARRAY_VM_LIMIT + " bytes";

  /** Delegated here for package visibility. */
  public static void resetLimits() {
    SystemLimitException.resetLimits();
  }

  @After
  public void reset() {
    System.clearProperty(MAX_BYTES_LENGTH_PROPERTY);
    System.clearProperty(MAX_COLLECTION_LENGTH_PROPERTY);
    System.clearProperty(MAX_STRING_LENGTH_PROPERTY);
    resetLimits();
  }

  private interface LongToIntegerFunction {
      int apply(Long value);
  }
  /**
   * A helper method that tests the consistent limit handling from system
   * properties.
   *
   * @param f                The function to be tested.
   * @param sysProperty      The system property used to control the custom limit.
   * @param errorVmLimit     The error message used when the property would be
   *                         over the VM limit.
   * @param errorCustomLimit The error message used when the property would be
   *                         over the custom limit of 1000.
   */
  void helpCheckSystemLimits(LongToIntegerFunction f, String sysProperty, String errorVmLimit,
      String errorCustomLimit) {
    // Correct values pass through
    assertEquals(0, f.apply(0L));
    assertEquals(1024, f.apply(1024L));
    assertEquals(MAX_ARRAY_VM_LIMIT, f.apply((long) MAX_ARRAY_VM_LIMIT));

    // Values that exceed the default system limits throw exceptions
    Exception ex = null;
    try {
      f.apply(Long.MAX_VALUE);
      fail("Expected UnsupportedOperationException to be thrown");
    } catch (UnsupportedOperationException e) {
      ex = e;
    }
    assertEquals(errorVmLimit, ex.getMessage());
    try {
        f.apply((long) MAX_ARRAY_VM_LIMIT + 1);
    } catch (UnsupportedOperationException e) {
        ex = e;
    }
    assertEquals(errorVmLimit, ex.getMessage());
    try {
        f.apply(-1L);
    } catch (AvroRuntimeException e) {
        ex = e;
    }
    assertEquals(ERROR_NEGATIVE, ex.getMessage());

    // Setting the system property to provide a custom limit.
    System.setProperty(sysProperty, Long.toString(1000L));
    resetLimits();

    // Correct values pass through
    assertEquals(0, f.apply(0L));
    assertEquals(102, f.apply(102L));

    // Values that exceed the custom system limits throw exceptions
    try {
        f.apply((long) MAX_ARRAY_VM_LIMIT + 1);
        fail("Expected UnsupportedOperationException to be thrown");
    } catch (UnsupportedOperationException e) {
        ex = e;
    }
    assertEquals(errorVmLimit, ex.getMessage());
    try {
        f.apply(1024L);
    } catch (SystemLimitException e) {
        ex = e;
    }
    assertEquals(errorCustomLimit, ex.getMessage());
    try {
        f.apply(-1L);
    } catch (AvroRuntimeException e) {
        ex = e;
    }
    assertEquals(ERROR_NEGATIVE, ex.getMessage());
  }

  @Test
  public void testCheckMaxBytesLength() {
    helpCheckSystemLimits(new LongToIntegerFunction() {
      public int apply(Long value) {
          return SystemLimitException.checkMaxBytesLength(value);
      }
    }, MAX_BYTES_LENGTH_PROPERTY, ERROR_VM_LIMIT_BYTES, "Bytes length 1024 exceeds maximum allowed");
  }

  @Test
  public void testCheckMaxCollectionLengthFromZero() {
    helpCheckSystemLimits(new LongToIntegerFunction() {
      public int apply(Long value) {
          return SystemLimitException.checkMaxCollectionLength(0L, value);
      }
    }, MAX_COLLECTION_LENGTH_PROPERTY, ERROR_VM_LIMIT_COLLECTION, "Collection length 1024 exceeds maximum allowed");
  }

  @Test
  public void testCheckMaxStringLength() {
    helpCheckSystemLimits(new LongToIntegerFunction() {
      public int apply(Long value) {
          return SystemLimitException.checkMaxStringLength(value);
      }
    }, MAX_STRING_LENGTH_PROPERTY, ERROR_VM_LIMIT_STRING, "String length 1024 exceeds maximum allowed");
  }

  @Test
  public void testCheckMaxCollectionLengthFromNonZero() {
    // Correct values pass through
    assertEquals(10, checkMaxCollectionLength(10L, 0L));
    assertEquals(MAX_ARRAY_VM_LIMIT, checkMaxCollectionLength(10L, MAX_ARRAY_VM_LIMIT - 10L));
    assertEquals(MAX_ARRAY_VM_LIMIT, checkMaxCollectionLength(MAX_ARRAY_VM_LIMIT - 10L, 10L));

    // Values that exceed the default system limits throw exceptions
    Exception ex = null;
    try {
      checkMaxCollectionLength(10L, MAX_ARRAY_VM_LIMIT - 9L);
      fail("Expected UnsupportedOperationException to be thrown");
    } catch (UnsupportedOperationException e) {
      ex = e;
    }
    assertEquals(ERROR_VM_LIMIT_COLLECTION, ex.getMessage());
    try {
      checkMaxCollectionLength(SystemLimitException.MAX_ARRAY_VM_LIMIT - 9L, 10L);
      fail("Expected UnsupportedOperationException to be thrown");
    } catch (UnsupportedOperationException e) {
      ex = e;
    }
    assertEquals(ERROR_VM_LIMIT_COLLECTION, ex.getMessage());

    try {
      checkMaxCollectionLength(10L, Long.MAX_VALUE - 10L);
      fail("Expected UnsupportedOperationException to be thrown");
    } catch (UnsupportedOperationException e) {
      ex = e;
    }
    assertEquals(ERROR_VM_LIMIT_COLLECTION, ex.getMessage());
    try {
      checkMaxCollectionLength(Long.MAX_VALUE - 10L, 10L);
      fail("Expected UnsupportedOperationException to be thrown");
    } catch (UnsupportedOperationException e) {
      ex = e;
    }
    assertEquals(ERROR_VM_LIMIT_COLLECTION, ex.getMessage());

    // Overflow that adds to negative
    try {
      checkMaxCollectionLength(10L, Long.MAX_VALUE);
      fail("Expected UnsupportedOperationException to be thrown");
    } catch (UnsupportedOperationException e) {
      ex = e;
    }
    assertEquals(ERROR_VM_LIMIT_COLLECTION, ex.getMessage());
    try {
      checkMaxCollectionLength(Long.MAX_VALUE, 10L);
      fail("Expected UnsupportedOperationException to be thrown");
    } catch (UnsupportedOperationException e) {
      ex = e;
    }
    assertEquals(ERROR_VM_LIMIT_COLLECTION, ex.getMessage());

    try {
      checkMaxCollectionLength(10L, -1L);
      fail("Expected AvroRuntimeException to be thrown");
    } catch (AvroRuntimeException e) {
      ex = e;
    }
    assertEquals(ERROR_NEGATIVE, ex.getMessage());
    try {
      checkMaxCollectionLength(-1L, 10L);
      fail("Expected AvroRuntimeException to be thrown");
    } catch (AvroRuntimeException e) {
      ex = e;
    }
    assertEquals(ERROR_NEGATIVE, ex.getMessage());

    // Setting the system property to provide a custom limit.
    System.setProperty(MAX_COLLECTION_LENGTH_PROPERTY, Long.toString(1000L));
    resetLimits();

    // Correct values pass through
    assertEquals(10, checkMaxCollectionLength(10L, 0L));
    assertEquals(102, checkMaxCollectionLength(10L, 92L));
    assertEquals(102, checkMaxCollectionLength(92L, 10L));

    // Values that exceed the custom system limits throw exceptions
    try {
      checkMaxCollectionLength(MAX_ARRAY_VM_LIMIT, 1);
      fail("Expected UnsupportedOperationException to be thrown");
    } catch (UnsupportedOperationException e) {
      ex = e;
    }
    assertEquals(ERROR_VM_LIMIT_COLLECTION, ex.getMessage());
    try {
      checkMaxCollectionLength(1, MAX_ARRAY_VM_LIMIT);
      fail("Expected UnsupportedOperationException to be thrown");
    } catch (UnsupportedOperationException e) {
      ex = e;
    }
    assertEquals(ERROR_VM_LIMIT_COLLECTION, ex.getMessage());

    try {
      checkMaxCollectionLength(999, 25);
      fail("Expected SystemLimitException to be thrown");
    } catch (SystemLimitException e) {
      ex = e;
    }
    assertEquals("Collection length 1024 exceeds maximum allowed", ex.getMessage());
    try {
      checkMaxCollectionLength(25, 999);
      fail("Expected SystemLimitException to be thrown");
    } catch (SystemLimitException e) {
      ex = e;
    }
    assertEquals("Collection length 1024 exceeds maximum allowed", ex.getMessage());
  }
}
