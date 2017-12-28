/**
 * Copyright (C) 2017 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atlasmap.xml.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;

import io.atlasmap.api.AtlasException;
import io.atlasmap.core.DefaultAtlasConversionService;
import io.atlasmap.spi.AtlasInternalSession;
import io.atlasmap.spi.AtlasInternalSession.Head;
import io.atlasmap.v2.AuditStatus;
import io.atlasmap.v2.Audits;
import io.atlasmap.v2.Field;
import io.atlasmap.v2.FieldType;
import io.atlasmap.xml.v2.AtlasXmlModelFactory;
import io.atlasmap.xml.v2.XmlField;

public class XmlFieldWriterTest {

    private XmlFieldWriter writer = null;

    private Document document = null;
    private String seedDocument = null;
    private Map<String, String> namespaces = new HashMap<>();
    private XmlFieldReader reader = new XmlFieldReader(DefaultAtlasConversionService.getInstance());

    @Before
    public void setup() throws Exception {
        this.writer = null;
        this.document = null;
        this.seedDocument = null;
        this.namespaces = new HashMap<>();
    }

    public void createWriter() throws Exception {
        writer = new XmlFieldWriter(namespaces, seedDocument);
        this.document = writer.getDocument();
        assertNotNull(document);
    }

    public void writeValue(String path, Object value) throws Exception {
        if (writer == null) {
            createWriter();
        }
        XmlField xmlField = AtlasXmlModelFactory.createXmlField();
        xmlField.setPath(path);
        xmlField.setValue(value);
        AtlasInternalSession session = mock(AtlasInternalSession.class);
        when(session.head()).thenReturn(mock(Head.class));
        when(session.head().getSourceField()).thenReturn(mock(Field.class));
        when(session.head().getTargetField()).thenReturn(xmlField);
        writer.write(session);
    }

    @Test
    public void testWriteValueToDefaultDocument() throws Exception {
        writeValue("/orders/order/id", "3333333354");
        final String expected = "<orders><order><id>3333333354</id></order></orders>";
        checkResult(expected);
    }

    @Test
    public void testWriteValueToAttributeWithDefaultDocument() throws Exception {
        writeValue("/orders/order/id/@custId", "b");
        final String expected = "<orders><order><id custId=\"b\"/></order></orders>";
        checkResult(expected);
    }

    @Test
    public void testWriteValueWithSeedDocument() throws Exception {
        seedDocument = "<orders/>";

        writeValue("/orders/order/id/@custId", "b");
        writeValue("/orders/order/id", "3333333354");

        final String expected = "<orders><order><id custId=\"b\">3333333354</id></order></orders>";
        checkResult(expected);
    }

    @Test
    public void testWriteValueWithSeedDocumentWithNamespaces() throws Exception {
        seedDocument = "<orders xmlns:x=\"http://www.example.com/x/\"/>";

        writeValue("/orders/order/x:id/@custId", "b");
        writeValue("/orders/order/x:id", "3333333354");

        final String expected = "<orders xmlns:x=\"http://www.example.com/x/\"><order><x:id custId=\"b\">3333333354</x:id></order></orders>";
        checkResult(expected);
    }

    @Test
    public void testWriteValueWithSeedDocumentWithDefaultNamespace() throws Exception {
        seedDocument = "<orders xmlns=\"http://www.example.com/x/\"/>";

        writeValue("/orders/order/id/@custId", "b");
        writeValue("/orders/order/id", "3333333354");

        final String expected = "<orders xmlns=\"http://www.example.com/x/\"><order><id custId=\"b\">3333333354</id></order></orders>";
        checkResult(expected);
    }

    @Test
    public void testWriteValueWithSeedDocumentWithNamespacesAddNamespace() throws Exception {
        seedDocument = "<orders xmlns:x=\"http://www.example.com/x/\"><x:order foo=\"bar\">preexisting</x:order></orders>";
        namespaces.put("y", "http://www.example.com/y/");

        writeValue("/orders/y:order/x:id/@custId", "b");
        writeValue("/orders/y:order/x:id", "3333333354");

        final String expected = "<orders xmlns:x=\"http://www.example.com/x/\" xmlns:y=\"http://www.example.com/y/\"><x:order foo=\"bar\">preexisting</x:order><y:order><x:id custId=\"b\">3333333354</x:id></y:order></orders>";
        checkResult(expected);
    }

    @Test
    public void testWriteValueToDefaultDocumentComplex() throws Exception {
        this.seedDocument = new String(Files.readAllBytes(Paths.get("src/test/resources/complex_example_write.xml")));

        writeValue("/orders/order[2]/id[2]", "54554555");

        checkResultFromFile("complex_example.xml");
    }

    @Test
    public void testWriteNewNodeWithAttrToDocumentComplex() throws Exception {
        this.seedDocument = new String(
                Files.readAllBytes(Paths.get("src/test/resources/complex_example_write_attr.xml")));

        writeValue("/orders/order[2]/id[2]", "54554555");
        writeValue("/orders/order[2]/id[2]/@custId", "c");

        checkResultFromFile("complex_example.xml");
    }

    @Test
    public void testBuildSimpleExampleDocument() throws Exception {
        writeValue("/orders/@totalCost", "12525.00");
        writeValue("/orders/order/id/@custId", "a");
        writeValue("/orders/order/id", "12312");
        writeValue("/orders/order/id[1]/@custId", "b");
        writeValue("/orders/order/id[1]", "4423423");

        checkResultFromFile("simple_example.xml");
    }

    public void checkResultFromFile(String expectedFilename) throws Exception {
        String filename = "src/test/resources/" + expectedFilename;
        String expected = new String(Files.readAllBytes(Paths.get(filename)));
        checkResult(expected);
    }

    public void checkResult(String s) throws Exception {
        String expected = s;
        if (document == null) {
            throw new Exception("document is not initialized.");
        }
        /*
         * Diff diff =
         * DiffBuilder.compare(Input.fromString(expected)).withTest(Input.fromDocument(
         * document)).ignoreWhitespace().build(); assertFalse(diff.toString(),
         * diff.hasDifferences());
         */
        String actual = XmlIOHelper.writeDocumentToString(true, writer.getDocument());
        expected = expected.replaceAll("\n|\r", "");
        expected = expected.replaceAll("> *?<", "><");
        expected = expected.replace("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>", "");

        System.out.println("Expected: " + expected);
        System.out.println("Actual:   " + actual);
        assertEquals(expected, actual);
    }

    @Test
    public void testBuildSimpleExampleDocumentFromSeedWithNamespace() throws Exception {
        namespaces.put("x", "http://www.example.com/x/");

        writeValue("/x:orders/@totalCost", "12525.00");
        writeValue("/x:orders/order/id/@custId", "a");
        writeValue("/x:orders/order/id", "12312");
        writeValue("/x:orders/order/id[1]/@custId", "b");
        writeValue("/x:orders/order/id[1]", "4423423");

        checkResultFromFile("simple_example_single_ns.xml");
    }

    @Test
    public void testBuildSimpleExampleDocumentWithMultipleNamespaces() throws Exception {
        namespaces.put("x", "http://www.example.com/x/");
        namespaces.put("y", "http://www.example.com/y/");

        writeValue("/x:orders/@totalCost", "12525.00");
        writeValue("/x:orders/order/y:id/@custId", "a");
        writeValue("/x:orders/order/y:id", "12312");
        writeValue("/x:orders/order/y:id[1]/@custId", "b");
        writeValue("/x:orders/order/y:id[1]", "4423423");

        checkResultFromFile("simple_example_multiple_ns.xml");
    }

    @Test
    public void testBuildSimpleExampleDocumentWithMultipleNamespacesConstructor() throws Exception {
        namespaces.put("x", "http://www.example.com/x/");
        namespaces.put("y", "http://www.example.com/y/");

        writeValue("/x:orders/@totalCost", "12525.00");
        writeValue("/x:orders/order/y:id/@custId", "a");
        writeValue("/x:orders/order/y:id", "12312");
        writeValue("/x:orders/order/y:id[1]/@custId", "b");
        writeValue("/x:orders/order/y:id[1]", "4423423");

        checkResultFromFile("simple_example_multiple_ns.xml");
    }

    @Test
    public void testBuildSimpleExampleDocumentWithNamespaceSingleFieldAndNS() throws Exception {
        namespaces.put("x", "http://www.example.com/x/");

        writeValue("/x:orders/@totalCost", "12525.00");

        final String expected = "<x:orders xmlns:x=\"http://www.example.com/x/\" totalCost=\"12525.00\"/>";
        checkResult(expected);
    }

    @Test
    public void testBuildDocumentWithMixedParentAttributeNamespaces() throws Exception {
        namespaces.put("", "http://www.example.com/x/");
        namespaces.put("y", "http://www.example.com/y/");

        writeValue("/orders/order/@y:totalCost", "12525.00");

        checkResultFromFile("simple_example_mixed_ns.xml");
    }

    @Test
    public void testBuildComplexNamespaceDuplicateElements() throws Exception {
        namespaces.put("", "http://www.example.com/x/");
        namespaces.put("y", "http://www.example.com/y/");
        namespaces.put("q", "http://www.example.com/q/");

        writeValue("/orders/@totalCost", "12525.00");
        writeValue("/orders/order/id", "a12312");
        writeValue("/orders/order/id/@y:custId", "aa");
        writeValue("/orders/order/id[1]", "b4423423");
        writeValue("/orders/order/id[1]/@y:custId", "bb");

        writeValue("/orders/q:order/id", "c12312");
        writeValue("/orders/q:order/id/@y:custId", "cx");

        writeValue("/orders/order[1]/id", "d54554555");
        writeValue("/orders/order[1]/id/@y:custId", "dc");
        writeValue("/orders/q:order[1]/id", "e12312");
        writeValue("/orders/q:order[1]/id/@y:custId", "ea");

        checkResultFromFile("complex_example_multiple_ns.xml");
    }

    @Test(expected = AtlasException.class)
    public void testThrowExceptionOnNullXmlField() throws Exception {
        createWriter();
        XmlField field = null;
        AtlasInternalSession session = mock(AtlasInternalSession.class);
        when(session.head()).thenReturn(mock(Head.class));
        when(session.head().getSourceField()).thenReturn(mock(Field.class));
        when(session.head().getTargetField()).thenReturn(field);
        writer.write(session);
    }

    private void validateBoundaryValue(FieldType fieldType, Path path, Object testObject) throws Exception {
        validatePrimitiveValue(fieldType, path, testObject, testObject);
    }

    private void validatePrimitiveValue(FieldType fieldType, Path path, Object testObject, Object expectedObject) throws Exception {
        AtlasInternalSession session = readSession(fieldType, path, testObject);

        assertNotNull(session.head().getSourceField().getValue());
        assertEquals(expectedObject, session.head().getSourceField().getValue());
    }

    @Test
    public void testXmlFieldDoubleMax() throws Exception {
        FieldType fieldType = FieldType.DOUBLE;
        Path path = Paths.get("target" + File.separator + "test-write-field-double-max.xml");
        Object testObject = Double.MAX_VALUE;

        validateBoundaryValue(fieldType, path, testObject);
    }

    @Test
    public void testXmlFieldDoubleMin() throws Exception {
        FieldType fieldType = FieldType.DOUBLE;
        Path path = Paths.get("target" + File.separator + "test-write-field-double-min.xml");
        Object testObject = Double.MIN_VALUE;

        validateBoundaryValue(fieldType, path, testObject);
    }

    @Test
    public void testXmlFieldFloatMax() throws Exception {
        FieldType fieldType = FieldType.FLOAT;
        Path path = Paths.get("target" + File.separator + "test-write-field-float-max.xml");
        Object testObject = Float.MAX_VALUE;

        validateBoundaryValue(fieldType, path, testObject);
    }

    @Test
    public void testXmlFieldFloatMin() throws Exception {
        FieldType fieldType = FieldType.FLOAT;
        Path path = Paths.get("target" + File.separator + "test-write-field-float-min.xml");
        Object testObject = Float.MIN_VALUE;

        validateBoundaryValue(fieldType, path, testObject);
    }

    @Test
    public void testXmlFieldLongMax() throws Exception {
        FieldType fieldType = FieldType.LONG;
        Path path = Paths.get("target" + File.separator + "test-write-field-long-max.xml");
        Object testObject = Long.MAX_VALUE;

        validateBoundaryValue(fieldType, path, testObject);
    }

    @Test
    public void testXmlFieldLongMin() throws Exception {
        FieldType fieldType = FieldType.LONG;
        Path path = Paths.get("target" + File.separator + "test-write-field-long-min.xml");
        Object testObject = Long.MIN_VALUE;

        validateBoundaryValue(fieldType, path, testObject);
    }

    @Test
    public void testXmlFieldIntegerMax() throws Exception {
        FieldType fieldType = FieldType.INTEGER;
        Path path = Paths.get("target" + File.separator + "test-write-field-integer-max.xml");
        Object testObject = Integer.MAX_VALUE;

        validateBoundaryValue(fieldType, path, testObject);
    }

    @Test
    public void testXmlFieldIntegerMin() throws Exception {
        FieldType fieldType = FieldType.INTEGER;
        Path path = Paths.get("target" + File.separator + "test-write-field-integer-min.xml");
        Object testObject = Integer.MIN_VALUE;

        validateBoundaryValue(fieldType, path, testObject);
    }

    @Test
    public void testXmlFieldShortMax() throws Exception {
        FieldType fieldType = FieldType.SHORT;
        Path path = Paths.get("target" + File.separator + "test-write-field-short-max.xml");
        Object testObject = Short.MAX_VALUE;

        validateBoundaryValue(fieldType, path, testObject);
    }

    @Test
    public void testXmlFieldShortMin() throws Exception {
        FieldType fieldType = FieldType.SHORT;
        Path path = Paths.get("target" + File.separator + "test-write-field-short-min.xml");
        Object testObject = Short.MIN_VALUE;

        validateBoundaryValue(fieldType, path, testObject);
    }

    @Test
    public void testXmlFieldChar() throws Exception {
        FieldType fieldType = FieldType.CHAR;
        Path path = Paths.get("target" + File.separator + "test-write-field-char.xml");
        Object testObject = '\u0021';

        validateBoundaryValue(fieldType, path, testObject);
    }

    @Test
    public void testXmlFieldByteMax() throws Exception {
        FieldType fieldType = FieldType.BYTE;
        Path path = Paths.get("target" + File.separator + "test-write-field-byte-max.xml");
        Object testObject = Byte.MAX_VALUE;

        validateBoundaryValue(fieldType, path, testObject);
    }

    @Test
    public void testXmlFieldByteMin() throws Exception {
        FieldType fieldType = FieldType.BYTE;
        Path path = Paths.get("target" + File.separator + "test-write-field-byte-min.xml");
        Object testObject = Byte.MIN_VALUE;

        validateBoundaryValue(fieldType, path, testObject);
    }

    @Test
    public void testXmlFieldBooleanTrue() throws Exception {
        FieldType fieldType = FieldType.BOOLEAN;
        Path path = Paths.get("target" + File.separator + "test-write-field-boolean-true.xml");
        Object testObject = Boolean.TRUE;

        validateBoundaryValue(fieldType, path, testObject);
    }

    @Test
    public void testXmlFieldBooleanFalse() throws Exception {
        FieldType fieldType = FieldType.BOOLEAN;
        Path path = Paths.get("target" + File.separator + "test-write-field-boolean-false.xml");
        Object testObject = Boolean.FALSE;

        validateBoundaryValue(fieldType, path, testObject);
    }

    @Test
    public void testXmlFieldBooleanNumber1() throws Exception {
        FieldType fieldType = FieldType.BOOLEAN;
        Path path = Paths.get("target" + File.separator + "test-write-field-boolean-one.xml");
        Object testObject = 1;
        Object expectedObject = Boolean.TRUE;

        validatePrimitiveValue(fieldType, path, testObject, expectedObject);
    }

    @Test
    public void testXmlFieldBooleanNumber0() throws Exception {
        FieldType fieldType = FieldType.BOOLEAN;
        Path path = Paths.get("target" + File.separator + "test-write-field-boolean-zero.xml");
        Object testObject = 0;
        Object expectedObject = Boolean.FALSE;

        validatePrimitiveValue(fieldType, path, testObject, expectedObject);
    }

    @Test
    public void testXmlFieldBooleanLetterT() throws Exception {
        FieldType fieldType = FieldType.BOOLEAN;
        Path path = Paths.get("target" + File.separator + "test-write-field-boolean-letter-T.xml");
        Object testObject = "T";
        Object expectedObject = Boolean.TRUE;

        validatePrimitiveValue(fieldType, path, testObject, expectedObject);
    }

    @Test
    public void testXmlFieldBooleanLetterF() throws Exception {
        FieldType fieldType = FieldType.BOOLEAN;
        Path path = Paths.get("target" + File.separator + "test-write-field-boolean-letter-F.xml");
        Object testObject = "F";
        Object expectedObject = Boolean.FALSE;

        validatePrimitiveValue(fieldType, path, testObject, expectedObject);
    }

    private void writeToFile(String fieldPath, Path path, Object testObject) throws Exception {
        writeValue(fieldPath, testObject.toString());
        String output = XmlIOHelper.writeDocumentToString(true, writer.getDocument());
        Files.write(path, output.getBytes());
    }

    private AtlasInternalSession readFromFile(String fieldPath, FieldType fieldType, Path path) throws Exception {
        String input = new String(Files.readAllBytes(path));
        reader.setDocument(input, false);
        XmlField xmlField = AtlasXmlModelFactory.createXmlField();
        xmlField.setPath(fieldPath);
        xmlField.setPrimitive(Boolean.TRUE);
        xmlField.setFieldType(fieldType);
        assertNull(xmlField.getValue());

        AtlasInternalSession session = mock(AtlasInternalSession.class);
        when(session.head()).thenReturn(mock(Head.class));
        when(session.head().getSourceField()).thenReturn(xmlField);
        Audits audits = new Audits();
        when(session.getAudits()).thenReturn(audits);
        reader.read(session);
        return session;
    }

    @Test
    public void testXmlFieldDoubleMaxRangeOut() throws Exception {
        FieldType fieldType = FieldType.DOUBLE;
        Path path = Paths.get("target" + File.separator + "test-write-field-double-max-range-out.xml");
        Object testObject = "1.7976931348623157E309";

        validateRangeOutValue(fieldType, path, testObject);
    }

    @Test
    public void testXmlFieldDoubleMinRangeOut() throws Exception {
        FieldType fieldType = FieldType.DOUBLE;
        Path path = Paths.get("target" + File.separator + "test-write-field-double-min-range-out.xml");
        Object testObject = "4.9E-325";
        Object expectedObject = 0.0;

        validateRangeOutMinValue(fieldType, path, testObject, expectedObject);
    }

    @Test
    public void testXmlFieldFloatMaxRangeOut() throws Exception {
        FieldType fieldType = FieldType.FLOAT;
        Path path = Paths.get("target" + File.separator + "test-write-field-float-max-range-out.xml");
        Object testObject = "3.4028235E39";

        validateRangeOutValue(fieldType, path, testObject);
    }

    @Test
    public void testXmlFieldFloatMinRangeOut() throws Exception {
        FieldType fieldType = FieldType.FLOAT;
        Path path = Paths.get("target" + File.separator + "test-write-field-float-min-range-out.xml");
        Object testObject = "1.4E-46";
        Object expectedObject = 0.0f;

        validateRangeOutMinValue(fieldType, path, testObject, expectedObject);
    }

    private void validateRangeOutMinValue(FieldType fieldType, Path path, Object testObject, Object expectedObject) throws Exception {
        AtlasInternalSession session = readSession(fieldType, path, testObject);

        assertEquals(expectedObject, session.head().getSourceField().getValue());
        assertEquals(0, session.getAudits().getAudit().size());
    }

    private AtlasInternalSession readSession(FieldType fieldType, Path path, Object testObject) throws Exception {
        String fieldPath = "/primitive/value";
        writeToFile(fieldPath, path, testObject);

        AtlasInternalSession session = readFromFile(fieldPath, fieldType, path);
        return session;
    }

    @Test
    public void testXmlFieldLongMaxRangeOut() throws Exception {
        FieldType fieldType = FieldType.LONG;
        Path path = Paths.get("target" + File.separator + "test-write-field-long-max-range-out.xml");
        Object testObject = "9223372036854775808";

        validateRangeOutValue(fieldType, path, testObject);
    }

    @Test
    public void testXmlFieldLongMinRangeOut() throws Exception {
        FieldType fieldType = FieldType.LONG;
        Path path = Paths.get("target" + File.separator + "test-write-field-long-min-range-out.xml");
        Object testObject = "-9223372036854775809";

        validateRangeOutValue(fieldType, path, testObject);
    }

    @Test
    public void testXmlFieldIntegerMaxRangeOut() throws Exception {
        FieldType fieldType = FieldType.INTEGER;
        Path path = Paths.get("target" + File.separator + "test-write-field-integer-max-range-out.xml");
        Object testObject = Long.MAX_VALUE;

        validateRangeOutValue(fieldType, path, testObject);
    }

    @Test
    public void testXmlFieldIntegerMinRangeOut() throws Exception {
        FieldType fieldType = FieldType.INTEGER;
        Path path = Paths.get("target" + File.separator + "test-write-field-integer-min-range-out.xml");
        Object testObject = Long.MIN_VALUE;

        validateRangeOutValue(fieldType, path, testObject);
    }

    @Test
    public void testXmlFieldShortMaxRangeOut() throws Exception {
        FieldType fieldType = FieldType.SHORT;
        Path path = Paths.get("target" + File.separator + "test-write-field-short-max-range-out.xml");
        Object testObject = Long.MAX_VALUE;

        validateRangeOutValue(fieldType, path, testObject);
    }

    @Test
    public void testXmlFieldShortMinRangeOut() throws Exception {
        FieldType fieldType = FieldType.SHORT;
        Path path = Paths.get("target" + File.separator + "test-write-field-short-min-range-out.xml");
        Object testObject = Long.MIN_VALUE;

        validateRangeOutValue(fieldType, path, testObject);
    }

    @Test
    public void testXmlFieldCharMaxRangeOut() throws Exception {
        FieldType fieldType = FieldType.CHAR;
        Path path = Paths.get("target" + File.separator + "test-write-field-char-max-range-out.xml");
        Object testObject = Long.MAX_VALUE;

        validateRangeOutValue(fieldType, path, testObject);
    }

    @Test
    public void testXmlFieldCharMinRangeOut() throws Exception {
        FieldType fieldType = FieldType.CHAR;
        Path path = Paths.get("target" + File.separator + "test-write-field-char-min-range-out.xml");
        Object testObject = Long.MIN_VALUE;

        validateRangeOutValue(fieldType, path, testObject);
    }

    @Test
    public void testXmlFieldByteMaxRangeOut() throws Exception {
        FieldType fieldType = FieldType.BYTE;
        Path path = Paths.get("target" + File.separator + "test-write-field-byte-max-range-out.xml");
        Object testObject = Long.MAX_VALUE;

        validateRangeOutValue(fieldType, path, testObject);
    }

    @Test
    public void testXmlFieldByteMinRangeOut() throws Exception {
        FieldType fieldType = FieldType.BYTE;
        Path path = Paths.get("target" + File.separator + "test-write-field-byte-min-range-out.xml");
        Object testObject = Long.MIN_VALUE;

        validateRangeOutValue(fieldType, path, testObject);
    }

    @Test
    public void testXmlFieldBooleanRangeOut() throws Exception {
        FieldType fieldType = FieldType.BOOLEAN;
        Path path = Paths.get("target" + File.separator + "test-write-field-boolean-range-out.xml");
        Object testObject = "abcd";
        Object expectedObject = Boolean.TRUE;

        validatePrimitiveValue(fieldType, path, testObject, expectedObject);
    }

    @Test
    public void testXmlFieldBooleanDecimal() throws Exception {
        FieldType fieldType = FieldType.BOOLEAN;
        Path path = Paths.get("target" + File.separator + "test-write-field-boolean-decimal.xml");
        Object testObject = Double.valueOf("126.1234");
        Object expectedObject = Boolean.TRUE;

        validatePrimitiveValue(fieldType, path, testObject, expectedObject);
    }

    @Test
    public void testXmlFieldLongDecimal() throws Exception {
        FieldType fieldType = FieldType.LONG;
        Path path = Paths.get("target" + File.separator + "test-write-field-long-decimal.xml");
        Object testObject = Double.valueOf("126.1234");

        validateRangeOutValue(fieldType, path, testObject);
    }

    @Test
    public void testXmlFieldIntegerDecimal() throws Exception {
        FieldType fieldType = FieldType.INTEGER;
        Path path = Paths.get("target" + File.separator + "test-write-field-integer-decimal.xml");
        Object testObject = Double.valueOf("126.1234");

        validateRangeOutValue(fieldType, path, testObject);
    }

    @Test
    public void testXmlFieldShortDecimal() throws Exception {
        FieldType fieldType = FieldType.SHORT;
        Path path = Paths.get("target" + File.separator + "test-write-field-short-decimal.xml");
        Object testObject = Double.valueOf("126.1234");

        validateRangeOutValue(fieldType, path, testObject);
    }

    @Test
    public void testXmlFieldCharDecimal() throws Exception {
        FieldType fieldType = FieldType.CHAR;
        Path path = Paths.get("target" + File.separator + "test-write-field-char-decimal.xml");
        Object testObject = Double.valueOf("126.1234");

        validateRangeOutValue(fieldType, path, testObject);
    }

    @Test
    public void testXmlFieldByteDecimal() throws Exception {
        FieldType fieldType = FieldType.BYTE;
        Path path = Paths.get("target" + File.separator + "test-write-field-byte-decimal.xml");
        Object testObject = Double.valueOf("126.1234");

        validateRangeOutValue(fieldType, path, testObject);
    }

    @Test
    public void testXmlFieldDoubleString() throws Exception {
        FieldType fieldType = FieldType.DOUBLE;
        Path path = Paths.get("target" + File.separator + "test-write-field-double-string.xml");
        Object testObject = "abcd";

        validateRangeOutValue(fieldType, path, testObject);
    }

    @Test
    public void testXmlFieldFloatString() throws Exception {
        FieldType fieldType = FieldType.FLOAT;
        Path path = Paths.get("target" + File.separator + "test-write-field-float-string.xml");
        Object testObject = "abcd";

        validateRangeOutValue(fieldType, path, testObject);
    }

    @Test
    public void testXmlFieldLongString() throws Exception {
        FieldType fieldType = FieldType.LONG;
        Path path = Paths.get("target" + File.separator + "test-write-field-long-string.xml");
        Object testObject = "abcd";

        validateRangeOutValue(fieldType, path, testObject);
    }

    @Test
    public void testXmlFieldIntegerString() throws Exception {
        FieldType fieldType = FieldType.INTEGER;
        Path path = Paths.get("target" + File.separator + "test-write-field-integer-string.xml");
        Object testObject = "abcd";

        validateRangeOutValue(fieldType, path, testObject);
    }

    @Test
    public void testXmlFieldShortString() throws Exception {
        FieldType fieldType = FieldType.SHORT;
        Path path = Paths.get("target" + File.separator + "test-write-field-short-string.xml");
        Object testObject = "abcd";

        validateRangeOutValue(fieldType, path, testObject);
    }

    @Test
    public void testXmlFieldCharString() throws Exception {
        FieldType fieldType = FieldType.CHAR;
        Path path = Paths.get("target" + File.separator + "test-write-field-char-string.xml");
        Object testObject = "abcd";

        validateRangeOutValue(fieldType, path, testObject);
    }

    @Test
    public void testXmlFieldByteString() throws Exception {
        FieldType fieldType = FieldType.BYTE;
        Path path = Paths.get("target" + File.separator + "test-write-field-byte-string.xml");
        Object testObject = "abcd";

        validateRangeOutValue(fieldType, path, testObject);
    }

    private void validateRangeOutValue(FieldType fieldType, Path path, Object testObject) throws Exception {
        AtlasInternalSession session = readSession(fieldType, path, testObject);

        assertEquals(null, session.head().getSourceField().getValue());
        assertEquals(1, session.getAudits().getAudit().size());
        assertEquals("Failed to convert field value '" + testObject.toString() + "' into type '" + fieldType.value().toUpperCase() + "'", session.getAudits().getAudit().get(0).getMessage());
        assertEquals(testObject.toString(), session.getAudits().getAudit().get(0).getValue());
        assertEquals(AuditStatus.ERROR, session.getAudits().getAudit().get(0).getStatus());
    }

}
