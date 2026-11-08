/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.avro.specific;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.avro.FooBarSpecificRecord;
import org.apache.avro.Schema;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.reflect.ReflectDatumReader;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests that classes named by the java-class / java-key-class schema
 * properties are checked against {@link SpecificDatumReader#DENY_LIST}
 * before being instantiated (CVE-2024-47561).
 */
public class TestSpecificDatumReaderDenyList {

  private static final String DENIED_CLASS =
      "javax.management.BadAttributeValueExpException";

  /** A stand-in gadget that records every call of its String constructor. */
  public static class Gadget {
    static int instances = 0;

    public Gadget(String value) {
      instances++;
    }
  }

  @Before
  public void resetGadget() {
    Gadget.instances = 0;
  }

  private static Schema stringSchema(String javaClass) {
    Schema schema = Schema.create(Schema.Type.STRING);
    schema.addProp(SpecificData.CLASS_PROP, javaClass);
    return schema;
  }

  private static Schema mapSchema(String javaKeyClass) {
    Schema schema = Schema.createMap(Schema.create(Schema.Type.STRING));
    schema.addProp(SpecificData.KEY_CLASS_PROP, javaKeyClass);
    return schema;
  }

  private static byte[] encodeString(String value) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
    encoder.writeString(value);
    encoder.flush();
    return out.toByteArray();
  }

  private static byte[] encodeStringMap(String key, String value)
      throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
    encoder.writeMapStart();
    encoder.setItemCount(1);
    encoder.startItem();
    encoder.writeString(key);
    encoder.writeString(value);
    encoder.writeMapEnd();
    encoder.flush();
    return out.toByteArray();
  }

  private static Object read(DatumReader<Object> reader, byte[] bytes)
      throws IOException {
    return reader.read(null, DecoderFactory.get().binaryDecoder(bytes, null));
  }

  private static void assertForbidden(DatumReader<Object> reader, byte[] bytes)
      throws IOException {
    try {
      Object result = read(reader, bytes);
      fail("Expected SecurityException, but read returned " + result);
    } catch (SecurityException expected) {
      assertTrue(expected.getMessage(),
          expected.getMessage().contains("Forbidden"));
    }
  }

  @Test
  public void testDeniedClassDefaultConstructor() throws IOException {
    SpecificDatumReader<Object> reader = new SpecificDatumReader<Object>();
    reader.setSchema(stringSchema(DENIED_CLASS));
    assertForbidden(reader, encodeString("payload"));
  }

  @Test
  public void testDeniedClassSpecificDataConstructor() throws IOException {
    SpecificDatumReader<Object> reader =
        new SpecificDatumReader<Object>(SpecificData.get());
    reader.setSchema(stringSchema(DENIED_CLASS));
    assertForbidden(reader, encodeString("payload"));
  }

  @Test
  public void testDeniedClassSchemaConstructors() throws IOException {
    Schema schema = stringSchema(DENIED_CLASS);
    assertForbidden(new SpecificDatumReader<Object>(schema),
        encodeString("payload"));
    assertForbidden(new SpecificDatumReader<Object>(schema, schema),
        encodeString("payload"));
    assertForbidden(
        new SpecificDatumReader<Object>(schema, schema, SpecificData.get()),
        encodeString("payload"));
  }

  @Test
  public void testDeniedClassReflectDatumReader() throws IOException {
    assertForbidden(new ReflectDatumReader<Object>(stringSchema(DENIED_CLASS)),
        encodeString("payload"));
  }

  @Test
  public void testDeniedMapKeyClass() throws IOException {
    SpecificDatumReader<Object> reader =
        new SpecificDatumReader<Object>(mapSchema(DENIED_CLASS));
    assertForbidden(reader, encodeStringMap("key", "value"));
  }

  @Test
  public void testDeniedUrlClassLoader() throws IOException {
    SpecificDatumReader<Object> reader = new SpecificDatumReader<Object>(
        stringSchema("java.net.URLClassLoader"));
    assertForbidden(reader, encodeString("http://attacker.example/"));
  }

  @Test
  public void testDeniedPackageEntry() {
    SpecificDatumReader<Object> reader = new SpecificDatumReader<Object>();
    assertTrue(reader.getDenyList().contains("org.mozilla.javascript"));
    assertTrue(reader.isDenied("org.mozilla.javascript"));
    assertTrue(reader.isDenied("org.mozilla.javascript.Context"));
    assertTrue(reader.isDenied("org.mozilla.javascript.tools.shell.Main"));
    assertFalse(reader.isDenied("org.mozilla.javascriptx.Context"));
    assertTrue(reader.isDenied(DENIED_CLASS));
    assertTrue(reader.isDenied("java.net.URLClassLoader"));
    // the deny list holds fully qualified names, not simple names
    assertFalse(reader.isDenied("com.example.URLClassLoader"));
    assertFalse(reader.isDenied("java.math.BigDecimal"));
    assertFalse(reader.isDenied("java.lang.String"));
    assertFalse(reader.isDenied(null));
  }

  @Test
  public void testDeniedPackageEntryEndToEnd() throws IOException {
    SpecificDatumReader<Object> reader = new SpecificDatumReader<Object>(
        stringSchema("java.math.BigDecimal"));
    reader.getDenyList().add("java.math");
    assertForbidden(reader, encodeString("1.5"));
  }

  @Test
  public void testDenyListPopulatedForEveryConstructor() {
    Schema schema = stringSchema("java.math.BigDecimal");
    List<String> expected = Arrays.asList(SpecificDatumReader.DENY_LIST);
    assertEquals(expected, new SpecificDatumReader<Object>().getDenyList());
    assertEquals(expected,
        new SpecificDatumReader<FooBarSpecificRecord>(FooBarSpecificRecord.class)
            .getDenyList());
    assertEquals(expected, new SpecificDatumReader<Object>(schema).getDenyList());
    assertEquals(expected,
        new SpecificDatumReader<Object>(schema, schema).getDenyList());
    assertEquals(expected, new SpecificDatumReader<Object>(
        schema, schema, SpecificData.get()).getDenyList());
    assertEquals(expected,
        new SpecificDatumReader<Object>(SpecificData.get()).getDenyList());
  }

  @Test
  public void testStringClassConstructorIsInvokedWhenNotDenied()
      throws IOException {
    // The attack vector: java-class makes the reader call an arbitrary
    // class's String constructor with attacker-controlled data.
    SpecificDatumReader<Object> reader = new SpecificDatumReader<Object>(
        stringSchema(Gadget.class.getName()));
    Object result = read(reader, encodeString("payload"));
    assertTrue(result instanceof Gadget);
    assertEquals(1, Gadget.instances);
  }

  @Test
  public void testDeniedStringClassConstructorIsNotInvoked()
      throws IOException {
    SpecificDatumReader<Object> reader = new SpecificDatumReader<Object>(
        stringSchema(Gadget.class.getName()));
    reader.getDenyList().add(Gadget.class.getName());
    assertForbidden(reader, encodeString("payload"));
    assertEquals(0, Gadget.instances);
  }

  @Test
  public void testAllowedBigDecimal() throws IOException {
    SpecificDatumReader<Object> reader = new SpecificDatumReader<Object>(
        stringSchema("java.math.BigDecimal"));
    assertEquals(new BigDecimal("1.5"), read(reader, encodeString("1.5")));

    SpecificDatumReader<Object> dataReader =
        new SpecificDatumReader<Object>(SpecificData.get());
    dataReader.setSchema(stringSchema("java.math.BigDecimal"));
    assertEquals(new BigDecimal("1.5"), read(dataReader, encodeString("1.5")));
  }

  @Test
  public void testAllowedMapKeyClass() throws IOException {
    SpecificDatumReader<Object> reader = new SpecificDatumReader<Object>(
        mapSchema("java.math.BigDecimal"));
    Map<?, ?> result = (Map<?, ?>) read(reader, encodeStringMap("2.5", "v"));
    assertEquals(1, result.size());
    assertTrue(result.containsKey(new BigDecimal("2.5")));
  }
}
