/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.apache.avro;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;


public class TestFixed {

  @Test
  public void fixedLengthOutOfLimit() {
    Exception ex = null;
    try {
        Schema.createFixed("oversize", "doc", "space", Integer.MAX_VALUE);
        fail("Expected UnsupportedOperationException to be thrown");
    } catch (UnsupportedOperationException e) {
        // Expected exception
        ex = e;
    }
    assertEquals(TestSystemLimitException.ERROR_VM_LIMIT_BYTES, ex.getMessage());
  }

  @Test
  public void fixedNegativeLength() {
    Exception ex = null;
    try {
        Schema.createFixed("negative", "doc", "space", -1);
        fail("Expected UnsupportedOperationException to be thrown");
    } catch (AvroRuntimeException e) {
        // Expected exception
        ex = e;
    }
    assertEquals(TestSystemLimitException.ERROR_NEGATIVE, ex.getMessage());
  }
}
